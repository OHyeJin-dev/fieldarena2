-- V13__add_audit_columns.sql
-- Add created_by/updated_by to all 5 tables; add updated_at to users only.
-- All timestamp columns follow V8 KST naive TIMESTAMP convention.

-- users: created_at already exists (V10). Add updated_at + created_by + updated_by.
ALTER TABLE users
  ADD COLUMN updated_at TIMESTAMP,
  ADD COLUMN created_by VARCHAR(50),
  ADD COLUMN updated_by VARCHAR(50);

-- customers / policies / proposals / claims: created_at + updated_at already exist. Only by-columns missing.
ALTER TABLE customers
  ADD COLUMN created_by VARCHAR(50),
  ADD COLUMN updated_by VARCHAR(50);

ALTER TABLE policies
  ADD COLUMN created_by VARCHAR(50),
  ADD COLUMN updated_by VARCHAR(50);

ALTER TABLE proposals
  ADD COLUMN created_by VARCHAR(50),
  ADD COLUMN updated_by VARCHAR(50);

ALTER TABLE claims
  ADD COLUMN created_by VARCHAR(50),
  ADD COLUMN updated_by VARCHAR(50);

-- Backfill existing rows with SYSTEM sentinel
UPDATE users
   SET updated_at = COALESCE(created_at, (now() AT TIME ZONE 'Asia/Seoul'))
 WHERE updated_at IS NULL;
UPDATE users     SET created_by = 'SYSTEM', updated_by = 'SYSTEM' WHERE created_by IS NULL;
UPDATE customers SET created_by = 'SYSTEM', updated_by = 'SYSTEM' WHERE created_by IS NULL;
UPDATE policies  SET created_by = 'SYSTEM', updated_by = 'SYSTEM' WHERE created_by IS NULL;
UPDATE proposals SET created_by = 'SYSTEM', updated_by = 'SYSTEM' WHERE created_by IS NULL;
UPDATE claims    SET created_by = 'SYSTEM', updated_by = 'SYSTEM' WHERE created_by IS NULL;

-- Enforce NOT NULL on all new columns
ALTER TABLE users
  ALTER COLUMN updated_at SET NOT NULL,
  ALTER COLUMN created_by SET NOT NULL,
  ALTER COLUMN updated_by SET NOT NULL;
ALTER TABLE customers ALTER COLUMN created_by SET NOT NULL, ALTER COLUMN updated_by SET NOT NULL;
ALTER TABLE policies  ALTER COLUMN created_by SET NOT NULL, ALTER COLUMN updated_by SET NOT NULL;
ALTER TABLE proposals ALTER COLUMN created_by SET NOT NULL, ALTER COLUMN updated_by SET NOT NULL;
ALTER TABLE claims    ALTER COLUMN created_by SET NOT NULL, ALTER COLUMN updated_by SET NOT NULL;

-- Set DEFAULT on users.updated_at for safety (matches V8 KST convention)
ALTER TABLE users ALTER COLUMN updated_at SET DEFAULT (now() AT TIME ZONE 'Asia/Seoul');
