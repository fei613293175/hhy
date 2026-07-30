-- R04 media and multi-object-storage safety baseline.
-- Existing rows remain readable; every new or updated row must satisfy these constraints.

ALTER TABLE hhy.storage_scope_bindings
  ADD CONSTRAINT ck_r04_storage_scope_code
  CHECK (scope_code IN ('public_media','private_kyc','private_chat','audit_evidence','apk_release','backup')) NOT VALID,
  ADD CONSTRAINT ck_r04_storage_provider_code
  CHECK (provider_code IN ('CLOUDFLARE_R2','ALIYUN_OSS')) NOT VALID,
  ADD CONSTRAINT ck_r04_storage_binding_status
  CHECK (status IN ('DRAFT','TESTING','VERIFIED','ACTIVE','INACTIVE','FAILED')) NOT VALID,
  ADD CONSTRAINT ck_r04_storage_binding_bucket
  CHECK (bucket IS NOT NULL AND btrim(bucket) <> '') NOT VALID,
  ADD CONSTRAINT ck_r04_storage_binding_config
  CHECK (config_version_id IS NOT NULL) NOT VALID,
  ADD CONSTRAINT ck_r04_storage_private_domain
  CHECK (scope_code = 'public_media' OR public_domain IS NULL) NOT VALID;

CREATE UNIQUE INDEX uq_r04_storage_scope_active
  ON hhy.storage_scope_bindings (scope_code)
  WHERE status = 'ACTIVE';

CREATE UNIQUE INDEX uq_r04_storage_active_bucket
  ON hhy.storage_scope_bindings (provider_code, bucket)
  WHERE status = 'ACTIVE';

ALTER TABLE hhy.media_objects
  ADD CONSTRAINT ck_r04_media_object_key
  CHECK (object_key IS NOT NULL AND btrim(object_key) <> '') NOT VALID,
  ADD CONSTRAINT ck_r04_media_size_nonnegative
  CHECK (size IS NULL OR size >= 0) NOT VALID,
  ADD CONSTRAINT ck_r04_media_sha256
  CHECK (sha256 IS NULL OR sha256 ~ '^[0-9A-Fa-f]{64}$') NOT VALID,
  ADD CONSTRAINT ck_r04_media_visibility
  CHECK (visibility IN ('PUBLIC','PRIVATE')) NOT VALID;

CREATE INDEX ix_r04_media_owner_visibility
  ON hhy.media_objects (owner_id, visibility, created_at DESC);

ALTER TABLE hhy.upload_sessions
  ADD CONSTRAINT ck_r04_upload_status
  CHECK (status IN ('CREATED','UPLOADING','COMPLETING','COMPLETED','FAILED','CANCELLED','EXPIRED')) NOT VALID,
  ADD CONSTRAINT ck_r04_upload_expiry
  CHECK (expires_at IS NOT NULL AND expires_at > created_at) NOT VALID,
  ADD CONSTRAINT ck_r04_upload_version_nonnegative
  CHECK (version >= 0) NOT VALID;

CREATE INDEX ix_r04_upload_user_status_expiry
  ON hhy.upload_sessions (user_id, status, expires_at);

ALTER TABLE hhy.media_access_tokens
  ADD CONSTRAINT ck_r04_media_token_expiry
  CHECK (expires_at IS NOT NULL AND expires_at > created_at) NOT VALID,
  ADD CONSTRAINT ck_r04_media_token_used_at
  CHECK (used_at IS NULL OR (used_at >= created_at AND used_at <= expires_at)) NOT VALID;

CREATE INDEX ix_r04_media_token_subject_expiry
  ON hhy.media_access_tokens (subject, expires_at);

ALTER TABLE hhy.storage_migration_jobs
  ADD CONSTRAINT ck_r04_storage_migration_scope
  CHECK (scope_code IN ('public_media','private_kyc','private_chat','audit_evidence','apk_release','backup')) NOT VALID,
  ADD CONSTRAINT ck_r04_storage_migration_bindings
  CHECK (target_binding_id IS NOT NULL AND source_binding_id <> target_binding_id) NOT VALID,
  ADD CONSTRAINT ck_r04_storage_migration_counts
  CHECK (
    total >= 0 AND COALESCE(success, 0) >= 0 AND COALESCE(failed, 0) >= 0
    AND COALESCE(success, 0) + COALESCE(failed, 0) <= total
  ) NOT VALID,
  ADD CONSTRAINT ck_r04_storage_migration_status
  CHECK (status IN ('PENDING','RUNNING','PAUSED','COMPLETED','FAILED','CANCELLED')) NOT VALID;

CREATE INDEX ix_r04_storage_migration_resume
  ON hhy.storage_migration_jobs (status, scope_code, updated_at);
