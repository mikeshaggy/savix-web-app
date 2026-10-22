-- ═══════════════════════════════════════════════════════════════════════════
-- 06_insights.sql — Insights v2 persistence (Stage 7.1)
-- Depends on: 01_init.sql + 02_funds.sql + 03_fund_currency_status.sql + 04_pay_cycle.sql + 05_pace_exclusion.sql
-- Apply with: psql -v ON_ERROR_STOP=1 --single-transaction -U savix-admin -d savix -f 06_insights.sql
--
--   1. `insights` — one persisted insight event per (wallet, type, cycle start, dedupe key). The unique
--      constraint is the deduplication guarantee: concurrent evaluations of the same wallet and cycle cannot
--      store the same event twice, and a dismissed row keeps its key, so the event is never re-created
--      for that cycle. Dismissal sets `dismissed_at`; rows are never deleted by the application.
--   2. Partial index for the dashboard read (active insights of a wallet's current cycle).
--   3. Index on `user_id` (ownership checks, cascade on user delete).
-- Purely additive and idempotent; safe to re-run. Must be applied before `app.features.insights-v2` is enabled
-- (with the flag off the application never reads or writes this table).
-- ═══════════════════════════════════════════════════════════════════════════

-- 1. Insight events.
create table if not exists insights (
    id           bigserial primary key,
    user_id      uuid not null references users(id) on delete cascade,
    wallet_id    int  not null references wallets(id) on delete cascade,
    type         varchar(40) not null,
    severity     varchar(16) not null check (severity in ('INFO','WARN','ALERT')),
    cycle_start  date not null,
    dedupe_key   varchar(120) not null,
    title        varchar(200) not null,
    description  text,
    payload      jsonb,
    action_type  varchar(40),
    action_ref   varchar(120),
    created_at   timestamp not null default now(),
    dismissed_at timestamp,
    constraint uq_insights_wallet_type_cycle_key unique (wallet_id, type, cycle_start, dedupe_key)
);

-- 2. Active insights of a wallet's cycle.
create index if not exists idx_insights_wallet_cycle on insights (wallet_id, cycle_start) where dismissed_at is null;

-- 3. Ownership / cascade lookups.
create index if not exists idx_insights_user on insights (user_id);
