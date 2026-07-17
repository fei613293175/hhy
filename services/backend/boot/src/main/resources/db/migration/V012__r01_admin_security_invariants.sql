-- R01 administrator authentication and security invariants.
-- Forward-only migration. V002/V009 are released history and must remain unchanged.
SET search_path TO hhy, public;

-- The normalized method table is the only writable MFA secret source. Preserve
-- legacy values by migrating them before making admin_users.mfa_secret_ref a
-- compatibility-only column.
DO $$
DECLARE
  v_id bigint;
BEGIN
  SELECT id INTO v_id FROM hhy.admin_users WHERE version < 0 LIMIT 1;
  IF v_id IS NOT NULL THEN
    RAISE EXCEPTION 'ADMIN_USER_INVALID_UPGRADE_DATA id=%', v_id USING ERRCODE = '23514';
  END IF;

  SELECT min(id) INTO v_id FROM hhy.admin_sessions
  WHERE refresh_hash IS NOT NULL
  GROUP BY refresh_hash HAVING count(*) > 1
  LIMIT 1;
  IF v_id IS NOT NULL THEN
    RAISE EXCEPTION 'ADMIN_SESSION_DUPLICATE_REFRESH_HASH id=%', v_id USING ERRCODE = '23505';
  END IF;

  SELECT admin_user.id INTO v_id
  FROM hhy.admin_users AS admin_user
  JOIN hhy.admin_mfa_methods AS method
    ON method.admin_user_id = admin_user.id AND method.method = 'TOTP'
  WHERE admin_user.mfa_secret_ref IS NOT NULL
    AND (method.secret_ref IS DISTINCT FROM admin_user.mfa_secret_ref
      OR method.status <> 'ACTIVE' OR method.confirmed_at IS NULL)
  LIMIT 1;
  IF v_id IS NOT NULL THEN
    RAISE EXCEPTION 'ADMIN_MFA_LEGACY_CONFLICT admin_user_id=%', v_id
      USING ERRCODE = '23514';
  END IF;

  SELECT id INTO v_id FROM hhy.admin_sessions
  WHERE btrim(access_jti) = '' OR (refresh_hash IS NOT NULL AND btrim(refresh_hash) = '')
     OR version < 0 OR expires_at <= created_at
     OR (revoked_at IS NOT NULL AND revoked_at < created_at)
     OR (last_active_at IS NOT NULL AND last_active_at < created_at)
     OR (revoked_at IS NOT NULL AND last_active_at IS NOT NULL AND revoked_at < last_active_at)
  LIMIT 1;
  IF v_id IS NOT NULL THEN
    RAISE EXCEPTION 'ADMIN_SESSION_INVALID_UPGRADE_DATA id=%', v_id USING ERRCODE = '23514';
  END IF;

  SELECT id INTO v_id FROM hhy.admin_mfa_methods
  WHERE btrim(method) = '' OR version < 0 OR NOT (
    (status = 'PENDING' AND btrim(COALESCE(secret_ref, '')) <> ''
      AND confirmed_at IS NULL AND disabled_at IS NULL) OR
    (status = 'ACTIVE' AND btrim(COALESCE(secret_ref, '')) <> ''
      AND confirmed_at >= created_at AND disabled_at IS NULL) OR
    (status = 'DISABLED' AND secret_ref IS NULL AND disabled_at >= created_at)
  )
  LIMIT 1;
  IF v_id IS NOT NULL THEN
    RAISE EXCEPTION 'ADMIN_MFA_METHOD_INVALID_UPGRADE_DATA id=%', v_id USING ERRCODE = '23514';
  END IF;

  SELECT id INTO v_id FROM hhy.admin_recovery_codes
  WHERE btrim(code_hash) = '' OR (used_at IS NOT NULL AND used_at < created_at)
     OR (expires_at IS NOT NULL AND expires_at <= created_at)
     OR (used_at IS NOT NULL AND expires_at IS NOT NULL AND used_at > expires_at)
  LIMIT 1;
  IF v_id IS NOT NULL THEN
    RAISE EXCEPTION 'ADMIN_RECOVERY_CODE_INVALID_UPGRADE_DATA id=%', v_id USING ERRCODE = '23514';
  END IF;

  SELECT id INTO v_id FROM hhy.admin_login_logs
  WHERE btrim(event_type) = '' OR btrim(result) = ''
     OR (username_masked IS NOT NULL AND btrim(username_masked) = '')
     OR (request_id IS NOT NULL AND btrim(request_id) = '')
     OR (upper(result) IN ('SUCCESS', 'SUCCEEDED') AND failure_code IS NOT NULL)
  LIMIT 1;
  IF v_id IS NOT NULL THEN
    RAISE EXCEPTION 'ADMIN_LOGIN_LOG_INVALID_UPGRADE_DATA id=%', v_id USING ERRCODE = '23514';
  END IF;
