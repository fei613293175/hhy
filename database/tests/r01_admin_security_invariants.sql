\set ON_ERROR_STOP on
SET search_path TO hhy, public;
BEGIN;

CREATE OR REPLACE FUNCTION pg_temp.assert_true(p_condition boolean, p_message text)
RETURNS void
LANGUAGE plpgsql
AS $$
BEGIN
  IF p_condition IS NOT TRUE THEN
    RAISE EXCEPTION 'ASSERTION_FAILED: %', p_message;
  END IF;
END;
$$;

CREATE OR REPLACE FUNCTION pg_temp.expect_sqlstate(
  p_sql text,
  p_expected_state text,
  p_message text
)
RETURNS void
LANGUAGE plpgsql
AS $$
DECLARE
  v_state text;
BEGIN
  BEGIN
    EXECUTE p_sql;
  EXCEPTION WHEN OTHERS THEN
    GET STACKED DIAGNOSTICS v_state = RETURNED_SQLSTATE;
    IF v_state <> p_expected_state THEN
      RAISE EXCEPTION 'ASSERTION_FAILED: %, expected SQLSTATE %, got %',
        p_message, p_expected_state, v_state;
    END IF;
    RETURN;
  END;
  RAISE EXCEPTION 'ASSERTION_FAILED: %, statement unexpectedly succeeded', p_message;
END;
$$;

INSERT INTO hhy.admin_users(username, password_hash, status)
VALUES ('r01-db-admin', repeat('a', 64), 'ACTIVE');

SELECT pg_temp.expect_sqlstate(
  $$UPDATE hhy.admin_users SET mfa_secret_ref='legacy-secret' WHERE username='r01-db-admin'$$,
  '23514', 'legacy MFA secret source must be read-only and empty'
);

INSERT INTO hhy.admin_sessions(
  admin_user_id, access_jti, refresh_hash, expires_at, last_active_at
)
SELECT id, 'r01-session-1', 'r01-refresh-1', clock_timestamp() + interval '1 hour', clock_timestamp()
FROM hhy.admin_users WHERE username='r01-db-admin';
SELECT pg_temp.expect_sqlstate(
  $$INSERT INTO hhy.admin_sessions(admin_user_id,access_jti,mfa_level,expires_at)
    SELECT id,'r01-session-invalid-initial','VERIFIED',clock_timestamp()+interval '1 hour'
    FROM hhy.admin_users WHERE username='r01-db-admin'$$,
  '23514', 'administrator sessions must start at MFA level NONE'
);

UPDATE hhy.admin_sessions
SET mfa_level='VERIFIED', version=version+1
WHERE access_jti='r01-session-1';
UPDATE hhy.admin_sessions
SET refresh_hash='r01-refresh-rotated', expires_at=expires_at+interval '1 hour', version=version+1
WHERE access_jti='r01-session-1';
UPDATE hhy.admin_sessions
SET mfa_level='STEP_UP', version=version+1
WHERE access_jti='r01-session-1';
SELECT pg_temp.expect_sqlstate(
  $$UPDATE hhy.admin_sessions SET mfa_level='NONE', version=version+1 WHERE access_jti='r01-session-1'$$,
  '23514', 'MFA assurance must not downgrade inside a session'
);
SELECT pg_temp.expect_sqlstate(
  $$UPDATE hhy.admin_sessions SET last_active_at=clock_timestamp() WHERE access_jti='r01-session-1'$$,
  '40001', 'session updates must use optimistic versioning'
);
UPDATE hhy.admin_sessions
SET revoked_at=clock_timestamp(), version=version+1
WHERE access_jti='r01-session-1';
SELECT pg_temp.expect_sqlstate(
  $$UPDATE hhy.admin_sessions SET last_active_at=clock_timestamp(), version=version+1 WHERE access_jti='r01-session-1'$$,
  '55000', 'revoked sessions must be terminal'
);
SELECT pg_temp.expect_sqlstate(
  $$DELETE FROM hhy.admin_sessions WHERE access_jti='r01-session-1'$$,
  '55000', 'session security facts must not be deleted'
);

