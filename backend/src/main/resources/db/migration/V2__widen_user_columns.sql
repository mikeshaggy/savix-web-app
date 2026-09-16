-- Data-preserving reconciliation only; retain all other accepted V1 semantics.
ALTER TABLE users
    ALTER COLUMN email TYPE varchar(255),
    ALTER COLUMN password_hash TYPE varchar(255);
