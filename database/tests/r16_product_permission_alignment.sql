SET search_path TO hhy, public;

DO $$
DECLARE
  legacy_role_id bigint;
BEGIN
  IF (SELECT count(*) FROM hhy.admin_permissions
      WHERE code IN ('product.read', 'product.write')) <> 2 THEN
    RAISE EXCEPTION 'R16_PRODUCT_PERMISSION_ROWS_MISSING';
  END IF;

  SELECT id INTO legacy_role_id
  FROM hhy.admin_roles
  WHERE code = 'R16_PRODUCT_LEGACY_TEST';
  IF legacy_role_id IS NULL THEN
    RAISE EXCEPTION 'R16_PRODUCT_LEGACY_TEST_ROLE_MISSING';
  END IF;

  IF (SELECT count(*)
      FROM hhy.admin_role_permissions grant_row
      JOIN hhy.admin_permissions permission ON permission.id = grant_row.permission_id
      WHERE grant_row.role_id = legacy_role_id
        AND permission.code IN ('product.read', 'product.write')) <> 2 THEN
    RAISE EXCEPTION 'R16_PRODUCT_LEGACY_ROLE_NOT_MAPPED';
  END IF;

  IF (SELECT count(*)
      FROM hhy.admin_role_permissions grant_row
      JOIN hhy.admin_roles role ON role.id = grant_row.role_id
      JOIN hhy.admin_permissions permission ON permission.id = grant_row.permission_id
      WHERE role.code = 'SUPER_ADMIN'
        AND role.status = 'ACTIVE'
        AND permission.code IN ('product.read', 'product.write')) <> 2 THEN
    RAISE EXCEPTION 'R16_PRODUCT_SUPER_ADMIN_NOT_MAPPED';
  END IF;
END;
$$;

SELECT 'R16_PRODUCT_PERMISSION_ALIGNMENT PASS' AS result;

