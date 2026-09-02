-- One-off: fold the duplicate justjoin.it / nofluffjobs rows into the row portal_search.py wrote.
--
-- Why they exist: db/migrate.py used to derive every job_id from the URL's last path segment.
-- That is the real id for LinkedIn (/jobs/view/4461718425) and a meaningless slug for the two
-- portals, whose rows are keyed by the site's own identifier (jjit-<guid>, nfj-<id>). So every
-- load inserted a second row for a posting already on the board. migrate.py now resolves the slug
-- against the existing row before the upsert, which stops NEW twins; this script cleans up the 40
-- pairs that accumulated before that fix (2026-09-02).
--
-- Nothing on the twin is thrown away: is_selected, selected_date, gap, the earliest found_date,
-- posted_at, has_text, job_languages and any job_status mark are folded onto the kept row first.
-- Run inside one transaction with ON_ERROR_STOP=1:
--   docker compose exec -T db psql -U postgres -d jobdashboard -v ON_ERROR_STOP=1 \
--       < db/merge_portal_twins.sql

begin;

create temp table twin_map on commit drop as
select v.job_id                as drop_id,
       m.job_id                as keep_id
from vacancy v
join (select url, min(job_id) as job_id
        from vacancy
       where job_id ~ '^(jjit|nfj)-'
       group by url) m on m.url = v.url
where v.job_id !~ '^(jjit|nfj)-'
  and v.job_id <> m.job_id
  and (v.url like '%justjoin.it%' or v.url like '%nofluffjobs%');

\echo 'pairs to merge:'
select count(*) from twin_map;

-- 1. fold the twin's board state onto the row that stays
update vacancy k
   set is_selected   = k.is_selected or d.is_selected,
       selected_date = greatest(k.selected_date, d.selected_date),
       gap           = coalesce(k.gap, d.gap),
       found_date    = least(k.found_date, d.found_date),
       posted_at     = coalesce(k.posted_at, d.posted_at),
       has_text      = k.has_text or d.has_text,
       title         = coalesce(nullif(k.title, ''), d.title),
       company       = coalesce(nullif(k.company, ''), d.company),
       track         = coalesce(k.track, d.track),
       layer         = coalesce(k.layer, d.layer),
       ai_kind       = coalesce(k.ai_kind, d.ai_kind),
       apply_url     = coalesce(k.apply_url, d.apply_url),
       easy_apply    = coalesce(k.easy_apply, d.easy_apply)
  from twin_map t
  join vacancy d on d.job_id = t.drop_id
 where k.job_id = t.keep_id;

-- 2. move the marks the board itself owns, keeping the kept row's own mark if it has one
update job_status s
   set job_id = t.keep_id
  from twin_map t
 where s.job_id = t.drop_id
   and not exists (select 1 from job_status k where k.job_id = t.keep_id);

delete from job_status s using twin_map t where s.job_id = t.drop_id;

-- 3. move the language links, skipping any the kept row already has
insert into job_languages (job_id, language_id, is_primary)
select t.keep_id, l.language_id, l.is_primary
  from job_languages l
  join twin_map t on t.drop_id = l.job_id
 on conflict (job_id, language_id) do nothing;

delete from job_languages l using twin_map t where l.job_id = t.drop_id;

-- 4. the twin has nothing left of its own
delete from vacancy v using twin_map t where v.job_id = t.drop_id;

\echo 'portal urls still carrying more than one row (expect 0):'
select count(*) from (
  select url from vacancy
   where url like '%justjoin.it%' or url like '%nofluffjobs%'
   group by url having count(*) > 1) x;

commit;
