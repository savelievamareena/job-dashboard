-- 2026-09-01: на графиках только LinkedIn - её правило, сказано дважды (2026-08-31 и повторно
-- сегодня). Правило в её словах живёт в memory charts-linkedin-only.md; здесь только техника.
-- Три тренд-вьюхи считают постинги со всех источников; фильтр по source ставится один раз тут,
-- чтобы каждый график не тащил фильтр в себе. Именование и стиль - как в alter-2026-08-12.
begin;

create or replace view trend_language as
select coalesce(v.posted_at::date, v.found_date) as day, l.name as series, count(*)::int as count
from job_languages jl
join vacancy v on v.job_id = jl.job_id
join languages l on l.id = jl.language_id
where v.source = 'linkedin'          -- ТОЛЬКО LinkedIn, других источников на графиках нет
  and l.name not in ('ruby', 'php')
group by 1, 2
order by 1, 2;

create or replace view trend_layer as
select coalesce(posted_at::date, found_date) as day, layer as series, count(*)::int as count
from vacancy v
where v.source = 'linkedin'          -- ТОЛЬКО LinkedIn
  and layer in ('frontend', 'backend', 'fullstack', 'devops', 'back-ops')
group by 1, 2
order by 1, 2;

create or replace view trend_ai as
select coalesce(posted_at::date, found_date) as day, ai_kind as series, count(*)::int as count
from vacancy v
where v.source = 'linkedin'          -- ТОЛЬКО LinkedIn
  and ai_kind is not null
group by 1, 2
order by 1, 2;

commit;
