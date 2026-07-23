SET search_path TO hhy, public;
BEGIN;
SET CONSTRAINTS ALL DEFERRED;

DO $$
DECLARE
  actor_id bigint;
  other_actor_id bigint;
  logo_id bigint;
  profile_id bigint;
  draft_id bigint;
BEGIN
  INSERT INTO hhy.users(phone,status,invite_code)
  VALUES ('13900001101','ACTIVE','R11DBTEST') RETURNING id INTO actor_id;
  INSERT INTO hhy.users(phone,status,invite_code)
  VALUES ('13900001102','ACTIVE','R11OTHER') RETURNING id INTO other_actor_id;
  INSERT INTO hhy.media_objects(owner_id,bucket,object_key,mime,size,sha256,visibility)
  VALUES (actor_id,'r11-test','leaders/logo.png','image/png',256,repeat('e',64),'PUBLIC')
  RETURNING id INTO logo_id;

  INSERT INTO hhy.content_posts(owner_id,type,title,status)
  VALUES (actor_id,'TEAM_LEADER','R11 complete profile','DRAFT') RETURNING id INTO profile_id;
  INSERT INTO hhy.team_leader_details(
    content_id,team_name,nickname,logo_media_id,region,personal_intro,team_intro,size_range,
    skills,cooperation_types,cooperation_requirement,past_cases,accept_private_chat
  ) VALUES (
    profile_id,'星火团队','林队长',logo_id,'CN-44','个人推广经验','团队执行介绍','10-20人',
    '社群推广','项目推广','资料真实且目标明确','["案例一"]'::jsonb,true
  );
  UPDATE hhy.content_posts SET status='PENDING_REVIEW' WHERE id=profile_id;
  SET CONSTRAINTS ALL IMMEDIATE;
  SET CONSTRAINTS ALL DEFERRED;

  BEGIN
    INSERT INTO hhy.team_leader_details(content_id,team_name)
    VALUES (profile_id,'另一团队');
    RAISE EXCEPTION 'R11_DUPLICATE_DETAIL_WAS_ACCEPTED';
  EXCEPTION WHEN unique_violation THEN NULL;
  END;
  BEGIN
    INSERT INTO hhy.content_posts(owner_id,type,title,status)
    VALUES (actor_id,'TEAM_LEADER','Second active profile','DRAFT');
    RAISE EXCEPTION 'R11_DUPLICATE_OWNER_PROFILE_WAS_ACCEPTED';
  EXCEPTION WHEN unique_violation THEN NULL;
  END;
  BEGIN
    UPDATE hhy.team_leader_details SET skills='   ' WHERE content_id=profile_id;
    RAISE EXCEPTION 'R11_BLANK_SKILLS_WAS_ACCEPTED';
  EXCEPTION WHEN check_violation THEN NULL;
  END;
  BEGIN
    UPDATE hhy.team_leader_details SET past_cases='{}'::jsonb WHERE content_id=profile_id;
    RAISE EXCEPTION 'R11_NON_ARRAY_CASES_WERE_ACCEPTED';
  EXCEPTION WHEN check_violation THEN NULL;
  END;
  BEGIN
    UPDATE hhy.team_leader_details SET logo_media_id=9223372036854770000 WHERE content_id=profile_id;
    RAISE EXCEPTION 'R11_UNKNOWN_LOGO_WAS_ACCEPTED';
  EXCEPTION WHEN foreign_key_violation THEN NULL;
  END;
  BEGIN
    UPDATE hhy.content_posts SET status='ONLINE' WHERE id=profile_id;
    SET CONSTRAINTS ALL IMMEDIATE;
    RAISE EXCEPTION 'R11_ONLINE_WITHOUT_CONTACT_WAS_ACCEPTED';
  EXCEPTION WHEN check_violation THEN NULL;
  END;
  SET CONSTRAINTS ALL DEFERRED;
  INSERT INTO hhy.content_contacts(content_id,channel,value_cipher,display_mask,sort_order)
  VALUES (profile_id,'WECHAT','hhy-contact-v1:r11-owner','微信***',0);
  UPDATE hhy.content_posts SET status='ONLINE' WHERE id=profile_id;
  SET CONSTRAINTS ALL IMMEDIATE;
  SET CONSTRAINTS ALL DEFERRED;

  BEGIN
    DELETE FROM hhy.content_contacts WHERE content_id=profile_id;
    SET CONSTRAINTS ALL IMMEDIATE;
    RAISE EXCEPTION 'R11_LAST_CONTACT_DELETE_WAS_ACCEPTED';
  EXCEPTION WHEN check_violation THEN NULL;
  END;
  SET CONSTRAINTS ALL DEFERRED;
  BEGIN
    DELETE FROM hhy.team_leader_details WHERE content_id=profile_id;
    SET CONSTRAINTS ALL IMMEDIATE;
    RAISE EXCEPTION 'R11_ACTIVE_DETAIL_DELETE_WAS_ACCEPTED';
  EXCEPTION WHEN check_violation THEN NULL;
  END;
  SET CONSTRAINTS ALL DEFERRED;

  INSERT INTO hhy.content_posts(owner_id,type,title,status)
  VALUES (other_actor_id,'TEAM_LEADER','R11 incomplete draft','DRAFT') RETURNING id INTO draft_id;
  INSERT INTO hhy.team_leader_details(content_id,team_name)
  VALUES (draft_id,'草稿团队');
  SET CONSTRAINTS ALL IMMEDIATE;
  SET CONSTRAINTS ALL DEFERRED;
  BEGIN
    UPDATE hhy.content_posts SET status='PENDING_REVIEW' WHERE id=draft_id;
    SET CONSTRAINTS ALL IMMEDIATE;
    RAISE EXCEPTION 'R11_INCOMPLETE_REVIEW_PROFILE_WAS_ACCEPTED';
  EXCEPTION WHEN check_violation THEN NULL;
  END;
END;
$$;

ROLLBACK;
