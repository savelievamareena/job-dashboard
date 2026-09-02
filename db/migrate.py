#!/usr/bin/env python3
"""Loads the dashboard's file data into Postgres.

One row per posting, keyed by the LinkedIn id out of its URL - except for justjoin.it and
nofluffjobs, whose id cannot be read off the URL and is resolved against the row portal_search.py
already wrote (see emit_vacancy_upsert). A posting seen again on a later day updates its row
rather than adding one, so the charts count a find once however many days it kept showing up. is_selected is raised by the rows that reached selected.csv and is never
lowered here — a posting that drops out of the folders keeps the decision made about it.

The board's fields are filled the way the file readers used to read them, so the flagged rows
reproduce what /api/vacancies served before the board moved onto this database.

job_status is not loaded here and must not be. The board writes its marks straight to that table
now, so a truncate-and-refill from _status.json would throw away everything marked since the last
import. The file is no longer read at all.

Emits SQL on stdout instead of connecting itself, so it needs no driver installed and the load
can be read before it lands:

    python3 db/migrate.py > /tmp/data.sql
    docker compose exec -T db psql -U postgres -d jobdashboard -v ON_ERROR_STOP=1 < /tmp/data.sql

One transaction, upsert throughout: running it twice is safe and deletes nothing.
"""

import csv
import json
import os
import re
import sys
from pathlib import Path, PurePosixPath
from urllib.parse import urlparse

# Mirrors backend/src/main/resources/application.yml. Kept here rather than parsed out of it:
# this runs once per import, and a yaml parser is not worth adding for four constants.
ROOTS = ["DailySearch", "PortalSearch"]
# The board's source label follows the posting's own domain, not the folder it was parsed
# from: a jjit mirror caught through the LinkedIn scan still lives on justjoin.it.
DOMAIN_SOURCES = {"linkedin.com": "linkedin", "justjoin.it": "jjit", "nofluffjobs.com": "nfj"}
# The tracks a picked posting may reach the board from, which is the two that name a CV core and
# no more. other-stacks and unsorted were listed here until 2026-08-11 and never once carried a
# pick: nothing was removed from the board by dropping them, and nothing about the charts changes
# either, because every row still loads and the trend views count what was found rather than what
# was picked. What it stops is a selection run in a track no CV answers, which would put a posting
# on the board that the tailoring step cannot act on. /select-jobs no longer offers those tracks.
BOARD_TRACKS = ["frontend", "fullstack"]
CV_ROOT = "cv-tailored/Poland"
CV_CORES = ["Frontend", "Fullstack"]

DATE_FOLDER = re.compile(r"\d{4}-\d{2}-\d{2}")

# The skills write the runtime, not the language. A chart asking "which language" must not have
# to know that, so the mapping happens here and the column holds the answer.
LANGUAGE = {"node": "javascript", "nodejs": "javascript", "typescript": "javascript",
            "js": "javascript", "ts": "javascript", "dotnet": "csharp", "c#": "csharp",
            ".net": "csharp",
            # Kept identical to DB_LANGUAGE in li_search.py, which explains why react is here:
            # it is the frontend folder's label, not a language, and unmapped it drew a curve
            # Statistics.tsx does not plot at all.
            "react": "javascript"}
# This map only RENAMES; it is read as LAYER.get(value, value), so anything absent passes through
# unchanged. That is why `fullstack` and `back-ops` are not listed - the skill and the database
# spell them the same way - while `front`, `back` and the skill's short `ops` are. "unknown" and
# "unsorted" both mean the skill could not tell, and both map to empty: undetermined is NULL in
# this column and in `track` since her 2026-09-01 migration ("чтобы не было путаницы"; before
# that both carried the sentinel 'unsorted').
# The five layers that reach the chart: frontend, backend, fullstack, devops, back-ops.
LAYER = {"front": "frontend", "back": "backend", "ops": "devops", "unknown": "", "unsorted": ""}


