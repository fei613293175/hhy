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

SELECT pg_temp.assert_true(
  (SELECT count(*) = 2
   FROM hhy.admin_permissions
   WHERE (code = 'admin.self.read' AND resource = 'admin.self' AND action = 'read')
      OR (code = 'admin.self.security' AND resource = 'admin.self' AND action = 'security')),
  'R01 administrator self-service permissions are incomplete or drifted'
);

SELECT pg_temp.assert_true(
  (SELECT count(*) = 2
   FROM hhy.admin_role_permissions AS role_permission
   JOIN hhy.admin_roles AS role ON role.id = role_permission.role_id
   JOIN hhy.admin_permissions AS permission ON permission.id = role_permission.permission_id
   WHERE role.code = 'SUPER_ADMIN'
     AND permission.code IN ('admin.self.read', 'admin.self.security')),
  'SUPER_ADMIN must own both R01 administrator self-service permissions'
);

SELECT pg_temp.expect_sqlstate(
  $$INSERT INTO hhy.admin_permissions(code, resource, action)
    VALUES ('admin.self.read', 'drifted', 'read')$$,
  '23505', 'permission codes must stay unique'
);

SELECT pg_temp.expect_sqlstate(
  $$INSERT INTO hhy.admin_role_permissions(role_id, permission_id)
    SELECT role.id, permission.id
    FROM hhy.admin_roles AS role
    JOIN hhy.admin_permissions AS permission ON permission.code = 'admin.self.read'
    WHERE role.code = 'SUPER_ADMIN'$$,
  '23505', 'role-permission bindings must stay unique'
);

ROLLBACK;
SELECT 'R01_ADMIN_SELF_RBAC_OK' AS result;
