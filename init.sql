create table users (
    id serial primary key,
    username varchar(100) unique not null,
    password varchar(100) not null,
    created_at timestamp default NOW()
);

create table categories (
    id serial primary key,
    fk_user_id int not null references users(id) on delete cascade,
    name varchar(50) not null,
    created_at timestamp default NOW(),
    unique(fk_user_id, name)
);

create table transactions (
    id bigserial primary key,
    fk_user_id int not null references users(id) on delete cascade,
    fk_category_id int not null references categories(id) on delete cascade,
    title varchar(50) not null,
    amount numeric(12,2) not null,
    transaction_date date not null,
    notes text,
    importance varchar(20) not null check (importance in (
        'ESSENTIAL', 'HAVE_TO_HAVE', 'NICE_TO_HAVE', 'SHOULDNT_HAVE'
    )),
    cycle varchar(20) not null check (cycle in (
        'ONE_TIME', 'MONTHLY', 'YEARLY', 'WEEKLY', 'IRREGULAR'
    )),
    type varchar(20) not null check (type in ('INCOME', 'EXPENSE')),
    created_at timestamp default NOW()
);