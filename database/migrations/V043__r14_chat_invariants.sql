-- R14 direct-chat identity, message, evidence, and state invariants.
SET search_path TO hhy, public;

LOCK TABLE hhy.conversations IN SHARE ROW EXCLUSIVE MODE;
LOCK TABLE hhy.conversation_members IN SHARE ROW EXCLUSIVE MODE;
LOCK TABLE hhy.chat_messages IN SHARE ROW EXCLUSIVE MODE;
LOCK TABLE hhy.chat_message_attachments IN SHARE ROW EXCLUSIVE MODE;
LOCK TABLE hhy.chat_read_receipts IN SHARE ROW EXCLUSIVE MODE;
LOCK TABLE hhy.user_blocks IN SHARE ROW EXCLUSIVE MODE;
LOCK TABLE hhy.chat_reports IN SHARE ROW EXCLUSIVE MODE;
LOCK TABLE hhy.media_objects IN SHARE ROW EXCLUSIVE MODE;
LOCK TABLE hhy.outbox_events IN SHARE ROW EXCLUSIVE MODE;

-- Refuse ambiguous legacy facts before the first DDL. Flyway executes this
-- migration transactionally, so every marker below leaves the V042 schema intact.
DO $$
BEGIN
  IF EXISTS (SELECT 1 FROM hhy.conversations WHERE type<>'DIRECT') THEN
    RAISE EXCEPTION 'R14_DIRTY_UPGRADE_UNSUPPORTED_CONVERSATION_TYPE' USING ERRCODE='23514';
  END IF;
  IF EXISTS (
    SELECT 1 FROM hhy.conversations c
    WHERE (SELECT count(*) FROM hhy.conversation_members m WHERE m.conversation_id=c.id)<2
  ) THEN
    RAISE EXCEPTION 'R14_DIRTY_UPGRADE_DIRECT_MEMBERS_MISSING' USING ERRCODE='23514';
  END IF;
  IF EXISTS (
    SELECT 1 FROM hhy.conversations c
    WHERE (SELECT count(*) FROM hhy.conversation_members m WHERE m.conversation_id=c.id)>2
  ) THEN
    RAISE EXCEPTION 'R14_DIRTY_UPGRADE_DIRECT_MEMBERS_EXCESS' USING ERRCODE='23514';
  END IF;
  IF EXISTS (
    SELECT 1 FROM hhy.conversation_members
    GROUP BY conversation_id HAVING count(*)<>count(DISTINCT user_id)
  ) THEN
    RAISE EXCEPTION 'R14_DIRTY_UPGRADE_DIRECT_SELF_CHAT' USING ERRCODE='23514';
  END IF;
  IF EXISTS (
    SELECT 1
    FROM (
      SELECT min(user_id) low_id,max(user_id) high_id,count(*) conversations
      FROM hhy.conversation_members
      GROUP BY conversation_id
    ) pairs
    GROUP BY low_id,high_id HAVING count(*)>1
  ) THEN
    RAISE EXCEPTION 'R14_DIRTY_UPGRADE_DUPLICATE_DIRECT_PAIR' USING ERRCODE='23505';
  END IF;
  IF EXISTS (
    SELECT 1 FROM hhy.conversation_members
    WHERE unread_count IS NOT NULL AND unread_count<0
  ) THEN
    RAISE EXCEPTION 'R14_DIRTY_UPGRADE_NEGATIVE_UNREAD_COUNT' USING ERRCODE='23514';
  END IF;
  IF EXISTS (
    SELECT 1 FROM hhy.conversation_members member
    JOIN hhy.chat_messages message ON message.id=member.last_read_message_id
    WHERE message.conversation_id<>member.conversation_id
  ) THEN
    RAISE EXCEPTION 'R14_DIRTY_UPGRADE_FOREIGN_READ_CURSOR' USING ERRCODE='23514';
  END IF;
  IF EXISTS (
    SELECT 1 FROM hhy.conversations conversation
    JOIN hhy.chat_messages message ON message.id=conversation.last_message_id
    WHERE message.conversation_id<>conversation.id
  ) THEN
    RAISE EXCEPTION 'R14_DIRTY_UPGRADE_FOREIGN_LAST_MESSAGE' USING ERRCODE='23514';
  END IF;
  IF EXISTS (
    SELECT 1 FROM hhy.conversations conversation
    WHERE EXISTS (SELECT 1 FROM hhy.chat_messages WHERE conversation_id=conversation.id)
      AND NOT EXISTS (
        SELECT 1 FROM hhy.chat_messages message
        WHERE message.id=conversation.last_message_id
          AND message.conversation_id=conversation.id
          AND message.id=(SELECT max(id) FROM hhy.chat_messages WHERE conversation_id=conversation.id)
          AND conversation.last_message_at=message.created_at
      )
  ) THEN
    RAISE EXCEPTION 'R14_DIRTY_UPGRADE_STALE_LAST_MESSAGE' USING ERRCODE='23514';
  END IF;
  IF EXISTS (
    SELECT 1 FROM hhy.chat_messages message
    WHERE message.client_msg_id IS NULL
       OR btrim(message.client_msg_id::text)=''
       OR char_length(message.client_msg_id::text)>64
  ) THEN
    RAISE EXCEPTION 'R14_DIRTY_UPGRADE_INVALID_CLIENT_MESSAGE_ID' USING ERRCODE='23514';
  END IF;
  IF EXISTS (
    SELECT 1 FROM hhy.chat_messages message
    WHERE message.type NOT IN ('TEXT','IMAGE','CONTENT_CARD','CONTACT_CARD')
       OR message.type IS NULL
       OR message.body_json IS NULL
       OR jsonb_typeof(message.body_json)<>'object'
       OR message.status NOT IN ('SENT','DELIVERED','READ')
       OR NOT EXISTS (
         SELECT 1 FROM hhy.conversation_members member
         WHERE member.conversation_id=message.conversation_id
           AND member.user_id=message.sender_id
       )
  ) THEN
    RAISE EXCEPTION 'R14_DIRTY_UPGRADE_INVALID_CHAT_MESSAGE' USING ERRCODE='23514';
  END IF;
  IF EXISTS (
    SELECT 1 FROM hhy.chat_message_attachments attachment
    JOIN hhy.chat_messages message ON message.id=attachment.message_id
    WHERE attachment.media_id IS NULL OR attachment.sort_order IS NULL
       OR attachment.sort_order<0 OR message.type<>'IMAGE'
  ) THEN
    RAISE EXCEPTION 'R14_DIRTY_UPGRADE_INVALID_CHAT_ATTACHMENT' USING ERRCODE='23514';
  END IF;
  IF EXISTS (
    SELECT 1 FROM hhy.chat_message_attachments
    GROUP BY message_id,media_id HAVING count(*)>1
  ) OR EXISTS (
    SELECT 1 FROM hhy.chat_message_attachments
    GROUP BY message_id,sort_order HAVING count(*)>1
  ) THEN
    RAISE EXCEPTION 'R14_DIRTY_UPGRADE_DUPLICATE_CHAT_ATTACHMENT' USING ERRCODE='23505';
  END IF;
  IF EXISTS (
    SELECT 1 FROM hhy.chat_read_receipts receipt
    JOIN hhy.chat_messages message ON message.id=receipt.message_id
    WHERE receipt.read_at IS NULL OR receipt.user_id=message.sender_id
       OR NOT EXISTS (
         SELECT 1 FROM hhy.conversation_members member
         WHERE member.conversation_id=message.conversation_id
           AND member.user_id=receipt.user_id
       )
  ) THEN
    RAISE EXCEPTION 'R14_DIRTY_UPGRADE_INVALID_READ_RECEIPT' USING ERRCODE='23514';
  END IF;
  IF EXISTS (SELECT 1 FROM hhy.user_blocks WHERE user_id=blocked_user_id) THEN
    RAISE EXCEPTION 'R14_DIRTY_UPGRADE_SELF_BLOCK' USING ERRCODE='23514';
  END IF;
  -- Existing reports cannot be upgraded honestly because V042 has no reason or
  -- media-evidence columns. Refuse them instead of fabricating moderation facts.
  IF EXISTS (SELECT 1 FROM hhy.chat_reports) THEN
    RAISE EXCEPTION 'R14_DIRTY_UPGRADE_REPORT_CONTRACT_MISSING' USING ERRCODE='23514';
  END IF;
