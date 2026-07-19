DROP INDEX IF EXISTS hhy.ix_r04_storage_migration_resume;
ALTER TABLE hhy.storage_migration_jobs
  DROP CONSTRAINT IF EXISTS ck_r04_storage_migration_status,
  DROP CONSTRAINT IF EXISTS ck_r04_storage_migration_counts,
  DROP CONSTRAINT IF EXISTS ck_r04_storage_migration_bindings,
  DROP CONSTRAINT IF EXISTS ck_r04_storage_migration_scope;

DROP INDEX IF EXISTS hhy.ix_r04_media_token_subject_expiry;
ALTER TABLE hhy.media_access_tokens
  DROP CONSTRAINT IF EXISTS ck_r04_media_token_used_at,
  DROP CONSTRAINT IF EXISTS ck_r04_media_token_expiry;

DROP INDEX IF EXISTS hhy.ix_r04_upload_user_status_expiry;
ALTER TABLE hhy.upload_sessions
  DROP CONSTRAINT IF EXISTS ck_r04_upload_version_nonnegative,
  DROP CONSTRAINT IF EXISTS ck_r04_upload_expiry,
  DROP CONSTRAINT IF EXISTS ck_r04_upload_status;

DROP INDEX IF EXISTS hhy.ix_r04_media_owner_visibility;
ALTER TABLE hhy.media_objects
  DROP CONSTRAINT IF EXISTS ck_r04_media_visibility,
  DROP CONSTRAINT IF EXISTS ck_r04_media_sha256,
  DROP CONSTRAINT IF EXISTS ck_r04_media_size_nonnegative,
  DROP CONSTRAINT IF EXISTS ck_r04_media_object_key;

DROP INDEX IF EXISTS hhy.uq_r04_storage_scope_active;
DROP INDEX IF EXISTS hhy.uq_r04_storage_active_bucket;
ALTER TABLE hhy.storage_scope_bindings
  DROP CONSTRAINT IF EXISTS ck_r04_storage_private_domain,
  DROP CONSTRAINT IF EXISTS ck_r04_storage_binding_config,
  DROP CONSTRAINT IF EXISTS ck_r04_storage_binding_bucket,
  DROP CONSTRAINT IF EXISTS ck_r04_storage_binding_status,
  DROP CONSTRAINT IF EXISTS ck_r04_storage_provider_code,
  DROP CONSTRAINT IF EXISTS ck_r04_storage_scope_code;
