-- A vacancy can genuinely need more than one language - her Fullstack core is React AND Java,
-- so a real fullstack posting needs BOTH counted. `vacancy.language` is one text cell and could
-- only ever hold one, so every fullstack posting counted as "java" alone and javascript lost a
-- data point on every single one of them - that is why the java curve always ran roughly double
-- javascript on trend_language. Her diagnosis, 2026-08-25, after the Boeing posting (see
-- DailySearch/2026-08-04/fullstack/reclassified.csv) turned out to have no Java at all despite
-- sitting in the fullstack folder for three weeks with language = 'java'.
--
-- many-to-many, her call: languages(id, name) + job_languages(job_id, language_id) rather than a
-- delimited string in one cell - normal filters/GROUP BY/indexes, no string parsing, room for
-- metadata like is_primary later without another migration.
--
-- vacancy.language is NOT dropped here. It stays exactly what it always was - one word for the
-- tracks that only ever need one (frontend, other-stacks, ai) - and db/migrate.py's
-- emit_job_languages() carries it into job_languages as a single row. Dropping the column is a
-- separate, later decision once this is confirmed working.
--
-- A confirmed fullstack posting's SECOND language never touches this column and never touches a
-- delimiter: /select-jobs writes it as a second row in reclassified.csv (same url, one language
-- each), and db/migrate.py reads both rows straight into two job_languages rows. No string is
-- ever split to get there - see languages_by_url() in db/migrate.py.
--
-- Backfill carries forward whatever is already in vacancy.language AS IS, one row each - her
-- call, 2026-08-25: old fullstack rows are not getting re-read for a second language, only new
-- ones going forward.

begin;

create table languages (
    id   bigint generated always as identity primary key,
    name text not null unique
);

create table job_languages (
    job_id      text not null references vacancy (job_id) on delete cascade,
    language_id bigint not null references languages (id),
    is_primary  boolean not null default true,
    primary key (job_id, language_id)
);

create index job_languages_language_idx on job_languages (language_id);

insert into languages (name)
select distinct language from vacancy where language is not null
on conflict (name) do nothing;

insert into job_languages (job_id, language_id, is_primary)
select v.job_id, l.id, true
from vacancy v join languages l on l.name = v.language
where v.language is not null
on conflict (job_id, language_id) do nothing;

-- Obsolete the moment the chart stops reading vacancy.language directly.
drop index if exists vacancy_language_idx;

-- Same shape as before (day, series, count) - StatisticsRepository.java and Statistics.tsx read
-- this unchanged. A fullstack posting with two job_languages rows now counts once in EACH
-- series instead of once in whichever single word used to sit in vacancy.language.
create or replace view trend_language as
select coalesce(v.posted_at::date, v.found_date) as day, l.name as series, count(*)::int as count
from job_languages jl
join vacancy v on v.job_id = jl.job_id
join languages l on l.id = jl.language_id
where l.name not in ('ruby', 'php')
group by 1, 2
order by 1, 2;

commit;