END;
$$;

INSERT INTO hhy.admin_mfa_methods(
  admin_user_id, method, secret_ref, status, confirmed_at, version
)
SELECT id, 'TOTP', mfa_secret_ref, 'ACTIVE', clock_timestamp(), 0
FROM hhy.admin_users
WHERE mfa_secret_ref IS NOT NULL
ON CONFLICT (admin_user_id, method) DO NOTHING;

UPDATE hhy.admin_users
SET mfa_secret_ref = NULL, version = version + 1
WHERE mfa_secret_ref IS NOT NULL;

ALTER TABLE hhy.admin_users
  ADD CONSTRAINT ck_admin_users_legacy_mfa_secret_empty
  CHECK (mfa_secret_ref IS NULL),
  ADD CONSTRAINT ck_admin_users_version_nonnegative
  CHECK (version >= 0);

ALTER TABLE hhy.admin_sessions
  ADD CONSTRAINT ck_admin_sessions_identity_nonblank CHECK (
    btrim(access_jti) <> '' AND (refresh_hash IS NULL OR btrim(refresh_hash) <> '')
  ),
  ADD CONSTRAINT ck_admin_sessions_version_nonnegative CHECK (version >= 0),
  ADD CONSTRAINT ck_admin_sessions_lifecycle_time CHECK (
    expires_at > created_at AND
    (revoked_at IS NULL OR revoked_at >= created_at) AND
    (last_active_at IS NULL OR last_active_at >= created_at) AND
    (revoked_at IS NULL OR last_active_at IS NULL OR revoked_at >= last_active_at)
  );

DROP INDEX IF EXISTS hhy.ix_admin_sessions_refresh_hash;
CREATE UNIQUE INDEX uq_admin_sessions_refresh_hash
  ON hhy.admin_sessions (refresh_hash)
  WHERE refresh_hash IS NOT NULL;
CREATE INDEX ix_admin_sessions_active_by_admin
  ON hhy.admin_sessions (admin_user_id, expires_at DESC)
  WHERE revoked_at IS NULL;

CREATE OR REPLACE FUNCTION hhy.guard_admin_session_mutation()
RETURNS trigger
LANGUAGE plpgsql
AS $$
BEGIN
  IF TG_OP = 'INSERT' THEN
    IF NEW.version <> 0 OR NEW.revoked_at IS NOT NULL OR NEW.mfa_level <> 'NONE' THEN
      RAISE EXCEPTION 'ADMIN_SESSION_INVALID_INITIAL_STATE access_jti=%', NEW.access_jti
        USING ERRCODE = '23514';
    END IF;
    RETURN NEW;
  END IF;

  IF TG_OP = 'DELETE' THEN
    RAISE EXCEPTION 'ADMIN_SESSION_DELETE_FORBIDDEN access_jti=%', OLD.access_jti
      USING ERRCODE = '55000';
  END IF;

  IF ROW(NEW.admin_user_id, NEW.access_jti, NEW.created_at)
     IS DISTINCT FROM
     ROW(OLD.admin_user_id, OLD.access_jti, OLD.created_at) THEN
    RAISE EXCEPTION 'ADMIN_SESSION_IDENTITY_IMMUTABLE access_jti=%', OLD.access_jti
      USING ERRCODE = '55000';
  END IF;
  IF NEW.version <> OLD.version + 1 THEN
    RAISE EXCEPTION 'ADMIN_SESSION_VERSION_CONFLICT access_jti=% old=% new=%',
      OLD.access_jti, OLD.version, NEW.version USING ERRCODE = '40001';
  END IF;
  IF NEW.expires_at < OLD.expires_at THEN
    RAISE EXCEPTION 'ADMIN_SESSION_EXPIRY_REGRESSION access_jti=%', OLD.access_jti
      USING ERRCODE = '23514';
  END IF;
  IF OLD.revoked_at IS NOT NULL AND NEW IS DISTINCT FROM OLD THEN
    RAISE EXCEPTION 'ADMIN_SESSION_REVOKED_TERMINAL access_jti=%', OLD.access_jti
      USING ERRCODE = '55000';
  END IF;
  IF NEW.mfa_level IS DISTINCT FROM OLD.mfa_level AND NOT (
    (OLD.mfa_level = 'NONE' AND NEW.mfa_level IN ('VERIFIED', 'STEP_UP')) OR
    (OLD.mfa_level = 'VERIFIED' AND NEW.mfa_level = 'STEP_UP')
  ) THEN
    RAISE EXCEPTION 'ADMIN_SESSION_INVALID_MFA_TRANSITION access_jti=% from=% to=%',
      OLD.access_jti, OLD.mfa_level, NEW.mfa_level USING ERRCODE = '23514';
  END IF;
  IF NEW.last_active_at IS NOT NULL AND OLD.last_active_at IS NOT NULL
     AND NEW.last_active_at < OLD.last_active_at THEN
    RAISE EXCEPTION 'ADMIN_SESSION_ACTIVITY_REGRESSION access_jti=%', OLD.access_jti
      USING ERRCODE = '23514';
  END IF;
  RETURN NEW;
