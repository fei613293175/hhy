-- Development-only rollback. Never discard an encrypted first-response fact.
DO $$
BEGIN
  IF EXISTS (
    SELECT 1 FROM hhy.idempotency_records
    WHERE response_type IS NOT NULL OR response_payload_ciphertext IS NOT NULL
  ) THEN
    RAISE EXCEPTION 'R01_IDEMPOTENCY_SNAPSHOT_ROLLBACK_BLOCKED_SNAPSHOTS_EXIST'
      USING ERRCODE = '23514';
  END IF;
END;
$$;

ALTER TABLE hhy.idempotency_records
  DROP CONSTRAINT ck_idempotency_records_snapshot_pair;

ALTER TABLE hhy.idempotency_records
  DROP COLUMN response_payload_ciphertext,
  DROP COLUMN response_type;
