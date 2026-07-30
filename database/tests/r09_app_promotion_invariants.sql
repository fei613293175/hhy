SET search_path TO hhy, public;
BEGIN;

DO $$
DECLARE
  actor_id bigint;
  app_content_id bigint;
  detail_id bigint;
  image_id bigint;
  apk_id bigint;
BEGIN
  INSERT INTO hhy.users(phone, status, invite_code)
  VALUES ('13900000901', 'ACTIVE', 'R09DBTEST') RETURNING id INTO actor_id;

  BEGIN
    INSERT INTO hhy.content_posts(owner_id, type, title, status)
    VALUES (actor_id, 'APP', 'missing App detail', 'DRAFT');
    SET CONSTRAINTS trg_r09_app_detail_required IMMEDIATE;
    RAISE EXCEPTION 'R09_MISSING_APP_DETAIL_WAS_ACCEPTED';
  EXCEPTION WHEN check_violation THEN NULL;
  END;

  INSERT INTO hhy.content_posts(owner_id, type, title, status)
  VALUES (actor_id, 'APP', 'R09 App invariant', 'DRAFT') RETURNING id INTO app_content_id;

  BEGIN
    INSERT INTO hhy.app_details(content_id, app_name) VALUES (app_content_id, '   ');
    RAISE EXCEPTION 'R09_BLANK_APP_NAME_WAS_ACCEPTED';
  EXCEPTION WHEN check_violation THEN NULL;
  END;
  BEGIN
    INSERT INTO hhy.app_details(content_id, app_name, download_url)
    VALUES (app_content_id, '合伙云测试App', '   ');
    RAISE EXCEPTION 'R09_BLANK_OPTIONAL_APP_FIELD_WAS_ACCEPTED';
  EXCEPTION WHEN check_violation THEN NULL;
  END;

  INSERT INTO hhy.app_details(content_id, app_name, platform, version_text, download_url, website)
  VALUES (
    app_content_id, '合伙云测试App', 'ANDROID', '1.0.0',
    'https://download.example.invalid/app', 'https://app.example.invalid'
  ) RETURNING id INTO detail_id;
  SET CONSTRAINTS trg_r09_app_detail_required IMMEDIATE;

  INSERT INTO hhy.media_objects(owner_id, bucket, object_key, mime, size, sha256, visibility)
  VALUES (actor_id, 'r09-test', 'apps/screenshot.png', 'image/png', 128, repeat('a', 64), 'PUBLIC')
  RETURNING id INTO image_id;
  INSERT INTO hhy.media_objects(owner_id, bucket, object_key, mime, size, sha256, visibility)
  VALUES (
    actor_id, 'r09-test', 'apps/third-party.apk',
    'application/vnd.android.package-archive', 256, repeat('b', 64), 'PRIVATE'
  ) RETURNING id INTO apk_id;

  INSERT INTO hhy.content_media(content_id, media_id, media_type, sort_order)
  VALUES (app_content_id, image_id, 'SCREENSHOT', 0);
  BEGIN
    INSERT INTO hhy.content_media(content_id, media_id, media_type, sort_order)
    VALUES (app_content_id, apk_id, 'FILE', 1);
    RAISE EXCEPTION 'R09_APK_MIME_WAS_ACCEPTED';
  EXCEPTION WHEN check_violation THEN NULL;
  END;
  BEGIN
    INSERT INTO hhy.content_media(content_id, media_id, media_type, sort_order)
    VALUES (app_content_id, image_id, 'APK', 1);
    RAISE EXCEPTION 'R09_APK_MEDIA_TYPE_WAS_ACCEPTED';
  EXCEPTION WHEN check_violation THEN NULL;
  END;
  BEGIN
    UPDATE hhy.media_objects
    SET mime = 'application/vnd.android.package-archive', object_key = 'apps/changed.apk'
    WHERE id = image_id;
    RAISE EXCEPTION 'R09_LINKED_IMAGE_BECAME_APK';
  EXCEPTION WHEN check_violation THEN NULL;
  END;

  BEGIN
    DELETE FROM hhy.app_details WHERE id = detail_id;
    RAISE EXCEPTION 'R09_ACTIVE_APP_DETAIL_DELETE_WAS_ACCEPTED';
  EXCEPTION WHEN object_not_in_prerequisite_state THEN NULL;
  END;

  INSERT INTO hhy.idempotency_records(scope, idem_key, request_hash)
  VALUES ('R09_APP_CREATE:' || actor_id, '0123456789abcdef', repeat('c', 64));
  BEGIN
    INSERT INTO hhy.idempotency_records(scope, idem_key, request_hash)
    VALUES ('R09_APP_CREATE:' || actor_id, '0123456789abcdef', repeat('c', 64));
    RAISE EXCEPTION 'R09_APP_DUPLICATE_IDEMPOTENCY_KEY_WAS_ACCEPTED';
  EXCEPTION WHEN unique_violation THEN NULL;
  END;

  UPDATE hhy.content_posts SET status = 'DELETED' WHERE id = app_content_id;
  DELETE FROM hhy.content_media WHERE content_id = app_content_id;
  DELETE FROM hhy.app_details WHERE id = detail_id;
END;
$$;

ROLLBACK;
