#!/usr/bin/env python3
"""Loads the dashboard's file data into Postgres.

One row per posting, keyed by the LinkedIn id out of its URL. A posting seen again on a later
day updates its row rather than adding one, so the charts count a find once however many days it
kept showing up. is_selected is raised by the rows that reached selected.csv and is never
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

# Mirrors backend/src/main/resources/application.yml. Kept here rather than parsed out of it:
# this runs once per import, and a yaml parser is not worth adding for four constants.
ROOTS = ["DailySearch", "PortalSearch"]
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
# spell them the same way - while `front`, `back` and the skill's short `ops` are. "unknown" is
# the skill saying it could not tell, which is absence, not another layer, so it maps to None.
# The five layers that reach the chart: frontend, backend, fullstack, devops, back-ops.
LAYER = {"front": "frontend", "back": "backend", "ops": "devops", "unknown": None}


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


def collect_sightings(root_dir: Path, notes: dict, scan_days: set) -> list:
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
        source = root.name.replace("Search", "")
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
                        "track": track,
                        "language": LANGUAGE.get(stack, stack) or None,
                        "layer": LAYER.get(layer, layer) or None,
                        "ai_kind": column(job, "ai_kind").lower() or None,
                        "posted_at": column(job, "posted") or None,
                        "found_date": day.name,
                        "is_selected": selected is not None,
                        # The day this sighting was picked, null when it was only found. The
                        # board shows this rather than found_date; see schema.sql.
                        "selected_date": day.name if selected is not None else None,
                        "gap": column(selected, "gap") or None,
                        "source": source or None,
                        "easy_apply": easy_apply(record),
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

SIGHTING_COLUMNS = ["job_id", "url", "company", "title", "track", "language", "layer", "ai_kind",
                    "posted_at", "found_date", "is_selected", "selected_date", "gap", "source",
                    "easy_apply", "level", "job_type", "location", "applicants", "has_text"]

# Everything the newest sighting answers for. found_date, selected_date, is_selected and has_text
# are aggregated instead: the first is the earliest sighting, the second the latest pick, the
# other two are ever-true.
LATEST = ["url", "company", "title", "track", "language", "layer", "ai_kind", "posted_at",
          "gap", "source", "easy_apply", "level", "job_type", "location", "applicants"]
BLANKABLE = ["source", "level", "job_type", "location", "applicants"]


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


def emit_vacancy_upsert(sightings: list) -> None:
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
    level       text,
    job_type    text,
    location    text,
    applicants  text,
    has_text    boolean not null
) on commit drop;
""".strip())
    insert("vacancy_load", SIGHTING_COLUMNS, sightings)

    def latest(name):
        return (f"(array_agg({name} order by found_date desc, ord desc) "
                f"filter (where {name} is not null))[1]")

    target = ["job_id"] + LATEST + ["found_date", "is_selected", "selected_date", "has_text"]
    # The blankable columns are not-null with a '' default, so they cannot take the null the
    # aggregate produces for a posting no sighting ever described.
    picks = [f"    coalesce({latest(c)}, '') as {c}" if c in BLANKABLE
             else f"    {latest(c)} as {c}" for c in LATEST]
    updates = [f"    {c} = coalesce(excluded.{c}, v.{c})"
               for c in LATEST if c not in BLANKABLE]
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

    # The parser only classifies the other-stacks and ai tracks; for these two the search itself
    # is language-scoped, so the folder is the answer and no column was ever written. Applied on
    # every load rather than once by hand, or the next import leaves its new rows unclassified.
    # Only fills nulls, so a language the parser did write always wins.
    print("update vacancy set language = 'javascript' "
          "where track = 'frontend' and language is null;")
    print("update vacancy set language = 'java' "
          "where track = 'fullstack' and language is null;")


def main() -> None:
    root_dir = dashboard_root()
    if not root_dir.is_dir():
        sys.exit(f"DASHBOARD_ROOT points at {root_dir}, which is not a folder.")

    notes = {"selected_off_board": 0, "picked_without_jobs_row": 0}
    scan_days = set()
    sightings = collect_sightings(root_dir, notes, scan_days)
    cv_rows = collect_cv_queue(root_dir)

    print(f"-- generated by db/migrate.py from {root_dir}")
    print("begin;")
    emit_vacancy_upsert(sightings)
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
    if notes["selected_off_board"]:
        print(f"note: {notes['selected_off_board']} selected rows sit in a track the board does "
              f"not scan and were left unflagged", file=sys.stderr)
    if notes["ambiguous_job_ids"]:
        print(f"WARNING: {notes['ambiguous_job_ids']} job ids map to more than one url",
              file=sys.stderr)


if __name__ == "__main__":
    main()
