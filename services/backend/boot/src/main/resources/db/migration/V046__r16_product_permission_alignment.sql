-- Align the R16 product APIs with the frozen granular permissions.
SET search_path TO hhy, public;

DO $$
BEGIN
  IF NOT EXISTS (
    SELECT 1 FROM hhy.admin_permissions WHERE code = 'product.manage'
  ) THEN
    RAISE EXCEPTION 'R16_PRODUCT_MANAGE_PERMISSION_MISSING'
      USING ERRCODE = '23514';
  END IF;
  IF NOT EXISTS (
    SELECT 1 FROM hhy.admin_roles
    WHERE code = 'SUPER_ADMIN' AND status = 'ACTIVE'
  ) THEN
    RAISE EXCEPTION 'R16_SUPER_ADMIN_ROLE_MISSING_OR_INACTIVE'
      USING ERRCODE = '23514';
  END IF;
END;
$$;

INSERT INTO hhy.admin_permissions(code, resource, action) VALUES
  ('product.read', 'product', 'read'),
  ('product.write', 'product', 'write')
ON CONFLICT (code) DO UPDATE
SET resource = EXCLUDED.resource,
    action = EXCLUDED.action;

-- Existing product managers retain the complete capability after the split.
INSERT INTO hhy.admin_role_permissions(role_id, permission_id)
SELECT legacy.role_id, granular.id
FROM hhy.admin_role_permissions legacy
JOIN hhy.admin_permissions legacy_permission
  ON legacy_permission.id = legacy.permission_id
CROSS JOIN hhy.admin_permissions granular
WHERE legacy_permission.code = 'product.manage'
  AND granular.code IN ('product.read', 'product.write')
ON CONFLICT (role_id, permission_id) DO NOTHING;

-- SUPER_ADMIN receives permissions introduced after the baseline repair.
INSERT INTO hhy.admin_role_permissions(role_id, permission_id)
SELECT role.id, permission.id
FROM hhy.admin_roles role
CROSS JOIN hhy.admin_permissions permission
WHERE role.code = 'SUPER_ADMIN'
  AND role.status = 'ACTIVE'
  AND permission.code IN ('product.read', 'product.write')
ON CONFLICT (role_id, permission_id) DO NOTHING;

DO $$
BEGIN
  IF (SELECT count(*) FROM hhy.admin_permissions
      WHERE code IN ('product.read', 'product.write')
        AND resource = 'product'
        AND action IN ('read', 'write')) <> 2 THEN
    RAISE EXCEPTION 'R16_PRODUCT_GRANULAR_PERMISSIONS_INCOMPLETE'
      USING ERRCODE = '23514';
  END IF;

  IF EXISTS (
    SELECT 1
    FROM hhy.admin_role_permissions legacy
    JOIN hhy.admin_permissions legacy_permission
      ON legacy_permission.id = legacy.permission_id
    WHERE legacy_permission.code = 'product.manage'
      AND EXISTS (
        SELECT 1
        FROM hhy.admin_permissions granular
        WHERE granular.code IN ('product.read', 'product.write')
          AND NOT EXISTS (
            SELECT 1
            FROM hhy.admin_role_permissions mapped
            WHERE mapped.role_id = legacy.role_id
              AND mapped.permission_id = granular.id
          )
      )
  ) THEN
    RAISE EXCEPTION 'R16_PRODUCT_MANAGER_PERMISSION_MAPPING_INCOMPLETE'
      USING ERRCODE = '23514';
  END IF;

  IF (SELECT count(*)
      FROM hhy.admin_role_permissions grant_row
      JOIN hhy.admin_roles role ON role.id = grant_row.role_id
      JOIN hhy.admin_permissions permission ON permission.id = grant_row.permission_id
      WHERE role.code = 'SUPER_ADMIN'
        AND role.status = 'ACTIVE'
        AND permission.code IN ('product.read', 'product.write')) <> 2 THEN
    RAISE EXCEPTION 'R16_SUPER_ADMIN_PRODUCT_PERMISSION_BINDING_INCOMPLETE'
      USING ERRCODE = '23514';
  END IF;
END;
$$;