def dashboard_root() -> Path:
    """DASHBOARD_ROOT from the environment, or from the .env the app reads."""
    value = os.environ.get("DASHBOARD_ROOT")
    if not value:
        env = Path(__file__).resolve().parent.parent / ".env"
        if env.is_file():
            for line in env.read_text(encoding="utf-8").splitlines():
                key, sep, raw = line.partition("=")
                if sep and key.strip() == "DASHBOARD_ROOT":
                    value = raw.strip().strip('"').strip("'")
    if not value:
        sys.exit("DASHBOARD_ROOT is not set: put it in .env at the repository root, or export it.")
    return Path(value)


# --- the readers, field for field with the Java ones -------------------------------------------

def text(record: dict, key: str) -> str:
    """Jackson's asText("") plus the app's rule that Python wrote its None out as literal text."""
    value = record.get(key)
    if value is None:
        as_text = ""
    elif isinstance(value, bool):
        as_text = "true" if value else "false"
    elif isinstance(value, (int, float, str)):
        as_text = str(value)
    else:
        as_text = ""  # asText on an array or object gives the default, not its contents
    as_text = as_text.strip()
    return "" if as_text == "None" else as_text


def first_non_blank(*values):
    for value in values:
        if value and value.strip():
            return value
    return None


def applicant_count(record: dict):
    """How many have applied, or None where the record does not actually say.

    "applies" is a placeholder, not a count. The newer scrape writes it on every posting and it
    is zero on all 184 cached records that carry it, while the older "applicants" holds the real
    text ("80 applicants"). Read literally, a posting seen again under the newer scrape has its
    real count overwritten by a zero, because the merge below takes the newest sighting that says
    anything. So a zero here counts as saying nothing.
    """
    for key in ("applicants", "applies"):
        value = text(record, key)
        if value and value.strip() and value.strip() not in ("0", "0.0"):
            return value
    return None


def column(row, name: str) -> str:
    if not row:
        return ""
    value = row.get(name)
    return value.strip() if value else ""


def easy_apply(record: dict):
    """None keeps "not known" apart from a known "no"; a string is read as Boolean.parseBoolean."""
    value = record.get("easy_apply")
    if isinstance(value, bool):
        return value
    if isinstance(value, str) and value.strip():
        return value.lower() == "true"
    return None


def job_id(url: str) -> str:
    trimmed = url[:-1] if url.endswith("/") else url
    cut = trimmed.rfind("/")
    return trimmed if cut < 0 else trimmed[cut + 1:]


def read_csv(path: Path) -> list:
    if not path.is_file():
        return []
    try:
        with path.open(encoding="utf-8", newline="") as handle:
            return list(csv.DictReader(handle, skipinitialspace=True))
    except (OSError, csv.Error) as error:
        # One malformed file must not hide every other day on the board.
        print(f"cannot read {path}: {error}", file=sys.stderr)
        return []


def read_json(path: Path) -> dict:
    if not path.is_file():
        return {}
    try:
        loaded = json.loads(path.read_text(encoding="utf-8"))
        return loaded if isinstance(loaded, dict) else {}
    except (OSError, ValueError) as error:
        print(f"cannot read {path}: {error}", file=sys.stderr)
        return {}


def by_url(rows: list) -> dict:
    """Last row wins, the way a re-run of the skill overwrites its own earlier line."""
    return {u: row for row in rows if (u := column(row, "url"))}


def languages_by_url(rows: list) -> dict:
    """Every distinct, non-blank `language` a reclassified.csv names for a url, in file order.

    Additive, unlike by_url()'s last-wins: a language correction states one confirmed language
    per row rather than overwriting the previous one, so a confirmed fullstack posting is TWO
    rows sharing a url - one for java, one for javascript - never one cell joined with a
    delimiter. That is what lets job_languages stay a real one-row-per-language table all the
    way from the file up, with no string to parse anywhere in the pipeline."""
    result: dict = {}
    for row in rows:
        u = column(row, "url")
        lang = column(row, "language").lower()
        if u and lang and lang not in result.setdefault(u, []):
            result[u].append(lang)
    return result


