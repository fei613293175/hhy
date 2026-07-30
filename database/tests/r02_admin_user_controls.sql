SET search_path TO hhy, public;
BEGIN;

DO $$
DECLARE
  v_user_id bigint;
  v_admin_id bigint;
  v_permission_count integer;
  v_grant_count integer;
BEGIN
  SELECT count(*) INTO v_permission_count
  FROM admin_permissions
  WHERE code IN ('user.restrict','user.freeze','user.security');
  IF v_permission_count <> 3 THEN
    RAISE EXCEPTION 'R02 user-control permissions are incomplete';
  END IF;

  SELECT count(*) INTO v_grant_count
  FROM admin_role_permissions grant_row
  JOIN admin_roles role_row ON role_row.id=grant_row.role_id
  JOIN admin_permissions permission_row ON permission_row.id=grant_row.permission_id
  WHERE role_row.code='SUPER_ADMIN'
    AND permission_row.code IN ('user.restrict','user.freeze','user.security');
  IF v_grant_count <> 3 THEN
    RAISE EXCEPTION 'R02 SUPER_ADMIN user-control grants are incomplete';
  END IF;

  INSERT INTO admin_users(username,password_hash,status)
  VALUES ('r02-user-control-smoke','$2b$12$R02SmokeOnlyHashPlaceholder00000000000000000000000000','ACTIVE')
  RETURNING id INTO v_admin_id;
  INSERT INTO users(phone,status) VALUES ('13900009991','ACTIVE') RETURNING id INTO v_user_id;
  INSERT INTO user_restrictions(user_id,restriction_type,reason,status,created_by)
  VALUES (v_user_id,'LOGIN','R02 migration smoke','ACTIVE',v_admin_id);

  BEGIN
    INSERT INTO user_restrictions(user_id,restriction_type,reason,status,created_by)
    VALUES (v_user_id,'LOGIN','duplicate active restriction','ACTIVE',v_admin_id);
    RAISE EXCEPTION 'R02 duplicate active restriction was accepted';
  EXCEPTION WHEN unique_violation THEN
    NULL;
  END;

  UPDATE user_restrictions
  SET status='REMOVED',removed_at=clock_timestamp(),version=version+1
  WHERE user_id=v_user_id AND restriction_type='LOGIN' AND status='ACTIVE';
  INSERT INTO user_restrictions(user_id,restriction_type,reason,status,created_by)
  VALUES (v_user_id,'LOGIN','replacement after removal','ACTIVE',v_admin_id);
END $$;

ROLLBACK;
SELECT 'R02_ADMIN_USER_CONTROLS_OK' AS result;
