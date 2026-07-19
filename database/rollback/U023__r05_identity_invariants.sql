DROP INDEX IF EXISTS hhy.ix_r05_sensitive_access_media;
DROP INDEX IF EXISTS hhy.ix_r05_sensitive_access_resource;
ALTER TABLE hhy.sensitive_data_access_logs
  DROP CONSTRAINT IF EXISTS ck_r05_sensitive_access_request,
  DROP CONSTRAINT IF EXISTS ck_r05_sensitive_access_operation,
  DROP CONSTRAINT IF EXISTS ck_r05_sensitive_access_resource,
  DROP CONSTRAINT IF EXISTS ck_r05_sensitive_access_reason,
  DROP CONSTRAINT IF EXISTS fk_r05_sensitive_media,
  DROP COLUMN IF EXISTS media_object_id,
  DROP COLUMN IF EXISTS request_id,
  DROP COLUMN IF EXISTS operation;

DROP TRIGGER IF EXISTS trg_r05_identity_review_immutable ON hhy.identity_review_records;
DROP INDEX IF EXISTS hhy.uq_r05_identity_review_idempotency;
ALTER TABLE hhy.identity_review_records
  DROP CONSTRAINT IF EXISTS ck_r05_identity_review_idempotency,
  DROP CONSTRAINT IF EXISTS ck_r05_identity_review_history,
  DROP CONSTRAINT IF EXISTS ck_r05_identity_review_reason,
  DROP CONSTRAINT IF EXISTS ck_r05_identity_review_decision,
  DROP COLUMN IF EXISTS expected_version,
  DROP COLUMN IF EXISTS idempotency_key,
  DROP COLUMN IF EXISTS event,
  DROP COLUMN IF EXISTS to_status,
  DROP COLUMN IF EXISTS from_status;

DROP INDEX IF EXISTS hhy.uq_r05_identity_media_object;
DROP INDEX IF EXISTS hhy.uq_r05_identity_media_type;
ALTER TABLE hhy.identity_media
  DROP CONSTRAINT IF EXISTS ck_r05_identity_media_version,
  DROP CONSTRAINT IF EXISTS ck_r05_identity_media_type,
  DROP CONSTRAINT IF EXISTS ck_r05_identity_media_scope,
  DROP COLUMN IF EXISTS version,
  DROP COLUMN IF EXISTS purpose,
  DROP COLUMN IF EXISTS storage_scope;

DROP INDEX IF EXISTS hhy.ix_r05_identity_provider_status;
DROP INDEX IF EXISTS hhy.uq_r05_identity_provider_order;
DROP INDEX IF EXISTS hhy.uq_r05_identity_provider_idempotency;
ALTER TABLE hhy.identity_provider_requests
  DROP CONSTRAINT IF EXISTS ck_r05_identity_provider_completion,
  DROP CONSTRAINT IF EXISTS ck_r05_identity_provider_history,
  DROP CONSTRAINT IF EXISTS ck_r05_identity_provider_status,
  DROP CONSTRAINT IF EXISTS ck_r05_identity_provider_hash,
  DROP CONSTRAINT IF EXISTS ck_r05_identity_provider_attempt,
  DROP COLUMN IF EXISTS completed_at,
  DROP COLUMN IF EXISTS error_code,
  DROP COLUMN IF EXISTS event,
  DROP COLUMN IF EXISTS to_status,
  DROP COLUMN IF EXISTS from_status,
  DROP COLUMN IF EXISTS attempt_no,
  DROP COLUMN IF EXISTS status,
  DROP COLUMN IF EXISTS request_hash,
  DROP COLUMN IF EXISTS idempotency_key;

DROP INDEX IF EXISTS hhy.ix_r05_identity_session_retry;
DROP INDEX IF EXISTS hhy.uq_r05_identity_active_session;
DROP INDEX IF EXISTS hhy.uq_r05_identity_session_idempotency;
ALTER TABLE hhy.identity_verification_sessions
  DROP CONSTRAINT IF EXISTS ck_r05_identity_session_retry,
  DROP CONSTRAINT IF EXISTS ck_r05_identity_session_completion,
  DROP CONSTRAINT IF EXISTS ck_r05_identity_session_expiry,
  DROP CONSTRAINT IF EXISTS ck_r05_identity_session_idempotency,
  DROP CONSTRAINT IF EXISTS ck_r05_identity_session_attempt,
  DROP CONSTRAINT IF EXISTS fk_r05_identity_retry_session,
  DROP COLUMN IF EXISTS completed_at,
  DROP COLUMN IF EXISTS last_event,
  DROP COLUMN IF EXISTS retry_of_session_id,
  DROP COLUMN IF EXISTS attempt_no,
  DROP COLUMN IF EXISTS idempotency_key;

DROP INDEX IF EXISTS hhy.ix_r05_identity_profile_frozen;
ALTER TABLE hhy.identity_profiles
  DROP CONSTRAINT IF EXISTS ck_r05_identity_profile_frozen,
  DROP CONSTRAINT IF EXISTS ck_r05_identity_profile_verified,
  DROP CONSTRAINT IF EXISTS ck_r05_identity_profile_version,
  DROP COLUMN IF EXISTS freeze_reason,
  DROP COLUMN IF EXISTS frozen_at,
  DROP COLUMN IF EXISTS verified_at;
