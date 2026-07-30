DO $$
BEGIN
  IF EXISTS (
    SELECT 1 FROM hhy.identity_provider_requests
    WHERE response_cipher IS NOT NULL AND length(response_cipher) > 255
  ) THEN
    RAISE EXCEPTION 'R05_IDENTITY_PROVIDER_PAYLOAD_TOO_LONG_FOR_U025_ROLLBACK';
  END IF;
END $$;

ALTER TABLE hhy.identity_provider_requests
  ALTER COLUMN response_cipher TYPE varchar(255);
