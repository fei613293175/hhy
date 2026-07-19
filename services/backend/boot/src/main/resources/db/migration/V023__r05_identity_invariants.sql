-- R05 identity verification data invariants. Sensitive payloads remain encrypted;
-- lifecycle history is append-only through provider and manual review records.

ALTER TABLE hhy.identity_profiles
  ADD COLUMN verified_at timestamptz,
  ADD COLUMN frozen_at timestamptz,
  ADD COLUMN freeze_reason text;

ALTER TABLE hhy.identity_profiles
  ADD CONSTRAINT ck_r05_identity_profile_version
    CHECK (version >= 0) NOT VALID,
  ADD CONSTRAINT ck_r05_identity_profile_verified
    CHECK (
      status <> 'VERIFIED'
      OR (
        id_no_cipher IS NOT NULL AND btrim(id_no_cipher) <> ''
        AND id_hash IS NOT NULL AND id_hash ~ '^[0-9A-Fa-f]{64,128}$'
        AND verified_at IS NOT NULL
      )
    ) NOT VALID,
  ADD CONSTRAINT ck_r05_identity_profile_frozen
    CHECK (
      (frozen_at IS NULL AND freeze_reason IS NULL)
      OR (frozen_at IS NOT NULL AND freeze_reason IS NOT NULL AND btrim(freeze_reason) <> '')
    ) NOT VALID;

CREATE INDEX ix_r05_identity_profile_frozen
  ON hhy.identity_profiles (frozen_at) WHERE frozen_at IS NOT NULL;

ALTER TABLE hhy.identity_verification_sessions
  ADD COLUMN idempotency_key varchar(128),
  ADD COLUMN attempt_no integer NOT NULL DEFAULT 1,
  ADD COLUMN retry_of_session_id bigint,
  ADD COLUMN last_event varchar(64),
  ADD COLUMN completed_at timestamptz;

ALTER TABLE hhy.identity_verification_sessions
  ADD CONSTRAINT fk_r05_identity_retry_session
    FOREIGN KEY (retry_of_session_id) REFERENCES hhy.identity_verification_sessions(id)
    ON DELETE RESTRICT NOT VALID,
  ADD CONSTRAINT ck_r05_identity_session_attempt
    CHECK (attempt_no > 0 AND version >= 0) NOT VALID,
  ADD CONSTRAINT ck_r05_identity_session_idempotency
    CHECK (idempotency_key IS NULL OR btrim(idempotency_key) <> '') NOT VALID,
  ADD CONSTRAINT ck_r05_identity_session_expiry
    CHECK (expires_at IS NULL OR expires_at > created_at) NOT VALID,
  ADD CONSTRAINT ck_r05_identity_session_completion
    CHECK (
      (status IN ('VERIFIED','REJECTED','EXPIRED')) = (completed_at IS NOT NULL)
      AND (completed_at IS NULL OR completed_at >= created_at)
    ) NOT VALID,
  ADD CONSTRAINT ck_r05_identity_session_retry
    CHECK (retry_of_session_id IS NULL OR retry_of_session_id <> id) NOT VALID;

CREATE UNIQUE INDEX uq_r05_identity_session_idempotency
  ON hhy.identity_verification_sessions (user_id, idempotency_key)
  WHERE idempotency_key IS NOT NULL;
CREATE UNIQUE INDEX uq_r05_identity_active_session
  ON hhy.identity_verification_sessions (user_id)
  WHERE status IN ('SESSION_CREATED','LIVENESS_PENDING','PROVIDER_PROCESSING','MANUAL_REVIEW');
CREATE INDEX ix_r05_identity_session_retry
  ON hhy.identity_verification_sessions (retry_of_session_id, attempt_no)
  WHERE retry_of_session_id IS NOT NULL;

ALTER TABLE hhy.identity_provider_requests
  ADD COLUMN idempotency_key varchar(128),
  ADD COLUMN request_hash varchar(64),
  ADD COLUMN status varchar(64),
  ADD COLUMN attempt_no integer NOT NULL DEFAULT 1,
  ADD COLUMN from_status varchar(64),
  ADD COLUMN to_status varchar(64),
  ADD COLUMN event varchar(64),
  ADD COLUMN error_code varchar(128),
  ADD COLUMN completed_at timestamptz;

ALTER TABLE hhy.identity_provider_requests
  ADD CONSTRAINT ck_r05_identity_provider_attempt
    CHECK (attempt_no > 0 AND version >= 0) NOT VALID,
  ADD CONSTRAINT ck_r05_identity_provider_hash
    CHECK (request_hash IS NULL OR request_hash ~ '^[0-9A-Fa-f]{64}$') NOT VALID,
  ADD CONSTRAINT ck_r05_identity_provider_status
    CHECK (status IS NULL OR status IN ('CREATED','PROCESSING','SUCCEEDED','FAILED','TIMED_OUT')) NOT VALID,
  ADD CONSTRAINT ck_r05_identity_provider_history
    CHECK (
      (from_status IS NULL AND to_status IS NULL AND event IS NULL)
      OR (
        from_status IS NOT NULL AND btrim(from_status) <> ''
        AND to_status IS NOT NULL AND btrim(to_status) <> ''
        AND event IS NOT NULL AND btrim(event) <> ''
      )
    ) NOT VALID,
  ADD CONSTRAINT ck_r05_identity_provider_completion
    CHECK (
      (status IN ('SUCCEEDED','FAILED','TIMED_OUT')) = (completed_at IS NOT NULL)
      AND (completed_at IS NULL OR completed_at >= created_at)
    ) NOT VALID;

