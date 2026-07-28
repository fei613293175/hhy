SET search_path TO hhy, public;

DO $$
DECLARE
  actor bigint;
  event uuid := gen_random_uuid();
  sequence_value bigint;
BEGIN
  INSERT INTO hhy.users(phone,status,invite_code)
  VALUES ('13900004401','ACTIVE','R14WS') RETURNING id INTO actor;

  INSERT INTO hhy.websocket_user_sequences(user_id,high_watermark)
  VALUES (actor,1)
  RETURNING high_watermark INTO sequence_value;
  INSERT INTO hhy.websocket_deliveries(
    event_id,user_id,server_sequence,event_type,affected_scope,envelope,
    ack_required,expires_at)
  VALUES (event,actor,sequence_value,'chat.message.new','CHAT',
    jsonb_build_object('eventId',event,'eventType','chat.message.new',
      'occurredAt',clock_timestamp(),'serverSequence',sequence_value,
      'ackRequired',true,'payload',jsonb_build_object()),
    true,clock_timestamp()+interval '72 hours');

  UPDATE hhy.websocket_deliveries
  SET delivery_attempts=1,first_delivered_at=clock_timestamp(),last_delivered_at=clock_timestamp()
  WHERE event_id=event;
  UPDATE hhy.websocket_deliveries SET acked_at=clock_timestamp() WHERE event_id=event;

  BEGIN
    UPDATE hhy.websocket_user_sequences SET high_watermark=0 WHERE user_id=actor;
    RAISE EXCEPTION 'R14_WS_SEQUENCE_REGRESSION_WAS_ACCEPTED';
  EXCEPTION WHEN check_violation THEN NULL;
  END;
  BEGIN
    UPDATE hhy.websocket_deliveries SET event_type='notification.new' WHERE event_id=event;
    RAISE EXCEPTION 'R14_WS_DELIVERY_MUTATION_WAS_ACCEPTED';
  EXCEPTION WHEN check_violation THEN NULL;
  END;
  BEGIN
    UPDATE hhy.websocket_deliveries SET delivery_attempts=0 WHERE event_id=event;
    RAISE EXCEPTION 'R14_WS_DELIVERY_ATTEMPT_REGRESSION_WAS_ACCEPTED';
  EXCEPTION WHEN check_violation THEN NULL;
  END;

  INSERT INTO hhy.websocket_gap_watermarks(user_id,expired_through_sequence,affected_scopes)
  VALUES (actor,1,ARRAY['CHAT']::varchar(32)[]);
  BEGIN
    UPDATE hhy.websocket_gap_watermarks
    SET expired_through_sequence=0 WHERE user_id=actor;
    RAISE EXCEPTION 'R14_WS_GAP_REGRESSION_WAS_ACCEPTED';
  EXCEPTION WHEN check_violation THEN NULL;
  END;
END;
$$;

DO $$
DECLARE table_count integer;
BEGIN
  SELECT count(*) INTO table_count
  FROM information_schema.tables
  WHERE table_schema='hhy' AND table_type='BASE TABLE';
  IF table_count <> 203 THEN
    RAISE EXCEPTION 'R14_WS_EXPECTED_203_TABLES found=%',table_count;
  END IF;
END;
$$;

SELECT 'R14_WEBSOCKET_RELIABILITY_INVARIANTS PASS' AS result;