END;
$$;

ALTER TABLE hhy.conversations
  ADD COLUMN direct_user_low_id bigint,
  ADD COLUMN direct_user_high_id bigint,
  ADD COLUMN version bigint NOT NULL DEFAULT 0;

UPDATE hhy.conversations conversation
SET direct_user_low_id=members.low_id,
    direct_user_high_id=members.high_id
FROM (
  SELECT conversation_id,min(user_id) low_id,max(user_id) high_id
  FROM hhy.conversation_members GROUP BY conversation_id
) members
WHERE members.conversation_id=conversation.id;

ALTER TABLE hhy.conversations
  ADD CONSTRAINT fk_r14_conversation_low_user
    FOREIGN KEY (direct_user_low_id) REFERENCES hhy.users(id) ON DELETE RESTRICT,
  ADD CONSTRAINT fk_r14_conversation_high_user
    FOREIGN KEY (direct_user_high_id) REFERENCES hhy.users(id) ON DELETE RESTRICT,
  ADD CONSTRAINT ck_r14_conversation_direct_pair
    CHECK (type='DIRECT' AND direct_user_low_id<direct_user_high_id),
  ADD CONSTRAINT ck_r14_conversation_version CHECK (version>=0);

CREATE UNIQUE INDEX uq_r14_conversation_direct_pair
  ON hhy.conversations(direct_user_low_id,direct_user_high_id);

ALTER TABLE hhy.conversation_members
  ADD COLUMN hidden_at timestamptz;
UPDATE hhy.conversation_members SET unread_count=0 WHERE unread_count IS NULL;
ALTER TABLE hhy.conversation_members
  ALTER COLUMN unread_count SET DEFAULT 0,
  ALTER COLUMN unread_count SET NOT NULL,
  ADD CONSTRAINT ck_r14_conversation_member_unread CHECK (unread_count>=0);

