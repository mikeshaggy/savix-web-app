-- Synthetic rows only. IDs, dates, names and hashes are invented for SHG-15.
INSERT INTO users (id, email, username, password_hash, created_at, updated_at)
VALUES ('00000000-0000-0000-0000-000000000015', 'shg15@example.invalid', 'shg15',
        'synthetic-not-a-real-password-hash', '2026-01-02 03:04:05', '2026-01-02 03:04:05');
INSERT INTO wallets (id, user_id, name, balance, is_fund) VALUES
    (1, '00000000-0000-0000-0000-000000000015', 'Everyday', 123.45, false),
    (2, '00000000-0000-0000-0000-000000000015', 'Savings', 50.00, true);
INSERT INTO categories (id, user_id, name, type, emoji) VALUES
    (1, '00000000-0000-0000-0000-000000000015', 'Bills', 'EXPENSE', 'x');
INSERT INTO transactions (id, wallet_id, category_id, title, amount, transaction_date, notes, importance)
VALUES (1, 1, 1, 'Synthetic bill', 12.34, '2026-01-03', 'fixture', 'ESSENTIAL');
INSERT INTO transfers (id, from_wallet_id, to_wallet_id, amount, transfer_date, notes)
VALUES (1, 1, 2, 50.00, '2026-01-04', 'fixture transfer');
INSERT INTO wallet_entries (id, wallet_id, amount_signed, entry_date, source_type, source_id, balance_after)
VALUES (1, 1, -12.34, '2026-01-03', 'TRANSACTION', 1, 123.45);
INSERT INTO fixed_payments (id, wallet_id, category_id, title, amount, anchor_date, cycle, active_from, notes)
VALUES (1, 1, 1, 'Monthly bill', 12.34, '2026-01-03', 'MONTHLY', '2026-01-01', 'fixture');
INSERT INTO fixed_payment_occurrences
    (id, fixed_payment_id, due_date, expected_amount, paid_amount, status, paid_at, transaction_id)
VALUES (1, 1, '2026-01-03', 12.34, 12.34, 'PAID', '2026-01-03 12:00:00', 1);
INSERT INTO category_budgets (id, wallet_id, category_id, amount)
VALUES (1, 1, 1, 200.00);
INSERT INTO funds (id, user_id, fund_wallet_id, source_wallet_id, name, description, target_amount,
                   status, emoji, color, deadline_date)
VALUES (1, '00000000-0000-0000-0000-000000000015', 2, 1, 'Rainy day', 'fixture fund',
        500.00, 'ACTIVE', 'x', 'blue', '2026-12-31');
