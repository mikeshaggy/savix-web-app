-- ═══════════════════════════════════════════════════════════════════════════
-- 03_fund_currency_status.sql — Fund model alignment
-- Depends on: 01_init.sql + 02_funds.sql (existing databases)
-- Apply with: psql -v ON_ERROR_STOP=1 --single-transaction -U savix-admin -d savix -f 03_fund_currency_status.sql
--
-- Brings the live `funds` table in line with the desired model in 01_init.sql:
--   1. Funds no longer carry a `currency` (money uses the app/wallet default).
--   2. Funds support a COMPLETED status, distinct from ARCHIVED.
-- Re-runnable. Drops stored currency values; preserves balances and other fund data.
-- ═══════════════════════════════════════════════════════════════════════════

-- 1. Drop the per-fund currency column.
ALTER TABLE funds DROP COLUMN IF EXISTS currency;

-- 2. Allow COMPLETED in the status check.
--    The inline CHECK from 02_funds.sql is auto-named `funds_status_check`.
ALTER TABLE funds DROP CONSTRAINT IF EXISTS funds_status_check;
ALTER TABLE funds ADD CONSTRAINT funds_status_check
    CHECK (status IN ('ACTIVE', 'COMPLETED', 'ARCHIVED'));