ALTER TABLE hhy.chat_messages
  DROP CONSTRAINT uq_chat_messages_sender_id_client_msg_id;
ALTER TABLE hhy.chat_messages
  ALTER COLUMN client_msg_id TYPE varchar(64) USING client_msg_id::text,
  ALTER COLUMN client_msg_id SET NOT NULL,
  ALTER COLUMN type SET NOT NULL,
  ALTER COLUMN body_json SET NOT NULL,
  ADD CONSTRAINT fk_r14_chat_message_sender
    FOREIGN KEY (sender_id) REFERENCES hhy.users(id) ON DELETE RESTRICT,
  ADD CONSTRAINT ck_r14_chat_message_client_id
    CHECK (char_length(client_msg_id) BETWEEN 1 AND 64),
  ADD CONSTRAINT ck_r14_chat_message_type
    CHECK (type IN ('TEXT','IMAGE','CONTENT_CARD','CONTACT_CARD')),
  ADD CONSTRAINT ck_r14_chat_message_status
    CHECK (status IN ('SENT','DELIVERED','READ')),
  ADD CONSTRAINT ck_r14_chat_message_body_object
    CHECK (jsonb_typeof(body_json)='object'),
  ADD CONSTRAINT uq_chat_messages_sender_id_client_msg_id
    UNIQUE (sender_id,client_msg_id);

ALTER TABLE hhy.chat_message_attachments
  ALTER COLUMN media_id SET NOT NULL,
  ALTER COLUMN sort_order SET DEFAULT 0,
  ALTER COLUMN sort_order SET NOT NULL,
  ADD CONSTRAINT ck_r14_chat_attachment_order CHECK (sort_order>=0),
  ADD CONSTRAINT uq_r14_chat_attachment_media UNIQUE (message_id,media_id),
  ADD CONSTRAINT uq_r14_chat_attachment_order UNIQUE (message_id,sort_order);

ALTER TABLE hhy.chat_read_receipts
  ALTER COLUMN read_at SET NOT NULL;

ALTER TABLE hhy.user_blocks
  ADD CONSTRAINT ck_r14_user_block_not_self CHECK (user_id<>blocked_user_id),
  ADD CONSTRAINT ck_r14_user_block_reason
    CHECK (reason IS NULL OR char_length(reason)<=2000);

ALTER TABLE hhy.chat_reports
  ADD COLUMN reason_code varchar(2000) NOT NULL,
  ADD COLUMN description text NOT NULL,
  ADD COLUMN evidence_media_ids jsonb NOT NULL DEFAULT '[]'::jsonb,
  ADD COLUMN version bigint NOT NULL DEFAULT 0,
  ALTER COLUMN target_user_id SET NOT NULL,
  ALTER COLUMN conversation_id SET NOT NULL,
  ALTER COLUMN message_ids SET DEFAULT '[]'::jsonb,
  ALTER COLUMN message_ids SET NOT NULL,
  ADD CONSTRAINT fk_r14_chat_report_reporter
    FOREIGN KEY (reporter_id) REFERENCES hhy.users(id) ON DELETE RESTRICT,
  ADD CONSTRAINT ck_r14_chat_report_text
    CHECK (char_length(reason_code)<=2000 AND char_length(description)<=2000),
  ADD CONSTRAINT ck_r14_chat_report_status
    CHECK (status IN ('PENDING','APPROVED','REJECTED','ESCALATED')),
  ADD CONSTRAINT ck_r14_chat_report_version CHECK (version>=0);

