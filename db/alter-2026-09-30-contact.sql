-- The CV email column becomes a contact column.
--
-- Her ask, 2026-09-30: company mailboxes turned up for few applications, so /cv-automation now
-- saves the recruiter's LinkedIn profile instead - the person the posting names under "Meet the
-- hiring team", or a recruiter who hires engineers found on the company's LinkedIn page. It does
-- this only for the vacancies it submits, at the step where it reads the apply link.
--
-- A rename, not a new column: the addresses already found stay, and the board opens either kind.
-- Only a profile or address read on a page ever lands here, never a guessed one.
--
-- null means "nobody has looked, or looking found nothing"; the loader never writes it, so a
-- rerun of db/migrate.py leaves it alone the same way it leaves apply_url alone.
--
-- Safe to run twice.

begin;

do $$
begin
    if exists (select 1 from information_schema.columns
               where table_name = 'vacancy' and column_name = 'cv_email') then
        alter table vacancy rename column   cv_email to contact;
    end if;
end $$;

alter table vacancy add column if not exists contact text;

commit;
