SET search_path TO hhy, public;

DELETE FROM hhy.admin_role_permissions role_permission
USING hhy.admin_permissions permission
WHERE role_permission.permission_id=permission.id
  AND permission.code IN (
    'provider.config.read','provider.config.write','provider.config.test',
    'provider.config.activate','provider.certificate.read','provider.certificate.write',
    'provider.certificate.rotate','domain.read','domain.write','domain.verify');
DELETE FROM hhy.admin_permissions WHERE code IN (
  'provider.config.read','provider.config.write','provider.config.test',
  'provider.config.activate','provider.certificate.read','provider.certificate.write',
  'provider.certificate.rotate','domain.read','domain.write','domain.verify');

DROP INDEX IF EXISTS hhy.ix_dns_action_items_owner_due;
ALTER TABLE hhy.dns_action_items DROP CONSTRAINT IF EXISTS ck_dns_action_items_due_at;
ALTER TABLE hhy.dns_action_items
  DROP COLUMN IF EXISTS last_error_code,
  DROP COLUMN IF EXISTS due_at,
  DROP COLUMN IF EXISTS owner_admin_id,
  DROP COLUMN IF EXISTS record_value;

ALTER TABLE hhy.domain_configs
  DROP CONSTRAINT IF EXISTS ck_domain_configs_service_health_status,
  DROP CONSTRAINT IF EXISTS ck_domain_configs_certificate_status,
  DROP CONSTRAINT IF EXISTS ck_domain_configs_https_status,
  DROP CONSTRAINT IF EXISTS ck_domain_configs_dns_status,
  DROP CONSTRAINT IF EXISTS ck_domain_configs_certificate_mode,
  DROP COLUMN IF EXISTS last_verified_at,
  DROP COLUMN IF EXISTS last_probe_code,
  DROP COLUMN IF EXISTS service_health_status,
  DROP COLUMN IF EXISTS certificate_status,
  DROP COLUMN IF EXISTS https_status,
  DROP COLUMN IF EXISTS dns_status,
  DROP COLUMN IF EXISTS certificate_mode;

DROP TRIGGER IF EXISTS trg_provider_certificate_access_logs_append_only
  ON hhy.provider_certificate_access_logs;
DROP TRIGGER IF EXISTS trg_provider_certificates_material_guard ON hhy.provider_certificates;
DROP FUNCTION IF EXISTS hhy.r03_append_only_guard();
DROP FUNCTION IF EXISTS hhy.r03_provider_certificate_material_guard();
DROP INDEX IF EXISTS hhy.ix_provider_certificates_provider_type_status;
DROP INDEX IF EXISTS hhy.uq_provider_certificates_active_type;
ALTER TABLE hhy.provider_certificates
  DROP CONSTRAINT IF EXISTS ck_provider_certificates_validity,
  DROP CONSTRAINT IF EXISTS ck_provider_certificates_fingerprint,
  DROP CONSTRAINT IF EXISTS ck_provider_certificates_password_ref,
  DROP CONSTRAINT IF EXISTS ck_provider_certificates_secret_ref,
  DROP CONSTRAINT IF EXISTS ck_provider_certificates_status_r03,
  DROP COLUMN IF EXISTS version,
  DROP COLUMN IF EXISTS approval_ref,
  DROP COLUMN IF EXISTS rotated_to_id,
  DROP COLUMN IF EXISTS created_by,
  DROP COLUMN IF EXISTS secret_ref,
  DROP COLUMN IF EXISTS alias;

DROP TRIGGER IF EXISTS trg_provider_config_versions_payload_guard
  ON hhy.provider_config_versions;
DROP TRIGGER IF EXISTS trg_provider_config_versions_updated_at
  ON hhy.provider_config_versions;
DROP FUNCTION IF EXISTS hhy.r03_provider_config_payload_guard();
DROP INDEX IF EXISTS hhy.ix_provider_config_versions_provider_environment_status;
DROP INDEX IF EXISTS hhy.uq_provider_config_versions_active_provider;
ALTER TABLE hhy.provider_config_versions
  DROP CONSTRAINT IF EXISTS ck_provider_config_versions_approval_separation,
  DROP CONSTRAINT IF EXISTS ck_provider_config_versions_test_result,
  DROP CONSTRAINT IF EXISTS ck_provider_config_versions_secret_refs,
  DROP CONSTRAINT IF EXISTS ck_provider_config_versions_values_object,
  DROP CONSTRAINT IF EXISTS ck_provider_config_versions_environment,
  DROP COLUMN IF EXISTS updated_at,
  DROP COLUMN IF EXISTS version,
  DROP COLUMN IF EXISTS approval_reviewer_id,
  DROP COLUMN IF EXISTS approval_requester_id,
  DROP COLUMN IF EXISTS approval_ref,
  DROP COLUMN IF EXISTS tested_at,
  DROP COLUMN IF EXISTS masked_test_result,
  DROP COLUMN IF EXISTS connection_successful,
  DROP COLUMN IF EXISTS remark,
  DROP COLUMN IF EXISTS secret_refs_json,
  DROP COLUMN IF EXISTS values_json,
  DROP COLUMN IF EXISTS environment;
DROP FUNCTION IF EXISTS hhy.r03_secret_refs_valid(jsonb);
