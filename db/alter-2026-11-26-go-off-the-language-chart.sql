begin;

create or replace view trend_language as
select coalesce(v.posted_at::date, v.found_date) as day, v.country, l.name as series,
       count(*)::int as count
from job_languages jl
join vacancy v on v.job_id = jl.job_id
join languages l on l.id = jl.language_id
where (v.source = 'linkedin' or (v.source = '' and v.url ~* '^https?://([a-z0-9-]+\.)?linkedin\.com/'))
  and l.name not in ('ruby', 'php', 'go')
group by 1, 2, 3
order by 1, 3;

commit;