def collect_sightings(root_dir: Path, notes: dict, scan_days: set,
                      corrections: dict = None, language_corrections: dict = None) -> list:
    """Every (day, track) appearance. Collapsing them to one row per posting happens in SQL.

    Fills scan_days on the way through: a date folder exists exactly when a run happened, which
    is what makes it a usable record of when the search ran. Deriving that from the postings
    instead would miss a run that turned up nothing new.
    """
    sightings = []
    for root_name in ROOTS:
        root = root_dir / root_name
        if not root.is_dir():
            print(f"search root {root} does not exist, skipping", file=sys.stderr)
            continue
        source = "linkedin" if root.name == "DailySearch" else root.name.replace("Search", "")
        days = sorted((d for d in root.iterdir()
                       if d.is_dir() and DATE_FOLDER.fullmatch(d.name)), reverse=True)
        for day in days:
            scan_days.add(day.name)
            cache = day / "_descriptions"
            titles = read_json(day / "_titles.json")
            # Every track folder on disk, not only the four the board scans: the search now also
            # writes an "ai" track, and the charts count what was found, not what the board lists.
            tracks = sorted(t.name for t in day.iterdir()
                            if t.is_dir() and not t.name.startswith("_"))
            for track in tracks:
                found = by_url(read_csv(day / track / "jobs.csv"))
                picked = by_url(read_csv(day / track / "selected.csv"))
                # What the reader of the DESCRIPTION corrected, written by /select-jobs. The
                # search classifies from the title and files the posting into a folder; the
                # selection then opens the text and sometimes finds the folder is wrong - a
                # `Senior Frontend Developer` that turns out to be five years of Angular is not
                # her frontend track. Kept beside jobs.csv rather than edited into it: jobs.csv
                # is the record of what the parser decided, and overwriting it loses the only
                # evidence of what the title-only rule actually does.
                #
                # It has to reach the loader, not just the database. `track`, `language` and
                # `layer` are all rebuilt from the folder on every load, so an UPDATE run
                # straight against Postgres is undone by the next /find-jobs - that happened to
                # 15 rows on 2026-08-11. Applied here, the correction is re-applied every time.
                fixed_rows = read_csv(day / track / "reclassified.csv")
                fixed = by_url(fixed_rows)
                fixed_langs = languages_by_url(fixed_rows)
                if picked and track not in BOARD_TRACKS:
                    # The board would never reach these, so flagging them would put rows on it
                    # that /api/vacancies does not return.
                    notes["selected_off_board"] += len(picked)
                    picked = {}
                notes["picked_without_jobs_row"] += sum(1 for u in picked if u not in found)

                for url in list(found) + [u for u in picked if u not in found]:
                    job, selected = found.get(url), picked.get(url)
                    identifier = job_id(url)
                    record = read_json(cache / f"{identifier}.json")
                    stack = column(job, "stack").lower()
                    layer = column(job, "layer").lower()
                    # Every day folder written before 2026-08-11 carries `devops` in the STACK
                    # column, because the skill treated it as a language then. Read literally it
                    # would put devops straight back into `language` on every load and quietly
                    # undo the alter script - the loader runs after each search, so the fix would
                    # never survive a day. Translated here instead of rewriting those files:
                    # the folders are the record of what was parsed, and editing them to match a
                    # later rule loses the only evidence of what the older rule actually did.
                    if stack == "devops":
                        stack = ""
                        layer = layer if layer in ("ops", "devops", "back-ops") else "ops"
                    # The description won where it was read. Only the three columns the
                    # correction names are replaced, and a blank cell means "no correction for
                    # this one" rather than "make it empty", so a row that only fixes the track
                    # keeps the language the search worked out.
                    correction = fixed.get(url)
                    if correction:
                        notes["reclassified"] += 1
                        stack = column(correction, "language").lower() or stack
                        layer = column(correction, "layer").lower() or layer
                        # Also kept aside as its own statement. The classification columns below
                        # are fill-only-if-null now, so a correction folded into a sighting would
                        # lose to whatever the search already wrote - which is exactly the value
                        # the reader of the description is overruling. Days are walked newest
                        # first, so the first correction seen for a posting is the newest one.
                        if corrections is not None:
                            corrections.setdefault(identifier, {
                                "track": column(correction, "track").lower() or None,
                                "language": column(correction, "language").lower() or None,
                                "layer": column(correction, "layer").lower() or None})
                    # Every language reclassified.csv named for this url, not just the one on the
                    # last row - see languages_by_url(). This is the ONLY path a confirmed
                    # fullstack posting's second language reaches job_languages; the `correction`
                    # dict above still only ever sees the last row's single value.
                    langs = fixed_langs.get(url)
                    if langs and language_corrections is not None:
                        existing = language_corrections.setdefault(identifier, [])
                        for lang in langs:
                            if lang not in existing:
                                existing.append(lang)
                    row_track = column(correction, "track").lower() or track
                    # Fullstack without Java is not one of her tracks, whatever folder the row
                    # sits in. Her rule, 2026-08-11: "везде где у нас фулстек технология НО БЕЗ
                    # ДЖАВЫ например реакт и джанго, ангуляр и дотнет, пишется трек other-stacks".
                    #
                    # li_search.py routes new postings this way already, so this covers the day
                    # folders written BEFORE the rule existed. It has to live here rather than in
                    # a one-off UPDATE, because `track` is rebuilt from the folder on every load:
                    # the same 15 rows went back to `frontend` on 2026-08-11 each time the loader
                    # ran, which is four times in one day that the fix looked applied and was not.
                    # In the FULLSTACK folder `stack` has to be non-empty: day folders written
                    # before 2026-08-10 have no stack column at all, so an empty string there
                    # means "this file never recorded a language", not "the language is not Java".
                    # Read as the latter it moved 127 rows out of `fullstack` on the first run of
                    # this rule, 9 of them already selected. An unknown language leaves the
                    # folder's answer alone.
                    #
                    # In the FRONTEND folder the empty stack proves nothing either, but the FOLDER
                    # does: a fullstack-layer posting with Java on the server half is filed under
                    # fullstack/ by every version of li_search.py there has ever been. So a
                    # fullstack layer sitting in frontend/ is non-Java by construction, and the
                    # language column is not needed to say so.
                    #
                    # Without this half the rule could not fire at all on the two folders it
                    # names. frontend/jobs.csv and fullstack/jobs.csv have never carried a `stack`
                    # column - not since 2026-08-11, never, on any day: for these two tracks the
                    # language was taken to follow from the folder, which is what the two updates
                    # at the bottom of this file still do. So `and stack` was never satisfied and
                    # every such row reloaded as `frontend` on every load. The one path that does
                    # supply a language here is reclassified.csv, which is why the fullstack half
                    # of the condition below is still worth keeping.
                    if LAYER.get(layer, layer) == "fullstack" and (
                            row_track == "frontend"
                            or (row_track == "fullstack" and stack
                                and LANGUAGE.get(stack, stack) != "java")):
                        row_track = "other-stacks"
                    sightings.append({
                        "job_id": identifier,
                        "url": url,
                        # selected.csv wins where both name the company: that is the one the
                        # board already shows.
                        "company": first_non_blank(column(selected, "company"),
                                                   column(job, "company")),
                        "title": first_non_blank(text(record, "job_title"),
                                                 titles.get(identifier, ""),
                                                 column(selected, "title")),
                        # The old unsorted/ day folder is not a track either: "nobody decided
                        # this" is NULL since the 2026-09-01 migration, not a word that reads as
                        # a value. Its rows re-enter classification through /classify-jobs.
                        "track": None if row_track == "unsorted" else row_track,
                        "language": LANGUAGE.get(stack, stack) or None,
                        "layer": LAYER.get(layer, layer) or None,
                        "ai_kind": column(job, "ai_kind").lower() or None,
                        "posted_at": column(job, "posted") or None,
                        "found_date": day.name,
                        "is_selected": selected is not None,
                        # A fallback only, for the board's posted_at gap - see reset-schema.sql. The day
                        # this sighting was picked, null when it was only found.
                        "selected_date": day.name if selected is not None else None,
                        "gap": column(selected, "gap") or None,
                        # Derive the label from the URL's own domain, so the board column
                        # always names the site the link opens (jjit / linkedin / ...).
                        "source": DOMAIN_SOURCES.get(
                            (urlparse(url).hostname or "").removeprefix("www."), source) or None,
                        "easy_apply": easy_apply(record),
                        # Written into the cached record by /get-apply-link, one named posting at
                        # a time. Absent for almost every posting, and that is the normal state:
                        # null here means nobody has paid to ask yet.
                        "apply_url": text(record, "apply_url") or None,
                        "level": text(record, "experience_level") or None,
                        "job_type": text(record, "job_type") or None,
                        "location": first_non_blank(text(record, "location"),
                                                    text(record, "job_location")),
                        "applicants": applicant_count(record),
                        "has_text": (cache / f"{identifier}.txt").is_file(),
                    })

    ids = {}
    for s in sightings:
        ids.setdefault(s["job_id"], set()).add(s["url"])
    notes["ambiguous_job_ids"] = sum(1 for urls in ids.values() if len(urls) > 1)
    notes["postings"] = len(ids)
    return sightings


