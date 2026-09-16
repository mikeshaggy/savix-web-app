-- ═══════════════════════════════════════════════════════════════════════════
-- 05_pace_exclusion.sql — Forecast v2 pace exclusion flags (Stage 4.1)
-- Depends on: 01_init.sql + 02_funds.sql + 03_fund_currency_status.sql + 04_pay_cycle.sql (existing databases)
-- Apply with: psql -v ON_ERROR_STOP=1 --single-transaction -U savix-admin -d savix -f 05_pace_exclusion.sql
--
--   1. `categories.excluded_from_pace`   — every transaction in the category stays out of the spending pace.
--   2. `transactions.excluded_from_pace` — per-transaction override (e.g. a one-off repayment).
--   3. One-shot seed: transfer-like EXPENSE categories (case-insensitive, per user) start excluded.
--   4. Partial index for the pace queries (only flagged rows are indexed).
-- Reporting totals, ledger, dashboard and fixed-payment sums ignore both flags; only the pace
-- inputs (`sumUnlinkedForPace`, `dailyVariableTotals`) read them.
-- Idempotent; safe to re-run. The seed runs only in the same step that adds `categories.excluded_from_pace`
-- (first upgrade of a legacy database): on a rerun, or on a database created from `01_init.sql`, the column
-- already exists, the seed is skipped and user choices made in the UI are preserved.
-- ═══════════════════════════════════════════════════════════════════════════

-- 1./3. Category flag + one-shot seed (only when this run adds the column).
do $$
begin
    if exists (
        select 1 from information_schema.columns
        where table_schema = current_schema()
          and table_name = 'categories'
          and column_name = 'excluded_from_pace'
    ) then
        raise notice 'categories.excluded_from_pace already exists, skipping column and seed';
        return;
    end if;

    execute 'alter table categories add column excluded_from_pace boolean not null default false';
    execute $seed$
        update categories set excluded_from_pace = true
         where type = 'EXPENSE'
           and lower(name) in ('money lent', 'refunds & settlements', 'refunds and settlements', 'pożyczki', 'zwroty')
    $seed$;
end
$$;

-- 2. Transaction override flag.
alter table transactions add column if not exists excluded_from_pace boolean not null default false;

-- 4. Pace-query index (flagged rows only).
create index if not exists idx_transactions_excluded_from_pace on transactions (wallet_id, excluded_from_pace) where excluded_from_pace;
