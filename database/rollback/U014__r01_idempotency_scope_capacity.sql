-- Development-only rollback. Refuse to truncate any already persisted scope.
DO $$
BEGIN
  IF EXISTS (
    SELECT 1 FROM hhy.idempotency_records WHERE char_length(scope) > 64
  ) THEN
    RAISE EXCEPTION 'R01_IDEMPOTENCY_SCOPE_ROLLBACK_BLOCKED_LONG_VALUES_EXIST'
      USING ERRCODE = '23514';
  END IF;
END;
$$;

ALTER TABLE hhy.idempotency_records
  ALTER COLUMN scope TYPE varchar(64);

COMMENT ON COLUMN hhy.idempotency_records.scope IS NULL;
