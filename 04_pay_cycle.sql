-- ═══════════════════════════════════════════════════════════════════════════
-- 04_pay_cycle.sql — Pay cycle foundation: salary wallet + configured payday rule
-- Depends on: 01_init.sql + 02_funds.sql + 03_fund_currency_status.sql (existing databases)
-- Apply with: psql -v ON_ERROR_STOP=1 --single-transaction -U savix-admin -d savix -f 04_pay_cycle.sql
--
--   1. `users.salary_wallet_id` — the wallet the salary lands in (drives pay-cycle resolution).
--   2. `user_payday_rules` — optional user-configured payday rule (day of month + weekend shift).
--   3. Backfill: for users without a salary wallet, pick the (non-fund) wallet holding their most
--      recent anchor-category transaction. Users without an anchor category stay NULL and can set
--      the salary wallet from /wallets.
-- Idempotent; safe to re-run (additive only, backfill touches NULLs only).
-- ═══════════════════════════════════════════════════════════════════════════

-- 1. Salary wallet reference.
alter table users add column if not exists salary_wallet_id int references wallets(id) on delete set null;

-- 2. Configured payday rule (one per user).
create table if not exists user_payday_rules (
    user_id       uuid primary key references users(id) on delete cascade,
    day_of_month  int  not null check (day_of_month between 1 and 31),
    weekend_shift varchar(24) not null default 'PREVIOUS_BUSINESS_DAY'
                  check (weekend_shift in ('NONE','PREVIOUS_BUSINESS_DAY','NEXT_BUSINESS_DAY')),
    updated_at    timestamp default now()
);

-- 3. Backfill: the wallet holding each user's latest anchor-category transaction.
update users u set salary_wallet_id = sub.wallet_id from (
    select distinct on (w.user_id) w.user_id, t.wallet_id
    from transactions t join wallets w on w.id = t.wallet_id
    join categories c on c.id = t.category_id and c.is_cycle_anchor
    where w.is_fund = false
    order by w.user_id, t.transaction_date desc, t.id desc) sub
where u.id = sub.user_id and u.salary_wallet_id is null;