END;
$$;

CREATE TRIGGER trg_admin_sessions_guard
BEFORE INSERT OR UPDATE OR DELETE ON hhy.admin_sessions
FOR EACH ROW EXECUTE FUNCTION hhy.guard_admin_session_mutation();

ALTER TABLE hhy.admin_mfa_methods
  ADD CONSTRAINT ck_admin_mfa_methods_method CHECK (btrim(method) <> ''),
  ADD CONSTRAINT ck_admin_mfa_methods_version_nonnegative CHECK (version >= 0),
  ADD CONSTRAINT ck_admin_mfa_methods_lifecycle CHECK (
    (status = 'PENDING' AND btrim(COALESCE(secret_ref, '')) <> ''
      AND confirmed_at IS NULL AND disabled_at IS NULL) OR
    (status = 'ACTIVE' AND btrim(COALESCE(secret_ref, '')) <> ''
      AND confirmed_at >= created_at AND disabled_at IS NULL) OR
    (status = 'DISABLED' AND secret_ref IS NULL AND disabled_at >= created_at)
  );

CREATE OR REPLACE FUNCTION hhy.guard_admin_mfa_method_mutation()
RETURNS trigger
LANGUAGE plpgsql
AS $$
BEGIN
  IF TG_OP = 'INSERT' THEN
    IF NEW.status <> 'PENDING' OR NEW.version <> 0 THEN
      RAISE EXCEPTION 'ADMIN_MFA_METHOD_INVALID_INITIAL_STATE admin_user_id=% method=%',
        NEW.admin_user_id, NEW.method USING ERRCODE = '23514';
    END IF;
    RETURN NEW;
  END IF;

  IF TG_OP = 'DELETE' THEN
    RAISE EXCEPTION 'ADMIN_MFA_METHOD_DELETE_FORBIDDEN admin_user_id=% method=%',
      OLD.admin_user_id, OLD.method USING ERRCODE = '55000';
  END IF;

  IF ROW(NEW.admin_user_id, NEW.method, NEW.created_at)
     IS DISTINCT FROM ROW(OLD.admin_user_id, OLD.method, OLD.created_at) THEN
    RAISE EXCEPTION 'ADMIN_MFA_METHOD_IDENTITY_IMMUTABLE admin_user_id=% method=%',
      OLD.admin_user_id, OLD.method USING ERRCODE = '55000';
  END IF;
  IF NEW.version <> OLD.version + 1 THEN
    RAISE EXCEPTION 'ADMIN_MFA_METHOD_VERSION_CONFLICT admin_user_id=% method=% old=% new=%',
      OLD.admin_user_id, OLD.method, OLD.version, NEW.version USING ERRCODE = '40001';
  END IF;
  IF NEW.status IS DISTINCT FROM OLD.status AND NOT (
    (OLD.status = 'PENDING' AND NEW.status IN ('ACTIVE', 'DISABLED')) OR
    (OLD.status = 'ACTIVE' AND NEW.status = 'DISABLED') OR
    (OLD.status = 'DISABLED' AND NEW.status = 'PENDING')
  ) THEN
    RAISE EXCEPTION 'ADMIN_MFA_METHOD_INVALID_TRANSITION admin_user_id=% method=% from=% to=%',
      OLD.admin_user_id, OLD.method, OLD.status, NEW.status USING ERRCODE = '23514';
  END IF;
  RETURN NEW;
END;
$$;

CREATE TRIGGER trg_admin_mfa_methods_guard
BEFORE INSERT OR UPDATE OR DELETE ON hhy.admin_mfa_methods
FOR EACH ROW EXECUTE FUNCTION hhy.guard_admin_mfa_method_mutation();

ALTER TABLE hhy.admin_recovery_codes
  ADD CONSTRAINT ck_admin_recovery_codes_hash_nonblank CHECK (btrim(code_hash) <> ''),
  ADD CONSTRAINT ck_admin_recovery_codes_lifecycle_time CHECK (
    (used_at IS NULL OR used_at >= created_at) AND
    (expires_at IS NULL OR expires_at > created_at) AND
    (used_at IS NULL OR expires_at IS NULL OR used_at <= expires_at)
  );
