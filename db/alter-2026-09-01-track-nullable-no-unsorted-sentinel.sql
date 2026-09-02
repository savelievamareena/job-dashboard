-- Her call, 2026-09-01: "в идеале поменять структуру базы данных чтобы track тоже мог хранить
-- пустое значение чтобы не было путаницы". 'unsorted' was the sentinel the schema was born with
-- (track NOT NULL); from now on undetermined is NULL in track and layer alike, and every gap
-- query checks IS NULL (classify-jobs' SELECT, db_track_candidates(), the loader's coalesce).
-- Reversing is safe: the rows this touches are exactly the rows that read "nobody classified
-- this", so re-stamping them loses nothing.
alter table vacancy alter column track drop not null;
update vacancy set track = null where track = 'unsorted';
update vacancy set layer = null where layer = 'unsorted';
