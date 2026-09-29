-- The address a CV can be emailed to, beside the form it was submitted through.
--
-- Her ask, 2026-09-28: a copy of the application sent to the company's own mailbox is advice that
-- works, so /cv-automation now looks that address up for every company it applies to and the
-- board shows it next to the apply link.
--
-- Only an address read on a page - the posting itself or the company's own careers/contact page -
-- ever lands here. Never a guessed jobs@<domain>: a pattern that bounces costs her nothing to
-- skip, one that reaches a stranger costs her the application.
--
-- null means "nobody has looked, or looking found nothing"; the loader never writes it, so a
-- rerun of db/migrate.py leaves it alone the same way it leaves apply_url alone.
--
-- Safe to run twice.

begin;

alter table vacancy add column if not exists cv_email text;

commit;
