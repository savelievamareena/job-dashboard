-- Her call, 2026-09-01: "зачем нам его оставлять если у нас есть еще таблица
-- отдельная под языки". vacancy.language was the pre-join primary (one word per
-- posting); the link table has carried every language since 2026-08-25, and the
-- move was verified lossless before the drop: 0 vacancies with a language and no
-- link row, 0 link rows without the column value. The writers were migrated in
-- the same pass: li_search.py's db_rows_sql() and write_classification() write
-- job_languages rows directly now, and db/migrate.py's rebuild fills only holes.
-- trend_language never read this column after 2026-08-25.
alter table vacancy drop column if exists language;
