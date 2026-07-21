-- Transactional assertions for the V032 contact cipher contract.
SET search_path TO hhy, public;

DO $$
DECLARE
  actual_type text;
  constraint_count integer;
BEGIN
  SELECT format_type(attribute.atttypid, attribute.atttypmod)
    INTO actual_type
  FROM pg_attribute attribute
  JOIN pg_class relation ON relation.oid = attribute.attrelid
  JOIN pg_namespace namespace ON namespace.oid = relation.relnamespace
  WHERE namespace.nspname = 'hhy'
    AND relation.relname = 'content_contacts'
    AND attribute.attname = 'value_cipher'
    AND attribute.attnum > 0
    AND NOT attribute.attisdropped;
  IF actual_type <> 'character varying(2048)' THEN
    RAISE EXCEPTION 'R07_CONTACT_CIPHER_TYPE_UNEXPECTED: %', actual_type;
  END IF;

  SELECT count(*) INTO constraint_count
  FROM pg_constraint
  WHERE connamespace = 'hhy'::regnamespace
    AND conname = 'ck_r07_contact_cipher_envelope';
  IF constraint_count <> 1 THEN
    RAISE EXCEPTION 'R07_CONTACT_CIPHER_CONSTRAINT_MISSING';
  END IF;
END;
$$;

BEGIN;
DO $$
DECLARE
  test_user_id bigint;
  test_content_id bigint;
BEGIN
  INSERT INTO hhy.users(phone,status,invite_code)
  VALUES ('13900000704','ACTIVE','R07CIPHERTEST')
  RETURNING id INTO test_user_id;
  INSERT INTO hhy.content_posts(owner_id,type,title,status)
  VALUES (test_user_id,'PROJECT','R07 cipher invariant','ONLINE')
  RETURNING id INTO test_content_id;

  INSERT INTO hhy.content_contacts(content_id,channel,value_cipher,display_mask,sort_order)
  VALUES (test_content_id,'EMAIL','hhy-contact-v1.test-nonce.test-ciphertext','c***@example.com',1);
  IF NOT EXISTS (
    SELECT 1 FROM hhy.content_contacts
    WHERE content_id = test_content_id
      AND value_cipher = 'hhy-contact-v1.test-nonce.test-ciphertext'
  ) THEN
    RAISE EXCEPTION 'R07_CONTACT_CIPHER_ENVELOPE_NOT_PERSISTED';
  END IF;

  BEGIN
    INSERT INTO hhy.content_contacts(content_id,channel,value_cipher,display_mask,sort_order)
    VALUES (test_content_id,'PHONE',NULL,'138****0000',2);
    RAISE EXCEPTION 'R07_CONTACT_CIPHER_NULL_WAS_ACCEPTED';
  EXCEPTION
    WHEN check_violation THEN NULL;
  END;
END;
$$;
ROLLBACK;
