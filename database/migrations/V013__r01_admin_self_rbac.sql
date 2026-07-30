-- R01 administrator self-service RBAC seed.
-- The permission codes already exist in the frozen R01 API contract. This
-- migration only materializes them and grants them to the existing SUPER_ADMIN.
SET search_path TO hhy, public;

DO $$
DECLARE
  v_role_id bigint;
  v_conflict text;
BEGIN
  SELECT id INTO v_role_id
  FROM hhy.admin_roles
  WHERE code = 'SUPER_ADMIN' AND status = 'ACTIVE';

  IF v_role_id IS NULL THEN
    RAISE EXCEPTION 'R01_SUPER_ADMIN_ROLE_MISSING_OR_INACTIVE'
      USING ERRCODE = '23514';
  END IF;

  SELECT code INTO v_conflict
  FROM hhy.admin_permissions
  WHERE (code = 'admin.self.read'
         AND (resource <> 'admin.self' OR action IS DISTINCT FROM 'read'))
     OR (code = 'admin.self.security'
         AND (resource <> 'admin.self' OR action IS DISTINCT FROM 'security'))
  LIMIT 1;

  IF v_conflict IS NOT NULL THEN
    RAISE EXCEPTION 'R01_ADMIN_SELF_PERMISSION_CONFLICT code=%', v_conflict
      USING ERRCODE = '23514';
  END IF;
END;
$$;

INSERT INTO hhy.admin_permissions(code, resource, action)
VALUES
  ('admin.self.read', 'admin.self', 'read'),
  ('admin.self.security', 'admin.self', 'security')
ON CONFLICT (code) DO NOTHING;

INSERT INTO hhy.admin_role_permissions(role_id, permission_id)
SELECT role.id, permission.id
FROM hhy.admin_roles AS role
JOIN hhy.admin_permissions AS permission
  ON permission.code IN ('admin.self.read', 'admin.self.security')
WHERE role.code = 'SUPER_ADMIN'
  AND role.status = 'ACTIVE'
ON CONFLICT (role_id, permission_id) DO NOTHING;

DO $$
BEGIN
  IF (
    SELECT count(*)
    FROM hhy.admin_role_permissions AS role_permission
    JOIN hhy.admin_roles AS role ON role.id = role_permission.role_id
    JOIN hhy.admin_permissions AS permission ON permission.id = role_permission.permission_id
    WHERE role.code = 'SUPER_ADMIN'
      AND permission.code IN ('admin.self.read', 'admin.self.security')
  ) <> 2 THEN
    RAISE EXCEPTION 'R01_SUPER_ADMIN_PERMISSION_BINDING_INCOMPLETE'
      USING ERRCODE = '23514';
  END IF;
END;
$$;
