-- Her call, 2026-09-05: /find-jobs asks which country to scan (Poland / Germany / UK), so a
-- posting has to say which market it came from - the day folder names the day, never the place.
alter table vacancy add column if not exists country text;

-- Not a guess: every scan until today asked for Poland's geoId, and both portals are Polish.
update vacancy set country = 'poland' where country is null;

-- Nullable, no default: an old day folder says nothing, and a default would read as poland.
