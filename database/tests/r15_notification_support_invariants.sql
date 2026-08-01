BEGIN;

SET search_path TO hhy, public;

DO $$
DECLARE
  v_user_id bigint;
  v_notification_id bigint;
  v_delivery_id bigint;
  v_announcement_id bigint;
  v_read_id bigint;
  v_ticket_id bigint;
BEGIN
  INSERT INTO hhy.users(phone, status, invite_code)
  VALUES (
    '139' || substr(replace(gen_random_uuid()::text, '-', ''), 1, 8),
    'ACTIVE',
    'R15' || substr(replace(gen_random_uuid()::text, '-', ''), 1, 16)
  )
  RETURNING id INTO v_user_id;

  BEGIN
    INSERT INTO hhy.notifications(user_id, type, title, body, biz_type, biz_id)
    VALUES (v_user_id, 'SYSTEM', 'missing idempotency', 'body', 'r15', 1);
    RAISE EXCEPTION 'R15_NOTIFICATION_MISSING_IDEMPOTENCY_WAS_ACCEPTED';
  EXCEPTION WHEN check_violation THEN
    NULL;
  END;

  BEGIN
    INSERT INTO hhy.notifications(
      user_id, type, title, body, biz_type, biz_id,
      legacy_without_idempotency
    )
    VALUES (v_user_id, 'SYSTEM', 'legacy marker', 'body', 'r15', 1, true);
    RAISE EXCEPTION 'R15_NOTIFICATION_LEGACY_MARKER_WAS_ACCEPTED';
  EXCEPTION WHEN check_violation THEN
    NULL;
  END;

  INSERT INTO hhy.notifications(
    user_id, type, title, body, biz_type, biz_id,
    idempotency_key, request_hash
  )
  VALUES (
    v_user_id, 'SYSTEM', 'created', 'body', 'r15', 1,
    'r15-notification-key-0001', repeat('a', 64)
  )
  RETURNING id INTO v_notification_id;

  BEGIN
    INSERT INTO hhy.notifications(
      user_id, type, title, body, biz_type, biz_id,
      idempotency_key, request_hash
    )
    VALUES (
      v_user_id, 'SYSTEM', 'duplicate', 'body', 'r15', 1,
      'r15-notification-key-0001', repeat('b', 64)
    );
    RAISE EXCEPTION 'R15_DUPLICATE_NOTIFICATION_IDEMPOTENCY_WAS_ACCEPTED';
  EXCEPTION WHEN unique_violation THEN
    NULL;
  END;

  BEGIN
    UPDATE hhy.notifications
    SET idempotency_key = 'r15-notification-key-0002'
    WHERE id = v_notification_id;
    RAISE EXCEPTION 'R15_NOTIFICATION_IDEMPOTENCY_MUTATION_WAS_ACCEPTED';
  EXCEPTION WHEN object_not_in_prerequisite_state THEN
    NULL;
  END;

  BEGIN
    INSERT INTO hhy.notification_deliveries(notification_id, channel, status)
    VALUES (v_notification_id, 'PUSH', 'PENDING');
    RAISE EXCEPTION 'R15_DELIVERY_MISSING_IDEMPOTENCY_WAS_ACCEPTED';
  EXCEPTION WHEN check_violation THEN
    NULL;
  END;

  INSERT INTO hhy.notification_deliveries(
    notification_id, channel, status, provider_msg_id,
    idempotency_key, request_hash
  )
  VALUES (
    v_notification_id, 'PUSH', 'PENDING', 'provider-1',
    'r15-delivery-key-000001', repeat('c', 64)
  )
  RETURNING id INTO v_delivery_id;

  BEGIN
    INSERT INTO hhy.notification_deliveries(
      notification_id, channel, status, provider_msg_id,
      idempotency_key, request_hash
    )
    VALUES (
      v_notification_id, 'PUSH', 'PENDING', 'provider-2',
      'r15-delivery-key-000001', repeat('d', 64)
    );
    RAISE EXCEPTION 'R15_DUPLICATE_DELIVERY_IDEMPOTENCY_WAS_ACCEPTED';
  EXCEPTION WHEN unique_violation THEN
    NULL;
  END;

  BEGIN
    UPDATE hhy.notification_deliveries
    SET request_hash = repeat('e', 64)
    WHERE id = v_delivery_id;
    RAISE EXCEPTION 'R15_DELIVERY_IDEMPOTENCY_MUTATION_WAS_ACCEPTED';
  EXCEPTION WHEN object_not_in_prerequisite_state THEN
    NULL;
  END;

  INSERT INTO hhy.announcements(title, content, status, published_at)
  VALUES ('R15 announcement', 'content', 'PUBLISHED', clock_timestamp())
  RETURNING id INTO v_announcement_id;

  BEGIN
    INSERT INTO hhy.announcement_reads(announcement_id, user_id, read_at)
    VALUES (v_announcement_id, v_user_id, clock_timestamp());
    RAISE EXCEPTION 'R15_ANNOUNCEMENT_READ_MISSING_IDEMPOTENCY_WAS_ACCEPTED';
  EXCEPTION WHEN check_violation THEN
    NULL;
  END;

  INSERT INTO hhy.announcement_reads(
    announcement_id, user_id, read_at, idempotency_key, request_hash
  )
  VALUES (
    v_announcement_id, v_user_id, clock_timestamp(),
    'r15-announcement-read-0001', repeat('f', 64)
  )
  RETURNING id INTO v_read_id;

  BEGIN
    UPDATE hhy.announcement_reads
    SET request_hash = repeat('1', 64)
    WHERE id = v_read_id;
    RAISE EXCEPTION 'R15_ANNOUNCEMENT_READ_IDEMPOTENCY_MUTATION_WAS_ACCEPTED';
  EXCEPTION WHEN object_not_in_prerequisite_state THEN
    NULL;
  END;

  BEGIN
    INSERT INTO hhy.support_tickets(ticket_no, user_id, type, status)
    VALUES ('R15-TICKET-BAD-' || v_user_id::text, v_user_id, 'QUESTION', 'CLOSED');
    RAISE EXCEPTION 'R15_SUPPORT_TICKET_BAD_INITIAL_STATUS_WAS_ACCEPTED';
  EXCEPTION WHEN check_violation THEN
    NULL;
  END;

  INSERT INTO hhy.support_tickets(ticket_no, user_id, type, status)
  VALUES ('R15-TICKET-' || v_user_id::text, v_user_id, 'QUESTION', 'OPEN')
  RETURNING id INTO v_ticket_id;

  BEGIN
    UPDATE hhy.support_tickets SET status = 'CLOSED' WHERE id = v_ticket_id;
    RAISE EXCEPTION 'R15_SUPPORT_TICKET_OPEN_TO_CLOSED_WAS_ACCEPTED';
  EXCEPTION WHEN check_violation THEN
    NULL;
  END;

  UPDATE hhy.support_tickets SET status = 'PENDING' WHERE id = v_ticket_id;

  BEGIN
    UPDATE hhy.support_tickets SET status = 'CLOSED' WHERE id = v_ticket_id;
    RAISE EXCEPTION 'R15_SUPPORT_TICKET_PENDING_TO_CLOSED_WAS_ACCEPTED';
  EXCEPTION WHEN check_violation THEN
    NULL;
  END;

  UPDATE hhy.support_tickets SET status = 'RESOLVED' WHERE id = v_ticket_id;
  UPDATE hhy.support_tickets SET status = 'CLOSED' WHERE id = v_ticket_id;

  IF NOT EXISTS (
    SELECT 1
    FROM hhy.outbox_events
    WHERE aggregate_type = 'support_tickets'
      AND aggregate_id = v_ticket_id::text
      AND event_type = 'platform.status.changed.v1'
      AND payload->>'fromStatus' = 'RESOLVED'
      AND payload->>'toStatus' = 'CLOSED'
  ) THEN
    RAISE EXCEPTION 'R15_SUPPORT_TICKET_RESOLVED_CLOSED_HISTORY_MISSING';
  END IF;
END;
$$;

SELECT 'R15_NOTIFICATION_SUPPORT_INVARIANTS PASS';

ROLLBACK;