CREATE OR REPLACE FUNCTION hhy.r14_jsonb_id_array_valid(value jsonb,max_items integer)
RETURNS boolean LANGUAGE sql IMMUTABLE PARALLEL SAFE AS $$
  SELECT jsonb_typeof(value)='array'
    AND jsonb_array_length(value)<=max_items
    AND NOT EXISTS (
      SELECT 1 FROM jsonb_array_elements(value) item
      WHERE jsonb_typeof(item)<>'string'
         OR item#>>'{}' !~ '^[1-9][0-9]*$'
    )
    AND jsonb_array_length(value)=(
      SELECT count(DISTINCT item#>>'{}') FROM jsonb_array_elements(value) item
    );
$$;

ALTER TABLE hhy.chat_reports
  ADD CONSTRAINT ck_r14_chat_report_message_ids
    CHECK (hhy.r14_jsonb_id_array_valid(message_ids,100)),
  ADD CONSTRAINT ck_r14_chat_report_evidence_media_ids
    CHECK (hhy.r14_jsonb_id_array_valid(evidence_media_ids,100));

CREATE OR REPLACE FUNCTION hhy.r14_chat_payload_valid(kind varchar,payload jsonb)
RETURNS boolean LANGUAGE plpgsql IMMUTABLE PARALLEL SAFE AS $$
DECLARE
  field jsonb;
BEGIN
  IF jsonb_typeof(payload)<>'object' THEN RETURN false; END IF;
  IF kind='TEXT' THEN
    RETURN COALESCE(payload ? 'text'
      AND payload-'text'='{}'::jsonb
      AND jsonb_typeof(payload->'text')='string'
      AND char_length(payload->>'text') BETWEEN 1 AND 5000,false);
  ELSIF kind='IMAGE' THEN
    IF NOT payload ? 'mediaId'
       OR payload-ARRAY['mediaId','thumbnailUrl','width','height']<>'{}'::jsonb
       OR jsonb_typeof(payload->'mediaId')<>'string'
       OR char_length(payload->>'mediaId') NOT BETWEEN 1 AND 64 THEN RETURN false; END IF;
    IF payload ? 'thumbnailUrl' AND (
      jsonb_typeof(payload->'thumbnailUrl')<>'string'
      OR char_length(payload->>'thumbnailUrl')>2048
    ) THEN RETURN false; END IF;
    IF payload ? 'width' AND (
      jsonb_typeof(payload->'width')<>'number'
      OR payload->>'width' !~ '^[1-9][0-9]*$'
      OR (payload->>'width')::numeric NOT BETWEEN 1 AND 10000
    ) THEN RETURN false; END IF;
    IF payload ? 'height' AND (
      jsonb_typeof(payload->'height')<>'number'
      OR payload->>'height' !~ '^[1-9][0-9]*$'
      OR (payload->>'height')::numeric NOT BETWEEN 1 AND 10000
    ) THEN RETURN false; END IF;
    RETURN true;
  ELSIF kind='CONTENT_CARD' THEN
    RETURN COALESCE(payload ?& ARRAY['contentId','contentType','title']
      AND payload-ARRAY['contentId','contentType','title','coverUrl']='{}'::jsonb
      AND jsonb_typeof(payload->'contentId')='string'
      AND char_length(payload->>'contentId') BETWEEN 1 AND 64
      AND jsonb_typeof(payload->'contentType')='string'
      AND payload->>'contentType' IN ('PROJECT','APP','GROUP_CHAT','TEAM_LEADER')
      AND jsonb_typeof(payload->'title')='string'
      AND char_length(payload->>'title') BETWEEN 1 AND 160
      AND (NOT payload ? 'coverUrl' OR (
        jsonb_typeof(payload->'coverUrl')='string'
        AND char_length(payload->>'coverUrl')<=2048
      )),false);
  ELSIF kind='CONTACT_CARD' THEN
    IF NOT payload ? 'fields'
       OR payload-ARRAY['fields','note']<>'{}'::jsonb
       OR jsonb_typeof(payload->'fields')<>'array'
       OR jsonb_array_length(payload->'fields') NOT BETWEEN 1 AND 5
       OR (payload ? 'note' AND (
         jsonb_typeof(payload->'note')<>'string' OR char_length(payload->>'note')>200
       )) THEN RETURN false; END IF;
    FOR field IN SELECT value FROM jsonb_array_elements(payload->'fields') LOOP
      IF jsonb_typeof(field)<>'object' OR NOT field ?& ARRAY['type','value']
         OR field-ARRAY['type','label','value']<>'{}'::jsonb
         OR jsonb_typeof(field->'type')<>'string'
         OR field->>'type' NOT IN ('PHONE','WECHAT','QQ','EMAIL','OTHER')
         OR jsonb_typeof(field->'value')<>'string'
         OR char_length(field->>'value') NOT BETWEEN 1 AND 256
         OR (field ? 'label' AND (
           jsonb_typeof(field->'label')<>'string' OR char_length(field->>'label')>32
         )) THEN RETURN false; END IF;
    END LOOP;
    RETURN true;
  END IF;
  RETURN false;
END;
$$;

ALTER TABLE hhy.chat_messages
  ADD CONSTRAINT ck_r14_chat_message_payload
    CHECK (hhy.r14_chat_payload_valid(type,body_json));

CREATE OR REPLACE FUNCTION hhy.sync_r14_direct_pair()
RETURNS trigger LANGUAGE plpgsql AS $$
DECLARE
  target_conversation_id bigint:=COALESCE(NEW.conversation_id,OLD.conversation_id);
  member_count integer;
  low_id bigint;
  high_id bigint;
BEGIN
  IF NOT EXISTS (SELECT 1 FROM hhy.conversations WHERE id=target_conversation_id) THEN
    RETURN COALESCE(NEW,OLD);
  END IF;
  SELECT count(*),min(user_id),max(user_id)
  INTO member_count,low_id,high_id
  FROM hhy.conversation_members WHERE conversation_id=target_conversation_id;
  UPDATE hhy.conversations
  SET direct_user_low_id=CASE WHEN member_count=2 THEN low_id ELSE NULL END,
      direct_user_high_id=CASE WHEN member_count=2 THEN high_id ELSE NULL END
  WHERE id=target_conversation_id
    AND (direct_user_low_id,direct_user_high_id) IS DISTINCT FROM
        (CASE WHEN member_count=2 THEN low_id ELSE NULL END,
         CASE WHEN member_count=2 THEN high_id ELSE NULL END);
  RETURN COALESCE(NEW,OLD);
END;
$$;

CREATE TRIGGER trg_r14_sync_direct_pair
AFTER INSERT OR UPDATE OF conversation_id,user_id OR DELETE ON hhy.conversation_members
FOR EACH ROW EXECUTE FUNCTION hhy.sync_r14_direct_pair();

CREATE OR REPLACE FUNCTION hhy.guard_r14_conversation_member()
RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
  IF TG_OP='DELETE' THEN
    RAISE EXCEPTION 'R14_CONVERSATION_MEMBER_DELETE_FORBIDDEN member_id=%',OLD.id
      USING ERRCODE='23514';
  END IF;
  IF (NEW.conversation_id,NEW.user_id) IS DISTINCT FROM
     (OLD.conversation_id,OLD.user_id) THEN
    RAISE EXCEPTION 'R14_CONVERSATION_MEMBER_IDENTITY_IMMUTABLE member_id=%',OLD.id
      USING ERRCODE='55000';
  END IF;
  RETURN NEW;
END;
$$;
CREATE TRIGGER trg_r14_conversation_member_guard
BEFORE UPDATE OR DELETE ON hhy.conversation_members
FOR EACH ROW EXECUTE FUNCTION hhy.guard_r14_conversation_member();

CREATE OR REPLACE FUNCTION hhy.assert_r14_direct_conversation()
RETURNS trigger LANGUAGE plpgsql AS $$
DECLARE
  target_id bigint;
  member_count integer;
  low_id bigint;
  high_id bigint;
  row_data jsonb;
BEGIN
  row_data:=COALESCE(to_jsonb(NEW),to_jsonb(OLD));
  target_id:=CASE WHEN TG_TABLE_NAME='conversations'
                  THEN (row_data->>'id')::bigint
                  ELSE (row_data->>'conversation_id')::bigint END;
  IF NOT EXISTS (SELECT 1 FROM hhy.conversations WHERE id=target_id) THEN RETURN NULL; END IF;
  SELECT count(*),min(user_id),max(user_id)
  INTO member_count,low_id,high_id
  FROM hhy.conversation_members WHERE conversation_id=target_id;
  IF member_count<>2 OR low_id=high_id THEN
    RAISE EXCEPTION 'R14_DIRECT_CONVERSATION_REQUIRES_TWO_DISTINCT_MEMBERS conversation_id=% count=%',
      target_id,member_count USING ERRCODE='23514';
  END IF;
  IF NOT EXISTS (
    SELECT 1 FROM hhy.conversations
    WHERE id=target_id AND type='DIRECT'
      AND direct_user_low_id=low_id AND direct_user_high_id=high_id
  ) THEN
    RAISE EXCEPTION 'R14_DIRECT_PAIR_MISMATCH conversation_id=%',target_id USING ERRCODE='23514';
  END IF;
  IF EXISTS (
    SELECT 1 FROM hhy.conversations conversation
    JOIN hhy.chat_messages message ON message.id=conversation.last_message_id
    WHERE conversation.id=target_id
      AND (message.conversation_id<>target_id
        OR conversation.last_message_at IS DISTINCT FROM message.created_at)
  ) THEN
    RAISE EXCEPTION 'R14_LAST_MESSAGE_MISMATCH conversation_id=%',target_id USING ERRCODE='23514';
  END IF;
  IF EXISTS (SELECT 1 FROM hhy.chat_messages WHERE conversation_id=target_id) AND NOT EXISTS (
    SELECT 1
    FROM hhy.conversations conversation
    JOIN hhy.chat_messages message ON message.id=conversation.last_message_id
    WHERE conversation.id=target_id AND message.conversation_id=target_id
      AND message.id=(SELECT max(id) FROM hhy.chat_messages WHERE conversation_id=target_id)
      AND conversation.last_message_at=message.created_at
  ) THEN
    RAISE EXCEPTION 'R14_LAST_MESSAGE_NOT_LATEST conversation_id=%',target_id USING ERRCODE='23514';
  END IF;
  IF NOT EXISTS (SELECT 1 FROM hhy.chat_messages WHERE conversation_id=target_id) AND EXISTS (
    SELECT 1 FROM hhy.conversations
    WHERE id=target_id AND (last_message_id IS NOT NULL OR last_message_at IS NOT NULL)
  ) THEN
    RAISE EXCEPTION 'R14_EMPTY_CONVERSATION_HAS_LAST_MESSAGE conversation_id=%',target_id
      USING ERRCODE='23514';
  END IF;
  IF EXISTS (
    SELECT 1 FROM hhy.conversation_members member
    JOIN hhy.chat_messages message ON message.id=member.last_read_message_id
    WHERE member.conversation_id=target_id AND message.conversation_id<>target_id
  ) THEN
    RAISE EXCEPTION 'R14_READ_CURSOR_MISMATCH conversation_id=%',target_id USING ERRCODE='23514';
  END IF;
  RETURN NULL;
END;
$$;

CREATE CONSTRAINT TRIGGER trg_r14_direct_conversation_commit
AFTER INSERT OR UPDATE OR DELETE ON hhy.conversations
DEFERRABLE INITIALLY DEFERRED FOR EACH ROW
EXECUTE FUNCTION hhy.assert_r14_direct_conversation();
CREATE CONSTRAINT TRIGGER trg_r14_direct_member_commit
AFTER INSERT OR UPDATE OR DELETE ON hhy.conversation_members
DEFERRABLE INITIALLY DEFERRED FOR EACH ROW
EXECUTE FUNCTION hhy.assert_r14_direct_conversation();
CREATE CONSTRAINT TRIGGER trg_r14_direct_message_commit
AFTER INSERT OR UPDATE OR DELETE ON hhy.chat_messages
DEFERRABLE INITIALLY DEFERRED FOR EACH ROW
EXECUTE FUNCTION hhy.assert_r14_direct_conversation();

CREATE OR REPLACE FUNCTION hhy.guard_r14_chat_message()
RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
  IF TG_OP='DELETE' THEN
    RAISE EXCEPTION 'R14_MESSAGE_DELETE_FORBIDDEN message_id=%',OLD.id
      USING ERRCODE='55000';
  ELSIF TG_OP='INSERT' THEN
    IF NEW.status<>'SENT' THEN
      RAISE EXCEPTION 'R14_MESSAGE_MUST_START_SENT' USING ERRCODE='23514';
    END IF;
  ELSE
    IF (NEW.conversation_id,NEW.sender_id,NEW.type,NEW.body_json,NEW.client_msg_id)
       IS DISTINCT FROM
       (OLD.conversation_id,OLD.sender_id,OLD.type,OLD.body_json,OLD.client_msg_id) THEN
      RAISE EXCEPTION 'R14_MESSAGE_FACTS_IMMUTABLE message_id=%',OLD.id USING ERRCODE='55000';
    END IF;
    IF NEW.status IS DISTINCT FROM OLD.status AND
       (OLD.status,NEW.status) NOT IN (('SENT','DELIVERED'),('DELIVERED','READ')) THEN
      RAISE EXCEPTION 'R14_MESSAGE_STATUS_TRANSITION_INVALID from=% to=%',OLD.status,NEW.status
        USING ERRCODE='23514';
    END IF;
  END IF;
  IF NOT EXISTS (
    SELECT 1 FROM hhy.conversation_members
    WHERE conversation_id=NEW.conversation_id AND user_id=NEW.sender_id
  ) THEN
    RAISE EXCEPTION 'R14_MESSAGE_SENDER_NOT_MEMBER' USING ERRCODE='23514';
  END IF;
  RETURN NEW;
END;
$$;

CREATE TRIGGER trg_r14_chat_message_guard
BEFORE INSERT OR UPDATE OR DELETE ON hhy.chat_messages
FOR EACH ROW EXECUTE FUNCTION hhy.guard_r14_chat_message();
CREATE TRIGGER trg_r14_chat_message_status_history
AFTER UPDATE OF status ON hhy.chat_messages
FOR EACH ROW EXECUTE FUNCTION hhy.record_platform_status_history();

CREATE OR REPLACE FUNCTION hhy.guard_r14_chat_attachment()
RETURNS trigger LANGUAGE plpgsql AS $$
DECLARE
  message_record hhy.chat_messages%ROWTYPE;
BEGIN
  IF TG_OP='DELETE' THEN
    RAISE EXCEPTION 'R14_ATTACHMENT_DELETE_FORBIDDEN attachment_id=%',OLD.id
      USING ERRCODE='55000';
  ELSIF TG_OP='UPDATE' THEN
    RAISE EXCEPTION 'R14_ATTACHMENT_FACTS_IMMUTABLE attachment_id=%',OLD.id
      USING ERRCODE='55000';
  END IF;
  SELECT * INTO message_record FROM hhy.chat_messages WHERE id=NEW.message_id;
  IF message_record.id IS NULL OR message_record.type<>'IMAGE'
     OR message_record.body_json->>'mediaId'<>NEW.media_id::text THEN
    RAISE EXCEPTION 'R14_ATTACHMENT_REQUIRES_IMAGE_MESSAGE' USING ERRCODE='23514';
  END IF;
  IF NOT EXISTS (
    SELECT 1 FROM hhy.media_objects media
    WHERE media.id=NEW.media_id AND media.owner_id=message_record.sender_id
      AND media.status='READY' AND media.deleted_at IS NULL
      AND media.storage_scope='private_chat'
  ) THEN
    RAISE EXCEPTION 'R14_ATTACHMENT_MEDIA_NOT_SENDABLE' USING ERRCODE='23514';
  END IF;
  RETURN NEW;
END;
$$;
CREATE TRIGGER trg_r14_chat_attachment_guard
BEFORE INSERT OR UPDATE OR DELETE ON hhy.chat_message_attachments
FOR EACH ROW EXECUTE FUNCTION hhy.guard_r14_chat_attachment();

CREATE OR REPLACE FUNCTION hhy.assert_r14_attachment_media(target_media_id bigint)
RETURNS void LANGUAGE plpgsql AS $$
BEGIN
  IF EXISTS (
    SELECT 1
    FROM hhy.chat_message_attachments attachment
    JOIN hhy.chat_messages message ON message.id=attachment.message_id
    LEFT JOIN hhy.media_objects media ON media.id=attachment.media_id
    WHERE attachment.media_id=target_media_id
      AND (media.id IS NULL OR media.owner_id<>message.sender_id
        OR media.status<>'READY' OR media.deleted_at IS NOT NULL
        OR media.storage_scope<>'private_chat'
        OR message.type<>'IMAGE'
        OR message.body_json->>'mediaId'<>attachment.media_id::text)
  ) THEN
    RAISE EXCEPTION 'R14_ATTACHED_MEDIA_BECAME_INVALID media_id=%',target_media_id
      USING ERRCODE='23514';
  END IF;
END;
$$;

CREATE OR REPLACE FUNCTION hhy.guard_r14_read_receipt()
RETURNS trigger LANGUAGE plpgsql AS $$
DECLARE
  message_record hhy.chat_messages%ROWTYPE;
BEGIN
  SELECT * INTO message_record FROM hhy.chat_messages WHERE id=NEW.message_id;
  IF message_record.id IS NULL OR NEW.user_id=message_record.sender_id
     OR NEW.read_at<message_record.created_at OR NOT EXISTS (
       SELECT 1 FROM hhy.conversation_members
       WHERE conversation_id=message_record.conversation_id AND user_id=NEW.user_id
     ) THEN
    RAISE EXCEPTION 'R14_READ_RECEIPT_NOT_AUTHORIZED' USING ERRCODE='23514';
  END IF;
  RETURN NEW;
END;
$$;
CREATE TRIGGER trg_r14_read_receipt_guard
BEFORE INSERT OR UPDATE ON hhy.chat_read_receipts
FOR EACH ROW EXECUTE FUNCTION hhy.guard_r14_read_receipt();

CREATE OR REPLACE FUNCTION hhy.guard_r14_user_block()
RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
  IF TG_OP='UPDATE' AND (NEW.user_id,NEW.blocked_user_id) IS DISTINCT FROM
     (OLD.user_id,OLD.blocked_user_id) THEN
    RAISE EXCEPTION 'R14_BLOCK_PAIR_IMMUTABLE block_id=%',OLD.id USING ERRCODE='55000';
  END IF;
  RETURN NEW;
END;
$$;
CREATE TRIGGER trg_r14_user_block_guard
BEFORE UPDATE ON hhy.user_blocks
FOR EACH ROW EXECUTE FUNCTION hhy.guard_r14_user_block();

CREATE OR REPLACE FUNCTION hhy.assert_r14_report_evidence(report_id bigint)
RETURNS void LANGUAGE plpgsql AS $$
DECLARE
  report hhy.chat_reports%ROWTYPE;
  item text;
BEGIN
  SELECT * INTO report FROM hhy.chat_reports WHERE id=report_id;
  IF report.id IS NULL THEN RETURN; END IF;
  IF report.reporter_id=report.target_user_id OR NOT EXISTS (
    SELECT 1 FROM hhy.conversation_members
    WHERE conversation_id=report.conversation_id AND user_id=report.reporter_id
  ) OR NOT EXISTS (
    SELECT 1 FROM hhy.conversation_members
    WHERE conversation_id=report.conversation_id AND user_id=report.target_user_id
  ) THEN
    RAISE EXCEPTION 'R14_REPORT_PARTIES_NOT_CONVERSATION_MEMBERS report_id=%',report.id
      USING ERRCODE='23514';
  END IF;
  FOR item IN SELECT jsonb_array_elements_text(report.message_ids) LOOP
    IF NOT EXISTS (
      SELECT 1 FROM hhy.chat_messages
      WHERE id=item::bigint AND conversation_id=report.conversation_id
    ) THEN
      RAISE EXCEPTION 'R14_REPORT_MESSAGE_NOT_IN_CONVERSATION report_id=% message_id=%',report.id,item
        USING ERRCODE='23514';
    END IF;
  END LOOP;
  FOR item IN SELECT jsonb_array_elements_text(report.evidence_media_ids) LOOP
    IF NOT EXISTS (
      SELECT 1 FROM hhy.media_objects media
      WHERE media.id=item::bigint AND media.status='READY' AND media.deleted_at IS NULL
        AND media.storage_scope IN ('private_chat','audit_evidence')
        AND (media.owner_id=report.reporter_id OR EXISTS (
          SELECT 1 FROM hhy.chat_message_attachments attachment
          JOIN hhy.chat_messages message ON message.id=attachment.message_id
          WHERE attachment.media_id=media.id
            AND message.conversation_id=report.conversation_id
        ))
    ) THEN
      RAISE EXCEPTION 'R14_REPORT_MEDIA_NOT_AUTHORIZED report_id=% media_id=%',report.id,item
        USING ERRCODE='23514';
    END IF;
  END LOOP;
END;
$$;

CREATE OR REPLACE FUNCTION hhy.guard_r14_chat_report()
RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
  IF TG_OP='INSERT' THEN
    IF NEW.status<>'PENDING' OR NEW.version<>0 THEN
      RAISE EXCEPTION 'R14_REPORT_MUST_START_PENDING_VERSION_ZERO' USING ERRCODE='23514';
    END IF;
  ELSE
    IF (NEW.reporter_id,NEW.target_user_id,NEW.conversation_id,NEW.message_ids,
        NEW.reason_code,NEW.description,NEW.evidence_media_ids)
       IS DISTINCT FROM
       (OLD.reporter_id,OLD.target_user_id,OLD.conversation_id,OLD.message_ids,
        OLD.reason_code,OLD.description,OLD.evidence_media_ids) THEN
      RAISE EXCEPTION 'R14_REPORT_FACTS_IMMUTABLE report_id=%',OLD.id USING ERRCODE='55000';
    END IF;
    IF NEW.version<>OLD.version+1 THEN
      RAISE EXCEPTION 'R14_REPORT_VERSION_MUST_INCREMENT report_id=%',OLD.id USING ERRCODE='40001';
    END IF;
    IF (OLD.status,NEW.status) NOT IN (
      ('PENDING','APPROVED'),('PENDING','REJECTED'),('PENDING','ESCALATED'),
      ('ESCALATED','APPROVED'),('ESCALATED','REJECTED')
    ) THEN
      RAISE EXCEPTION 'R14_REPORT_STATUS_TRANSITION_INVALID from=% to=%',OLD.status,NEW.status
        USING ERRCODE='23514';
    END IF;
  END IF;
  RETURN NEW;
END;
$$;
CREATE TRIGGER trg_r14_chat_report_guard
BEFORE INSERT OR UPDATE ON hhy.chat_reports
FOR EACH ROW EXECUTE FUNCTION hhy.guard_r14_chat_report();
CREATE TRIGGER trg_r14_chat_report_status_history
AFTER UPDATE OF status ON hhy.chat_reports
FOR EACH ROW EXECUTE FUNCTION hhy.record_platform_status_history();

CREATE OR REPLACE FUNCTION hhy.check_r14_report_evidence()
RETURNS trigger LANGUAGE plpgsql AS $$
DECLARE
  report_id bigint;
  entity_id bigint;
  affected_media_id bigint;
  row_data jsonb;
BEGIN
  row_data:=COALESCE(to_jsonb(NEW),to_jsonb(OLD));
  entity_id:=(row_data->>'id')::bigint;
  IF TG_TABLE_NAME='chat_reports' THEN
    PERFORM hhy.assert_r14_report_evidence(entity_id);
  ELSIF TG_TABLE_NAME='chat_messages' THEN
    FOR report_id IN SELECT id FROM hhy.chat_reports
      WHERE message_ids ? entity_id::text LOOP
      PERFORM hhy.assert_r14_report_evidence(report_id);
    END LOOP;
  ELSIF TG_TABLE_NAME='chat_message_attachments' THEN
    affected_media_id:=(row_data->>'media_id')::bigint;
    FOR report_id IN SELECT report.id FROM hhy.chat_reports report
      WHERE report.evidence_media_ids ? affected_media_id::text LOOP
      PERFORM hhy.assert_r14_report_evidence(report_id);
    END LOOP;
  ELSIF TG_TABLE_NAME='media_objects' THEN
    PERFORM hhy.assert_r14_attachment_media(entity_id);
    FOR report_id IN SELECT id FROM hhy.chat_reports
      WHERE evidence_media_ids ? entity_id::text LOOP
      PERFORM hhy.assert_r14_report_evidence(report_id);
    END LOOP;
  END IF;
  RETURN NULL;
END;
$$;

CREATE CONSTRAINT TRIGGER trg_r14_report_evidence_commit
AFTER INSERT OR UPDATE OR DELETE ON hhy.chat_reports
DEFERRABLE INITIALLY DEFERRED FOR EACH ROW EXECUTE FUNCTION hhy.check_r14_report_evidence();
CREATE CONSTRAINT TRIGGER trg_r14_report_message_commit
AFTER UPDATE OR DELETE ON hhy.chat_messages
DEFERRABLE INITIALLY DEFERRED FOR EACH ROW EXECUTE FUNCTION hhy.check_r14_report_evidence();
CREATE CONSTRAINT TRIGGER trg_r14_report_attachment_commit
AFTER UPDATE OR DELETE ON hhy.chat_message_attachments
DEFERRABLE INITIALLY DEFERRED FOR EACH ROW EXECUTE FUNCTION hhy.check_r14_report_evidence();
CREATE CONSTRAINT TRIGGER trg_r14_report_media_commit
AFTER UPDATE OR DELETE ON hhy.media_objects
DEFERRABLE INITIALLY DEFERRED FOR EACH ROW EXECUTE FUNCTION hhy.check_r14_report_evidence();

CREATE INDEX ix_r14_conversation_member_visible
  ON hhy.conversation_members(user_id,updated_at DESC,conversation_id)
  WHERE hidden_at IS NULL;
CREATE INDEX ix_r14_chat_message_conversation_sequence
  ON hhy.chat_messages(conversation_id,id DESC);
CREATE INDEX ix_r14_chat_report_status_version
  ON hhy.chat_reports(status,version,created_at,id);
CREATE INDEX ix_r14_chat_report_messages_gin ON hhy.chat_reports USING gin(message_ids);
CREATE INDEX ix_r14_chat_report_media_gin ON hhy.chat_reports USING gin(evidence_media_ids);
