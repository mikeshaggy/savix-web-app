-- Accepted production catalog recorded by SHG-11 on 2026-09-15 (PostgreSQL 16.11).
-- Fresh databases execute V1. Existing production must be explicitly baselined at 1.
-- Preserve names, widths, timestamp semantics, column order and the live index set.
-- Unqualified names intentionally use the explicit Flyway default schema.
-- Once applied, this migration is immutable; add V3+ for subsequent changes.

CREATE TABLE categories (
    id serial NOT NULL,
    user_id uuid NOT NULL,
    name character varying(50) NOT NULL,
    type character varying(20) NOT NULL,
    created_at timestamp without time zone DEFAULT now(),
    emoji character varying(16),
    is_cycle_anchor boolean DEFAULT false NOT NULL,
    excluded_from_top_categories boolean DEFAULT false NOT NULL
);

CREATE TABLE category_budgets (
    id serial NOT NULL,
    wallet_id integer NOT NULL,
    category_id integer NOT NULL,
    amount numeric(12,2) NOT NULL,
    warning_threshold_percent integer DEFAULT 80 NOT NULL,
    active boolean DEFAULT true NOT NULL,
    created_at timestamp without time zone DEFAULT now(),
    updated_at timestamp without time zone DEFAULT now()
);

CREATE TABLE fixed_payment_occurrences (
    id bigserial NOT NULL,
    fixed_payment_id integer NOT NULL,
    due_date date NOT NULL,
    expected_amount numeric(12,2) NOT NULL,
    paid_amount numeric(12,2),
    status character varying(20) NOT NULL,
    paid_at timestamp without time zone,
    transaction_id bigint,
    created_at timestamp without time zone DEFAULT now()
);

CREATE TABLE fixed_payments (
    id serial NOT NULL,
    wallet_id integer NOT NULL,
    category_id integer NOT NULL,
    title character varying(50) NOT NULL,
    amount numeric(12,2) NOT NULL,
    anchor_date date NOT NULL,
    cycle character varying(20) NOT NULL,
    active_from date NOT NULL,
    active_to date,
    notes text,
    created_at timestamp without time zone DEFAULT now()
);

CREATE TABLE funds (
    id bigserial NOT NULL,
    user_id uuid NOT NULL,
    fund_wallet_id integer NOT NULL,
    source_wallet_id integer,
    name character varying(100) NOT NULL,
    description text,
    target_amount numeric(12,2) NOT NULL,
    status character varying(20) DEFAULT 'ACTIVE'::character varying NOT NULL,
    emoji character varying(16),
    color character varying(20),
    deadline_date date,
    created_at timestamp without time zone DEFAULT now(),
    updated_at timestamp without time zone DEFAULT now()
);

CREATE TABLE transactions (
    id bigserial NOT NULL,
    wallet_id integer NOT NULL,
    category_id integer NOT NULL,
    title character varying(50) NOT NULL,
    amount numeric(12,2) NOT NULL,
    transaction_date date NOT NULL,
    notes text,
    importance character varying(20),
    created_at timestamp without time zone DEFAULT now()
);

CREATE TABLE transfers (
    id bigserial NOT NULL,
    from_wallet_id integer NOT NULL,
    to_wallet_id integer NOT NULL,
    amount numeric(12,2) NOT NULL,
    transfer_date date NOT NULL,
    notes text,
    created_at timestamp without time zone DEFAULT now()
);

CREATE TABLE users (
    id uuid DEFAULT gen_random_uuid() NOT NULL,
    email character varying(100) NOT NULL,
    username character varying(100) NOT NULL,
    password_hash character varying(100) NOT NULL,
    created_at timestamp without time zone DEFAULT now(),
    updated_at timestamp without time zone DEFAULT now()
);

