SET search_path TO hhy, public;
BEGIN;

DO $$
DECLARE
  owner_id bigint;
  project_id bigint;
  team_id bigint;
  version_id bigint;
  status_id bigint;
BEGIN
  INSERT INTO hhy.users(phone, status, invite_code)
  VALUES ('13900000601', 'ACTIVE', 'R06DBTEST') RETURNING id INTO owner_id;

  INSERT INTO hhy.content_posts(owner_id, type, title, status)
  VALUES (owner_id, 'PROJECT', 'R06 project', 'DRAFT') RETURNING id INTO project_id;
  INSERT INTO hhy.project_details(content_id, cooperation)
  VALUES (project_id, 'Joint operation');

  BEGIN
    INSERT INTO hhy.app_details(content_id, app_name) VALUES (project_id, 'Wrong type');
    RAISE EXCEPTION 'R06_DETAIL_TYPE_MISMATCH_WAS_ACCEPTED';
  EXCEPTION WHEN check_violation THEN NULL;
  END;

  BEGIN
    UPDATE hhy.content_posts SET type='APP' WHERE id=project_id;
    RAISE EXCEPTION 'R06_CONTENT_TYPE_MUTATION_WAS_ACCEPTED';
  EXCEPTION WHEN check_violation THEN NULL;
  END;

  BEGIN
    INSERT INTO hhy.content_posts(owner_id, type, title, status, refresh_times)
    VALUES (owner_id, 'PROJECT', 'Bad counter', 'DRAFT', -1);
    RAISE EXCEPTION 'R06_NEGATIVE_REFRESH_WAS_ACCEPTED';
  EXCEPTION WHEN check_violation THEN NULL;
  END;

  INSERT INTO hhy.content_posts(owner_id, type, title, status)
  VALUES (owner_id, 'TEAM_LEADER', 'Team profile', 'DRAFT') RETURNING id INTO team_id;
  INSERT INTO hhy.team_leader_details(content_id, team_name)
  VALUES (team_id, 'R06 team');

  BEGIN
    INSERT INTO hhy.content_posts(owner_id, type, title, status)
    VALUES (owner_id, 'TEAM_LEADER', 'Second team profile', 'DRAFT');
    RAISE EXCEPTION 'R06_DUPLICATE_TEAM_OWNER_WAS_ACCEPTED';
  EXCEPTION WHEN unique_violation THEN NULL;
  END;

  BEGIN
    INSERT INTO hhy.content_contacts(content_id, channel, value_cipher, display_mask, sort_order)
    VALUES (project_id, '', 1, '***', 0);
    RAISE EXCEPTION 'R06_BLANK_CONTACT_CHANNEL_WAS_ACCEPTED';
  EXCEPTION WHEN check_violation THEN NULL;
  END;

  INSERT INTO hhy.content_versions(content_id, version_no, snapshot_json, created_by)
  VALUES (project_id, '1', '{"title":"R06 project"}'::jsonb, 'r06-test') RETURNING id INTO version_id;
  BEGIN
    UPDATE hhy.content_versions SET version_no='2' WHERE id=version_id;
    RAISE EXCEPTION 'R06_VERSION_MUTATION_WAS_ACCEPTED';
  EXCEPTION WHEN object_not_in_prerequisite_state THEN NULL;
  END;

  INSERT INTO hhy.content_status_logs(content_id, from_status, to_status, operator)
  VALUES (project_id, NULL, 'DRAFT', 'r06-test') RETURNING id INTO status_id;
  BEGIN
    DELETE FROM hhy.content_status_logs WHERE id=status_id;
    RAISE EXCEPTION 'R06_STATUS_HISTORY_DELETE_WAS_ACCEPTED';
  EXCEPTION WHEN object_not_in_prerequisite_state THEN NULL;
  END;

  BEGIN
    INSERT INTO hhy.home_modules(code, title, display_order, enabled)
    VALUES ('bad-order', 'Bad order', -1, true);
    RAISE EXCEPTION 'R06_NEGATIVE_HOME_ORDER_WAS_ACCEPTED';
  EXCEPTION WHEN check_violation THEN NULL;
  END;

  BEGIN
    INSERT INTO hhy.banners(position, starts_at, ends_at)
    VALUES ('HOME', clock_timestamp(), clock_timestamp() - interval '1 minute');
    RAISE EXCEPTION 'R06_INVALID_BANNER_SCHEDULE_WAS_ACCEPTED';
  EXCEPTION WHEN check_violation THEN NULL;
  END;
END;
$$;

ROLLBACK;
