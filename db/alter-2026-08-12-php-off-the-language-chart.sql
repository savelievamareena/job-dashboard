-- php leaves the language chart, the way ruby did on 2026-08-10.
--
-- Her call, 2026-08-12: "убирай php - мы больше не будем его искать отдельно и уберем из
-- графиков". The paid query '"php"' is gone from li_search.py in the same change.
--
-- What this does NOT do is delete anything. A posting titled "PHP Developer" still arrives
-- through "backend" and the catch-alls, is still saved to `vacancy`, and still carries
-- language = 'php': the rows stay, it is the CURVE that goes. That is the same shape as ruby,
-- and it is the shape that keeps the table honest about a market she is not tracking - a
-- language deleted at write time cannot be brought back if she ever wants the history.
--
-- Safe to run twice: create or replace, nothing is dropped and no data is touched.

begin;

create or replace view trend_language as
select coalesce(posted_at::date, found_date) as day, language as series, count(*)::int as count
from vacancy
where track <> 'ai' and language is not null and language not in ('ruby', 'php')
group by 1, 2
order by 1, 2;

commit;
