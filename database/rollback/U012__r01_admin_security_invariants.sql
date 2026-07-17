-- Development-only rollback for R01 administrator security invariants.
SET search_path TO hhy, public;

DROP TRIGGER IF EXISTS trg_admin_login_logs_immutable ON hhy.admin_login_logs;
DROP INDEX IF EXISTS hhy.ix_admin_login_logs_ip_lock_window;
DROP INDEX IF EXISTS hhy.ix_admin_login_logs_lock_window;
ALTER TABLE hhy.admin_login_logs
  DROP CONSTRAINT IF EXISTS ck_admin_login_logs_failure_pair,
  DROP CONSTRAINT IF EXISTS ck_admin_login_logs_identity_nonblank;

DROP FUNCTION IF EXISTS hhy.consume_admin_recovery_code(bigint, varchar);
DROP TRIGGER IF EXISTS trg_admin_recovery_codes_guard ON hhy.admin_recovery_codes;
DROP FUNCTION IF EXISTS hhy.guard_admin_recovery_code_mutation();
DROP INDEX IF EXISTS hhy.ix_admin_recovery_codes_available;
ALTER TABLE hhy.admin_recovery_codes
  DROP CONSTRAINT IF EXISTS ck_admin_recovery_codes_lifecycle_time,
  DROP CONSTRAINT IF EXISTS ck_admin_recovery_codes_hash_nonblank;

DROP TRIGGER IF EXISTS trg_admin_mfa_methods_guard ON hhy.admin_mfa_methods;
DROP FUNCTION IF EXISTS hhy.guard_admin_mfa_method_mutation();
ALTER TABLE hhy.admin_mfa_methods
  DROP CONSTRAINT IF EXISTS ck_admin_mfa_methods_lifecycle,
  DROP CONSTRAINT IF EXISTS ck_admin_mfa_methods_version_nonnegative,
  DROP CONSTRAINT IF EXISTS ck_admin_mfa_methods_method;

DROP TRIGGER IF EXISTS trg_admin_sessions_guard ON hhy.admin_sessions;
DROP FUNCTION IF EXISTS hhy.guard_admin_session_mutation();
DROP INDEX IF EXISTS hhy.ix_admin_sessions_active_by_admin;
DROP INDEX IF EXISTS hhy.uq_admin_sessions_refresh_hash;
CREATE INDEX IF NOT EXISTS ix_admin_sessions_refresh_hash
  ON hhy.admin_sessions (refresh_hash);
ALTER TABLE hhy.admin_sessions
  DROP CONSTRAINT IF EXISTS ck_admin_sessions_lifecycle_time,
  DROP CONSTRAINT IF EXISTS ck_admin_sessions_version_nonnegative,
  DROP CONSTRAINT IF EXISTS ck_admin_sessions_identity_nonblank;

ALTER TABLE hhy.admin_users
  DROP CONSTRAINT IF EXISTS ck_admin_users_version_nonnegative,
  DROP CONSTRAINT IF EXISTS ck_admin_users_legacy_mfa_secret_empty;

UPDATE hhy.admin_users AS admin_user
SET mfa_secret_ref = method.secret_ref,
    version = admin_user.version + 1
FROM hhy.admin_mfa_methods AS method
WHERE method.admin_user_id = admin_user.id
  AND method.method = 'TOTP'
  AND method.status = 'ACTIVE'
  AND method.secret_ref IS NOT NULL
  AND char_length(method.secret_ref) <= 255;

-- References longer than the frozen legacy varchar(255) remain losslessly in
-- admin_mfa_methods. They are intentionally not truncated during rollback.
