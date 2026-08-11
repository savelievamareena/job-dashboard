-- devops moves out of `language` and into `layer`.
--
-- Her call, 2026-08-11: "давай при записи в базу будем девопс писать не как язык а как layer, а
-- там где в требованиях и бэкенд язык И девопс (ажур, авс и так далее) - будем писать
-- layer = back-ops".
--
-- Why it matters rather than being tidiness: `devops` sat in the column called `language` and so
-- drew its own curve beside Python and Java, and the rule that produced it tested cloud words
-- BEFORE languages, so it also swallowed the language of every ad that named a cloud in passing.
-- `Python Developer (AWS)`, `Middle-Senior C# Developer (Azure & AKS)` and `Senior Backend
-- Engineer - Node.js & AWS` all came out as devops with no language at all.
--
-- Two new layer values:
--   devops    - the platform IS the job and no language is named. language stays NULL, because
--               inventing one is how `Cloud Engineer` came to be filed as ruby.
--   back-ops  - a backend language AND the platform. Keeps its language, so it still counts on
--               the language chart; the layer records the other half.
--
-- Safe to run twice: every statement is conditional on the state it changes.

begin;

-- 1. The chart has to know the new values exist, or they are silently dropped exactly the way
--    `unknown` is - the failure this file exists to prevent.
create or replace view trend_layer as
select coalesce(posted_at::date, found_date) as day, layer as series, count(*)::int as count
from vacancy
where layer in ('frontend', 'backend', 'fullstack', 'devops', 'back-ops')
group by 1, 2
order by 1, 2;

-- 2. The rows already collected under the old rule. They are read back as pure infrastructure:
--    the ad said devops and named no language we can still see, since the language it might have
--    named was overwritten by `devops` at write time and cannot be recovered from this table. A
--    posting still listed will be re-read on the next search and can become `back-ops` then.
update vacancy set layer = 'devops', language = null where language = 'devops';

-- 3. trend_language kept ruby out for five rows collected on 04-09.08; devops needs no such
--    clause now that step 2 has emptied it, and the view is left alone deliberately.

commit;