CREATE INDEX ix_admin_recovery_codes_available
  ON hhy.admin_recovery_codes (admin_user_id, expires_at)
  WHERE used_at IS NULL;

CREATE OR REPLACE FUNCTION hhy.guard_admin_recovery_code_mutation()
RETURNS trigger
LANGUAGE plpgsql
AS $$
BEGIN
  IF TG_OP = 'INSERT' THEN
    IF NEW.used_at IS NOT NULL THEN
      RAISE EXCEPTION 'ADMIN_RECOVERY_CODE_MUST_START_UNUSED admin_user_id=%', NEW.admin_user_id
        USING ERRCODE = '23514';
    END IF;
    RETURN NEW;
  END IF;
  IF TG_OP = 'DELETE' THEN
    RAISE EXCEPTION 'ADMIN_RECOVERY_CODE_DELETE_FORBIDDEN id=%', OLD.id
      USING ERRCODE = '55000';
  END IF;
  IF ROW(NEW.admin_user_id, NEW.code_hash, NEW.created_at)
     IS DISTINCT FROM ROW(OLD.admin_user_id, OLD.code_hash, OLD.created_at) THEN
    RAISE EXCEPTION 'ADMIN_RECOVERY_CODE_IDENTITY_IMMUTABLE id=%', OLD.id
      USING ERRCODE = '55000';
  END IF;
  IF NEW.used_at IS DISTINCT FROM OLD.used_at THEN
    IF OLD.used_at IS NOT NULL OR NEW.used_at IS NULL THEN
      RAISE EXCEPTION 'ADMIN_RECOVERY_CODE_ALREADY_USED id=%', OLD.id
        USING ERRCODE = '23505';
    END IF;
  ELSIF NEW.expires_at IS DISTINCT FROM OLD.expires_at THEN
    IF OLD.used_at IS NOT NULL OR NEW.expires_at IS NULL
       OR (OLD.expires_at IS NOT NULL AND NEW.expires_at > OLD.expires_at) THEN
      RAISE EXCEPTION 'ADMIN_RECOVERY_CODE_EXPIRY_EXTENSION_FORBIDDEN id=%', OLD.id
        USING ERRCODE = '23514';
    END IF;
  ELSE
    RAISE EXCEPTION 'ADMIN_RECOVERY_CODE_MUTATION_FORBIDDEN id=%', OLD.id
      USING ERRCODE = '55000';
  END IF;
  RETURN NEW;
END;
$$;

CREATE TRIGGER trg_admin_recovery_codes_guard
BEFORE INSERT OR UPDATE OR DELETE ON hhy.admin_recovery_codes
FOR EACH ROW EXECUTE FUNCTION hhy.guard_admin_recovery_code_mutation();

CREATE OR REPLACE FUNCTION hhy.consume_admin_recovery_code(
  p_admin_user_id bigint,
  p_code_hash varchar
)
RETURNS boolean
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = hhy, pg_temp
AS $$
DECLARE
  v_id bigint;
BEGIN
  IF p_admin_user_id IS NULL OR btrim(COALESCE(p_code_hash, '')) = '' THEN
    RETURN false;
  END IF;
  UPDATE hhy.admin_recovery_codes
  SET used_at = clock_timestamp()
  WHERE admin_user_id = p_admin_user_id
    AND code_hash = p_code_hash
    AND used_at IS NULL
    AND (expires_at IS NULL OR expires_at > clock_timestamp())
  RETURNING id INTO v_id;
  RETURN v_id IS NOT NULL;
END;
$$;

REVOKE ALL ON FUNCTION hhy.consume_admin_recovery_code(bigint, varchar) FROM PUBLIC;

ALTER TABLE hhy.admin_login_logs
  ADD CONSTRAINT ck_admin_login_logs_identity_nonblank CHECK (
    btrim(event_type) <> '' AND btrim(result) <> '' AND
    (username_masked IS NULL OR btrim(username_masked) <> '') AND
    (request_id IS NULL OR btrim(request_id) <> '')
  ),
  ADD CONSTRAINT ck_admin_login_logs_failure_pair CHECK (
    upper(result) NOT IN ('SUCCESS', 'SUCCEEDED') OR failure_code IS NULL
  );

CREATE INDEX ix_admin_login_logs_lock_window
  ON hhy.admin_login_logs (admin_user_id, result, created_at DESC);
CREATE INDEX ix_admin_login_logs_ip_lock_window
  ON hhy.admin_login_logs (ip, result, created_at DESC);
CREATE TRIGGER trg_admin_login_logs_immutable
BEFORE UPDATE OR DELETE ON hhy.admin_login_logs
FOR EACH ROW EXECUTE FUNCTION hhy.prevent_immutable_mutation();
