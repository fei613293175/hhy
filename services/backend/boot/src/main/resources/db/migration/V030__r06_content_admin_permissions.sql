-- R06 administrator permissions required by the frozen content operations.
SET search_path TO hhy, public;

INSERT INTO hhy.admin_permissions(code, resource, action) VALUES
  ('content.ban', 'content', 'ban'),
  ('content.recommend', 'content', 'recommend'),
  ('content.official', 'content', 'official'),
  ('content.dict.read', 'content', 'dict.read'),
  ('content.dict.write', 'content', 'dict.write')
ON CONFLICT (code) DO NOTHING;

-- A fresh baseline has the content permission definitions but no role grants.
-- SUPER_ADMIN is the only frozen role allowed to receive the complete CMS set.
INSERT INTO hhy.admin_role_permissions(role_id, permission_id)
SELECT role.id, permission.id
FROM hhy.admin_roles role
CROSS JOIN hhy.admin_permissions permission
WHERE role.code='SUPER_ADMIN' AND role.status='ACTIVE'
  AND permission.code IN (
    'content.read', 'content.manage', 'content.ban', 'content.recommend',
    'content.official', 'content.dict.read', 'content.dict.write')
ON CONFLICT (role_id, permission_id) DO NOTHING;

INSERT INTO hhy.admin_role_permissions(role_id, permission_id)
SELECT DISTINCT existing.role_id, added.id
FROM hhy.admin_role_permissions existing
JOIN hhy.admin_permissions current_permission ON current_permission.id = existing.permission_id
JOIN hhy.admin_permissions added ON added.code IN (
  'content.ban', 'content.recommend', 'content.official', 'content.dict.write'
)
WHERE current_permission.code = 'content.manage'
ON CONFLICT (role_id, permission_id) DO NOTHING;

INSERT INTO hhy.admin_role_permissions(role_id, permission_id)
SELECT DISTINCT existing.role_id, added.id
FROM hhy.admin_role_permissions existing
JOIN hhy.admin_permissions current_permission ON current_permission.id = existing.permission_id
JOIN hhy.admin_permissions added ON added.code = 'content.dict.read'
WHERE current_permission.code = 'content.read'
ON CONFLICT (role_id, permission_id) DO NOTHING;
