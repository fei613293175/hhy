-- Development/test rollback for the R06 content permission expansion.
SET search_path TO hhy, public;

DELETE FROM hhy.admin_role_permissions
WHERE permission_id IN (
  SELECT id FROM hhy.admin_permissions
  WHERE code IN (
    'content.read', 'content.manage',
    'content.ban', 'content.recommend', 'content.official',
    'content.dict.read', 'content.dict.write'
  )
);
DELETE FROM hhy.admin_permissions
WHERE code IN (
  'content.ban', 'content.recommend', 'content.official',
  'content.dict.read', 'content.dict.write'
);
