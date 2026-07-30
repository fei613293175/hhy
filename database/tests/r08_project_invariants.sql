SET search_path TO hhy, public;
BEGIN;

DO $$
DECLARE
  actor_id bigint;
  content_id bigint;
  detail_id bigint;
BEGIN
  INSERT INTO hhy.users(phone, status, invite_code)
  VALUES ('13900000801', 'ACTIVE', 'R08DBTEST') RETURNING id INTO actor_id;

  BEGIN
    INSERT INTO hhy.content_posts(owner_id, type, title, status)
    VALUES (actor_id, 'PROJECT', 'invalid status', 'UNKNOWN');
    RAISE EXCEPTION 'R08_INVALID_CONTENT_STATUS_WAS_ACCEPTED';
  EXCEPTION WHEN check_violation THEN NULL;
  END;

  INSERT INTO hhy.content_posts(owner_id, type, title, status)
  VALUES (actor_id, 'PROJECT', 'R08 project invariant', 'DRAFT') RETURNING id INTO content_id;

  BEGIN
    INSERT INTO hhy.project_details(content_id, cooperation)
    VALUES (content_id, '   ');
    RAISE EXCEPTION 'R08_BLANK_PROJECT_COOPERATION_WAS_ACCEPTED';
  EXCEPTION WHEN check_violation THEN NULL;
  END;
  BEGIN
    INSERT INTO hhy.project_details(content_id, cooperation, website)
    VALUES (content_id, '联合推广', '   ');
    RAISE EXCEPTION 'R08_BLANK_OPTIONAL_PROJECT_FIELD_WAS_ACCEPTED';
  EXCEPTION WHEN check_violation THEN NULL;
  END;

  INSERT INTO hhy.project_details(content_id, cooperation, conditions, region, website)
  VALUES (content_id, '联合推广', '实名用户', 'CN-11', 'https://project.example.invalid')
  RETURNING id INTO detail_id;

  BEGIN
    INSERT INTO hhy.content_stats(content_id, organic_views, favorites)
    VALUES (content_id, '-1', 'not-a-number');
    RAISE EXCEPTION 'R08_NON_NUMERIC_STATS_WERE_ACCEPTED';
  EXCEPTION WHEN check_violation THEN NULL;
  END;
  INSERT INTO hhy.content_stats(content_id, organic_views, redpacket_views, task_views, favorites, chats, contacts)
  VALUES (content_id, '0', '0', '0', '0', '0', '0');

  BEGIN
    INSERT INTO hhy.content_versions(content_id, version_no, snapshot_json, created_by)
    VALUES (content_id, 'v1', '{"description":"invalid"}'::jsonb, 'r08-test');
    RAISE EXCEPTION 'R08_NON_NUMERIC_VERSION_WAS_ACCEPTED';
  EXCEPTION WHEN check_violation THEN NULL;
  END;
  INSERT INTO hhy.content_versions(content_id, version_no, snapshot_json, created_by)
  VALUES (content_id, '0', '{"description":"project","categoryCode":"cooperation"}'::jsonb, 'r08-test');

  BEGIN
    DELETE FROM hhy.project_details WHERE id = detail_id;
    RAISE EXCEPTION 'R08_ACTIVE_PROJECT_DETAIL_DELETE_WAS_ACCEPTED';
  EXCEPTION WHEN object_not_in_prerequisite_state THEN NULL;
  END;

  INSERT INTO hhy.idempotency_records(scope, idem_key, request_hash)
  VALUES ('R08_PROJECT_CREATE:' || actor_id, '0123456789abcdef', repeat('b', 64));
  BEGIN
    INSERT INTO hhy.idempotency_records(scope, idem_key, request_hash)
    VALUES ('R08_PROJECT_CREATE:' || actor_id, '0123456789abcdef', repeat('b', 64));
    RAISE EXCEPTION 'R08_PROJECT_DUPLICATE_IDEMPOTENCY_KEY_WAS_ACCEPTED';
  EXCEPTION WHEN unique_violation THEN NULL;
  END;

  UPDATE hhy.content_posts SET status = 'DELETED' WHERE id = content_id;
  DELETE FROM hhy.project_details WHERE id = detail_id;
END;
$$;

ROLLBACK;
