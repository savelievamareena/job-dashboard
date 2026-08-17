-- Drops selected_date. The board showed a posting by the day it was picked; her call, 2026-08-15:
-- she wants the board reading by publication date like the charts already do, and selected_date
-- serves no purpose once that is true - nothing else in the app used it.
--
-- Nothing is lost that could not be rebuilt: selected_date was always the folder date of whatever
-- day's selected.csv most recently named a posting, and those files are still on disk. If it is
-- ever needed again, migrate.py's old logic is in git history rather than repeated here.
--
-- Idempotent: running it twice changes nothing the second time.
--
-- Run with:
--   docker compose exec -T db psql -U postgres -d jobdashboard -v ON_ERROR_STOP=1 \
--       < db/alter-2026-08-15-drop-selected-date.sql

begin;

drop index if exists vacancy_board_idx;
alter table vacancy drop column if exists selected_date;

create index vacancy_board_idx
    on vacancy (coalesce(posted_at::date, found_date) desc, lower(company)) where is_selected;

commit;
