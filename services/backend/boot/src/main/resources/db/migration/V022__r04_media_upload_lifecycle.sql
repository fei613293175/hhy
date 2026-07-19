-- Runtime mirror of database/migrations/V022__r04_media_upload_lifecycle.sql.
-- This file is verified byte-for-byte (after the comment header) by repository tests.

ALTER TABLE hhy.upload_sessions
  ADD COLUMN purpose varchar(2000),
  ADD COLUMN file_name varchar(2000),
  ADD COLUMN content_type varchar(2000),
  ADD COLUMN size_bytes bigint,
  ADD COLUMN sha256 varchar(64),
  ADD COLUMN storage_scope varchar(64),
  ADD COLUMN storage_binding_id bigint,
  ADD COLUMN object_key text,
  ADD COLUMN provider_upload_id varchar(2000),
  ADD COLUMN provider_etag varchar(2000),
  ADD COLUMN media_id bigint,
  ADD COLUMN completed_at timestamptz;

ALTER TABLE hhy.upload_sessions
  ADD CONSTRAINT fk_r04_upload_storage_binding
    FOREIGN KEY (storage_binding_id) REFERENCES hhy.storage_scope_bindings(id) ON DELETE RESTRICT NOT VALID,
  ADD CONSTRAINT fk_r04_upload_media
    FOREIGN KEY (media_id) REFERENCES hhy.media_objects(id) ON DELETE RESTRICT NOT VALID,
  ADD CONSTRAINT ck_r04_upload_intent
    CHECK (
      purpose IS NULL OR (
        btrim(purpose) <> ''
        AND file_name IS NOT NULL AND btrim(file_name) <> ''
        AND content_type IS NOT NULL AND btrim(content_type) <> ''
        AND size_bytes IS NOT NULL AND size_bytes >= 0
        AND sha256 IS NOT NULL AND sha256 ~ '^[0-9A-Fa-f]{64}$'
        AND storage_scope IN ('public_media','private_kyc','private_chat','audit_evidence','apk_release','backup')
        AND storage_binding_id IS NOT NULL
        AND object_key IS NOT NULL AND btrim(object_key) <> ''
      )
    ) NOT VALID,
  ADD CONSTRAINT ck_r04_upload_completion
    CHECK (
      status <> 'COMPLETED'
      OR (media_id IS NOT NULL AND completed_at IS NOT NULL
          AND provider_etag IS NOT NULL AND btrim(provider_etag) <> '')
    ) NOT VALID,
  ADD CONSTRAINT ck_r04_upload_completion_time
    CHECK (completed_at IS NULL OR (completed_at >= created_at AND completed_at <= expires_at)) NOT VALID;

CREATE UNIQUE INDEX uq_r04_upload_media_id
  ON hhy.upload_sessions (media_id) WHERE media_id IS NOT NULL;
CREATE INDEX ix_r04_upload_binding_status
  ON hhy.upload_sessions (storage_binding_id, status, expires_at);

ALTER TABLE hhy.media_objects
  ADD COLUMN purpose varchar(2000),
  ADD COLUMN storage_scope varchar(64),
  ADD COLUMN storage_binding_id bigint,
  ADD COLUMN status varchar(64),
  ADD COLUMN version bigint NOT NULL DEFAULT 0,
  ADD COLUMN deleted_at timestamptz;

ALTER TABLE hhy.media_objects
  ADD CONSTRAINT fk_r04_media_storage_binding
    FOREIGN KEY (storage_binding_id) REFERENCES hhy.storage_scope_bindings(id) ON DELETE RESTRICT NOT VALID,
  ADD CONSTRAINT ck_r04_media_lifecycle
    CHECK (
      status IS NULL OR (
        status IN ('READY','DELETE_PENDING','DELETED')
        AND purpose IS NOT NULL AND btrim(purpose) <> ''
        AND storage_scope IN ('public_media','private_kyc','private_chat','audit_evidence','apk_release','backup')
        AND storage_binding_id IS NOT NULL
        AND version >= 0
      )
    ) NOT VALID,
  ADD CONSTRAINT ck_r04_media_deleted_at
    CHECK ((status = 'DELETED') = (deleted_at IS NOT NULL)) NOT VALID;

CREATE INDEX ix_r04_media_owner_status
  ON hhy.media_objects (owner_id, status, created_at DESC);
CREATE INDEX ix_r04_media_binding_status
  ON hhy.media_objects (storage_binding_id, status, created_at);
