SET search_path TO hhy, public;

DELETE FROM hhy.admin_role_permissions role_permission
USING hhy.admin_permissions permission
WHERE role_permission.permission_id=permission.id
  AND permission.code IN ('user.restrict','user.freeze','user.security');

DELETE FROM hhy.admin_permissions
WHERE code IN ('user.restrict','user.freeze','user.security');

DROP TABLE IF EXISTS hhy.user_restrictions;

