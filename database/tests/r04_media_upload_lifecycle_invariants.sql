BEGIN;
SET LOCAL search_path TO hhy, public;

DO $$
DECLARE
  config_id bigint;
  user_id bigint;
  binding_id bigint;
  media_id bigint;
BEGIN
  INSERT INTO provider_config_versions(
    provider_code,version_no,status,created_by,environment,values_json,secret_refs_json)
  VALUES ('storage','r04-upload-invariant','ACTIVE','1','STAGING','{}','{}')
  RETURNING id INTO config_id;

  INSERT INTO users(phone,status,invite_code)
  VALUES ('13900000405','ACTIVE','R04UPLOAD') RETURNING id INTO user_id;

  INSERT INTO storage_scope_bindings(
    scope_code,provider_code,config_version_id,bucket,public_domain,status)
  VALUES ('public_media','CLOUDFLARE_R2',config_id,'r04-upload-public','assets-upload.example.test','ACTIVE')
  RETURNING id INTO binding_id;

  INSERT INTO upload_sessions(
    user_id,scene,status,expires_at,purpose,file_name,content_type,size_bytes,sha256,
    storage_scope,storage_binding_id,object_key,provider_upload_id)
  VALUES (
    user_id,'PUBLIC_MEDIA','CREATED',now()+interval '5 minutes','content.cover','cover.png',
    'image/png',32,repeat('a',64),'public_media',binding_id,'public_media/test/cover.png','provider-1');

  BEGIN
    INSERT INTO upload_sessions(
      user_id,scene,status,expires_at,purpose,file_name,content_type,size_bytes,sha256,
      storage_scope,storage_binding_id,object_key)
    VALUES (
      user_id,'PUBLIC_MEDIA','CREATED',now()+interval '5 minutes','content.cover','cover.png',
      'image/png',32,repeat('a',40),'public_media',binding_id,'public_media/test/bad.png');
    RAISE EXCEPTION 'R04_SHORT_SHA_WAS_ACCEPTED';
  EXCEPTION WHEN check_violation THEN NULL;
  END;

  BEGIN
    INSERT INTO upload_sessions(
      user_id,scene,status,expires_at,purpose,file_name,content_type,size_bytes,sha256,
      storage_scope,storage_binding_id,object_key)
    VALUES (
      user_id,'PUBLIC_MEDIA','COMPLETED',now()+interval '5 minutes','content.cover','cover.png',
      'image/png',32,repeat('a',64),'public_media',binding_id,'public_media/test/incomplete.png');
    RAISE EXCEPTION 'R04_INCOMPLETE_COMPLETION_WAS_ACCEPTED';
  EXCEPTION WHEN check_violation THEN NULL;
  END;

  INSERT INTO media_objects(
    owner_id,bucket,object_key,mime,size,sha256,visibility,purpose,storage_scope,
    storage_binding_id,status)
  VALUES (
    user_id,'r04-upload-public','public_media/test/ready.png','image/png',32,repeat('b',64),
    'PUBLIC','content.cover','public_media',binding_id,'READY')
  RETURNING id INTO media_id;

  BEGIN
    UPDATE media_objects SET status='DELETED' WHERE id=media_id;
    RAISE EXCEPTION 'R04_DELETE_WITHOUT_TIMESTAMP_WAS_ACCEPTED';
  EXCEPTION WHEN check_violation THEN NULL;
  END;

  UPDATE media_objects SET status='DELETE_PENDING', version=version+1 WHERE id=media_id;
  UPDATE media_objects SET status='DELETED', deleted_at=now(), version=version+1 WHERE id=media_id;
END $$;

ROLLBACK;