CREATE TABLE wallet_entries (
    id bigserial NOT NULL,
    wallet_id integer NOT NULL,
    amount_signed numeric(12,2) NOT NULL,
    entry_date date NOT NULL,
    source_type character varying(20) NOT NULL,
    source_id bigint NOT NULL,
    created_at timestamp without time zone DEFAULT now(),
    balance_after numeric(12,2)
);

CREATE TABLE wallets (
    id serial NOT NULL,
    user_id uuid NOT NULL,
    name character varying(50) NOT NULL,
    balance numeric(12,2) DEFAULT 0.00 NOT NULL,
    created_at timestamp without time zone DEFAULT now(),
    version integer DEFAULT 0 NOT NULL,
    is_fund boolean DEFAULT false NOT NULL
);

ALTER TABLE categories ADD CONSTRAINT categories_pkey PRIMARY KEY (id);
ALTER TABLE categories ADD CONSTRAINT categories_type_check CHECK (((type)::text = ANY (ARRAY[('INCOME'::character varying)::text, ('EXPENSE'::character varying)::text])));
ALTER TABLE categories ADD CONSTRAINT categories_user_id_name_type_key UNIQUE (user_id, name, type);
ALTER TABLE category_budgets ADD CONSTRAINT category_budgets_amount_check CHECK ((amount > (0)::numeric));
ALTER TABLE category_budgets ADD CONSTRAINT category_budgets_pkey PRIMARY KEY (id);
ALTER TABLE category_budgets ADD CONSTRAINT category_budgets_warning_threshold_percent_check CHECK (((warning_threshold_percent >= 1) AND (warning_threshold_percent <= 100)));
ALTER TABLE fixed_payment_occurrences ADD CONSTRAINT fixed_payment_occurrences_expected_amount_check CHECK ((expected_amount > (0)::numeric));
ALTER TABLE fixed_payment_occurrences ADD CONSTRAINT fixed_payment_occurrences_fixed_payment_id_due_date_key UNIQUE (fixed_payment_id, due_date);
ALTER TABLE fixed_payment_occurrences ADD CONSTRAINT fixed_payment_occurrences_paid_amount_check CHECK ((paid_amount > (0)::numeric));
ALTER TABLE fixed_payment_occurrences ADD CONSTRAINT fixed_payment_occurrences_pkey PRIMARY KEY (id);
ALTER TABLE fixed_payment_occurrences ADD CONSTRAINT fixed_payment_occurrences_status_check CHECK (((status)::text = ANY (ARRAY[('PENDING'::character varying)::text, ('PAID'::character varying)::text, ('OVERDUE'::character varying)::text, ('SKIPPED'::character varying)::text])));
ALTER TABLE fixed_payments ADD CONSTRAINT fixed_payments_amount_check CHECK ((amount > (0)::numeric));
ALTER TABLE fixed_payments ADD CONSTRAINT fixed_payments_cycle_check CHECK (((cycle)::text = ANY (ARRAY[('WEEKLY'::character varying)::text, ('MONTHLY'::character varying)::text, ('QUARTERLY'::character varying)::text, ('YEARLY'::character varying)::text])));
ALTER TABLE fixed_payments ADD CONSTRAINT fixed_payments_pkey PRIMARY KEY (id);
ALTER TABLE funds ADD CONSTRAINT funds_pkey PRIMARY KEY (id);
ALTER TABLE funds ADD CONSTRAINT funds_status_check CHECK (((status)::text = ANY ((ARRAY['ACTIVE'::character varying, 'COMPLETED'::character varying, 'ARCHIVED'::character varying])::text[])));
ALTER TABLE funds ADD CONSTRAINT funds_target_amount_check CHECK ((target_amount > (0)::numeric));
ALTER TABLE transactions ADD CONSTRAINT transactions_amount_positive CHECK ((amount > (0)::numeric));
ALTER TABLE transactions ADD CONSTRAINT transactions_importance_check CHECK (((importance)::text = ANY (ARRAY[('ESSENTIAL'::character varying)::text, ('HAVE_TO_HAVE'::character varying)::text, ('NICE_TO_HAVE'::character varying)::text, ('SHOULDNT_HAVE'::character varying)::text, ('INVESTMENT'::character varying)::text])));
ALTER TABLE transactions ADD CONSTRAINT transactions_pkey PRIMARY KEY (id);
ALTER TABLE transfers ADD CONSTRAINT no_self_transfer CHECK ((from_wallet_id <> to_wallet_id));
ALTER TABLE transfers ADD CONSTRAINT transfers_amount_check CHECK ((amount > (0)::numeric));
ALTER TABLE transfers ADD CONSTRAINT transfers_pkey PRIMARY KEY (id);
ALTER TABLE users ADD CONSTRAINT users_email_key UNIQUE (email);
ALTER TABLE users ADD CONSTRAINT users_pkey PRIMARY KEY (id);
ALTER TABLE users ADD CONSTRAINT users_username_key UNIQUE (username);
ALTER TABLE wallet_entries ADD CONSTRAINT wallet_entries_amount_signed_check CHECK ((amount_signed <> (0)::numeric));
ALTER TABLE wallet_entries ADD CONSTRAINT wallet_entries_pkey PRIMARY KEY (id);
ALTER TABLE wallet_entries ADD CONSTRAINT wallet_entries_source_type_check CHECK (((source_type)::text = ANY (ARRAY[('TRANSACTION'::character varying)::text, ('TRANSFER'::character varying)::text, ('ADJUSTMENT'::character varying)::text])));
ALTER TABLE wallets ADD CONSTRAINT wallets_pkey PRIMARY KEY (id);
ALTER TABLE categories ADD CONSTRAINT categories_user_id_fkey FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE;
ALTER TABLE category_budgets ADD CONSTRAINT category_budgets_category_id_fkey FOREIGN KEY (category_id) REFERENCES categories(id) ON DELETE CASCADE;
ALTER TABLE category_budgets ADD CONSTRAINT category_budgets_wallet_id_fkey FOREIGN KEY (wallet_id) REFERENCES wallets(id) ON DELETE CASCADE;
ALTER TABLE fixed_payment_occurrences ADD CONSTRAINT fixed_payment_occurrences_fixed_payment_id_fkey FOREIGN KEY (fixed_payment_id) REFERENCES fixed_payments(id) ON DELETE CASCADE;
ALTER TABLE fixed_payment_occurrences ADD CONSTRAINT fixed_payment_occurrences_transaction_id_fkey FOREIGN KEY (transaction_id) REFERENCES transactions(id) ON DELETE SET NULL;
ALTER TABLE fixed_payments ADD CONSTRAINT fixed_payments_category_id_fkey FOREIGN KEY (category_id) REFERENCES categories(id) ON DELETE CASCADE;
ALTER TABLE fixed_payments ADD CONSTRAINT fixed_payments_wallet_id_fkey FOREIGN KEY (wallet_id) REFERENCES wallets(id) ON DELETE CASCADE;
ALTER TABLE funds ADD CONSTRAINT funds_fund_wallet_id_fkey FOREIGN KEY (fund_wallet_id) REFERENCES wallets(id) ON DELETE RESTRICT;
ALTER TABLE funds ADD CONSTRAINT funds_source_wallet_id_fkey FOREIGN KEY (source_wallet_id) REFERENCES wallets(id) ON DELETE SET NULL;
ALTER TABLE funds ADD CONSTRAINT funds_user_id_fkey FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE;
ALTER TABLE transactions ADD CONSTRAINT transactions_category_id_fkey FOREIGN KEY (category_id) REFERENCES categories(id) ON DELETE CASCADE;
ALTER TABLE transactions ADD CONSTRAINT transactions_wallet_id_fkey FOREIGN KEY (wallet_id) REFERENCES wallets(id) ON DELETE CASCADE;
ALTER TABLE transfers ADD CONSTRAINT transfers_from_wallet_id_fkey FOREIGN KEY (from_wallet_id) REFERENCES wallets(id) ON DELETE CASCADE;
ALTER TABLE transfers ADD CONSTRAINT transfers_to_wallet_id_fkey FOREIGN KEY (to_wallet_id) REFERENCES wallets(id) ON DELETE CASCADE;
ALTER TABLE wallet_entries ADD CONSTRAINT wallet_entries_wallet_id_fkey FOREIGN KEY (wallet_id) REFERENCES wallets(id) ON DELETE CASCADE;
ALTER TABLE wallets ADD CONSTRAINT wallets_user_id_fkey FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE;

