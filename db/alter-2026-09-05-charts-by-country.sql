-- Her call, 2026-09-05: the charts get a country selector, Poland by default. One run scans one
-- country, so a combined curve would alternate markets day to day and read as the market moving.
begin;

-- Every scan before today was Poland, so the backfill is a fact. Two searches in one day for two
-- countries are two scan days now, which is why the key widens instead of the column being added.
alter table scan_day add column country text not null default 'poland';
alter table scan_day alter column country drop default;
alter table scan_day drop constraint scan_day_pkey;
alter table scan_day add primary key (day, country);

-- The three views gain `country`; everything else about them is unchanged. Filtering happens on
-- the page, which already holds the whole payload and slices it by period the same way.
drop view trend_language;
create view trend_language as
select coalesce(v.posted_at::date, v.found_date) as day, v.country, l.name as series,
       count(*)::int as count
from job_languages jl
join vacancy v on v.job_id = jl.job_id
join languages l on l.id = jl.language_id
where l.name not in ('ruby', 'php')
group by 1, 2, 3
order by 1, 3;

drop view trend_layer;
create view trend_layer as
select coalesce(posted_at::date, found_date) as day, country, layer as series,
       count(*)::int as count
from vacancy
where layer in ('frontend', 'backend', 'fullstack', 'devops', 'back-ops')
group by 1, 2, 3
order by 1, 3;

drop view trend_ai;
create view trend_ai as
select coalesce(posted_at::date, found_date) as day, country, ai_kind as series,
       count(*)::int as count
from vacancy
where ai_kind is not null
group by 1, 2, 3
order by 1, 3;

commit;
