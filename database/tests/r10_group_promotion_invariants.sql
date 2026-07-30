SET search_path TO hhy, public;
BEGIN;
SET CONSTRAINTS ALL DEFERRED;

DO $$
DECLARE
  actor_id bigint;
  qr_media_id bigint;
  main_group_id bigint;
  main_detail_id bigint;
  main_contact_id bigint;
  requirement_only_id bigint;
  password_only_id bigint;
  parent_first_id bigint;
  child_first_id bigint;
  non_group_id bigint;
  soft_deleted_id bigint;
BEGIN
  INSERT INTO hhy.users(phone, status, invite_code)
  VALUES ('13900001001', 'ACTIVE', 'R10DBTEST') RETURNING id INTO actor_id;

  INSERT INTO hhy.media_objects(owner_id, bucket, object_key, mime, size, sha256, visibility)
  VALUES (
    actor_id, 'r10-test', 'groups/join-qr.png', 'image/png', 256,
    repeat('a', 64), 'PRIVATE'
  ) RETURNING id INTO qr_media_id;

  BEGIN
    INSERT INTO hhy.content_posts(owner_id, type, title, status)
    VALUES (actor_id, 'GROUP', 'missing group detail', 'DRAFT');
    SET CONSTRAINTS ALL IMMEDIATE;
    RAISE EXCEPTION 'R10_MISSING_GROUP_DETAIL_WAS_ACCEPTED';
  EXCEPTION WHEN check_violation THEN NULL;
  END;
  SET CONSTRAINTS ALL DEFERRED;

  INSERT INTO hhy.content_posts(owner_id, type, title, status)
  VALUES (actor_id, 'GROUP', 'R10 group invariant', 'DRAFT')
  RETURNING id INTO main_group_id;

  BEGIN
    INSERT INTO hhy.group_details(content_id, platform)
    VALUES (main_group_id, '   ');
    RAISE EXCEPTION 'R10_BLANK_GROUP_PLATFORM_WAS_ACCEPTED';
  EXCEPTION WHEN check_violation THEN NULL;
  END;
  BEGIN
    INSERT INTO hhy.group_details(content_id, platform, size_range)
    VALUES (main_group_id, 'WECHAT', '   ');
    RAISE EXCEPTION 'R10_BLANK_GROUP_SIZE_RANGE_WAS_ACCEPTED';
  EXCEPTION WHEN check_violation THEN NULL;
  END;
  BEGIN
    INSERT INTO hhy.group_details(content_id, platform, join_requirement)
    VALUES (main_group_id, 'WECHAT', '   ');
    RAISE EXCEPTION 'R10_BLANK_GROUP_JOIN_REQUIREMENT_WAS_ACCEPTED';
  EXCEPTION WHEN check_violation THEN NULL;
  END;
  BEGIN
    INSERT INTO hhy.group_details(content_id, platform, group_link)
    VALUES (main_group_id, 'WECHAT', '   ');
    RAISE EXCEPTION 'R10_BLANK_GROUP_LINK_WAS_ACCEPTED';
  EXCEPTION WHEN check_violation THEN NULL;
  END;
  BEGIN
    INSERT INTO hhy.group_details(content_id, platform, group_no)
    VALUES (main_group_id, 'WECHAT', '   ');
    RAISE EXCEPTION 'R10_BLANK_GROUP_NO_WAS_ACCEPTED';
  EXCEPTION WHEN check_violation THEN NULL;
  END;
  BEGIN
    INSERT INTO hhy.group_details(content_id, platform, qr_media_id)
    VALUES (main_group_id, 'WECHAT', 9223372036854770000);
    RAISE EXCEPTION 'R10_UNKNOWN_QR_MEDIA_WAS_ACCEPTED';
  EXCEPTION WHEN foreign_key_violation THEN NULL;
  END;

  INSERT INTO hhy.group_details(
    content_id, platform, size_range, join_requirement, qr_media_id
  ) VALUES (
    main_group_id, 'WECHAT', '100-200', '备注来源后申请', qr_media_id
  ) RETURNING id INTO main_detail_id;
  INSERT INTO hhy.content_contacts(
    content_id, channel, value_cipher, display_mask, sort_order
  ) VALUES (
    main_group_id, 'WECHAT', 'hhy-contact-v1:r10-owner', '群主***', 0
  ) RETURNING id INTO main_contact_id;
  UPDATE hhy.content_posts SET status='ONLINE' WHERE id=main_group_id;
  SET CONSTRAINTS ALL IMMEDIATE;
  SET CONSTRAINTS ALL DEFERRED;

  BEGIN
    INSERT INTO hhy.group_details(content_id, platform, group_no)
    VALUES (main_group_id, 'QQ', '123456');
    RAISE EXCEPTION 'R10_DUPLICATE_GROUP_DETAIL_WAS_ACCEPTED';
  EXCEPTION WHEN unique_violation THEN NULL;
  END;
  BEGIN
    UPDATE hhy.group_details SET qr_media_id=NULL WHERE id=main_detail_id;
    SET CONSTRAINTS ALL IMMEDIATE;
    RAISE EXCEPTION 'R10_GROUP_ENTRY_CHANNEL_REMOVAL_WAS_ACCEPTED';
  EXCEPTION WHEN check_violation THEN NULL;
  END;
  SET CONSTRAINTS ALL DEFERRED;
  BEGIN
    UPDATE hhy.group_details SET qr_media_id=9223372036854770000 WHERE id=main_detail_id;
    RAISE EXCEPTION 'R10_UNKNOWN_QR_MEDIA_UPDATE_WAS_ACCEPTED';
  EXCEPTION WHEN foreign_key_violation THEN NULL;
  END;
  BEGIN
    DELETE FROM hhy.media_objects WHERE id=qr_media_id;
    RAISE EXCEPTION 'R10_REFERENCED_QR_MEDIA_DELETE_WAS_ACCEPTED';
  EXCEPTION WHEN foreign_key_violation THEN NULL;
  END;
  BEGIN
    DELETE FROM hhy.group_details WHERE id=main_detail_id;
    SET CONSTRAINTS ALL IMMEDIATE;
    RAISE EXCEPTION 'R10_ACTIVE_GROUP_DETAIL_DELETE_WAS_ACCEPTED';
  EXCEPTION WHEN check_violation THEN NULL;
  END;
  SET CONSTRAINTS ALL DEFERRED;

  DELETE FROM hhy.group_details WHERE id=main_detail_id;
  INSERT INTO hhy.group_details(content_id, platform, qr_media_id)
  VALUES (main_group_id, 'WECHAT', qr_media_id) RETURNING id INTO main_detail_id;
  SET CONSTRAINTS ALL IMMEDIATE;
  SET CONSTRAINTS ALL DEFERRED;

  BEGIN
    DELETE FROM hhy.content_contacts WHERE id=main_contact_id;
    SET CONSTRAINTS ALL IMMEDIATE;
    RAISE EXCEPTION 'R10_LAST_ONLINE_GROUP_CONTACT_DELETE_WAS_ACCEPTED';
  EXCEPTION WHEN check_violation THEN NULL;
  END;
  SET CONSTRAINTS ALL DEFERRED;
  BEGIN
    UPDATE hhy.content_contacts SET channel='JOIN_PASSWORD' WHERE id=main_contact_id;
    SET CONSTRAINTS ALL IMMEDIATE;
    RAISE EXCEPTION 'R10_LAST_ONLINE_GROUP_CONTACT_UPDATE_AWAY_WAS_ACCEPTED';
  EXCEPTION WHEN check_violation THEN NULL;
  END;
  SET CONSTRAINTS ALL DEFERRED;

  DELETE FROM hhy.content_contacts WHERE id=main_contact_id;
  INSERT INTO hhy.content_contacts(content_id,channel,value_cipher,display_mask,sort_order)
  VALUES (main_group_id,'QQ','hhy-contact-v1:r10-qq','群主QQ***',0)
  RETURNING id INTO main_contact_id;
  SET CONSTRAINTS ALL IMMEDIATE;
  SET CONSTRAINTS ALL DEFERRED;
  INSERT INTO hhy.content_contacts(content_id,channel,value_cipher,display_mask,sort_order)
  VALUES (main_group_id,'WECHAT','hhy-contact-v1:r10-wechat','群主微信***',1);
  DELETE FROM hhy.content_contacts WHERE id=main_contact_id;
  SET CONSTRAINTS ALL IMMEDIATE;
  SET CONSTRAINTS ALL DEFERRED;

  INSERT INTO hhy.content_posts(owner_id,type,title,status)
  VALUES (actor_id,'GROUP','R10 requirement-only draft','DRAFT')
  RETURNING id INTO requirement_only_id;
  INSERT INTO hhy.group_details(content_id,platform,join_requirement)
  VALUES (requirement_only_id,'WECHAT','回答验证问题');
  INSERT INTO hhy.content_contacts(content_id,channel,value_cipher,display_mask,sort_order)
  VALUES (requirement_only_id,'WECHAT','hhy-contact-v1:r10-requirement-owner','群主***',0);
  SET CONSTRAINTS ALL IMMEDIATE;
  SET CONSTRAINTS ALL DEFERRED;
  BEGIN
    UPDATE hhy.content_posts SET status='ONLINE' WHERE id=requirement_only_id;
    SET CONSTRAINTS ALL IMMEDIATE;
    RAISE EXCEPTION 'R10_JOIN_REQUIREMENT_COUNTED_AS_ENTRY_CHANNEL';
  EXCEPTION WHEN check_violation THEN NULL;
  END;
  SET CONSTRAINTS ALL DEFERRED;

  INSERT INTO hhy.content_posts(owner_id,type,title,status)
  VALUES (actor_id,'GROUP','R10 password-only draft','DRAFT')
  RETURNING id INTO password_only_id;
  INSERT INTO hhy.group_details(content_id,platform)
  VALUES (password_only_id,'WECHAT');
  INSERT INTO hhy.content_contacts(content_id,channel,value_cipher,display_mask,sort_order)
  VALUES (password_only_id,'JOIN_PASSWORD','hhy-contact-v1:r10-password','口令***',0);
  BEGIN
    INSERT INTO hhy.content_contacts(content_id,channel,value_cipher,display_mask,sort_order)
    VALUES (password_only_id,'JOIN_PASSWORD','hhy-contact-v1:r10-password-2','口令2***',1);
    RAISE EXCEPTION 'R10_DUPLICATE_JOIN_PASSWORD_WAS_ACCEPTED';
  EXCEPTION WHEN unique_violation THEN NULL;
  END;
  BEGIN
    UPDATE hhy.content_posts SET status='ONLINE' WHERE id=password_only_id;
    SET CONSTRAINTS ALL IMMEDIATE;
    RAISE EXCEPTION 'R10_JOIN_PASSWORD_WITHOUT_OWNER_CONTACT_WAS_ACCEPTED';
  EXCEPTION WHEN check_violation THEN NULL;
  END;
  SET CONSTRAINTS ALL DEFERRED;
  INSERT INTO hhy.content_contacts(content_id,channel,value_cipher,display_mask,sort_order)
  VALUES (password_only_id,'WECHAT','hhy-contact-v1:r10-password-owner','群主***',1);
  UPDATE hhy.content_posts SET status='ONLINE' WHERE id=password_only_id;
  SET CONSTRAINTS ALL IMMEDIATE;
  SET CONSTRAINTS ALL DEFERRED;

  INSERT INTO hhy.content_posts(owner_id,type,title,status)
  VALUES (actor_id,'GROUP','R10 parent-first group','DRAFT')
  RETURNING id INTO parent_first_id;
  INSERT INTO hhy.group_details(content_id,platform)
  VALUES (parent_first_id,'WECHAT');
  UPDATE hhy.content_posts SET status='ONLINE' WHERE id=parent_first_id;
  INSERT INTO hhy.content_contacts(content_id,channel,value_cipher,display_mask,sort_order)
  VALUES
    (parent_first_id,'JOIN_PASSWORD','hhy-contact-v1:r10-parent-password','口令***',0),
    (parent_first_id,'WECHAT','hhy-contact-v1:r10-parent-owner','群主***',1);
  SET CONSTRAINTS ALL IMMEDIATE;
  SET CONSTRAINTS ALL DEFERRED;

  INSERT INTO hhy.content_posts(owner_id,type,title,status)
  VALUES (actor_id,'GROUP','R10 child-first group','DRAFT')
  RETURNING id INTO child_first_id;
  INSERT INTO hhy.group_details(content_id,platform,group_link)
  VALUES (child_first_id,'QQ','https://group.example.invalid/child-first');
  INSERT INTO hhy.content_contacts(content_id,channel,value_cipher,display_mask,sort_order)
  VALUES (child_first_id,'QQ','hhy-contact-v1:r10-child-owner','群主QQ***',0);
  UPDATE hhy.content_posts SET status='ONLINE' WHERE id=child_first_id;
  SET CONSTRAINTS ALL IMMEDIATE;
  SET CONSTRAINTS ALL DEFERRED;

  INSERT INTO hhy.content_posts(owner_id,type,title,status)
  VALUES (actor_id,'PROJECT','R10 non-group password probe','DRAFT')
  RETURNING id INTO non_group_id;
  INSERT INTO hhy.project_details(content_id,cooperation)
  VALUES (non_group_id,'数据库不变量验证');
  BEGIN
    INSERT INTO hhy.content_contacts(content_id,channel,value_cipher,display_mask,sort_order)
    VALUES (non_group_id,'JOIN_PASSWORD','hhy-contact-v1:r10-wrong-type','口令***',0);
    SET CONSTRAINTS ALL IMMEDIATE;
    RAISE EXCEPTION 'R10_JOIN_PASSWORD_NON_GROUP_WAS_ACCEPTED';
  EXCEPTION WHEN check_violation THEN NULL;
  END;
  SET CONSTRAINTS ALL DEFERRED;

  INSERT INTO hhy.idempotency_records(scope,idem_key,request_hash)
  VALUES ('R10_GROUP_CREATE:' || actor_id,'0123456789abcdef',repeat('b',64));
  BEGIN
    INSERT INTO hhy.idempotency_records(scope,idem_key,request_hash)
    VALUES ('R10_GROUP_CREATE:' || actor_id,'0123456789abcdef',repeat('b',64));
    RAISE EXCEPTION 'R10_GROUP_DUPLICATE_IDEMPOTENCY_KEY_WAS_ACCEPTED';
  EXCEPTION WHEN unique_violation THEN NULL;
  END;

  INSERT INTO hhy.content_posts(owner_id,type,title,status)
  VALUES (actor_id,'GROUP','R10 soft delete group','DRAFT')
  RETURNING id INTO soft_deleted_id;
  INSERT INTO hhy.group_details(content_id,platform)
  VALUES (soft_deleted_id,'QQ');
  UPDATE hhy.content_posts SET status='DELETED' WHERE id=soft_deleted_id;
  DELETE FROM hhy.group_details WHERE content_id=soft_deleted_id;
  SET CONSTRAINTS ALL IMMEDIATE;
END;
$$;

ROLLBACK;
