-- R01 administrator security uses a namespaced full HMAC-SHA256 digest as
-- the login idempotency scope. Preserve the full digest instead of truncating
-- security material to the legacy 64-character generic scope width.
ALTER TABLE hhy.idempotency_records
  ALTER COLUMN scope TYPE varchar(128);

COMMENT ON COLUMN hhy.idempotency_records.scope IS
  'Namespaced idempotency scope; supports full HMAC-SHA256 identifiers';
