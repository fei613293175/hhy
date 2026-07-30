-- Development-only rollback for the R01 administrator self-service RBAC seed.
SET search_path TO hhy, public;

DELETE FROM hhy.admin_role_permissions AS role_permission
USING hhy.admin_roles AS role, hhy.admin_permissions AS permission
WHERE role_permission.role_id = role.id
  AND role_permission.permission_id = permission.id
  AND role.code = 'SUPER_ADMIN'
  AND permission.code IN ('admin.self.read', 'admin.self.security');

-- Preserve a permission if a development database assigned it to another role.
-- Removing such an assignment implicitly would be more destructive than leaving
-- the contract permission available for a subsequent V013 replay.
DELETE FROM hhy.admin_permissions AS permission
WHERE permission.code IN ('admin.self.read', 'admin.self.security')
  AND NOT EXISTS (
    SELECT 1
    FROM hhy.admin_role_permissions AS role_permission
    WHERE role_permission.permission_id = permission.id
  );
