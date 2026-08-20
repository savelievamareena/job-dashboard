begin;

alter table vacancy add column if not exists may_submit boolean not null default false;

commit;
