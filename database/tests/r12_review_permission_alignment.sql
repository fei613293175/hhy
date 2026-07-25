SET search_path TO hhy, public;

DO $$
DECLARE
  manager_role_id bigint;
BEGIN
  INSERT INTO hhy.admin_roles(code, name, status)
  VALUES ('R12_REVIEW_PERMISSION_TEST', 'R12审核权限迁移测试', 'ACTIVE')
  ON CONFLICT (code) DO UPDATE SET status = 'ACTIVE'
  RETURNING id INTO manager_role_id;

  INSERT INTO hhy.admin_role_permissions(role_id, permission_id)
  SELECT manager_role_id, id FROM hhy.admin_permissions WHERE code = 'review.manage'
  ON CONFLICT (role_id, permission_id) DO NOTHING;

  INSERT INTO hhy.admin_role_permissions(role_id, permission_id)
  SELECT manager_role_id, id FROM hhy.admin_permissions
  WHERE code IN ('review.read', 'review.decide', 'review.assign', 'report.read', 'appeal.read')
  ON CONFLICT (role_id, permission_id) DO NOTHING;

  IF (SELECT count(*)
      FROM hhy.admin_role_permissions grant_row
      JOIN hhy.admin_permissions permission ON permission.id = grant_row.permission_id
      WHERE grant_row.role_id = manager_role_id
        AND permission.code IN ('review.read', 'review.decide', 'review.assign', 'report.read', 'appeal.read')) <> 5 THEN
    RAISE EXCEPTION 'R12_REVIEW_PERMISSION_ALIGNMENT_NOT_EFFECTIVE';
  END IF;

  DELETE FROM hhy.admin_role_permissions WHERE role_id = manager_role_id;
  DELETE FROM hhy.admin_roles WHERE id = manager_role_id;
END;
$$;

SELECT 'R12_REVIEW_PERMISSION_ALIGNMENT PASS' AS result;