CREATE UNIQUE INDEX uq_r05_identity_provider_idempotency
  ON hhy.identity_provider_requests (session_id, request_type, idempotency_key)
  WHERE idempotency_key IS NOT NULL;
CREATE UNIQUE INDEX uq_r05_identity_provider_order
  ON hhy.identity_provider_requests (provider_order_no)
  WHERE provider_order_no IS NOT NULL;
CREATE INDEX ix_r05_identity_provider_status
  ON hhy.identity_provider_requests (session_id, status, created_at DESC);

ALTER TABLE hhy.identity_media
  ADD COLUMN storage_scope varchar(64) NOT NULL DEFAULT 'private_kyc',
  ADD COLUMN purpose varchar(128),
  ADD COLUMN version bigint NOT NULL DEFAULT 0;

ALTER TABLE hhy.identity_media
  ADD CONSTRAINT ck_r05_identity_media_scope
    CHECK (storage_scope = 'private_kyc') NOT VALID,
  ADD CONSTRAINT ck_r05_identity_media_type
    CHECK (media_type IS NULL OR media_type IN ('ID_CARD_FRONT','ID_CARD_BACK','LIVENESS_PHOTO','FACE_COMPARE')) NOT VALID,
  ADD CONSTRAINT ck_r05_identity_media_version
    CHECK (version >= 0) NOT VALID;

CREATE UNIQUE INDEX uq_r05_identity_media_type
  ON hhy.identity_media (session_id, media_type)
  WHERE media_type IS NOT NULL;
CREATE UNIQUE INDEX uq_r05_identity_media_object
  ON hhy.identity_media (media_object_id)
  WHERE media_object_id IS NOT NULL;

ALTER TABLE hhy.identity_review_records
  ADD COLUMN from_status varchar(64),
  ADD COLUMN to_status varchar(64),
  ADD COLUMN event varchar(64),
  ADD COLUMN idempotency_key varchar(128),
  ADD COLUMN expected_version bigint;

ALTER TABLE hhy.identity_review_records
  ADD CONSTRAINT ck_r05_identity_review_decision
    CHECK (decision IS NULL OR decision IN ('APPROVE','REJECT','FREEZE')) NOT VALID,
  ADD CONSTRAINT ck_r05_identity_review_reason
    CHECK (decision IS NULL OR reason IS NOT NULL AND btrim(reason) <> '') NOT VALID,
  ADD CONSTRAINT ck_r05_identity_review_history
    CHECK (
      from_status IS NOT NULL AND btrim(from_status) <> ''
      AND to_status IS NOT NULL AND btrim(to_status) <> ''
      AND event IS NOT NULL AND btrim(event) <> ''
      AND expected_version IS NOT NULL AND expected_version >= 0
    ) NOT VALID,
  ADD CONSTRAINT ck_r05_identity_review_idempotency
    CHECK (idempotency_key IS NOT NULL AND btrim(idempotency_key) <> '') NOT VALID;

CREATE UNIQUE INDEX uq_r05_identity_review_idempotency
  ON hhy.identity_review_records (session_id, idempotency_key);
CREATE TRIGGER trg_r05_identity_review_immutable
  BEFORE UPDATE OR DELETE ON hhy.identity_review_records
  FOR EACH ROW EXECUTE FUNCTION hhy.prevent_immutable_mutation();

ALTER TABLE hhy.sensitive_data_access_logs
  ADD COLUMN operation varchar(64),
  ADD COLUMN request_id varchar(128),
  ADD COLUMN media_object_id bigint;

ALTER TABLE hhy.sensitive_data_access_logs
  ADD CONSTRAINT fk_r05_sensitive_media
    FOREIGN KEY (media_object_id) REFERENCES hhy.media_objects(id) ON DELETE RESTRICT NOT VALID,
  ADD CONSTRAINT ck_r05_sensitive_access_reason
    CHECK (reason IS NOT NULL AND btrim(reason) <> '') NOT VALID,
  ADD CONSTRAINT ck_r05_sensitive_access_resource
    CHECK (resource IS NOT NULL AND btrim(resource) <> '') NOT VALID,
  ADD CONSTRAINT ck_r05_sensitive_access_operation
    CHECK (operation IS NOT NULL AND operation IN ('VIEW_PROFILE','VIEW_MEDIA','REVIEW','FREEZE')) NOT VALID,
  ADD CONSTRAINT ck_r05_sensitive_access_request
    CHECK (request_id IS NOT NULL AND btrim(request_id) <> '') NOT VALID;

CREATE INDEX ix_r05_sensitive_access_resource
  ON hhy.sensitive_data_access_logs (user_id, resource, created_at DESC);
CREATE INDEX ix_r05_sensitive_access_media
  ON hhy.sensitive_data_access_logs (media_object_id, created_at DESC)
  WHERE media_object_id IS NOT NULL;
