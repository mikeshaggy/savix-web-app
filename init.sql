create table users (
    id serial primary key,
    username varchar(255),
    password varchar(255),
    created_at timestamp default NOW()
)

create table categories (
    id serial primary key,
    user_id int not null references users(id) on delete cascade unique,
    name varchar(50),
    created_at timestamp default NOW()
)

create table transactions (
    id bigserial primary key,
    user_id int not null references users(id) on delete cascade unique,
    category_id int not null references categories(id) on delete cascade unique,
    amount numeric(12,2) not null,
    transaction_date date not null,
    importance varchar(100) not null check (importance in (
        'ESSENTIAL', 'HAVE_TO_HAVE', 'NICE_TO_HAVE', 'SHOULDNT_HAVE'
    )),
    notes text,
    cycle varchar(20) not null check (cycle in (
        'ONE_TIME', 'MONTHLY', 'YEARLY', 'WEEKLY', 'IRREGULAR'
    )),
    type varchar(20) check (type in ('INCOME', 'EXPENSE')),
    created_at timestamp default NOW()
)