def cv_kind(company: str, pdf_path: str) -> str:
    """A CV built for a company sits in a folder carrying its name; everything else is the core."""
    parent = PurePosixPath(pdf_path).parent
    if parent.name and parent.name.lower() == company.strip().lower():
        return "tailored"
    return "base"


def collect_cv_queue(root_dir: Path) -> list:
    rows = []
    for core in CV_CORES:
        for row in read_csv(root_dir / CV_ROOT / core / "review-queue.csv"):
            company = column(row, "company")
            if not company:
                continue
            pdf_path = column(row, "pdf_path")
            rows.append({
                "core": core.lower(),
                "company": company,
                "url": column(row, "url") or None,
                "pdf_path": pdf_path,
                "built": column(row, "built"),
                "verdict": column(row, "verdict"),
                "kind": cv_kind(company, pdf_path),
            })
    return rows


# --- emitting ----------------------------------------------------------------------------------

SIGHTING_COLUMNS = ["job_id", "url", "company", "title", "track", "layer", "ai_kind",
                    "posted_at", "found_date", "is_selected", "selected_date", "gap", "source",
                    "easy_apply", "apply_url", "level", "job_type", "location", "applicants",
                    "has_text"]

# Everything the newest sighting answers for. found_date, selected_date, is_selected and has_text
# are aggregated instead: the first is the earliest sighting, the second the latest pick, the
# other two are ever-true.
LATEST = ["url", "company", "title", "track", "layer", "ai_kind", "posted_at",
          "gap", "source", "easy_apply", "apply_url", "level", "job_type", "location",
          "applicants"]
