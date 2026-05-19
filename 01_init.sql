create table users (
    id uuid primary key default gen_random_uuid(),
    email varchar(100) unique not null,
    username varchar(100) unique not null,
    password_hash varchar(255) not null,
    created_at timestamp default NOW(),
    updated_at timestamp default NOW()
);

create table wallets (
    id serial primary key,
    user_id uuid not null references users(id) on delete cascade,
    name varchar(50) not null,
    balance numeric(12,2) not null default 0.00,
    version integer not null default 0,
    created_at timestamp default NOW(),
    unique(user_id, name)
);

create table categories (
    id serial primary key,
    user_id uuid not null references users(id) on delete cascade,
    name varchar(50) not null,
    type varchar(20) not null check (type in ('INCOME', 'EXPENSE')),
    emoji varchar(16),
    is_cycle_anchor boolean not null default false,
    excluded_from_top_categories boolean not null default false,
    created_at timestamp default NOW(),
    unique(user_id, name, type),
    unique(user_id) where is_cycle_anchor = true
);

create table transactions (
    id bigserial primary key,
    wallet_id int not null references wallets(id) on delete cascade,
    category_id int not null references categories(id) on delete cascade,
    title varchar(50) not null,
    amount numeric(12,2) not null check (amount > 0),
    transaction_date date not null,
    notes text,
    importance varchar(20) check (importance in (
        'ESSENTIAL', 'HAVE_TO_HAVE', 'NICE_TO_HAVE', 'SHOULDNT_HAVE', 'INVESTMENT'
    )),
    created_at timestamp default NOW()
);

create table transfers (
    id bigserial primary key,
    from_wallet_id int not null references wallets(id) on delete cascade,
    to_wallet_id int not null references wallets(id) on delete cascade,
    amount numeric(12,2) not null check (amount > 0),
    transfer_date date not null,
    notes text,
    created_at timestamp default NOW(),
    constraint no_self_transfer check (from_wallet_id <> to_wallet_id)
);

create table wallet_entries (
    id bigserial primary key,
    wallet_id int not null references wallets(id) on delete cascade,
    amount_signed numeric(12,2) not null check (amount_signed <> 0),
    entry_date date not null,
    source_type varchar(20) not null check (source_type in (
        'TRANSACTION', 'TRANSFER', 'ADJUSTMENT'
    )),
    source_id bigint not null,
    created_at timestamp default NOW()
);

create table fixed_payments (
    id serial primary key,
    wallet_id int not null references wallets(id) on delete cascade,
    category_id int not null references categories(id) on delete cascade,
    title varchar(50) not null,
    amount numeric(12,2) not null check (amount > 0),
    anchor_date date not null,
    cycle varchar(20) not null check (cycle in ('WEEKLY', 'MONTHLY', 'QUARTERLY', 'YEARLY')),
    active_from date not null,
    active_to date,
    notes text,
    created_at timestamp default NOW()
);

create table fixed_payment_occurrences (
    id bigserial primary key,
    fixed_payment_id int not null references fixed_payments(id) on delete cascade,
    due_date  date not null,
    expected_amount numeric(12,2) not null check (expected_amount > 0),
    paid_amount numeric(12,2) check (paid_amount > 0),
    status varchar(20) not null check (status in ('PENDING', 'PAID', 'OVERDUE', 'SKIPPED')),
    paid_at timestamp,
    transaction_id bigint references transactions(id) on delete set null,
    created_at timestamp default NOW(),
    unique(fixed_payment_id, due_date)
);

-- transactions
CREATE INDEX idx_transactions_wallet ON transactions(wallet_id);
CREATE INDEX idx_transactions_category ON transactions(category_id);
CREATE INDEX idx_transactions_date ON transactions(transaction_date);
CREATE INDEX idx_transactions_wallet_date ON transactions(wallet_id, transaction_date);
CREATE INDEX idx_transactions_wallet_date_category ON transactions(wallet_id, transaction_date, category_id);

-- categories
CREATE INDEX idx_categories_user ON categories(user_id);
CREATE UNIQUE INDEX uq_categories_user_emoji ON categories(user_id, emoji) WHERE emoji IS NOT NULL;
CREATE INDEX idx_categories_cycle_anchor ON categories(user_id) WHERE is_cycle_anchor = true;

-- transfers
CREATE INDEX idx_transfers_from_wallet_date ON transfers(from_wallet_id, transfer_date);
CREATE INDEX idx_transfers_to_wallet_date ON transfers(to_wallet_id, transfer_date);
CREATE INDEX idx_transfers_date ON transfers(transfer_date);

-- wallet entries
CREATE INDEX idx_wallet_entries_wallet_date ON wallet_entries(wallet_id, entry_date);
CREATE INDEX idx_wallet_entries_source ON wallet_entries(source_type, source_id);

-- fixed payments
CREATE INDEX idx_fixed_payments_wallet ON fixed_payments(wallet_id);

-- fixed payment occurrences
CREATE INDEX idx_fpo_payment_date_status
    ON fixed_payment_occurrences(fixed_payment_id, due_date, status);
CREATE INDEX idx_fpo_transaction
    ON fixed_payment_occurrences(transaction_id)
    WHERE transaction_id IS NOT NULL;
