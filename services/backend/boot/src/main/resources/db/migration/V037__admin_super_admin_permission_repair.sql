SET search_path TO hhy, public;

DO $$
BEGIN
  IF NOT EXISTS (
    SELECT 1 FROM hhy.admin_roles
    WHERE code = 'SUPER_ADMIN' AND status = 'ACTIVE'
  ) THEN
    RAISE EXCEPTION 'ADMIN_SUPER_ADMIN_ROLE_MISSING_OR_INACTIVE'
      USING ERRCODE = '23514';
  END IF;
END;
$$;

INSERT INTO hhy.admin_role_permissions(role_id, permission_id)
SELECT role.id, permission.id
FROM hhy.admin_roles role
CROSS JOIN hhy.admin_permissions permission
WHERE role.code = 'SUPER_ADMIN'
  AND role.status = 'ACTIVE'
ON CONFLICT (role_id, permission_id) DO NOTHING;

DO $$
BEGIN
  IF EXISTS (
    SELECT 1
    FROM hhy.admin_permissions permission
    WHERE NOT EXISTS (
      SELECT 1
      FROM hhy.admin_roles role
      JOIN hhy.admin_role_permissions role_permission ON role_permission.role_id = role.id
      WHERE role.code = 'SUPER_ADMIN'
        AND role.status = 'ACTIVE'
        AND role_permission.permission_id = permission.id
    )
  ) THEN
    RAISE EXCEPTION 'ADMIN_SUPER_ADMIN_PERMISSION_REPAIR_INCOMPLETE'
      USING ERRCODE = '23514';
  END IF;
END;
$$;
