-- Align the R12 review workbench with the frozen granular API permissions.
SET search_path TO hhy, public;

INSERT INTO hhy.admin_permissions(code, resource, action) VALUES
  ('review.read', 'review', 'read'),
  ('review.decide', 'review', 'decide'),
  ('review.assign', 'review', 'assign'),
  ('report.read', 'report', 'read'),
  ('appeal.read', 'appeal', 'read')
ON CONFLICT (code) DO UPDATE
SET resource = EXCLUDED.resource,
    action = EXCLUDED.action;

-- Existing review managers retain every capability after the permission split.
INSERT INTO hhy.admin_role_permissions(role_id, permission_id)
SELECT legacy.role_id, granular.id
FROM hhy.admin_role_permissions legacy
JOIN hhy.admin_permissions legacy_permission
  ON legacy_permission.id = legacy.permission_id
CROSS JOIN hhy.admin_permissions granular
WHERE legacy_permission.code = 'review.manage'
  AND granular.code IN ('review.read', 'review.decide', 'review.assign', 'report.read', 'appeal.read')
ON CONFLICT (role_id, permission_id) DO NOTHING;

-- SUPER_ADMIN must receive permissions introduced after the baseline repair.
INSERT INTO hhy.admin_role_permissions(role_id, permission_id)
SELECT role.id, permission.id
FROM hhy.admin_roles role
CROSS JOIN hhy.admin_permissions permission
WHERE role.code = 'SUPER_ADMIN'
  AND role.status = 'ACTIVE'
  AND permission.code IN ('review.read', 'review.decide', 'review.assign', 'report.read', 'appeal.read')
ON CONFLICT (role_id, permission_id) DO NOTHING;

DO $$
BEGIN
  IF (SELECT count(*) FROM hhy.admin_permissions
      WHERE code IN ('review.read', 'review.decide', 'review.assign', 'report.read', 'appeal.read')) <> 5 THEN
    RAISE EXCEPTION 'R12_REVIEW_GRANULAR_PERMISSIONS_INCOMPLETE'
      USING ERRCODE = '23514';
  END IF;

  IF EXISTS (
    SELECT 1
    FROM hhy.admin_role_permissions legacy
    JOIN hhy.admin_permissions legacy_permission
      ON legacy_permission.id = legacy.permission_id
    WHERE legacy_permission.code = 'review.manage'
      AND EXISTS (
        SELECT 1 FROM hhy.admin_permissions granular
        WHERE granular.code IN ('review.read', 'review.decide', 'review.assign', 'report.read', 'appeal.read')
          AND NOT EXISTS (
            SELECT 1 FROM hhy.admin_role_permissions mapped
            WHERE mapped.role_id = legacy.role_id
              AND mapped.permission_id = granular.id
          )
      )
  ) THEN
    RAISE EXCEPTION 'R12_REVIEW_MANAGER_PERMISSION_MAPPING_INCOMPLETE'
      USING ERRCODE = '23514';
  END IF;
END;
$$;