BLANKABLE = ["source", "level", "job_type", "location", "applicants"]
# What the SEARCH owns. li_search.py works these out from the title and the description and writes
# them to `vacancy` itself, before any filtering; this loader only ever saw the folder a posting
# landed in, which is a coarser answer to the same question. Both writing them is what let the two
# drift: the same rule had to be spelled twice, and the copy living here silently stopped matching
# (the fullstack-without-Java rule was dead in this file while it worked in the search).
#
# So here they became fill-only-if-null: a posting the search never reached - a day the database
# was down, or every folder collected before save_to_db() existed on 2026-08-11 - still gets its
# classification from the folder, and everything else keeps the reading the search made. The one
# thing that overrules the search is reclassified.csv, and that is applied as its own statement
# after the upsert, precisely because it has to win.
CLASSIFICATION = ["track", "layer", "ai_kind"]


def literal(value) -> str:
    if value is None:
        return "null"
    if isinstance(value, bool):
        return "true" if value else "false"
    return "'" + str(value).replace("'", "''") + "'"


def insert(table: str, columns: list, rows: list, on_conflict: str = "") -> None:
    if not rows:
        print(f"-- {table}: nothing to load")
        return
    print(f"insert into {table} ({', '.join(columns)}) values")
    print(",\n".join(f"    ({', '.join(literal(row[c]) for c in columns)})" for row in rows)
          + (f"\n{on_conflict}" if on_conflict else "") + ";")