INSERT INTO hhy.admin_mfa_methods(admin_user_id, method, secret_ref)
SELECT id, 'TOTP', 'vault://r01/totp/1'
FROM hhy.admin_users WHERE username='r01-db-admin';
UPDATE hhy.admin_mfa_methods
SET status='ACTIVE', confirmed_at=clock_timestamp(), version=version+1
WHERE admin_user_id=(SELECT id FROM hhy.admin_users WHERE username='r01-db-admin')
  AND method='TOTP';
SELECT pg_temp.expect_sqlstate(
  $$UPDATE hhy.admin_mfa_methods SET status='PENDING', confirmed_at=NULL, version=version+1
    WHERE admin_user_id=(SELECT id FROM hhy.admin_users WHERE username='r01-db-admin') AND method='TOTP'$$,
  '23514', 'active MFA must not return directly to pending'
);
UPDATE hhy.admin_mfa_methods
SET status='DISABLED', secret_ref=NULL, disabled_at=clock_timestamp(), version=version+1
WHERE admin_user_id=(SELECT id FROM hhy.admin_users WHERE username='r01-db-admin')
  AND method='TOTP';
UPDATE hhy.admin_mfa_methods
SET status='PENDING', secret_ref='vault://r01/totp/2', confirmed_at=NULL,
    disabled_at=NULL, version=version+1
WHERE admin_user_id=(SELECT id FROM hhy.admin_users WHERE username='r01-db-admin')
  AND method='TOTP';

INSERT INTO hhy.admin_recovery_codes(admin_user_id, code_hash, expires_at)
SELECT id, repeat('b', 64), clock_timestamp() + interval '1 day'
FROM hhy.admin_users WHERE username='r01-db-admin';
INSERT INTO hhy.admin_recovery_codes(admin_user_id, code_hash, expires_at)
SELECT id, repeat('d', 64), clock_timestamp() + interval '1 day'
FROM hhy.admin_users WHERE username='r01-db-admin';
UPDATE hhy.admin_recovery_codes
SET expires_at=clock_timestamp()+interval '1 hour'
WHERE code_hash=repeat('d', 64);
SELECT pg_temp.expect_sqlstate(
  $$UPDATE hhy.admin_recovery_codes SET expires_at=expires_at+interval '1 day'
    WHERE code_hash=repeat('d', 64)$$,
  '23514', 'recovery-code expiry must not be extended'
);
SELECT pg_temp.assert_true(
  hhy.consume_admin_recovery_code(
    (SELECT id FROM hhy.admin_users WHERE username='r01-db-admin'), repeat('b', 64)
  ),
  'first recovery-code consumption must succeed'
);
SELECT pg_temp.assert_true(
  NOT hhy.consume_admin_recovery_code(
    (SELECT id FROM hhy.admin_users WHERE username='r01-db-admin'), repeat('b', 64)
  ),
  'replayed recovery-code consumption must fail closed'
);
SELECT pg_temp.expect_sqlstate(
  $$DELETE FROM hhy.admin_recovery_codes WHERE code_hash=repeat('b', 64)$$,
  '55000', 'recovery-code facts must not be deleted'
);

INSERT INTO hhy.admin_login_logs(
  admin_user_id, username_masked, event_type, result, request_id
)
SELECT id, 'r***n', 'PASSWORD_LOGIN', 'SUCCESS', 'r01-request-1'
FROM hhy.admin_users WHERE username='r01-db-admin';
SELECT pg_temp.expect_sqlstate(
  $$UPDATE hhy.admin_login_logs SET result='FAILED' WHERE request_id='r01-request-1'$$,
  '55000', 'login security logs must be immutable'
);
SELECT pg_temp.expect_sqlstate(
  $$INSERT INTO hhy.admin_login_logs(event_type,result,failure_code)
    VALUES ('PASSWORD_LOGIN','SUCCESS','BAD_PASSWORD')$$,
  '23514', 'successful log rows must not carry a failure code'
);

SELECT pg_temp.assert_true(
  (SELECT count(*) = 1 FROM hhy.admin_sessions WHERE access_jti='r01-session-1'),
  'session lifecycle test did not retain its security fact'
);
ROLLBACK;
SELECT 'R01_ADMIN_SECURITY_INVARIANTS_OK' AS result;
