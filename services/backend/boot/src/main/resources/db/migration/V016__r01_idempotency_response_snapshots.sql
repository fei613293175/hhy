-- R01 durable first-response snapshots for administrator idempotency.
-- Both snapshot columns are nullable for legacy response_ref-only records, but
-- a new snapshot is always written as an atomic pair.
ALTER TABLE hhy.idempotency_records
  ADD COLUMN response_type varchar(128),
  ADD COLUMN response_payload_ciphertext text;

ALTER TABLE hhy.idempotency_records
  ADD CONSTRAINT ck_idempotency_records_snapshot_pair
  CHECK (
    (response_type IS NULL AND response_payload_ciphertext IS NULL) OR
    (btrim(response_type) <> '' AND response_payload_ciphertext IS NOT NULL
      AND btrim(response_payload_ciphertext) <> '')
  );

COMMENT ON COLUMN hhy.idempotency_records.response_type IS
  'Versioned response DTO type bound into the authenticated snapshot AAD';
COMMENT ON COLUMN hhy.idempotency_records.response_payload_ciphertext IS
  'Versioned AES-256-GCM envelope of the immutable first response; never plaintext';