def emit_vacancy_upsert(sightings: list, corrections: dict = None) -> None:
    """Collapse the sightings to one row per posting, then merge them into what is already there.

    The newest sighting wins per column, but only where it has something to say: the skills
    started writing language, layer and ai_kind recently, so the first sighting of an old
    posting has none and a plain "newest row wins" would throw the later classification away.
    """
    print("""
create temp table vacancy_load (
    ord         bigint generated always as identity,
    job_id      text not null,
    url         text,
    company     text,
    title       text,
    track       text,
    language    text,
    layer       text,
    ai_kind     text,
    posted_at   timestamp,
    found_date  date not null,
    is_selected boolean not null,
    selected_date date,
    gap         text,
    source      text,
    easy_apply  boolean,
    apply_url   text,
    level       text,
    job_type    text,
    location    text,
    applicants  text,
    has_text    boolean not null
) on commit drop;
""".strip())
    insert("vacancy_load", SIGHTING_COLUMNS, sightings)

    # A justjoin.it / nofluffjobs posting is keyed in `vacancy` by the SITE's own identifier -
    # `jjit-<guid>`, `nfj-<id>` - because portal_search.py inserts it there itself, straight from
    # the portal API. job_id() cannot reconstruct that identifier: the guid is nowhere in the URL,
    # and even nofluffjobs' id differs from its own URL slug. So the loader derived a slug id
    # instead and every single load inserted a SECOND row for a posting already on the board.
    # 40 such pairs existed by 2026-09-01, and on the fullstack run that day the pick landed on
    # the slug twin for 27 of them - a twin that has no posted_at, so the board dated those by
    # selected_date instead of by publication.
    #
    # Resolved here rather than in job_id(): the url -> site-id mapping only exists in the
    # database (the cached _descriptions/*.json carry no URL), and this file deliberately emits
    # SQL rather than connecting, so the lookup has to happen where the rows are. min() keyed by
    # url makes the choice deterministic if a URL ever carries two prefixed rows.
    print("""
update vacancy_load l
   set job_id = m.job_id
  from (select url, min(job_id) as job_id
          from vacancy
         where job_id ~ '^(jjit|nfj)-'
         group by url) m
 where m.url = l.url
   and m.job_id <> l.job_id;
""".strip())

    def latest(name):
        return (f"(array_agg({name} order by found_date desc, ord desc) "
                f"filter (where {name} is not null))[1]")

    target = ["job_id"] + LATEST + ["found_date", "is_selected", "selected_date", "has_text"]
    # The blankable columns are not-null with a '' default, so they cannot take the null the
    # aggregate produces for a posting no sighting ever described.
    picks = [f"    coalesce({latest(c)}, '') as {c}" if c in BLANKABLE
             else f"    {latest(c)} as {c}" for c in LATEST]
    updates = [f"    {c} = coalesce(excluded.{c}, v.{c})"
               for c in LATEST if c not in BLANKABLE and c not in CLASSIFICATION]
    # Reversed on purpose: the value already in the table wins, and the folder only fills a hole.
    # See CLASSIFICATION.
    updates += [f"    {c} = coalesce(v.{c}, excluded.{c})" for c in CLASSIFICATION]
    updates += [f"    {c} = case when coalesce(excluded.{c}, '') <> '' "
                f"then excluded.{c} else v.{c} end" for c in BLANKABLE]
    updates += ["    found_date  = least(v.found_date, excluded.found_date)",
                "    is_selected = v.is_selected or excluded.is_selected",
                # greatest ignores nulls in Postgres, which is what keeps the last known pick on
                # a posting whose selected.csv line has since been removed. is_selected is never
                # lowered either, so the two stay consistent.
                "    selected_date = greatest(v.selected_date, excluded.selected_date)",
                "    has_text    = v.has_text or excluded.has_text"]
    separator = ",\n"

    print("insert into vacancy as v (" + ", ".join(target) + ")")
    print("select job_id,")
    print(separator.join(picks) + ",")
    print("    min(found_date) as found_date,")
    print("    bool_or(is_selected) as is_selected,")
    print("    max(selected_date) as selected_date,")
    print("    bool_or(has_text) as has_text")
    print("from vacancy_load")
    print("group by job_id")
    print("on conflict (job_id) do update set")
    print(separator.join(updates) + ";")

    # Somebody read the actual description and found the search wrong. This is the ONE thing that
    # overrules the search's own classification, so it is a statement of its own, after the upsert
    # where the classification columns only fill holes. Re-applied on every load rather than run
    # once by hand: a plain UPDATE against Postgres is undone by the next /find-jobs, which is
    # what happened to 15 rows on 2026-08-11.
    for job_id, fix in sorted((corrections or {}).items()):
        # Languages live in job_languages now (her call, 2026-09-01): a correction that
        # names one rebuilds the posting's link rows - the same delete + rebuild
        # write_classification() does - instead of updating a scalar column.
        langs = [v for c, v in fix.items() if v and c == "language"]
        sets = ", ".join(f"{c} = {literal(v)}" for c, v in fix.items() if v and c != "language")
        if langs:
            print(f"delete from job_languages where job_id = {literal(job_id)};")
            for lang in langs:
                print(f"insert into languages (name) values ({literal(LANGUAGE.get(lang, lang))}) "
                      f"on conflict (name) do nothing;")
            for i, lang in enumerate(langs):
                print(f"insert into job_languages (job_id, language_id, is_primary) "
                      f"select {literal(job_id)}, id, {'true' if i == 0 else 'false'} "
                      f"from languages where name = {literal(LANGUAGE.get(lang, lang))} "
                      f"on conflict (job_id, language_id) do nothing;")
        if sets:
            print(f"update vacancy set {sets} where job_id = {literal(job_id)};")

    # The parser only classifies the other-stacks and ai tracks; frontend is language-scoped too
    # (her core is React, always), so the folder is the answer and no column was ever written for
    # it. Applied on every load rather than once by hand, or the next import leaves its new rows
    # unclassified. Only fills nulls, so a language the parser did write always wins.
    #
    # fullstack does NOT get the same treatment any more (her call, 2026-08-25, after the Boeing
    # posting sat in the fullstack folder for three weeks with language defaulted to 'java' while
    # its description had no Java in it at all). A generic "Fullstack" title is a guess, not a
    # read - filling it here the same way filled it with the same guess classify() already
    # stopped making. language for a fullstack posting is null until /select-jobs actually opens
    # the description and writes it, same as track='unsorted' works for everything else.
    # The parser only classifies the other-stacks and ai tracks; frontend is language-scoped too
    # (her core is React, always), so the folder is the answer. Fills the LINK TABLE, not a
    # scalar column (her call, 2026-09-01 - the column does not exist any more): a frontend-track
    # posting with no link rows yet gets one javascript row, is_primary=true.
    print("insert into languages (name) values ('javascript') on conflict (name) do nothing;")
    print("insert into job_languages (job_id, language_id, is_primary) "
          "select v.job_id, l.id, true from vacancy v "
          "join languages l on l.name = 'javascript' "
          "where v.track = 'frontend' "
          "and not exists (select 1 from job_languages jl where jl.job_id = v.job_id);")

    # A vacancy collected twice (slug id from the first portal scan, guid id from the
    # current one - same URL, two rows) is ONE job. The flag belongs to the row the
    # board can show: the one with the title. Her rule, 2026-09-01: "дубли не нужно
    # удалять, просто показывай ту вакансию, которую нашли раньше / по которой больше
    # информации". The title-less twin keeps its row and its classification - nothing
    # is deleted; the transfer repeats on every load, so the pair can never drift.
    # First copy the pick onto the informative row (while the ghost still holds it),
    # then clear the ghost.
    print("update vacancy v set is_selected = true, "
          "selected_date = coalesce(v.selected_date, s.selected_date), "
          "gap = coalesce(v.gap, s.gap) "
          "from vacancy s where s.url = v.url and s.title is not null "
          "and v.title is null and s.is_selected;")
    print("update vacancy v set is_selected = false, selected_date = null "
          "where v.title is null and v.is_selected "
          "and exists (select 1 from vacancy s where s.url = v.url "
          "and s.title is not null);")


