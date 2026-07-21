-- R07 actual contact access requires a versioned AEAD envelope, not a numeric placeholder.
SET search_path TO hhy, public;

ALTER TABLE hhy.content_contacts
  ALTER COLUMN value_cipher TYPE varchar(2048)
  USING value_cipher::text;

ALTER TABLE hhy.content_contacts
  ADD CONSTRAINT ck_r07_contact_cipher_envelope
    CHECK (
      value_cipher IS NOT NULL
      AND btrim(value_cipher) <> ''
      AND char_length(value_cipher) <= 2048
    ) NOT VALID;

COMMENT ON COLUMN hhy.content_contacts.value_cipher IS
  'Versioned AES-256-GCM envelope. New values use hhy-contact-v1; legacy numeric references are retained but not readable.';
