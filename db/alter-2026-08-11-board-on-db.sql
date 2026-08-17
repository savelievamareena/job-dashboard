-- Brings a database created by the earlier schema.sql (renamed reset-schema.sql on 2026-08-15) up
-- to the shape the board needs, without dropping anything. reset-schema.sql itself cannot be
-- re-run here: it starts by dropping vacancy, and vacancy holds the whole find rather than a
-- cache of it.
--
-- Two changes, both from moving the board off the files and onto this database:
--
--   1. vacancy gains selected_date, the day a posting was picked. The board shows that date, and
--      it is not found_date: a posting found on one day is often picked the next. Left null here;
--      the next migrate.py run fills it from selected.csv.
--
--   2. job_status is rekeyed from url to job_id and gains a foreign key to vacancy. The rows are
--      carried over, deriving job_id from the url exactly as the loader and the application do:
--      drop a trailing slash, take what follows the last one.
--
-- Idempotent: running it twice changes nothing the second time.
--
-- Run with:
--   docker compose exec -T db psql -U postgres -d jobdashboard -v ON_ERROR_STOP=1 \
--       < db/alter-2026-08-11-board-on-db.sql

begin;

alter table vacancy add column if not exists selected_date date;

-- Clears the placeholder zero the old loader stored in applicants. "applies" is written by the
-- newer scrape on every posting and is zero in all 184 cached records that carry it, so it is a
-- default, not a count; the older "applicants" holds the real text. Two things went wrong with
-- reading it literally: a posting seen again lost its real count to the zero, and a third of the
-- board read "0 applicants" where the honest answer is that nobody knows.
--
-- migrate.py no longer writes this, but it also never clears a value it once wrote, so the rows
-- already carrying a zero have to be repaired here.
update vacancy set applicants = '' where applicants in ('0', '0.0');

drop index if exists vacancy_board_idx;
create index vacancy_board_idx
    on vacancy (coalesce(selected_date, found_date) desc, lower(company)) where is_selected;

-- Guarded on the old column so a second run is a no-op rather than an error.
do $$
begin
    if exists (select 1 from information_schema.columns
               where table_schema = 'public' and table_name = 'job_status'
                 and column_name = 'url')
    then
        create table job_status_rekeyed (
            job_id text primary key references vacancy (job_id) on delete cascade,
            status text not null default '',
            note   text not null default '',
            constraint job_status_not_empty check (status <> '' or note <> '')
        );

        -- A mark whose posting is not in vacancy cannot be carried over: the foreign key is the
        -- point of the change. Nothing is silently dropped, the count is reported below instead.
        insert into job_status_rekeyed (job_id, status, note)
        select regexp_replace(rtrim(s.url, '/'), '^.*/', ''), s.status, s.note
        from job_status s
        where exists (select 1 from vacancy v
                      where v.job_id = regexp_replace(rtrim(s.url, '/'), '^.*/', ''))
        -- Two urls for one posting collapse to one mark; the check above proved there are none
        -- today, and this keeps the insert from failing if that ever stops being true.
        on conflict (job_id) do nothing;

        raise notice 'job_status: % of % marks carried over',
            (select count(*) from job_status_rekeyed), (select count(*) from job_status);

        drop table job_status;
        alter table job_status_rekeyed rename to job_status;
    end if;
end $$;

-- Renaming the table leaves every constraint Postgres named after it under the build-time name:
-- the primary key, the foreign key and the three not-null ones. Cosmetic, but it means a database
-- migrated by this script and one built from schema.sql report different names for the same
-- constraint, which is a confusing thing to hit while reading an error message. Done as a loop so
-- it also covers whatever Postgres decides to name next.
--
-- The primary key needs no separate "alter index": renaming its constraint renames the index that
-- backs it, and naming that index directly would be a statement no database ever reaches twice,
-- which reads as broken to anything that checks this file against a migrated database.
do $$
declare
    old_name text;
begin
    for old_name in
        select conname from pg_constraint
        where conrelid = 'job_status'::regclass and conname like 'job\_status\_rekeyed\_%'
    loop
        execute format('alter table job_status rename constraint %I to %I',
                       old_name, replace(old_name, 'job_status_rekeyed_', 'job_status_'));
    end loop;
end $$;

commit;