def emit_job_languages(language_corrections: dict = None) -> None:
    """job_languages is the only language storage (her call, 2026-09-01: the scalar
    vacancy.language column is dropped). The loader's job here is only to fill holes for
    postings it inserts from day folders that the search never reached - every other
    writer (write_classification, the search's own insert) maintains the link rows
    itself:

    1. `language_corrections` - legacy: languages a reclassified.csv row named for a posting
       (see languages_by_url()). Nothing WRITES those files any more (the writer was
       /select-jobs' old section 4a, removed 2026-08-31); corrections now land through
       write_classification(), which rebuilds the posting's link rows itself.
    2. The frontend fill below: a frontend-track posting with no link rows yet gets its
       javascript row - her core is React, always, so the folder is the answer (her call,
       2026-08-25, after the Boeing posting sat in the fullstack folder for three weeks
       with a guessed language; the fill never guesses beyond the folder).

    See alter-2026-08-25-multi-language-per-vacancy.sql for why the table looks like this.
    """

    for job_id, langs in sorted((language_corrections or {}).items()):
        for lang in langs:
            print(f"insert into languages (name) values ({literal(LANGUAGE.get(lang, lang))}) "
                  f"on conflict (name) do nothing;")
        for i, lang in enumerate(langs):
            print(f"insert into job_languages (job_id, language_id, is_primary) "
                  f"select {literal(job_id)}, id, {literal(i == 0)} from languages "
                  f"where name = {literal(LANGUAGE.get(lang, lang))} "
                  f"on conflict (job_id, language_id) do nothing;")


