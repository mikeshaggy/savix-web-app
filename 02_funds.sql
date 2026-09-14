-- ═══════════════════════════════════════════════════════════════════════════
-- 02_funds.sql — Saving Goals / Funds feature
-- Depends on: 01_init.sql
-- Apply with: psql -v ON_ERROR_STOP=1 --single-transaction -U savix-admin -d savix -f 02_funds.sql
-- ═══════════════════════════════════════════════════════════════════════════

-- ───────────────────────────────────────────────────────────────────────────
-- PHASE 1 — Wallet flag
-- Marks wallets that are hidden fund wallets.
-- Fund wallets are created automatically when a Fund entity is saved.
-- They must not appear in normal wallet lists or transfer selectors.
-- ───────────────────────────────────────────────────────────────────────────

ALTER TABLE wallets
    ADD COLUMN IF NOT EXISTS is_fund BOOLEAN NOT NULL DEFAULT FALSE;

-- ───────────────────────────────────────────────────────────────────────────
-- PHASE 2A — Relax wallet name uniqueness for fund wallets
--
-- The original unique(user_id, name) constraint on wallets was designed for
-- user-visible wallets only. Fund wallets are hidden and must not block:
--   1. Users creating a fund with the same name as a regular wallet
--   2. Users archiving a fund and creating a new one with the same name
--
-- Solution: drop the table-level constraint and replace it with a partial
-- unique index that only covers non-fund wallets.
-- ───────────────────────────────────────────────────────────────────────────

ALTER TABLE wallets
    DROP CONSTRAINT IF EXISTS wallets_user_id_name_key;

CREATE UNIQUE INDEX IF NOT EXISTS uq_wallets_user_name
    ON wallets(user_id, name)
    WHERE is_fund = false;

-- ───────────────────────────────────────────────────────────────────────────
-- PHASE 2B — Funds table
-- ───────────────────────────────────────────────────────────────────────────

CREATE TABLE IF NOT EXISTS funds (
    id               BIGSERIAL PRIMARY KEY,
    user_id          UUID    NOT NULL REFERENCES users(id)   ON DELETE CASCADE,
    fund_wallet_id   INT     NOT NULL REFERENCES wallets(id) ON DELETE RESTRICT,
    source_wallet_id INT              REFERENCES wallets(id) ON DELETE SET NULL,
    name             VARCHAR(100) NOT NULL,
    description      TEXT,
    target_amount    NUMERIC(12,2) NOT NULL CHECK (target_amount > 0),
    currency         VARCHAR(3)    NOT NULL DEFAULT 'PLN',
    status           VARCHAR(20)   NOT NULL DEFAULT 'ACTIVE'
                         CHECK (status IN ('ACTIVE', 'ARCHIVED')),
    emoji            VARCHAR(16),
    color            VARCHAR(20),
    deadline_date    DATE,
    created_at       TIMESTAMP DEFAULT NOW(),
    updated_at       TIMESTAMP DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_funds_user
    ON funds(user_id);

CREATE INDEX IF NOT EXISTS idx_funds_user_status
    ON funds(user_id, status);

CREATE INDEX IF NOT EXISTS idx_funds_wallet
    ON funds(fund_wallet_id);

-- Active-only unique fund name (case-insensitive).
-- Archived funds do not block reuse of the same name for a new active fund.
CREATE UNIQUE INDEX IF NOT EXISTS uq_active_fund_name
    ON funds(user_id, lower(name))
    WHERE status = 'ACTIVE';
