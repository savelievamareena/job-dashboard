-- Schema for the job dashboard.
--
-- The database holds every posting the search found; the board shows only the ones picked to
-- apply to. That is one table with a flag, not two tables: the charts count the whole set and
-- the board filters it, so splitting them would mean joining them back for every chart.
--
-- One row is one posting, keyed by the LinkedIn id out of its URL — not one row per sighting.
-- A posting found again on a later day updates its row; found_date stays at the first sighting,
-- which is what the trend charts count by.
--
--   vacancy    <- <root>/<date>/<track>/jobs.csv, the full find, with is_selected raised for the
--                 rows that also appear in selected.csv; enriched from _titles.json and the
--                 _descriptions cache
--   job_status <- nothing. The application owns it; see the table for why.
--   cv_queue   <- cv-tailored/<core>/review-queue.csv
--
-- Names are snake_case and unquoted on purpose: Postgres folds unquoted identifiers to lower
-- case, so an isSelected in a query silently becomes isselected and finds nothing. The app
-- renames at the edge instead — select is_selected as "isSelected".
--
-- Run with:  psql -v ON_ERROR_STOP=1 -f db/schema.sql
--
-- ONLY on an empty database. The drop below takes vacancy with it, and vacancy holds the whole
-- find, not a cache of it: the dated folders are cleaned up over time, so what is dropped here
-- cannot always be reloaded. To change the shape of a database already in use, write an alter
-- script next to this file instead and apply that; see db/alter-2026-08-11-board-on-db.sql.

begin;

-- cascade so the views go with the table: they are recreated at the bottom of this file, and
-- naming them here would miss any that an earlier version of the schema left behind.
drop table if exists vacancy, job_status, cv_queue cascade;

-- The days a search actually ran, so the page can tell "we did not look" apart from "nothing was
-- posted": a day missing here is a break in the line, not a zero. Without it 6 and 8 August are
-- drawn as zeros, which reads as the market collapsing and bouncing back.
--
-- Filled by the loader from the DailySearch/<date>/ folders, which exist exactly when a run
-- happened. It cannot be derived from the vacancies themselves: found_date is the day a posting
-- was FIRST seen, so a run that turns up only already-known postings adds no found_date at all
-- and would silently read as a day nobody looked. Not dropped above, and rows are only ever
-- added, so a day that ran stays a day that ran.
--
-- This holds while a folder means a completed run. If backfilling earlier days into their own
-- folders is ever added, the invariant breaks and the list has to be kept explicitly.
-- scan_day was a view over "select distinct found_date" before it was a table. "if exists" only
-- guards against the object being absent, not against it being a different kind, so a plain
-- drop view here fails with "scan_day is not a view" on any database already migrated. Guarded
-- on relkind so this file applies in both states.
--
-- Never turn this into "drop table if exists scan_day": that erases the record of which days a
-- search ran, and once the dated folders are cleaned up there is nowhere left to rebuild it from.
do $$
begin
    if exists (select 1 from pg_class c join pg_namespace n on n.oid = c.relnamespace
               where n.nspname = 'public' and c.relname = 'scan_day' and c.relkind = 'v')
    then
        execute 'drop view scan_day';
    end if;
end $$;

create table if not exists scan_day (day date primary key);

create table vacancy (
    job_id      text primary key,
    url         text not null,
    company     text,
    title       text,                -- selected.csv or the description cache, else null
    track       text not null,       -- frontend / fullstack / other-stacks / AI / unsorted

    -- Normalised at import, so a chart never has to know that the skills wrote node for
    -- JavaScript or dotnet for C#. Null means the skill did not classify this posting: the
    -- columns arrived recently, so most older rows have none, and a gap is shown as a gap.
    language    text,
    layer       text,
    ai_kind     text,

    posted_at   timestamp,           -- when the posting went up
    found_date  date not null,       -- the day it was first seen

    -- The board's flag. Only ever raised by an import, never lowered: a posting that drops out
    -- of the folders must not quietly lose the decision to apply to it. Clear it by hand.
    is_selected boolean not null default false,

    -- The day the posting was picked, which is the date the board shows in its first column.
    -- Deliberately not found_date: a posting is often found on one day and picked the next, and
    -- eight of the seventy-eight picked rows differ that way today. found_date cannot be
    -- redefined to mean this, because the trend charts count a posting on the day it was first
    -- seen, so the two dates have to live side by side.
    --
    -- The latest day it was picked, so a posting picked again carries the current decision.
    -- Null where is_selected is true but no selected.csv line is left to say when: the flag is
    -- never lowered, so it outlives the line. The board falls back to found_date there.
    selected_date date,
    gap         text,                -- from selected.csv

    -- What the board renders beside the above. Not part of the chart story, but the board reads
    -- these today, so the table has to carry them for it to ever move off the files.
    source      text not null default '',
    easy_apply  boolean,             -- null is "not known" which the board shows as "?"
    level       text not null default '',
    job_type    text not null default '',
    location    text not null default '',
    applicants  text not null default '',
    has_text    boolean not null default false
);

