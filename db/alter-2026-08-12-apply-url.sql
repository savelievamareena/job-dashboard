-- Where the Apply button actually leads.
--
-- Her ask, 2026-08-12, while working out how a posting gets applied to without her filling the
-- same form by hand every time. The URL is the first thing that step needs and the one thing
-- nothing in this pipeline could answer until now.
--
-- Why the column rather than reading it from the files each time: it is EXPENSIVE. The daily
-- search API carries no apply field at all, and LinkedIn's guest page shows a signed-out visitor
-- only the fact that the button leaves the site, never the address - verified 2026-08-12 against
-- ten guest pages and against a paid record, whose only URLs were the company's LinkedIn page and
-- the LinkedIn job itself. The address comes from a SEPARATE subscription (linkedin-data-api,
-- get-job-details -> applyMethod.companyApplyUrl) whose free tier is 50 requests a month against
-- roughly eleven postings picked a day. So each value here was paid for individually and must
-- survive; re-deriving it is not an option the way `layer` or `language` can be re-derived.
--
-- null means "nobody has asked yet", NOT "there is no link" - the same distinction easy_apply
-- makes beside it. Nothing fills this column in bulk: /get-apply-link asks about the postings she
-- names, one at a time.
--
-- Safe to run twice.

begin;

alter table vacancy add column if not exists apply_url text;

commit;
