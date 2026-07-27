SET search_path TO hhy, public;
BEGIN;

DO $$
<<r14_test>>
DECLARE
  config_id bigint;
  binding_id bigint;
  alice_id bigint;
  bob_id bigint;
  outsider_id bigint;
  conversation_id bigint;
  message_id bigint;
  contact_message_id bigint;
  image_message_id bigint;
  media_id bigint;
  report_id bigint;
  created_time timestamptz;
  property_actor_a bigint;
  property_actor_b bigint;
  property_conversation bigint;
BEGIN
  INSERT INTO hhy.provider_config_versions(
    provider_code,version_no,status,created_by,environment,values_json,secret_refs_json)
  VALUES ('storage','r14-chat-invariant','ACTIVE','1','STAGING','{}','{}')
  RETURNING id INTO config_id;
  INSERT INTO hhy.storage_scope_bindings(
    scope_code,provider_code,config_version_id,bucket,public_domain,status)
  VALUES ('private_chat','CLOUDFLARE_R2',config_id,'r14-private-chat',NULL,'ACTIVE')
  RETURNING id INTO binding_id;

  INSERT INTO hhy.users(phone,status,invite_code)
  VALUES ('13900004301','ACTIVE','R14CHAT01') RETURNING id INTO alice_id;
  INSERT INTO hhy.users(phone,status,invite_code)
  VALUES ('13900004302','ACTIVE','R14CHAT02') RETURNING id INTO bob_id;
  INSERT INTO hhy.users(phone,status,invite_code)
  VALUES ('13900004303','ACTIVE','R14CHAT03') RETURNING id INTO outsider_id;

  -- Existing R08 order remains valid: create the parent, then both members in
  -- the same transaction. The pair is synchronized before deferred commit checks.
  INSERT INTO hhy.conversations(type) VALUES ('DIRECT') RETURNING id INTO conversation_id;
  INSERT INTO hhy.conversation_members(conversation_id,user_id,unread_count)
  VALUES (conversation_id,alice_id,0),(conversation_id,bob_id,0);
  IF NOT EXISTS (
    SELECT 1 FROM hhy.conversations
    WHERE id=conversation_id AND direct_user_low_id=least(alice_id,bob_id)
      AND direct_user_high_id=greatest(alice_id,bob_id) AND version=0
  ) THEN RAISE EXCEPTION 'R14_DIRECT_PAIR_WAS_NOT_CANONICALIZED'; END IF;

  INSERT INTO hhy.chat_messages(
    conversation_id,sender_id,type,body_json,status,client_msg_id)
  VALUES (conversation_id,alice_id,'TEXT','{"text":"hello"}','SENT','r14-text-1')
  RETURNING id,created_at INTO message_id,created_time;
  UPDATE hhy.conversations
  SET last_message_id=message_id,last_message_at=created_time,version=version+1
  WHERE id=conversation_id;
  UPDATE hhy.conversation_members member SET unread_count=1
  WHERE member.conversation_id=r14_test.conversation_id AND member.user_id=bob_id;

  BEGIN
    INSERT INTO hhy.chat_messages(
      conversation_id,sender_id,type,body_json,status,client_msg_id)
    VALUES (conversation_id,alice_id,'TEXT','{"text":"duplicate"}','SENT','r14-text-1');
    RAISE EXCEPTION 'R14_DUPLICATE_CLIENT_MESSAGE_ID_WAS_ACCEPTED';
  EXCEPTION WHEN unique_violation THEN NULL;
  END;
  BEGIN
    INSERT INTO hhy.chat_messages(
      conversation_id,sender_id,type,body_json,status,client_msg_id)
    VALUES (conversation_id,outsider_id,'TEXT','{"text":"outsider"}','SENT','r14-outsider');
    RAISE EXCEPTION 'R14_OUTSIDER_MESSAGE_WAS_ACCEPTED';
  EXCEPTION WHEN check_violation THEN NULL;
  END;
  INSERT INTO hhy.chat_messages(
    conversation_id,sender_id,type,body_json,status,client_msg_id)
  VALUES (
    conversation_id,alice_id,'CONTACT_CARD',jsonb_build_object(
      'fields',jsonb_build_array(jsonb_build_object(
        'type','OTHER','value','hhy-contact-v1.'||repeat('A',16)||'.'||repeat('B',1400)))),
    'SENT','r14-contact-envelope')
  RETURNING id INTO contact_message_id;
  BEGIN
    INSERT INTO hhy.chat_messages(
      conversation_id,sender_id,type,body_json,status,client_msg_id)
    VALUES (
      conversation_id,alice_id,'CONTACT_CARD',
      '{"fields":[{"type":"PHONE","value":"13800138000"}]}'::jsonb,
      'SENT','r14-contact-plaintext');
    RAISE EXCEPTION 'R14_CONTACT_PLAINTEXT_WAS_ACCEPTED';
  EXCEPTION WHEN check_violation THEN NULL;
  END;
  BEGIN
    INSERT INTO hhy.chat_messages(
      conversation_id,sender_id,type,body_json,status,client_msg_id)
    VALUES (
      conversation_id,alice_id,'CONTACT_CARD',jsonb_build_object(
        'fields',jsonb_build_array(jsonb_build_object(
          'type','OTHER','value','hhy-contact-v1.'||repeat('A',16)||'.'||repeat('B',2020)))),
      'SENT','r14-contact-envelope-oversized');
    RAISE EXCEPTION 'R14_CONTACT_OVERSIZED_ENVELOPE_WAS_ACCEPTED';
  EXCEPTION WHEN check_violation THEN NULL;
  END;
  BEGIN
    INSERT INTO hhy.chat_messages(
      conversation_id,sender_id,type,body_json,status,client_msg_id)
    VALUES (conversation_id,alice_id,'TEXT','{}','SENT','r14-missing-text');
    RAISE EXCEPTION 'R14_MISSING_REQUIRED_PAYLOAD_FIELD_WAS_ACCEPTED';
  EXCEPTION WHEN check_violation THEN NULL;
  END;
  BEGIN
    INSERT INTO hhy.chat_messages(
      conversation_id,sender_id,type,body_json,status,client_msg_id)
    VALUES (conversation_id,alice_id,'IMAGE','{}','SENT','r14-missing-media');
    RAISE EXCEPTION 'R14_MISSING_IMAGE_MEDIA_ID_WAS_ACCEPTED';
  EXCEPTION WHEN check_violation THEN NULL;
  END;
  BEGIN
    UPDATE hhy.chat_messages SET status='READ' WHERE id=message_id;
    RAISE EXCEPTION 'R14_STATUS_SKIP_WAS_ACCEPTED';
  EXCEPTION WHEN check_violation THEN NULL;
  END;
  UPDATE hhy.chat_messages SET status='DELIVERED' WHERE id=message_id;
  INSERT INTO hhy.chat_read_receipts(message_id,user_id,read_at)
  VALUES (message_id,bob_id,clock_timestamp());
  UPDATE hhy.chat_messages SET status='READ' WHERE id=message_id;

  INSERT INTO hhy.media_objects(
    owner_id,bucket,object_key,mime,size,sha256,visibility,purpose,storage_scope,
    storage_binding_id,status)
  VALUES (
    alice_id,'r14-private-chat','private_chat/r14/image.png','image/png',64,repeat('e',64),
    'PRIVATE','chat.image','private_chat',binding_id,'READY')
  RETURNING id INTO media_id;
  INSERT INTO hhy.chat_messages(
    conversation_id,sender_id,type,body_json,status,client_msg_id)
  VALUES (
    conversation_id,alice_id,'IMAGE',jsonb_build_object('mediaId',media_id::text,'width',800,'height',600),
    'SENT','r14-image-1')
  RETURNING id,created_at INTO image_message_id,created_time;
  INSERT INTO hhy.chat_message_attachments(message_id,media_id,sort_order)
  VALUES (image_message_id,media_id,0);
  UPDATE hhy.conversations
  SET last_message_id=image_message_id,last_message_at=created_time,version=version+1
  WHERE id=conversation_id;
  UPDATE hhy.conversation_members member SET unread_count=2
  WHERE member.conversation_id=r14_test.conversation_id AND member.user_id=bob_id;

  BEGIN
    UPDATE hhy.chat_message_attachments attachment SET sort_order=1
    WHERE attachment.message_id=r14_test.image_message_id;
    RAISE EXCEPTION 'R14_ATTACHMENT_MUTATION_WAS_ACCEPTED';
  EXCEPTION WHEN SQLSTATE '55000' THEN NULL;
  END;
  BEGIN
    DELETE FROM hhy.chat_messages WHERE id=image_message_id;
    RAISE EXCEPTION 'R14_MESSAGE_DELETE_WAS_ACCEPTED';
  EXCEPTION WHEN SQLSTATE '55000' THEN NULL;
  END;

  INSERT INTO hhy.chat_reports(
    reporter_id,target_user_id,conversation_id,message_ids,status,
    reason_code,description,evidence_media_ids)
  VALUES (
    bob_id,alice_id,conversation_id,jsonb_build_array(image_message_id::text),'PENDING',
    'HARASSMENT','evidence retained',jsonb_build_array(media_id::text))
  RETURNING id INTO report_id;

  BEGIN
    UPDATE hhy.media_objects
    SET status='DELETED',deleted_at=clock_timestamp()
    WHERE id=media_id;
    SET CONSTRAINTS trg_r14_report_media_commit IMMEDIATE;
    RAISE EXCEPTION 'R14_ATTACHED_MEDIA_INVALIDATION_WAS_ACCEPTED';
  EXCEPTION WHEN check_violation THEN NULL;
  END;
  SET CONSTRAINTS ALL DEFERRED;

  BEGIN
    INSERT INTO hhy.chat_reports(
      reporter_id,target_user_id,conversation_id,message_ids,status,
      reason_code,description,evidence_media_ids)
    VALUES (
      bob_id,alice_id,conversation_id,jsonb_build_array('999999999999'),'PENDING',
      'OTHER','invalid message evidence','[]'::jsonb);
    SET CONSTRAINTS trg_r14_report_evidence_commit IMMEDIATE;
    RAISE EXCEPTION 'R14_FOREIGN_REPORT_MESSAGE_WAS_ACCEPTED';
  EXCEPTION WHEN check_violation THEN NULL;
  END;
  SET CONSTRAINTS ALL DEFERRED;

  UPDATE hhy.chat_reports SET status='ESCALATED',version=1 WHERE id=report_id;
  UPDATE hhy.chat_reports SET status='APPROVED',version=2 WHERE id=report_id;
  IF (SELECT count(*) FROM hhy.outbox_events
      WHERE aggregate_type='chat_reports' AND aggregate_id=report_id::text
        AND event_type='platform.status.changed.v1')<>2 THEN
    RAISE EXCEPTION 'R14_REPORT_STATUS_HISTORY_INCOMPLETE';
  END IF;
  IF (SELECT count(*) FROM hhy.outbox_events
      WHERE aggregate_type='chat_messages' AND aggregate_id=message_id::text
        AND event_type='platform.status.changed.v1')<>2 THEN
    RAISE EXCEPTION 'R14_MESSAGE_STATUS_HISTORY_INCOMPLETE';
  END IF;

  BEGIN
    INSERT INTO hhy.user_blocks(user_id,blocked_user_id,reason)
    VALUES (alice_id,alice_id,'self');
    RAISE EXCEPTION 'R14_SELF_BLOCK_WAS_ACCEPTED';
  EXCEPTION WHEN check_violation THEN NULL;
  END;
  INSERT INTO hhy.user_blocks(user_id,blocked_user_id,reason)
  VALUES (alice_id,bob_id,'user choice');

  BEGIN
    INSERT INTO hhy.conversations(type) VALUES ('DIRECT')
    RETURNING id INTO property_conversation;
    INSERT INTO hhy.conversation_members(conversation_id,user_id)
    VALUES (property_conversation,alice_id),(property_conversation,bob_id);
    RAISE EXCEPTION 'R14_DUPLICATE_DIRECT_PAIR_WAS_ACCEPTED';
  EXCEPTION WHEN unique_violation THEN NULL;
  END;

  BEGIN
    DELETE FROM hhy.conversation_members member
    WHERE member.conversation_id=r14_test.conversation_id AND member.user_id=bob_id;
    SET CONSTRAINTS trg_r14_direct_member_commit IMMEDIATE;
    RAISE EXCEPTION 'R14_PHYSICAL_MEMBER_DELETE_WAS_ACCEPTED';
  EXCEPTION WHEN check_violation OR not_null_violation THEN NULL;
  END;
  SET CONSTRAINTS ALL DEFERRED;
  BEGIN
    UPDATE hhy.conversation_members member SET user_id=outsider_id
    WHERE member.conversation_id=r14_test.conversation_id AND member.user_id=bob_id;
    RAISE EXCEPTION 'R14_MEMBER_IDENTITY_MUTATION_WAS_ACCEPTED';
  EXCEPTION WHEN SQLSTATE '55000' THEN NULL;
  END;

  -- Property matrix: canonicalization and unique pair storage hold for 32
  -- independent user pairs, not just one hand-picked example.
  FOR index IN 1..32 LOOP
    INSERT INTO hhy.users(phone,status,invite_code)
    VALUES ('13843'||lpad((index*2)::text,6,'0'),'ACTIVE','R14PA'||lpad(index::text,4,'0'))
    RETURNING id INTO property_actor_a;
    INSERT INTO hhy.users(phone,status,invite_code)
    VALUES ('13743'||lpad((index*2+1)::text,6,'0'),'ACTIVE','R14PB'||lpad(index::text,4,'0'))
    RETURNING id INTO property_actor_b;
    INSERT INTO hhy.conversations(type) VALUES ('DIRECT') RETURNING id INTO property_conversation;
    INSERT INTO hhy.conversation_members(conversation_id,user_id)
    VALUES (property_conversation,property_actor_b),(property_conversation,property_actor_a);
    IF NOT EXISTS (
      SELECT 1 FROM hhy.conversations
      WHERE id=property_conversation
        AND direct_user_low_id=least(property_actor_a,property_actor_b)
        AND direct_user_high_id=greatest(property_actor_a,property_actor_b)
    ) THEN RAISE EXCEPTION 'R14_PROPERTY_PAIR_NOT_CANONICAL index=%',index; END IF;
  END LOOP;
END;
$$;

SET CONSTRAINTS ALL IMMEDIATE;
SELECT 'R14_CHAT_INVARIANT_PROPERTY_MATRIX PASS';
ROLLBACK;
