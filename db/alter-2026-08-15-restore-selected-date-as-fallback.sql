-- Brings selected_date back, hours after alter-2026-08-15-drop-selected-date.sql removed it - not
-- a revert, a narrower reinstatement. Her call, same day: posted_at only starts on 2026-08-09, so
-- dropping selected_date left 75 of today's 93 picked postings falling straight through to
-- found_date, the day the SCAN happened to run rather than any date about the posting itself.
-- That is a worse fallback than the day she actually picked the posting, which is what
-- selected_date always was.
--
-- selected_date is a FALLBACK now, not the board's answer: the board orders by
-- coalesce(posted_at::date, selected_date, found_date), so a posting with a real posted_at is
-- never affected by this at all - only the postings selected before 2026-08-09 fall back to it.
--
-- Stopgap on purpose, her words: "через какое-то время мы вычистим". It stops earning its keep
-- once every is_selected row has a posted_at of its own - either because posted_at coverage now
-- reaches back far enough, or because the old picks have moved on (applied / not a fit / closed)
-- and dropped off what she is actually looking at. Nobody has to remember to come back for this;
-- check `select count(*) from vacancy where is_selected and posted_at is null` next time this
-- file is touched, and if it is zero, delete the column and this file's job is done.
--
-- Idempotent: running it twice changes nothing the second time.
--
-- Run with:
--   docker compose exec -T db psql -U postgres -d jobdashboard -v ON_ERROR_STOP=1 \
--       < db/alter-2026-08-15-restore-selected-date-as-fallback.sql

begin;

alter table vacancy add column if not exists selected_date date;

drop index if exists vacancy_board_idx;
create index vacancy_board_idx
    on vacancy (coalesce(posted_at::date, selected_date, found_date) desc, lower(company))
    where is_selected;

commit;
