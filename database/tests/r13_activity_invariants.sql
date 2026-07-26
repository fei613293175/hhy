SET search_path TO hhy, public;
BEGIN;

DO $$
DECLARE
  actor_id bigint;
  content_id bigint;
  favorite_id bigint;
  view_id bigint;
  access_id bigint;
BEGIN
  INSERT INTO hhy.users(phone,status,invite_code)
  VALUES ('13900004213','ACTIVE','R13DBTEST') RETURNING id INTO actor_id;
  INSERT INTO hhy.content_posts(owner_id,type,title,status)
  VALUES (actor_id,'PROJECT','R13 activity invariant','DRAFT') RETURNING id INTO content_id;
  INSERT INTO hhy.project_details(content_id,cooperation)
  VALUES (content_id,'联合验证');

  INSERT INTO hhy.content_favorites(user_id,content_id)
  VALUES (actor_id,content_id) RETURNING id INTO favorite_id;
  BEGIN
    INSERT INTO hhy.content_favorites(user_id,content_id)
    VALUES (actor_id,content_id);
    RAISE EXCEPTION 'R13_DUPLICATE_FAVORITE_WAS_ACCEPTED';
  EXCEPTION WHEN unique_violation THEN NULL;
  END;
  BEGIN
    UPDATE hhy.content_favorites SET updated_at=clock_timestamp() WHERE id=favorite_id;
    RAISE EXCEPTION 'R13_FAVORITE_UPDATE_WAS_ACCEPTED';
  EXCEPTION WHEN object_not_in_prerequisite_state THEN NULL;
  END;
  DELETE FROM hhy.content_favorites WHERE id=favorite_id;

  BEGIN
    INSERT INTO hhy.content_view_logs(user_id,content_id,traffic_type,duration,source)
    VALUES (actor_id,content_id,'UNKNOWN',0,NULL);
    RAISE EXCEPTION 'R13_UNKNOWN_TRAFFIC_TYPE_WAS_ACCEPTED';
  EXCEPTION WHEN check_violation THEN NULL;
  END;
  BEGIN
    INSERT INTO hhy.content_view_logs(user_id,content_id,traffic_type,duration,source)
    VALUES (actor_id,content_id,'ORGANIC_TRAFFIC',-1,'detail');
    RAISE EXCEPTION 'R13_NEGATIVE_DURATION_WAS_ACCEPTED';
  EXCEPTION WHEN check_violation THEN NULL;
  END;
  BEGIN
    INSERT INTO hhy.content_view_logs(user_id,content_id,traffic_type,duration,source)
    VALUES (actor_id,content_id,'SHARE',0,'SMS');
    RAISE EXCEPTION 'R13_UNKNOWN_SHARE_CHANNEL_WAS_ACCEPTED';
  EXCEPTION WHEN check_violation THEN NULL;
  END;
  INSERT INTO hhy.content_view_logs(user_id,content_id,traffic_type,duration,source)
  VALUES (actor_id,content_id,'SHARE',0,'COPY_LINK') RETURNING id INTO view_id;
  BEGIN
    UPDATE hhy.content_view_logs SET source='OTHER' WHERE id=view_id;
    RAISE EXCEPTION 'R13_VIEW_EVENT_UPDATE_WAS_ACCEPTED';
  EXCEPTION WHEN object_not_in_prerequisite_state THEN NULL;
  END;
  BEGIN
    DELETE FROM hhy.content_view_logs WHERE id=view_id;
    RAISE EXCEPTION 'R13_VIEW_EVENT_DELETE_WAS_ACCEPTED';
  EXCEPTION WHEN object_not_in_prerequisite_state THEN NULL;
  END;
  INSERT INTO hhy.content_view_logs(user_id,content_id,traffic_type,duration,source)
  VALUES (actor_id,content_id,'ORGANIC_TRAFFIC',12,'detail');

  BEGIN
    INSERT INTO hhy.content_contact_access_logs(user_id,content_id,channel,action)
    VALUES (actor_id,content_id,'SMS','VIEW');
    RAISE EXCEPTION 'R13_UNKNOWN_CONTACT_CHANNEL_WAS_ACCEPTED';
  EXCEPTION WHEN check_violation THEN NULL;
  END;
  BEGIN
    INSERT INTO hhy.content_contact_access_logs(user_id,content_id,channel,action)
    VALUES (actor_id,content_id,'PHONE','EXPORT');
    RAISE EXCEPTION 'R13_UNKNOWN_CONTACT_ACTION_WAS_ACCEPTED';
  EXCEPTION WHEN check_violation THEN NULL;
  END;
  INSERT INTO hhy.content_contact_access_logs(user_id,content_id,channel,action)
  VALUES (actor_id,content_id,'PHONE','COPY') RETURNING id INTO access_id;
  BEGIN
    DELETE FROM hhy.content_contact_access_logs WHERE id=access_id;
    RAISE EXCEPTION 'R13_CONTACT_AUDIT_DELETE_WAS_ACCEPTED';
  EXCEPTION WHEN object_not_in_prerequisite_state THEN NULL;
  END;

  BEGIN
    INSERT INTO hhy.content_reports(reporter_id,content_id,type,status)
    VALUES (actor_id+999999,content_id,'INVALID_CONTACT','PENDING');
    RAISE EXCEPTION 'R13_ORPHAN_REPORTER_WAS_ACCEPTED';
  EXCEPTION WHEN foreign_key_violation THEN NULL;
  END;
  BEGIN
    INSERT INTO hhy.content_reports(reporter_id,content_id,type,status)
    VALUES (actor_id,content_id,'   ','PENDING');
    RAISE EXCEPTION 'R13_BLANK_REPORT_TYPE_WAS_ACCEPTED';
  EXCEPTION WHEN check_violation THEN NULL;
  END;
  INSERT INTO hhy.content_reports(reporter_id,content_id,type,description,status)
  VALUES (actor_id,content_id,'INVALID_CONTACT','二维码已失效','PENDING');

  INSERT INTO hhy.idempotency_records(scope,idem_key,request_hash)
  VALUES ('r13.invalid-feedback:'||actor_id||':'||content_id,'0123456789abcdef',repeat('d',64));
  BEGIN
    INSERT INTO hhy.idempotency_records(scope,idem_key,request_hash)
    VALUES ('r13.invalid-feedback:'||actor_id||':'||content_id,'0123456789abcdef',repeat('d',64));
    RAISE EXCEPTION 'R13_DUPLICATE_IDEMPOTENCY_KEY_WAS_ACCEPTED';
  EXCEPTION WHEN unique_violation THEN NULL;
  END;
END;
$$;

ROLLBACK;
