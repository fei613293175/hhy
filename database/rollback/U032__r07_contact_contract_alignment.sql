-- Development/test rollback for the R07 contact cipher type correction.
SET search_path TO hhy, public;

DO $$
BEGIN
  IF EXISTS (
    SELECT 1 FROM hhy.content_contacts
    WHERE value_cipher IS NOT NULL AND value_cipher !~ '^[0-9]+$'
  ) THEN
    RAISE EXCEPTION 'R07_CONTACT_CIPHER_ROLLBACK_REQUIRES_DATA_EXPORT'
      USING ERRCODE = '55000';
  END IF;
END;
$$;

ALTER TABLE hhy.content_contacts
  DROP CONSTRAINT IF EXISTS ck_r07_contact_cipher_envelope,
  ALTER COLUMN value_cipher TYPE bigint
  USING value_cipher::bigint;

COMMENT ON COLUMN hhy.content_contacts.value_cipher IS NULL;
