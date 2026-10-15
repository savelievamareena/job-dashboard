-- Marks postings from product companies, so the board can sort them up and colour their rows.
--
-- true is a company building its own product, false an outsourcing house, consultancy, agency or
-- staffing firm, null "the text does not say" or "nobody has read it yet". /classify-jobs writes
-- it from the description through write_classification(); the loader never writes it, so a rerun
-- of db/migrate.py leaves it alone.
--
-- Safe to run twice.

begin;

alter table vacancy add column if not exists is_product boolean;

commit;
