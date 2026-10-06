-- Her calls, 2026-10-06: layer `mobile` for Android/iOS app work with no web in it, never on one of
-- her tracks; and the charts count LinkedIn only again (the 09-05 rebuild dropped that filter).
begin;

-- Replaces vacancy_backend_track: the backend rule is unchanged, the mobile rule is new.
create or replace function vacancy_track_by_layer() returns trigger language plpgsql as $$
begin
    if new.layer = 'mobile' and new.track in ('frontend', 'fullstack', 'backend') then
        new.track := 'other-stacks';
    elsif new.track = 'fullstack' and new.layer in ('backend', 'back-ops') then
        new.track := 'backend';
    elsif new.track = 'backend' and new.layer in ('frontend', 'fullstack') then
        new.track := 'fullstack';
    end if;
    return new;
end $$;

drop trigger if exists vacancy_backend_track on vacancy;
drop function if exists vacancy_backend_track();
drop trigger if exists vacancy_track_by_layer on vacancy;
create trigger vacancy_track_by_layer
    before insert or update of track, layer on vacancy
    for each row execute function vacancy_track_by_layer();

-- The one row still spelled with the short form, which trend_layer never counted.
update vacancy set layer = 'frontend' where layer = 'front';

-- A fresh LinkedIn row has an empty source until the next portal run or load stamps it.
create or replace view trend_language as
select coalesce(v.posted_at::date, v.found_date) as day, v.country, l.name as series,
       count(*)::int as count
from job_languages jl
join vacancy v on v.job_id = jl.job_id
join languages l on l.id = jl.language_id
where (v.source = 'linkedin' or (v.source = '' and v.url ~* '^https?://([a-z0-9-]+\.)?linkedin\.com/'))
  and l.name not in ('ruby', 'php')
group by 1, 2, 3
order by 1, 3;

create or replace view trend_layer as
select coalesce(posted_at::date, found_date) as day, country, layer as series,
       count(*)::int as count
from vacancy
where (source = 'linkedin' or (source = '' and url ~* '^https?://([a-z0-9-]+\.)?linkedin\.com/'))
  and layer in ('frontend', 'backend', 'fullstack', 'devops', 'back-ops')
group by 1, 2, 3
order by 1, 3;

create or replace view trend_ai as
select coalesce(posted_at::date, found_date) as day, country, ai_kind as series,
       count(*)::int as count
from vacancy
where (source = 'linkedin' or (source = '' and url ~* '^https?://([a-z0-9-]+\.)?linkedin\.com/'))
  and ai_kind is not null
group by 1, 2, 3
order by 1, 3;

commit;
