SET search_path TO hhy, public;

CREATE OR REPLACE FUNCTION hhy.r03_secret_refs_valid(payload jsonb)
RETURNS boolean LANGUAGE sql IMMUTABLE AS $$
  SELECT payload IS NOT NULL
    AND jsonb_typeof(payload) = 'object'
    AND NOT EXISTS (
      SELECT 1 FROM jsonb_each(payload) item
      WHERE jsonb_typeof(item.value) <> 'string'
         OR item.value #>> '{}' !~ '^(vault|kms)://[A-Za-z0-9_./:@-]{3,512}$'
    );
$$;

ALTER TABLE hhy.provider_config_versions
  ADD COLUMN environment varchar(16) NOT NULL DEFAULT 'STAGING',
  ADD COLUMN values_json jsonb NOT NULL DEFAULT '{}'::jsonb,
  ADD COLUMN secret_refs_json jsonb NOT NULL DEFAULT '{}'::jsonb,
  ADD COLUMN remark varchar(500) NOT NULL DEFAULT '',
  ADD COLUMN connection_successful boolean,
  ADD COLUMN masked_test_result varchar(255),
  ADD COLUMN tested_at timestamptz,
  ADD COLUMN approval_ref varchar(64),
  ADD COLUMN approval_requester_id bigint REFERENCES hhy.admin_users(id) ON DELETE RESTRICT,
  ADD COLUMN approval_reviewer_id bigint REFERENCES hhy.admin_users(id) ON DELETE RESTRICT,
  ADD COLUMN version bigint NOT NULL DEFAULT 0,
  ADD COLUMN updated_at timestamptz NOT NULL DEFAULT now(),
  ADD CONSTRAINT ck_provider_config_versions_environment
    CHECK (environment IN ('DEV','TEST','STAGING','PROD')),
  ADD CONSTRAINT ck_provider_config_versions_values_object
    CHECK (jsonb_typeof(values_json)='object'),
  ADD CONSTRAINT ck_provider_config_versions_secret_refs
    CHECK (hhy.r03_secret_refs_valid(secret_refs_json)),
  ADD CONSTRAINT ck_provider_config_versions_test_result
    CHECK ((connection_successful IS NULL AND tested_at IS NULL)
      OR (connection_successful IS NOT NULL AND tested_at IS NOT NULL)),
  ADD CONSTRAINT ck_provider_config_versions_approval_separation
    CHECK (approval_requester_id IS NULL OR approval_reviewer_id IS NULL
      OR approval_requester_id <> approval_reviewer_id);

CREATE UNIQUE INDEX uq_provider_config_versions_active_environment
  ON hhy.provider_config_versions(provider_code, environment)
  WHERE status='ACTIVE';
CREATE INDEX ix_provider_config_versions_provider_environment_status
  ON hhy.provider_config_versions(provider_code, environment, status, created_at DESC);
CREATE TRIGGER trg_provider_config_versions_updated_at
  BEFORE UPDATE ON hhy.provider_config_versions
  FOR EACH ROW EXECUTE FUNCTION hhy.set_updated_at();

CREATE OR REPLACE FUNCTION hhy.r03_provider_config_payload_guard()
RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
  IF NEW.provider_code IS DISTINCT FROM OLD.provider_code
     OR NEW.environment IS DISTINCT FROM OLD.environment
     OR NEW.version_no IS DISTINCT FROM OLD.version_no
     OR NEW.values_json IS DISTINCT FROM OLD.values_json
     OR NEW.secret_refs_json IS DISTINCT FROM OLD.secret_refs_json
     OR NEW.remark IS DISTINCT FROM OLD.remark
     OR NEW.created_by IS DISTINCT FROM OLD.created_by THEN
    RAISE EXCEPTION 'R03_PROVIDER_CONFIG_VERSION_IMMUTABLE';
  END IF;
  RETURN NEW;
END $$;
CREATE TRIGGER trg_provider_config_versions_payload_guard
  BEFORE UPDATE ON hhy.provider_config_versions
  FOR EACH ROW EXECUTE FUNCTION hhy.r03_provider_config_payload_guard();

ALTER TABLE hhy.provider_certificates
  ADD COLUMN alias varchar(200) NOT NULL DEFAULT '',
  ADD COLUMN secret_ref varchar(512),
  ADD COLUMN created_by bigint REFERENCES hhy.admin_users(id) ON DELETE RESTRICT,
  ADD COLUMN rotated_to_id bigint REFERENCES hhy.provider_certificates(id) ON DELETE RESTRICT,
  ADD COLUMN approval_ref varchar(64),
  ADD COLUMN version bigint NOT NULL DEFAULT 0,
  ADD CONSTRAINT ck_provider_certificates_status_r03
    CHECK (status IN ('STAGED','ACTIVE','ROTATED','REVOKED')),
  ADD CONSTRAINT ck_provider_certificates_secret_ref
    CHECK (secret_ref IS NULL OR secret_ref ~ '^(vault|kms)://[A-Za-z0-9_./:@-]{3,512}$'),
  ADD CONSTRAINT ck_provider_certificates_password_ref
    CHECK (encrypted_password_ref IS NULL
      OR encrypted_password_ref ~ '^(vault|kms)://[A-Za-z0-9_./:@-]{3,255}$'),
  ADD CONSTRAINT ck_provider_certificates_fingerprint
    CHECK (fingerprint IS NULL OR fingerprint ~ '^[a-f0-9]{64}$'),
  ADD CONSTRAINT ck_provider_certificates_validity
    CHECK (valid_to IS NULL OR valid_from IS NULL OR valid_to > valid_from);

