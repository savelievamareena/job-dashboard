-- Her call, 2026-09-24: the posting text itself lives in the database, not only in the gitignored
-- DailySearch/<day>/_descriptions/<job_id>.txt files. Null = no text on disk when it was copied.
alter table vacancy add column if not exists description text;