-- Matches the board's one query, ordering included: newest pick first, then company ignoring
-- case. The coalesce has to be repeated here or the index does not answer that order.
create index vacancy_board_idx
    on vacancy (coalesce(selected_date, found_date) desc, lower(company)) where is_selected;
create index vacancy_language_idx on vacancy (found_date, language) where language is not null;
create index vacancy_layer_idx on vacancy (found_date, layer) where layer is not null;
create index vacancy_ai_idx on vacancy (found_date, ai_kind) where ai_kind is not null;

-- What the board has been told about a posting: applied, not a fit, closed, plus a free note.
--
-- The one table here the application writes, and the only one the loader must not touch. It used
-- to mirror _status.json and was truncated and refilled on every import; now that the board
-- writes straight here, a refill would throw away every mark made since the last import. There is
-- no insert into this table in migrate.py, and there must not be one.
--
-- Keyed by job_id like vacancy, not by the url the old file used. A mark belongs to a posting,
-- and a url is a weaker name for one: the same posting written with and without a trailing slash
-- was two rows, and a mark could sit on a url no vacancy row had. The foreign key ends both.
--
-- An untouched posting has no row here, the same way it had no key in the file. Clearing a mark
-- deletes the row rather than blanking it, which is what the check constraint requires.
create table job_status (
    job_id text primary key references vacancy (job_id) on delete cascade,
    status text not null default '',
    note   text not null default '',
    constraint job_status_not_empty check (status <> '' or note <> '')
);

-- One row per review-queue.csv line. url is null on rows written before the queue carried one;
-- those answer for every posting of their company, which is all such a row can honestly say.
--
-- The list of cores stays in application.yml and must not be re-derived from this table: a core
-- with nothing built yet still owns its track. Read "fullstack" out of `select distinct core`
-- and it disappears, so a fullstack posting starts being answered from the Frontend queue, which
-- is the exact mix-up the per-core split exists to prevent.
create table cv_queue (
    id       bigint generated always as identity primary key,
    core     text not null,
    company  text not null,
    url      text,
    pdf_path text not null default '',
    built    text not null default '',
    verdict  text not null default '',
    kind     text not null check (kind in ('tailored', 'base'))
);

create index cv_queue_url_idx on cv_queue (core, url);
create index cv_queue_company_idx on cv_queue (core, lower(company));


-- --- the three charts ---------------------------------------------------------------------
--
-- Views, not tables: they are pure derivation, so there is nothing to keep in sync, and a
-- recut of the buckets applies to the whole history at once. All three share the shape
-- (day, series, count), so one chart component on the page can read any of them:
--
--     select day, series, count from trend_language order by day;
--
-- A posting counts once, on the day it was published. Counting by the day it was scanned
-- instead would put every posting of a skipped day onto the day the search next ran: a trough
-- where nothing ran and a spike right after it, neither of which happened on the market.
--
-- Coalesce is not optional. posted_at only arrived with the recent columns, so most rows
-- have none and the scan date is the only date they have; that share falls with every run.

-- The AI track is excluded because it is a cut of the same market by subject rather than by
-- language: leaving it in would count its postings twice, once here and once in trend_ai.
create view trend_language as
select coalesce(posted_at::date, found_date) as day, language as series, count(*)::int as count
from vacancy
where track <> 'ai' and language is not null and language <> 'ruby'
group by 1, 2
order by 1, 2;

-- "unknown" is dropped rather than drawn: it is the skill saying it could not tell, which is
-- not a fourth layer.
--
-- `devops` and `back-ops` joined the list on 2026-08-11, when devops stopped being a value of
-- `language`. It never was a language - it is what the person does - and in the language column
-- it drew a curve beside Python and Java while swallowing the language of every ad that happened
-- to name a cloud. `devops` is infrastructure with no language named; `back-ops` is an ad that
-- wants a backend language AND the platform, and it keeps its language so it still counts in
-- trend_language. The list is explicit rather than "layer is not null" on purpose: a value the
-- skill invents by accident should stay off the chart until someone adds it here deliberately.
create view trend_layer as
select coalesce(posted_at::date, found_date) as day, layer as series, count(*)::int as count
from vacancy
where layer in ('frontend', 'backend', 'fullstack', 'devops', 'back-ops')
group by 1, 2
order by 1, 2;

-- Placeholder buckets until the categories are pinned down: right now the skill writes ai_kind
-- only on the postings it fetched under the AI track.
create view trend_ai as
select coalesce(posted_at::date, found_date) as day, ai_kind as series, count(*)::int as count
from vacancy
where ai_kind is not null
group by 1, 2
order by 1, 2;

commit;
