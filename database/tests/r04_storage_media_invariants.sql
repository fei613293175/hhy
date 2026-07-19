BEGIN;
SET LOCAL search_path TO hhy, public;

DO $$
DECLARE
  config_id bigint;
  user_id bigint;
  public_binding_id bigint;
  private_binding_id bigint;
  target_binding_id bigint;
  media_id bigint;
BEGIN
  INSERT INTO provider_config_versions(
    provider_code,version_no,status,created_by,environment,values_json,secret_refs_json)
  VALUES ('storage','r04-invariant','ACTIVE','1','STAGING',jsonb_build_object(
    'storage.r2.endpoint','https://r2.example.test',
    'storage.r2.public_domain','assets.example.test',
    'storage.aliyun_oss.endpoint','https://oss.example.test'), '{}')
  RETURNING id INTO config_id;

  INSERT INTO users(phone,status,invite_code)
  VALUES ('13900000404','ACTIVE','R04DBTEST') RETURNING id INTO user_id;

  INSERT INTO storage_scope_bindings(
    scope_code,provider_code,config_version_id,bucket,public_domain,status)
  VALUES ('public_media','CLOUDFLARE_R2',config_id,'r04-public','assets.example.test','ACTIVE')
  RETURNING id INTO public_binding_id;
  INSERT INTO storage_scope_bindings(
    scope_code,provider_code,config_version_id,bucket,status)
  VALUES ('private_chat','ALIYUN_OSS',config_id,'r04-private-chat','ACTIVE')
  RETURNING id INTO private_binding_id;
  INSERT INTO storage_scope_bindings(
    scope_code,provider_code,config_version_id,bucket,public_domain,status)
  VALUES ('public_media','ALIYUN_OSS',config_id,'r04-public-target','target.example.test','VERIFIED')
  RETURNING id INTO target_binding_id;

  BEGIN
    INSERT INTO storage_scope_bindings(
      scope_code,provider_code,config_version_id,bucket,status)
    VALUES ('public_media','ALIYUN_OSS',config_id,'r04-second-public','ACTIVE');
    RAISE EXCEPTION 'R04_DUPLICATE_ACTIVE_SCOPE_WAS_ACCEPTED';
  EXCEPTION WHEN unique_violation THEN NULL;
  END;

  BEGIN
    INSERT INTO storage_scope_bindings(
      scope_code,provider_code,config_version_id,bucket,status)
    VALUES ('backup','CLOUDFLARE_R2',config_id,'r04-public','ACTIVE');
    RAISE EXCEPTION 'R04_SHARED_ACTIVE_BUCKET_WAS_ACCEPTED';
  EXCEPTION WHEN unique_violation THEN NULL;
  END;

  BEGIN
    INSERT INTO storage_scope_bindings(
      scope_code,provider_code,config_version_id,bucket,public_domain,status)
    VALUES ('private_kyc','CLOUDFLARE_R2',config_id,'r04-private-kyc','private.example.test','VERIFIED');
    RAISE EXCEPTION 'R04_PRIVATE_PUBLIC_DOMAIN_WAS_ACCEPTED';
  EXCEPTION WHEN check_violation THEN NULL;
  END;

  INSERT INTO media_objects(owner_id,bucket,object_key,mime,size,sha256,visibility)
  VALUES (user_id,'r04-public','public_media/test/image.png','image/png',32,repeat('a',64),'PUBLIC')
  RETURNING id INTO media_id;

  BEGIN
    INSERT INTO media_objects(owner_id,bucket,object_key,size,sha256,visibility)
    VALUES (user_id,'r04-public','public_media/test/bad.png',-1,repeat('a',64),'PUBLIC');
    RAISE EXCEPTION 'R04_NEGATIVE_MEDIA_SIZE_WAS_ACCEPTED';
  EXCEPTION WHEN check_violation THEN NULL;
  END;

  BEGIN
    INSERT INTO media_objects(owner_id,bucket,object_key,size,sha256,visibility)
    VALUES (user_id,'r04-public','public_media/test/bad-sha.png',1,'not-sha256','PUBLIC');
    RAISE EXCEPTION 'R04_BAD_MEDIA_SHA_WAS_ACCEPTED';
  EXCEPTION WHEN check_violation THEN NULL;
  END;

  BEGIN
    INSERT INTO upload_sessions(user_id,scene,status,expires_at)
    VALUES (user_id,'PUBLIC_MEDIA','CREATED',now()-interval '1 second');
    RAISE EXCEPTION 'R04_EXPIRED_UPLOAD_SESSION_WAS_ACCEPTED';
  EXCEPTION WHEN check_violation THEN NULL;
  END;

  BEGIN
    INSERT INTO media_access_tokens(media_id,subject,expires_at,used_at)
    VALUES (media_id,'r04-test',now()+interval '1 minute',now()+interval '2 minutes');
    RAISE EXCEPTION 'R04_TOKEN_USE_AFTER_EXPIRY_WAS_ACCEPTED';
  EXCEPTION WHEN check_violation THEN NULL;
  END;

  INSERT INTO storage_migration_jobs(
    source_binding_id,target_binding_id,scope_code,cursor,total,success,failed,status)
  VALUES (public_binding_id,target_binding_id,'public_media',NULL,0,0,0,'PENDING');
  BEGIN
    INSERT INTO storage_migration_jobs(
      source_binding_id,target_binding_id,scope_code,total,success,failed,status)
    VALUES (public_binding_id,target_binding_id,'public_media',1,1,1,'RUNNING');
    RAISE EXCEPTION 'R04_INVALID_MIGRATION_COUNTS_WERE_ACCEPTED';
  EXCEPTION WHEN check_violation THEN NULL;
  END;
END $$;

ROLLBACK;
