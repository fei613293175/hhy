-- R05 API storage alignment. New rows must retain the accepted consent version;
-- encrypted identity payloads use text so the frozen API limit cannot overflow ciphertext columns.

ALTER TABLE hhy.identity_profiles
  ALTER COLUMN name_cipher TYPE text,
  ALTER COLUMN id_no_cipher TYPE text;

ALTER TABLE hhy.identity_verification_sessions
  ADD COLUMN consent_version varchar(2000),
  ADD COLUMN failure_code varchar(128);

ALTER TABLE hhy.identity_verification_sessions
  ADD CONSTRAINT ck_r05_identity_session_consent
    CHECK (consent_version IS NOT NULL AND btrim(consent_version) <> '') NOT VALID,
  ADD CONSTRAINT ck_r05_identity_session_failure
    CHECK (failure_code IS NULL OR btrim(failure_code) <> '') NOT VALID;
