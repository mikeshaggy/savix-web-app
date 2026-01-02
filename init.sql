create table users (
    id serial primary key,
    username varchar(100) unique not null,
    password varchar(100) not null,
    created_at timestamp default NOW()
);

create table wallets (
    id serial primary key,
    user_id int not null references users(id) on delete cascade,
    name varchar(50) not null,
    balance numeric(12,2) not null default 0.00,
    created_at timestamp default NOW(),
    unique(user_id, name)
);

create table categories (
    id serial primary key,
    user_id int not null references users(id) on delete cascade,
    name varchar(50) not null,
    type varchar(20) not null check (type in ('INCOME', 'EXPENSE')),
    created_at timestamp default NOW(),
    unique(user_id, name, type)
);

create table transactions (
    id bigserial primary key,
    wallet_id int not null references wallets(id) on delete cascade,
    category_id int not null references categories(id) on delete cascade,
    title varchar(50) not null,
    amount numeric(12,2) not null,
    transaction_date date not null,
    notes text,
    importance varchar(20) check (importance in (
        'ESSENTIAL', 'HAVE_TO_HAVE', 'NICE_TO_HAVE', 'SHOULDNT_HAVE', 'INVESTMENT'
    )),
    cycle varchar(20) not null check (cycle in (
        'ONE_TIME', 'MONTHLY', 'YEARLY', 'WEEKLY', 'IRREGULAR'
    )),
    created_at timestamp default NOW()
);

CREATE INDEX idx_transactions_wallet ON transactions(wallet_id);
CREATE INDEX idx_transactions_category ON transactions(category_id);
CREATE INDEX idx_transactions_date ON transactions(transaction_date);
CREATE INDEX idx_categories_user ON categories(user_id);