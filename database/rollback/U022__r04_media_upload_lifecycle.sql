DROP INDEX IF EXISTS hhy.ix_r04_media_binding_status;
DROP INDEX IF EXISTS hhy.ix_r04_media_owner_status;
ALTER TABLE hhy.media_objects
  DROP CONSTRAINT IF EXISTS ck_r04_media_deleted_at,
  DROP CONSTRAINT IF EXISTS ck_r04_media_lifecycle,
  DROP CONSTRAINT IF EXISTS fk_r04_media_storage_binding,
  DROP COLUMN IF EXISTS deleted_at,
  DROP COLUMN IF EXISTS version,
  DROP COLUMN IF EXISTS status,
  DROP COLUMN IF EXISTS storage_binding_id,
  DROP COLUMN IF EXISTS storage_scope,
  DROP COLUMN IF EXISTS purpose;

DROP INDEX IF EXISTS hhy.ix_r04_upload_binding_status;
DROP INDEX IF EXISTS hhy.uq_r04_upload_media_id;
ALTER TABLE hhy.upload_sessions
  DROP CONSTRAINT IF EXISTS ck_r04_upload_completion_time,
  DROP CONSTRAINT IF EXISTS ck_r04_upload_completion,
  DROP CONSTRAINT IF EXISTS ck_r04_upload_intent,
  DROP CONSTRAINT IF EXISTS fk_r04_upload_media,
  DROP CONSTRAINT IF EXISTS fk_r04_upload_storage_binding,
  DROP COLUMN IF EXISTS completed_at,
  DROP COLUMN IF EXISTS media_id,
  DROP COLUMN IF EXISTS provider_etag,
  DROP COLUMN IF EXISTS provider_upload_id,
  DROP COLUMN IF EXISTS object_key,
  DROP COLUMN IF EXISTS storage_binding_id,
  DROP COLUMN IF EXISTS storage_scope,
  DROP COLUMN IF EXISTS sha256,
  DROP COLUMN IF EXISTS size_bytes,
  DROP COLUMN IF EXISTS content_type,
  DROP COLUMN IF EXISTS file_name,
  DROP COLUMN IF EXISTS purpose;
