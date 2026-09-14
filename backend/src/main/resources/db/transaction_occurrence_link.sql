-- Transaction <-> FixedPaymentOccurrence link — standalone migration for EXISTING databases.
--
-- This change is already included in 01_init.sql (Docker / fresh setup).
-- Run this script manually against an existing database that was created
-- before the two-way Transaction/FixedPaymentOccurrence link was added.
--
-- Usage:
--   psql -v ON_ERROR_STOP=1 --single-transaction -U <user> -d savix -f transaction_occurrence_link.sql
--
-- What it does:
--   Promotes the existing fixed_payment_occurrences.transaction_id link to a
--   true 1:1 relationship by replacing the non-unique partial index with a
--   UNIQUE partial index. No new column and no data backfill are required —
--   the FK already lives on fixed_payment_occurrences.transaction_id.
--
-- NOTE: If two occurrences currently reference the same transaction_id, the
-- unique index creation will fail. That indicates pre-existing bad data that
-- must be resolved first (a transaction can only pay one occurrence).

drop index if exists idx_fpo_transaction;

create unique index if not exists idx_fpo_transaction
    on fixed_payment_occurrences (transaction_id)
    where transaction_id is not null;