def main() -> None:
    root_dir = dashboard_root()
    if not root_dir.is_dir():
        sys.exit(f"DASHBOARD_ROOT points at {root_dir}, which is not a folder.")

    notes = {"selected_off_board": 0, "picked_without_jobs_row": 0, "reclassified": 0}
    scan_days = set()
    corrections: dict = {}
    language_corrections: dict = {}
    sightings = collect_sightings(root_dir, notes, scan_days, corrections, language_corrections)
    cv_rows = collect_cv_queue(root_dir)

    print(f"-- generated by db/migrate.py from {root_dir}")
    print("begin;")
    emit_vacancy_upsert(sightings, corrections)
    emit_job_languages(language_corrections)
    # Only ever added to: a day the search ran stays one, even if its folder is later cleaned up.
    insert("scan_day", ["day"], [{"day": day} for day in sorted(scan_days)],
           "on conflict do nothing")
    # A small mirror of one file, so it is replaced rather than merged. job_status is deliberately
    # absent from both this truncate and any insert: the board owns that table, and refilling it
    # from a file would discard every mark made since the last import.
    print("truncate cv_queue restart identity;")
    insert("cv_queue", ["core", "company", "url", "pdf_path", "built", "verdict", "kind"], cv_rows)
    print("commit;")

    selected = len({s["job_id"] for s in sightings if s["is_selected"]})
    print(f"sightings: {len(sightings)} -> postings: {notes['postings']} ({selected} selected), "
          f"cv rows: {len(cv_rows)}, scan days: {len(scan_days)}", file=sys.stderr)
    if notes["picked_without_jobs_row"]:
        print(f"note: {notes['picked_without_jobs_row']} selected rows have no jobs.csv row "
              f"under the same day and track", file=sys.stderr)
    if notes["reclassified"]:
        print(f"note: {notes['reclassified']} postings were reclassified from their description "
              f"by /select-jobs", file=sys.stderr)
    if notes["selected_off_board"]:
        print(f"note: {notes['selected_off_board']} selected rows sit in a track the board does "
              f"not scan and were left unflagged", file=sys.stderr)
    if notes["ambiguous_job_ids"]:
        print(f"WARNING: {notes['ambiguous_job_ids']} job ids map to more than one url",
              file=sys.stderr)


if __name__ == "__main__":
    main()
