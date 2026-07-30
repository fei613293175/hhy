\set ON_ERROR_STOP on
SET search_path TO hhy, public;

DO $$
DECLARE
  row_id bigint;
  rejected boolean := false;
BEGIN
  INSERT INTO idempotency_records(scope, idem_key, request_hash, response_ref, expires_at)
  VALUES ('r01.snapshot.test', 'legacy-both-null', repeat('a', 64), 'legacy:1', clock_timestamp() + interval '1 day')
  RETURNING id INTO row_id;

  BEGIN
    UPDATE idempotency_records
    SET response_type = 'r01.command-result.v1'
    WHERE id = row_id;
  EXCEPTION WHEN check_violation THEN
    rejected := true;
  END;
  IF NOT rejected THEN
    RAISE EXCEPTION 'R01_SNAPSHOT_PARTIAL_PAIR_WAS_ACCEPTED';
  END IF;

  UPDATE idempotency_records
  SET response_ref = 'session:23',
      response_type = 'r01.command-result.v1',
      response_payload_ciphertext = 'hhy-idem-v1.A256GCM.v1.test-nonce.test-ciphertext'
  WHERE id = row_id
    AND response_type IS NULL
    AND response_payload_ciphertext IS NULL;

  IF NOT FOUND OR NOT EXISTS (
    SELECT 1 FROM idempotency_records
    WHERE id = row_id
      AND response_type = 'r01.command-result.v1'
      AND response_payload_ciphertext LIKE 'hhy-idem-v1.A256GCM.%'
  ) THEN
    RAISE EXCEPTION 'R01_SNAPSHOT_ATOMIC_PAIR_WRITE_FAILED';
  END IF;

  rejected := false;
  BEGIN
    UPDATE idempotency_records SET response_payload_ciphertext = NULL WHERE id = row_id;
  EXCEPTION WHEN check_violation THEN
    rejected := true;
  END;
  IF NOT rejected THEN
    RAISE EXCEPTION 'R01_SNAPSHOT_PARTIAL_CLEAR_WAS_ACCEPTED';
  END IF;

  DELETE FROM idempotency_records WHERE id = row_id;
END;
$$;

SELECT 'R01_IDEMPOTENCY_SNAPSHOT_PAIR_OK';
