SET search_path TO hhy, public;
BEGIN;
SET CONSTRAINTS ALL DEFERRED;

DO $$
DECLARE
  owner_id bigint;
  admin_id bigint;
  media_id bigint;
  target_content_id bigint;
  snapshot_id bigint;
  initial_log_id bigint;
  review_id bigint;
BEGIN
  INSERT INTO hhy.users(phone,status,invite_code)
  VALUES ('13900001201','ACTIVE','R12DBTEST') RETURNING id INTO owner_id;
  INSERT INTO hhy.admin_users(username,password_hash,status)
  VALUES ('r12-reviewer',repeat('a',60),'ACTIVE') RETURNING id INTO admin_id;
  INSERT INTO hhy.media_objects(owner_id,bucket,object_key,mime,size,sha256,visibility)
  VALUES (owner_id,'r12-test','content/cover.png','image/png',512,repeat('b',64),'PUBLIC')
  RETURNING id INTO media_id;

  INSERT INTO hhy.content_posts(owner_id,type,title,summary,status,version)
  VALUES (owner_id,'PROJECT','R12 publish invariant','database workflow','DRAFT',0)
  RETURNING id INTO target_content_id;
  INSERT INTO hhy.project_details(content_id,cooperation,conditions,region)
  VALUES (target_content_id,'联合运营','资料真实','CN-44');
  INSERT INTO hhy.content_versions(content_id,version_no,snapshot_json,created_by)
  VALUES (target_content_id,'0','{"title":"R12 publish invariant"}'::jsonb,'user:r12')
  RETURNING id INTO snapshot_id;
  INSERT INTO hhy.content_contacts(content_id,channel,value_cipher,display_mask,sort_order)
  VALUES (target_content_id,'WECHAT','hhy-contact-v1:r12','微信***',0);
  INSERT INTO hhy.content_media(content_id,media_id,media_type,sort_order)
  VALUES (target_content_id,media_id,'image/png',0);
  SET CONSTRAINTS ALL IMMEDIATE;
  SET CONSTRAINTS ALL DEFERRED;

  SELECT id INTO initial_log_id FROM hhy.content_status_logs
  WHERE content_id=target_content_id AND from_status IS NULL AND to_status='DRAFT' AND transition_version=0;
  IF initial_log_id IS NULL THEN RAISE EXCEPTION 'R12_INITIAL_HISTORY_MISSING'; END IF;
  IF NOT EXISTS (
    SELECT 1 FROM hhy.outbox_events
    WHERE aggregate_type='CONTENT' AND aggregate_id=target_content_id::text
      AND payload->>'contentVersion'='0'
  ) THEN RAISE EXCEPTION 'R12_CREATE_OUTBOX_MISSING'; END IF;

  BEGIN
    UPDATE hhy.content_posts SET summary='unversioned write' WHERE id=target_content_id;
    RAISE EXCEPTION 'R12_UNVERSIONED_WRITE_ACCEPTED';
  EXCEPTION WHEN serialization_failure THEN NULL;
  END;
  BEGIN
    UPDATE hhy.content_posts SET status='ONLINE',version=version+1 WHERE id=target_content_id;
    RAISE EXCEPTION 'R12_ILLEGAL_DRAFT_ONLINE_ACCEPTED';
  EXCEPTION WHEN check_violation THEN NULL;
  END;
  BEGIN
    INSERT INTO hhy.content_contacts(content_id,channel,value_cipher,display_mask,sort_order)
    VALUES (target_content_id,'EMAIL','hhy-contact-v1:r12-email','r***@example.com',0);
    RAISE EXCEPTION 'R12_DUPLICATE_CONTACT_ORDER_ACCEPTED';
  EXCEPTION WHEN unique_violation THEN NULL;
  END;
  BEGIN
    INSERT INTO hhy.content_contacts(content_id,channel,value_cipher,display_mask,sort_order)
    VALUES (target_content_id,'WECHAT','hhy-contact-v1:r12-other','微信另***',1);
    RAISE EXCEPTION 'R12_DUPLICATE_CONTACT_CHANNEL_ACCEPTED';
  EXCEPTION WHEN unique_violation THEN NULL;
  END;
  BEGIN
    INSERT INTO hhy.content_media(content_id,media_id,media_type,sort_order)
    VALUES (target_content_id,media_id,'image/png',1);
    RAISE EXCEPTION 'R12_DUPLICATE_MEDIA_ACCEPTED';
  EXCEPTION WHEN unique_violation THEN NULL;
  END;

  -- Submit version 1. The immutable snapshot and transition history may be
  -- written after the parent update, but must exist before commit.
  UPDATE hhy.content_posts
  SET status='PENDING_REVIEW',version=version+1
  WHERE id=target_content_id;
  INSERT INTO hhy.content_versions(content_id,version_no,snapshot_json,created_by)
  VALUES (target_content_id,'1','{"title":"R12 publish invariant","submitted":true}'::jsonb,'user:r12')
  RETURNING id INTO snapshot_id;
  INSERT INTO hhy.content_status_logs(
    content_id,from_status,to_status,operator,transition_version
  ) VALUES (target_content_id,'DRAFT','PENDING_REVIEW','user:r12',1);
  SET CONSTRAINTS ALL IMMEDIATE;
  SET CONSTRAINTS ALL DEFERRED;

  -- Claim and assignment are distinct immutable commands bound to one submit snapshot.
  UPDATE hhy.content_posts SET status='REVIEWING',version=version+1 WHERE id=target_content_id;
  INSERT INTO hhy.content_review_records(
    content_id,version_no,decision,admin_id,snapshot_version_id,command_id
  ) VALUES (target_content_id,'1','ASSIGN',admin_id,snapshot_id,'r12-assign-1');
  INSERT INTO hhy.content_review_records(
    content_id,version_no,decision,admin_id,snapshot_version_id,command_id
  ) VALUES (target_content_id,'1','CLAIM',admin_id,snapshot_id,'r12-claim-1')
  RETURNING id INTO review_id;
  INSERT INTO hhy.content_status_logs(
    content_id,from_status,to_status,operator,transition_version
  ) VALUES (target_content_id,'PENDING_REVIEW','REVIEWING','admin:r12',2);
  SET CONSTRAINTS ALL IMMEDIATE;
  SET CONSTRAINTS ALL DEFERRED;

  BEGIN
    INSERT INTO hhy.content_review_records(
      content_id,version_no,decision,admin_id,snapshot_version_id,command_id
    ) VALUES (target_content_id,'1','ASSIGN',admin_id,snapshot_id,'r12-assign-1');
    RAISE EXCEPTION 'R12_DUPLICATE_REVIEW_COMMAND_ACCEPTED';
  EXCEPTION WHEN unique_violation THEN NULL;
  END;

  UPDATE hhy.content_posts SET status='APPROVED',version=version+1 WHERE id=target_content_id;
  INSERT INTO hhy.content_review_records(
    content_id,version_no,decision,admin_id,snapshot_version_id,command_id
  ) VALUES (target_content_id,'1','APPROVE',admin_id,snapshot_id,'r12-approve-1');
  INSERT INTO hhy.content_status_logs(
    content_id,from_status,to_status,operator,transition_version
  ) VALUES (target_content_id,'REVIEWING','APPROVED','admin:r12',3);
  SET CONSTRAINTS ALL IMMEDIATE;
  SET CONSTRAINTS ALL DEFERRED;

  UPDATE hhy.content_posts SET status='ONLINE',version=version+1 WHERE id=target_content_id;
  INSERT INTO hhy.content_status_logs(
    content_id,from_status,to_status,operator,transition_version
  ) VALUES (target_content_id,'APPROVED','ONLINE','user:r12',4);
  SET CONSTRAINTS ALL IMMEDIATE;
  SET CONSTRAINTS ALL DEFERRED;

  -- Missing history is detected by the deferred commit contract.
  BEGIN
    UPDATE hhy.content_posts SET status='OFFLINE_BY_OWNER',version=version+1 WHERE id=target_content_id;
    SET CONSTRAINTS ALL IMMEDIATE;
    RAISE EXCEPTION 'R12_MISSING_STATUS_HISTORY_ACCEPTED';
  EXCEPTION WHEN check_violation THEN NULL;
  END;
  SET CONSTRAINTS ALL DEFERRED;

  -- A caller-supplied mismatching Outbox version is rejected before insertion.
  BEGIN
    INSERT INTO hhy.outbox_events(
      aggregate_id,aggregate_type,event_id,event_type,event_version,headers,payload
    ) VALUES (
      target_content_id::text,'CONTENT',gen_random_uuid()::text,'content.invalid.v1',1,
      '{}'::jsonb,jsonb_build_object('contentVersion',999999)
    );
    RAISE EXCEPTION 'R12_MISMATCHED_OUTBOX_VERSION_ACCEPTED';
  EXCEPTION WHEN check_violation THEN NULL;
  END;

  -- Every protected child remains present even when deletion is attempted.
  BEGIN DELETE FROM hhy.content_contacts WHERE content_id=target_content_id;
    RAISE EXCEPTION 'R12_CONTACT_DELETE_ACCEPTED'; EXCEPTION WHEN OTHERS THEN
      IF SQLERRM NOT LIKE 'R12_CONTENT_PHYSICAL_DELETE_FORBIDDEN%' THEN RAISE; END IF;
  END;
  BEGIN DELETE FROM hhy.content_media WHERE content_id=target_content_id;
    RAISE EXCEPTION 'R12_MEDIA_DELETE_ACCEPTED'; EXCEPTION WHEN OTHERS THEN
      IF SQLERRM NOT LIKE 'R12_CONTENT_PHYSICAL_DELETE_FORBIDDEN%' THEN RAISE; END IF;
  END;
  BEGIN DELETE FROM hhy.project_details WHERE content_id=target_content_id;
    RAISE EXCEPTION 'R12_DETAIL_DELETE_ACCEPTED'; EXCEPTION WHEN OTHERS THEN
      IF SQLERRM NOT LIKE 'R12_CONTENT_PHYSICAL_DELETE_FORBIDDEN%' THEN RAISE; END IF;
  END;
  BEGIN DELETE FROM hhy.content_versions WHERE id=snapshot_id;
    RAISE EXCEPTION 'R12_VERSION_DELETE_ACCEPTED'; EXCEPTION WHEN OTHERS THEN
      IF SQLERRM NOT LIKE 'IMMUTABLE_TABLE:%' THEN RAISE; END IF;
  END;
  BEGIN DELETE FROM hhy.content_review_records WHERE id=review_id;
    RAISE EXCEPTION 'R12_REVIEW_DELETE_ACCEPTED'; EXCEPTION WHEN OTHERS THEN
      IF SQLERRM NOT LIKE 'IMMUTABLE_TABLE:%' THEN RAISE; END IF;
  END;
  BEGIN DELETE FROM hhy.content_status_logs WHERE id=initial_log_id;
    RAISE EXCEPTION 'R12_HISTORY_DELETE_ACCEPTED'; EXCEPTION WHEN OTHERS THEN
      IF SQLERRM NOT LIKE 'IMMUTABLE_TABLE:%' THEN RAISE; END IF;
  END;
  BEGIN DELETE FROM hhy.media_objects WHERE id=media_id;
    RAISE EXCEPTION 'R12_MEDIA_OBJECT_CASCADE_DELETE_ACCEPTED'; EXCEPTION WHEN OTHERS THEN
      IF SQLERRM NOT LIKE 'R12_CONTENT_PHYSICAL_DELETE_FORBIDDEN%' THEN RAISE; END IF;
  END;

  UPDATE hhy.content_posts SET status='BANNED',version=version+1 WHERE id=target_content_id;
  INSERT INTO hhy.content_status_logs(
    content_id,from_status,to_status,reason,operator,transition_version
  ) VALUES (target_content_id,'ONLINE','BANNED','高风险内容','admin:r12',5);
  SET CONSTRAINTS ALL IMMEDIATE;
  SET CONSTRAINTS ALL DEFERRED;
  BEGIN
    UPDATE hhy.content_posts SET status='DELETED',version=version+1 WHERE id=target_content_id;
    RAISE EXCEPTION 'R12_TERMINAL_STATE_ESCAPE_ACCEPTED';
  EXCEPTION WHEN check_violation THEN NULL;
  END;

  IF (SELECT count(*) FROM hhy.content_status_logs WHERE content_id=target_content_id)<>6 THEN
    RAISE EXCEPTION 'R12_STATUS_HISTORY_COUNT_UNEXPECTED';
  END IF;
  IF (SELECT count(*) FROM hhy.content_review_records WHERE content_id=target_content_id)<>3 THEN
    RAISE EXCEPTION 'R12_REVIEW_EVENT_COUNT_UNEXPECTED';
  END IF;
END;
$$;

ROLLBACK;
