ALTER TABLE hhy.identity_verification_sessions
  DROP CONSTRAINT IF EXISTS ck_r05_identity_session_failure,
  DROP CONSTRAINT IF EXISTS ck_r05_identity_session_consent,
  DROP COLUMN IF EXISTS failure_code,
  DROP COLUMN IF EXISTS consent_version;

DO $$
BEGIN
  IF EXISTS (
    SELECT 1 FROM hhy.identity_profiles
    WHERE length(name_cipher) > 255 OR length(id_no_cipher) > 255
  ) THEN
    RAISE EXCEPTION 'R05_IDENTITY_CIPHER_TOO_LONG_FOR_U024_ROLLBACK' USING ERRCODE='55000';
  END IF;
END $$;

ALTER TABLE hhy.identity_profiles
  ALTER COLUMN name_cipher TYPE varchar(255),
  ALTER COLUMN id_no_cipher TYPE varchar(255);
