-- Her call, 2026-10-06: the fullstack track splits in two by layer. Layer backend or back-ops
-- (no frontend work) is track backend; every other layer stays fullstack. Both use the Fullstack CV.
begin;

-- The one row spelled with the short form, which trend_layer does not count either.
update vacancy set layer = 'backend' where layer = 'back';

-- Every writer goes through this, so a new posting gets the same split as the old ones.
create or replace function vacancy_backend_track() returns trigger language plpgsql as $$
begin
    if new.track = 'fullstack' and new.layer in ('backend', 'back-ops') then
        new.track := 'backend';
    elsif new.track = 'backend' and new.layer in ('frontend', 'fullstack') then
        new.track := 'fullstack';
    end if;
    return new;
end $$;

drop trigger if exists vacancy_backend_track on vacancy;
create trigger vacancy_backend_track
    before insert or update of track, layer on vacancy
    for each row execute function vacancy_backend_track();

update vacancy set track = 'backend' where track = 'fullstack' and layer in ('backend', 'back-ops');

commit;