-- Standalone indexes (constraint-owned indexes are created above).
CREATE INDEX idx_categories_user ON categories USING btree (user_id);
CREATE UNIQUE INDEX uq_categories_one_cycle_anchor_per_user ON categories USING btree (user_id) WHERE (is_cycle_anchor = true);
CREATE UNIQUE INDEX uq_categories_user_emoji ON categories USING btree (user_id, emoji) WHERE (emoji IS NOT NULL);
CREATE INDEX idx_category_budgets_category ON category_budgets USING btree (category_id);
CREATE INDEX idx_category_budgets_wallet ON category_budgets USING btree (wallet_id);
CREATE UNIQUE INDEX uq_category_budgets_active ON category_budgets USING btree (wallet_id, category_id) WHERE (active = true);
CREATE INDEX idx_fpo_payment_date_status ON fixed_payment_occurrences USING btree (fixed_payment_id, due_date, status);
CREATE INDEX idx_fpo_payment_status_date ON fixed_payment_occurrences USING btree (fixed_payment_id, status, due_date);
CREATE UNIQUE INDEX idx_fpo_transaction ON fixed_payment_occurrences USING btree (transaction_id) WHERE (transaction_id IS NOT NULL);
CREATE INDEX idx_fixed_payments_wallet ON fixed_payments USING btree (wallet_id);
CREATE INDEX idx_fixed_payments_wallet_active_to ON fixed_payments USING btree (wallet_id, active_to);
CREATE INDEX idx_funds_user ON funds USING btree (user_id);
CREATE INDEX idx_funds_user_status ON funds USING btree (user_id, status);
CREATE INDEX idx_funds_wallet ON funds USING btree (fund_wallet_id);
CREATE UNIQUE INDEX uq_active_fund_name ON funds USING btree (user_id, lower((name)::text)) WHERE ((status)::text = 'ACTIVE'::text);
CREATE INDEX idx_transactions_category ON transactions USING btree (category_id);
CREATE INDEX idx_transactions_date ON transactions USING btree (transaction_date);
CREATE INDEX idx_transactions_wallet ON transactions USING btree (wallet_id);
CREATE INDEX idx_transactions_wallet_category_date ON transactions USING btree (wallet_id, category_id, transaction_date DESC);
CREATE INDEX idx_transactions_wallet_date_importance ON transactions USING btree (wallet_id, transaction_date, importance);
CREATE INDEX idx_transfers_date ON transfers USING btree (transfer_date);
CREATE INDEX idx_transfers_from_wallet_date ON transfers USING btree (from_wallet_id, transfer_date);
CREATE INDEX idx_transfers_to_wallet_date ON transfers USING btree (to_wallet_id, transfer_date);
CREATE INDEX idx_wallet_entries_source ON wallet_entries USING btree (source_type, source_id);
CREATE INDEX idx_wallet_entries_wallet_date ON wallet_entries USING btree (wallet_id, entry_date);
CREATE INDEX idx_wallet_entries_wallet_history_order ON wallet_entries USING btree (wallet_id, entry_date DESC, created_at DESC, id DESC);
CREATE INDEX idx_wallet_entries_wallet_ledger_order ON wallet_entries USING btree (wallet_id, entry_date, created_at, id);
CREATE UNIQUE INDEX uq_wallets_user_name ON wallets USING btree (user_id, name) WHERE (is_fund = false);
