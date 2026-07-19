BEGIN;
SET LOCAL search_path TO hhy, public;

DO $$
DECLARE
  user_id bigint;
  other_user_id bigint;
  admin_id bigint;
  session_id bigint;
  retry_session_id bigint;
  media_id bigint;
  review_id bigint;
  access_id bigint;
BEGIN
  INSERT INTO users(phone,status,invite_code)
  VALUES ('13900000505','ACTIVE','R05DBTEST') RETURNING id INTO user_id;
  INSERT INTO users(phone,status,invite_code)
  VALUES ('13900000506','ACTIVE','R05DBTEST2') RETURNING id INTO other_user_id;
  INSERT INTO admin_users(username,password_hash,status)
  VALUES ('r05-db-admin',repeat('a',64),'ACTIVE') RETURNING id INTO admin_id;

  INSERT INTO identity_profiles(
    user_id,name_cipher,id_no_cipher,id_hash,status,verified_at)
  VALUES (
    user_id,'cipher-name','cipher-id',repeat('b',64),'VERIFIED',now());

  BEGIN
    INSERT INTO identity_profiles(user_id,name_cipher,status)
    VALUES (other_user_id,'cipher-name-2','VERIFIED');
    RAISE EXCEPTION 'R05_UNVERIFIED_PROFILE_WAS_ACCEPTED';
  EXCEPTION WHEN check_violation THEN NULL;
  END;

  INSERT INTO identity_verification_sessions(
    user_id,state,status,expires_at,idempotency_key,attempt_no,last_event)
  VALUES (
    user_id,'r05-session-1','SESSION_CREATED',now()+interval '5 minutes',
    'r05-session-key-1',1,'SESSION_CREATED')
  RETURNING id INTO session_id;

  BEGIN
    INSERT INTO identity_verification_sessions(
      user_id,state,status,expires_at,idempotency_key)
    VALUES (
      user_id,'r05-session-duplicate-key','SESSION_CREATED',now()+interval '5 minutes',
      'r05-session-key-1');
    RAISE EXCEPTION 'R05_DUPLICATE_SESSION_IDEMPOTENCY_WAS_ACCEPTED';
  EXCEPTION WHEN unique_violation THEN NULL;
  END;

  BEGIN
    INSERT INTO identity_verification_sessions(
      user_id,state,status,expires_at,idempotency_key)
    VALUES (
      user_id,'r05-session-second-active','LIVENESS_PENDING',now()+interval '5 minutes',
      'r05-session-key-2');
    RAISE EXCEPTION 'R05_SECOND_ACTIVE_SESSION_WAS_ACCEPTED';
  EXCEPTION WHEN unique_violation THEN NULL;
  END;

  BEGIN
    INSERT INTO identity_verification_sessions(
      user_id,state,status,expires_at,idempotency_key)
    VALUES (
      other_user_id,'r05-session-bad-terminal','REJECTED',now()+interval '5 minutes',
      'r05-session-key-bad-terminal');
    RAISE EXCEPTION 'R05_TERMINAL_SESSION_WITHOUT_COMPLETION_WAS_ACCEPTED';
  EXCEPTION WHEN check_violation THEN NULL;
  END;

  UPDATE identity_verification_sessions
  SET status='REJECTED',completed_at=now(),last_event='REJECTED',version=version+1
  WHERE id=session_id;

  INSERT INTO identity_verification_sessions(
    user_id,state,status,expires_at,idempotency_key,attempt_no,retry_of_session_id,last_event)
  VALUES (
    user_id,'r05-session-retry','SESSION_CREATED',now()+interval '5 minutes',
    'r05-session-key-retry',2,session_id,'RETRY_CREATED')
  RETURNING id INTO retry_session_id;

  BEGIN
    UPDATE identity_verification_sessions SET retry_of_session_id=id WHERE id=retry_session_id;
    RAISE EXCEPTION 'R05_SELF_RETRY_WAS_ACCEPTED';
  EXCEPTION WHEN check_violation THEN NULL;
  END;

  INSERT INTO identity_provider_requests(
    session_id,request_type,provider_order_no,idempotency_key,request_hash,status,attempt_no)
  VALUES (
    retry_session_id,'LIVENESS_TOKEN','r05-provider-order-1','r05-provider-key-1',
    repeat('c',64),'PROCESSING',1);

  BEGIN
    INSERT INTO identity_provider_requests(
      session_id,request_type,idempotency_key,status)
    VALUES (retry_session_id,'LIVENESS_TOKEN','r05-provider-key-1','CREATED');
    RAISE EXCEPTION 'R05_DUPLICATE_PROVIDER_IDEMPOTENCY_WAS_ACCEPTED';
  EXCEPTION WHEN unique_violation THEN NULL;
  END;

  BEGIN
    INSERT INTO identity_provider_requests(
      session_id,request_type,provider_order_no,idempotency_key,status)
    VALUES (
      retry_session_id,'STATUS_QUERY','r05-provider-order-1','r05-provider-key-2','CREATED');
    RAISE EXCEPTION 'R05_DUPLICATE_PROVIDER_ORDER_WAS_ACCEPTED';
  EXCEPTION WHEN unique_violation THEN NULL;
  END;

  BEGIN
    INSERT INTO identity_provider_requests(
      session_id,request_type,idempotency_key,request_hash,status)
    VALUES (retry_session_id,'STATUS_QUERY','r05-provider-key-bad-hash','short','CREATED');
    RAISE EXCEPTION 'R05_BAD_PROVIDER_HASH_WAS_ACCEPTED';
  EXCEPTION WHEN check_violation THEN NULL;
  END;

  BEGIN
    INSERT INTO identity_provider_requests(
      session_id,request_type,idempotency_key,status,completed_at)
    VALUES (retry_session_id,'STATUS_QUERY','r05-provider-key-bad-completion',NULL,now());
    RAISE EXCEPTION 'R05_PROVIDER_COMPLETION_WITHOUT_STATUS_WAS_ACCEPTED';
  EXCEPTION WHEN check_violation THEN NULL;
  END;

  INSERT INTO media_objects(owner_id,bucket,object_key,mime,size,sha256,visibility)
  VALUES (
    user_id,'r05-private-kyc','private_kyc/r05/liveness.jpg','image/jpeg',32,
    repeat('d',64),'PRIVATE')
  RETURNING id INTO media_id;

  INSERT INTO identity_media(
    session_id,media_object_id,media_type,storage_scope,purpose)
  VALUES (
    retry_session_id,media_id,'LIVENESS_PHOTO','private_kyc','identity.liveness');

  BEGIN
    INSERT INTO identity_media(session_id,media_type,storage_scope,purpose)
    VALUES (retry_session_id,'FACE_COMPARE','private_kyc','identity.face_compare');
    RAISE EXCEPTION 'R05_MEDIA_WITHOUT_OBJECT_WAS_ACCEPTED';
  EXCEPTION WHEN check_violation THEN NULL;
  END;

  BEGIN
    INSERT INTO identity_media(
      session_id,media_object_id,media_type,storage_scope,purpose)
    VALUES (
      retry_session_id,media_id,'FACE_COMPARE','public_media','identity.face_compare');
    RAISE EXCEPTION 'R05_PUBLIC_IDENTITY_MEDIA_WAS_ACCEPTED';
  EXCEPTION WHEN check_violation THEN NULL;
  END;

  INSERT INTO identity_review_records(
    session_id,decision,reason,admin_id,from_status,to_status,event,idempotency_key,expected_version)
  VALUES (
    retry_session_id,'APPROVE','人工复核通过',admin_id,'MANUAL_REVIEW','VERIFIED',
    'REVIEW_APPROVED','r05-review-key-1',0)
  RETURNING id INTO review_id;

  BEGIN
    UPDATE identity_review_records SET reason='不得修改' WHERE id=review_id;
    RAISE EXCEPTION 'R05_REVIEW_MUTATION_WAS_ACCEPTED';
  EXCEPTION WHEN object_not_in_prerequisite_state THEN NULL;
  END;

  BEGIN
    INSERT INTO identity_review_records(
      session_id,decision,reason,admin_id,from_status,to_status,event,idempotency_key,expected_version)
    VALUES (
      retry_session_id,'REJECT','重复请求',admin_id,'MANUAL_REVIEW','REJECTED',
      'REVIEW_REJECTED','r05-review-key-1',0);
    RAISE EXCEPTION 'R05_DUPLICATE_REVIEW_IDEMPOTENCY_WAS_ACCEPTED';
  EXCEPTION WHEN unique_violation THEN NULL;
  END;

  INSERT INTO sensitive_data_access_logs(
    admin_id,user_id,resource,reason,ip,operation,request_id,media_object_id)
  VALUES (
    admin_id,user_id,'identity_media','人工复核查看','127.0.0.1','VIEW_MEDIA',
    'r05-access-request-1',media_id)
  RETURNING id INTO access_id;

  BEGIN
    UPDATE sensitive_data_access_logs SET reason='不得修改' WHERE id=access_id;
    RAISE EXCEPTION 'R05_ACCESS_AUDIT_MUTATION_WAS_ACCEPTED';
  EXCEPTION WHEN object_not_in_prerequisite_state THEN NULL;
  END;

  BEGIN
    INSERT INTO sensitive_data_access_logs(
      admin_id,user_id,resource,reason,operation,request_id)
    VALUES (admin_id,user_id,'identity_profile','测试','VIEW_PROFILE','');
    RAISE EXCEPTION 'R05_BLANK_ACCESS_REQUEST_WAS_ACCEPTED';
  EXCEPTION WHEN check_violation THEN NULL;
  END;
END $$;

ROLLBACK;
