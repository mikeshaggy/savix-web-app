-- All of these changes must roll back with the deliberate division-by-zero error.
UPDATE users SET username = 'must-rollback';
DELETE FROM wallet_entries;
ALTER TABLE users ADD COLUMN must_rollback text;
CREATE TABLE must_rollback (id integer);
SELECT 1 / 0;
