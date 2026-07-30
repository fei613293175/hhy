-- DEV/TEST only. Refuse to erase independently assigned granular grants.
BEGIN;
SET search_path TO hhy, public;

DO $$
BEGIN
  IF EXISTS (
    SELECT 1
    FROM hhy.admin_role_permissions grant_row
    JOIN hhy.admin_roles role ON role.id = grant_row.role_id
    JOIN hhy.admin_permissions permission ON permission.id = grant_row.permission_id
    WHERE permission.code IN ('product.read', 'product.write')
      AND role.code <> 'SUPER_ADMIN'
      AND NOT EXISTS (
        SELECT 1
        FROM hhy.admin_role_permissions legacy
        JOIN hhy.admin_permissions legacy_permission
          ON legacy_permission.id = legacy.permission_id
        WHERE legacy.role_id = grant_row.role_id
          AND legacy_permission.code = 'product.manage'
      )
  ) THEN
    RAISE EXCEPTION 'R16_U046_INDEPENDENT_GRANULAR_GRANTS_PRESENT'
      USING ERRCODE = '55000';
  END IF;
END;
$$;

DELETE FROM hhy.admin_role_permissions grant_row
USING hhy.admin_permissions permission
WHERE permission.id = grant_row.permission_id
  AND permission.code IN ('product.read', 'product.write');

DELETE FROM hhy.admin_permissions
WHERE code IN ('product.read', 'product.write');

COMMIT;

