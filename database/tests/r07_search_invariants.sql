SET search_path TO hhy, public;
BEGIN;

DO $$
DECLARE
  actor_id bigint;
  content_id bigint;
  access_id bigint;
BEGIN
  INSERT INTO hhy.users(phone, status, invite_code)
  VALUES ('13900000701', 'ACTIVE', 'R07DBTEST') RETURNING id INTO actor_id;

  BEGIN
    INSERT INTO hhy.search_histories(user_id, keyword) VALUES (actor_id, '   ');
    RAISE EXCEPTION 'R07_BLANK_SEARCH_HISTORY_WAS_ACCEPTED';
  EXCEPTION WHEN check_violation THEN NULL;
  END;

  INSERT INTO hhy.search_histories(user_id, keyword)
  VALUES (actor_id, '合伙云');

  BEGIN
    INSERT INTO hhy.hot_search_terms(keyword, weight, enabled) VALUES ('', 1, true);
    RAISE EXCEPTION 'R07_BLANK_HOT_TERM_WAS_ACCEPTED';
  EXCEPTION WHEN check_violation THEN NULL;
  END;

  BEGIN
    INSERT INTO hhy.hot_search_terms(keyword, weight, enabled) VALUES ('negative', -1, true);
    RAISE EXCEPTION 'R07_NEGATIVE_HOT_WEIGHT_WAS_ACCEPTED';
  EXCEPTION WHEN check_violation THEN NULL;
  END;

  BEGIN
    INSERT INTO hhy.hot_search_terms(keyword, starts_at, ends_at)
    VALUES ('bad schedule', clock_timestamp(), clock_timestamp() - interval '1 minute');
    RAISE EXCEPTION 'R07_INVALID_HOT_SCHEDULE_WAS_ACCEPTED';
  EXCEPTION WHEN check_violation THEN NULL;
  END;

  INSERT INTO hhy.hot_search_terms(keyword, weight, enabled)
  VALUES ('合作项目', 10, true);
  BEGIN
    INSERT INTO hhy.hot_search_terms(keyword, weight, enabled)
    VALUES ('  合作项目  ', 9, true);
    RAISE EXCEPTION 'R07_DUPLICATE_HOT_TERM_WAS_ACCEPTED';
  EXCEPTION WHEN unique_violation THEN NULL;
  END;

  INSERT INTO hhy.content_posts(owner_id, type, title, status)
  VALUES (actor_id, 'PROJECT', 'R07 contact audit', 'ONLINE') RETURNING id INTO content_id;
  BEGIN
    INSERT INTO hhy.content_contact_access_logs(user_id, content_id, channel, action)
    VALUES (actor_id, content_id, '', 'COPY');
    RAISE EXCEPTION 'R07_INCOMPLETE_CONTACT_ACCESS_WAS_ACCEPTED';
  EXCEPTION WHEN check_violation THEN NULL;
  END;

  INSERT INTO hhy.content_contact_access_logs(user_id, content_id, channel, action)
  VALUES (actor_id, content_id, 'PHONE', 'COPY') RETURNING id INTO access_id;
  BEGIN
    UPDATE hhy.content_contact_access_logs SET action='VIEW' WHERE id=access_id;
    RAISE EXCEPTION 'R07_CONTACT_ACCESS_MUTATION_WAS_ACCEPTED';
  EXCEPTION WHEN object_not_in_prerequisite_state THEN NULL;
  END;
  BEGIN
    DELETE FROM hhy.content_contact_access_logs WHERE id=access_id;
    RAISE EXCEPTION 'R07_CONTACT_ACCESS_DELETE_WAS_ACCEPTED';
  EXCEPTION WHEN object_not_in_prerequisite_state THEN NULL;
  END;

  INSERT INTO hhy.idempotency_records(scope, idem_key, request_hash)
  VALUES ('SEARCH_HISTORY_CLEAR:' || actor_id, '0123456789abcdef', repeat('a', 64));
  BEGIN
    INSERT INTO hhy.idempotency_records(scope, idem_key, request_hash)
    VALUES ('SEARCH_HISTORY_CLEAR:' || actor_id, '0123456789abcdef', repeat('a', 64));
    RAISE EXCEPTION 'R07_HISTORY_CLEAR_DUPLICATE_KEY_WAS_ACCEPTED';
  EXCEPTION WHEN unique_violation THEN NULL;
  END;
END;
$$;

ROLLBACK;
