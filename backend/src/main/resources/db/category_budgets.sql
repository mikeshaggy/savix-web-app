-- Category Budgets schema — standalone migration for EXISTING databases.
--
-- This table is already included in 01_init.sql (Docker / fresh setup).
-- Run this script manually against an existing database that was created
-- before the Category Budgets feature was added.
--
-- Usage:
--   psql -v ON_ERROR_STOP=1 --single-transaction -U <user> -d savix -f category_budgets.sql

create table if not exists category_budgets (
    id                        serial primary key,
    wallet_id                 int            not null references wallets(id) on delete cascade,
    category_id               int            not null references categories(id) on delete cascade,
    amount                    numeric(12, 2) not null check (amount > 0),
    warning_threshold_percent int            not null default 80
                                             check (warning_threshold_percent between 1 and 100),
    active                    boolean        not null default true,
    created_at                timestamp      default NOW(),
    updated_at                timestamp      default NOW()
);

create unique index if not exists uq_category_budgets_active
    on category_budgets (wallet_id, category_id)
    where active = true;

create index if not exists idx_category_budgets_wallet   on category_budgets (wallet_id);
create index if not exists idx_category_budgets_category on category_budgets (category_id);
