BEGIN;
SET LOCAL search_path TO hhy, public;

DO $$
DECLARE
  admin_one bigint;
  admin_two bigint;
  config_one bigint;
  certificate_one bigint;
  domain_one bigint;
  receipt_one bigint;
BEGIN
  IF (SELECT count(*) FROM hhy.domain_configs
      WHERE host LIKE '%.orbexa.cc' OR host='orbexa.cc') <> 12 THEN
    RAISE EXCEPTION 'R03_DOMAIN_SEEDS_INCOMPLETE';
  END IF;
  IF (SELECT count(*) FROM hhy.admin_permissions WHERE code IN (
      'provider.config.read','provider.config.write','provider.config.test',
      'provider.config.activate','provider.certificate.read','provider.certificate.write',
      'provider.certificate.rotate','domain.read','domain.write','domain.verify')) <> 10 THEN
    RAISE EXCEPTION 'R03_PERMISSION_SEEDS_INCOMPLETE';
  END IF;
  IF NOT hhy.r03_secret_refs_valid('{"api_key":"vault://r03/test/key"}'::jsonb)
      OR hhy.r03_secret_refs_valid('{"api_key":"plaintext-secret"}'::jsonb) THEN
    RAISE EXCEPTION 'R03_SECRET_REFERENCE_VALIDATOR_BROKEN';
  END IF;

  INSERT INTO hhy.admin_users(username,password_hash,status)
  VALUES ('r03-invariant-requester',repeat('a',64),'ACTIVE') RETURNING id INTO admin_one;
  INSERT INTO hhy.admin_users(username,password_hash,status)
  VALUES ('r03-invariant-reviewer',repeat('b',64),'ACTIVE') RETURNING id INTO admin_two;

  INSERT INTO hhy.provider_config_versions(
    provider_code,version_no,status,created_by,environment,values_json,secret_refs_json,
    approval_requester_id,approval_reviewer_id)
  VALUES ('r03_sms','v1','ACTIVE',admin_one::text,'STAGING','{}',
    '{"api_key":"vault://r03/test/key"}',admin_one,admin_two)
  RETURNING id INTO config_one;

  BEGIN
    INSERT INTO hhy.provider_config_versions(
      provider_code,version_no,status,created_by,environment,values_json,secret_refs_json)
    VALUES ('r03_sms','plaintext','DRAFT',admin_one::text,'STAGING','{}',
      '{"api_key":"plaintext-secret"}');
    RAISE EXCEPTION 'R03_PLAINTEXT_SECRET_WAS_ACCEPTED';
  EXCEPTION WHEN check_violation THEN NULL;
  END;

  BEGIN
    INSERT INTO hhy.provider_config_versions(
      provider_code,version_no,status,created_by,environment,values_json,secret_refs_json)
    VALUES ('r03_sms','v2','ACTIVE',admin_one::text,'STAGING','{}',
      '{"api_key":"kms://r03/test/key"}');
    RAISE EXCEPTION 'R03_DUPLICATE_ACTIVE_CONFIG_WAS_ACCEPTED';
  EXCEPTION WHEN unique_violation THEN NULL;
  END;

  BEGIN
    UPDATE hhy.provider_config_versions SET values_json='{"changed":true}' WHERE id=config_one;
    RAISE EXCEPTION 'R03_PROVIDER_PAYLOAD_MUTATION_WAS_ACCEPTED';
  EXCEPTION WHEN raise_exception THEN
    IF SQLERRM <> 'R03_PROVIDER_CONFIG_VERSION_IMMUTABLE' THEN RAISE; END IF;
  END;

  INSERT INTO hhy.provider_certificates(
    provider_code,cert_type,object_key,fingerprint,secret_ref,valid_from,valid_to,
    status,created_by)
  VALUES ('r03_payout','PRIVATE_KEY','r03/test/key',repeat('c',64),
    'vault://r03/test/certificate',now(),now()+interval '30 days','ACTIVE',admin_one)
  RETURNING id INTO certificate_one;

  BEGIN
    INSERT INTO hhy.provider_certificates(
      provider_code,cert_type,object_key,fingerprint,secret_ref,status,created_by)
    VALUES ('r03_payout','PRIVATE_KEY','r03/test/key-2',repeat('d',64),
      'kms://r03/test/certificate-2','ACTIVE',admin_one);
    RAISE EXCEPTION 'R03_DUPLICATE_ACTIVE_CERTIFICATE_WAS_ACCEPTED';
  EXCEPTION WHEN unique_violation THEN NULL;
  END;

  BEGIN
    UPDATE hhy.provider_certificates SET secret_ref='vault://r03/test/replaced'
    WHERE id=certificate_one;
    RAISE EXCEPTION 'R03_CERTIFICATE_MATERIAL_MUTATION_WAS_ACCEPTED';
  EXCEPTION WHEN raise_exception THEN
    IF SQLERRM <> 'R03_PROVIDER_CERTIFICATE_MATERIAL_IMMUTABLE' THEN RAISE; END IF;
  END;

  INSERT INTO hhy.provider_certificate_access_logs(
    certificate_id,admin_id,operation,reason)
  VALUES (certificate_one,admin_one,'R03_INVARIANT','append-only');
  BEGIN
    UPDATE hhy.provider_certificate_access_logs SET reason='mutated'
    WHERE certificate_id=certificate_one;
    RAISE EXCEPTION 'R03_CERTIFICATE_AUDIT_MUTATION_WAS_ACCEPTED';
  EXCEPTION WHEN raise_exception THEN
    IF SQLERRM <> 'R03_APPEND_ONLY_AUDIT' THEN RAISE; END IF;
  END;

  SELECT id INTO domain_one FROM hhy.domain_configs WHERE code='api';
  INSERT INTO hhy.domain_command_receipts(
    domain_config_id,operation,idempotency_key,fingerprint,result_json,expires_at)
  VALUES (domain_one,'VERIFY','r03-invariant',repeat('e',64),'{}',now()+interval '1 hour')
  RETURNING id INTO receipt_one;
  BEGIN
    DELETE FROM hhy.domain_command_receipts WHERE id=receipt_one;
    RAISE EXCEPTION 'R03_DOMAIN_RECEIPT_DELETE_WAS_ACCEPTED';
  EXCEPTION WHEN raise_exception THEN
    IF SQLERRM <> 'R03_APPEND_ONLY_AUDIT' THEN RAISE; END IF;
  END;

  BEGIN
    UPDATE hhy.domain_configs SET dns_status='UNSAFE' WHERE id=domain_one;
    RAISE EXCEPTION 'R03_INVALID_DOMAIN_STATE_WAS_ACCEPTED';
  EXCEPTION WHEN check_violation THEN NULL;
  END;
END $$;

ROLLBACK;