CREATE UNIQUE INDEX uq_provider_certificates_active_type
  ON hhy.provider_certificates(provider_code, cert_type) WHERE status='ACTIVE';
CREATE INDEX ix_provider_certificates_provider_type_status
  ON hhy.provider_certificates(provider_code, cert_type, status, created_at DESC);

CREATE OR REPLACE FUNCTION hhy.r03_provider_certificate_material_guard()
RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
  IF NEW.provider_code IS DISTINCT FROM OLD.provider_code
     OR NEW.cert_type IS DISTINCT FROM OLD.cert_type
     OR NEW.object_key IS DISTINCT FROM OLD.object_key
     OR NEW.secret_ref IS DISTINCT FROM OLD.secret_ref
     OR NEW.fingerprint IS DISTINCT FROM OLD.fingerprint
     OR NEW.encrypted_password_ref IS DISTINCT FROM OLD.encrypted_password_ref THEN
    RAISE EXCEPTION 'R03_PROVIDER_CERTIFICATE_MATERIAL_IMMUTABLE';
  END IF;
  RETURN NEW;
END $$;
CREATE TRIGGER trg_provider_certificates_material_guard
  BEFORE UPDATE ON hhy.provider_certificates
  FOR EACH ROW EXECUTE FUNCTION hhy.r03_provider_certificate_material_guard();

CREATE OR REPLACE FUNCTION hhy.r03_append_only_guard()
RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
  RAISE EXCEPTION 'R03_APPEND_ONLY_AUDIT';
END $$;
CREATE TRIGGER trg_provider_certificate_access_logs_append_only
  BEFORE UPDATE OR DELETE ON hhy.provider_certificate_access_logs
  FOR EACH ROW EXECUTE FUNCTION hhy.r03_append_only_guard();

ALTER TABLE hhy.domain_configs
  ADD COLUMN certificate_mode varchar(32) NOT NULL DEFAULT 'MANAGED',
  ADD COLUMN dns_status varchar(32) NOT NULL DEFAULT 'PENDING',
  ADD COLUMN https_status varchar(32) NOT NULL DEFAULT 'PENDING',
  ADD COLUMN certificate_status varchar(32) NOT NULL DEFAULT 'PENDING',
  ADD COLUMN service_health_status varchar(32) NOT NULL DEFAULT 'PENDING',
  ADD COLUMN last_probe_code varchar(64),
  ADD COLUMN last_verified_at timestamptz,
  ADD CONSTRAINT ck_domain_configs_certificate_mode
    CHECK (certificate_mode IN ('MANAGED','EXTERNAL')),
  ADD CONSTRAINT ck_domain_configs_dns_status
    CHECK (dns_status IN ('PENDING','PASSED','FAILED')),
  ADD CONSTRAINT ck_domain_configs_https_status
    CHECK (https_status IN ('PENDING','PASSED','FAILED')),
  ADD CONSTRAINT ck_domain_configs_certificate_status
    CHECK (certificate_status IN ('PENDING','PASSED','FAILED','EXPIRING')),
  ADD CONSTRAINT ck_domain_configs_service_health_status
    CHECK (service_health_status IN ('PENDING','PASSED','FAILED'));

ALTER TABLE hhy.dns_action_items
  ADD COLUMN record_value text,
  ADD COLUMN owner_admin_id bigint REFERENCES hhy.admin_users(id) ON DELETE RESTRICT,
  ADD COLUMN due_at timestamptz,
  ADD COLUMN last_error_code varchar(64),
  ADD CONSTRAINT ck_dns_action_items_due_at
    CHECK (due_at IS NULL OR due_at > created_at);
CREATE INDEX ix_dns_action_items_owner_due
  ON hhy.dns_action_items(owner_admin_id, due_at)
  WHERE status NOT IN ('COMPLETED','CANCELLED');

INSERT INTO hhy.admin_permissions(code,resource,action) VALUES
  ('provider.config.read','provider.config','read'),
  ('provider.config.write','provider.config','write'),
  ('provider.config.test','provider.config','test'),
  ('provider.config.activate','provider.config','activate'),
  ('provider.certificate.read','provider.certificate','read'),
  ('provider.certificate.write','provider.certificate','write'),
  ('provider.certificate.rotate','provider.certificate','rotate'),
  ('domain.read','domain','read'),
  ('domain.write','domain','write'),
  ('domain.verify','domain','verify')
ON CONFLICT(code) DO NOTHING;

INSERT INTO hhy.admin_role_permissions(role_id,permission_id)
SELECT role.id, permission.id FROM hhy.admin_roles role
CROSS JOIN hhy.admin_permissions permission
WHERE role.code='SUPER_ADMIN' AND role.status='ACTIVE'
  AND permission.code IN (
    'provider.config.read','provider.config.write','provider.config.test',
    'provider.config.activate','provider.certificate.read','provider.certificate.write',
    'provider.certificate.rotate','domain.read','domain.write','domain.verify')
ON CONFLICT(role_id,permission_id) DO NOTHING;

DO $$
BEGIN
  IF (SELECT count(*) FROM hhy.admin_permissions WHERE code IN (
      'provider.config.read','provider.config.write','provider.config.test',
      'provider.config.activate','provider.certificate.read','provider.certificate.write',
      'provider.certificate.rotate','domain.read','domain.write','domain.verify')) <> 10 THEN
    RAISE EXCEPTION 'R03_CONFIGURATION_PERMISSIONS_INCOMPLETE';
  END IF;
END $$;
