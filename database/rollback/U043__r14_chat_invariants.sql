-- Development/test rollback for R14 chat invariants.
-- Production correction must use a new forward migration.
SET search_path TO hhy, public;

LOCK TABLE hhy.conversations IN SHARE ROW EXCLUSIVE MODE;
LOCK TABLE hhy.conversation_members IN SHARE ROW EXCLUSIVE MODE;
LOCK TABLE hhy.chat_messages IN SHARE ROW EXCLUSIVE MODE;
LOCK TABLE hhy.chat_message_attachments IN SHARE ROW EXCLUSIVE MODE;
LOCK TABLE hhy.chat_read_receipts IN SHARE ROW EXCLUSIVE MODE;
LOCK TABLE hhy.user_blocks IN SHARE ROW EXCLUSIVE MODE;
LOCK TABLE hhy.chat_reports IN SHARE ROW EXCLUSIVE MODE;
LOCK TABLE hhy.outbox_events IN SHARE ROW EXCLUSIVE MODE;

-- A reverse schema rewrite is allowed only on an empty disposable database.
-- This check precedes every DDL and never removes business or Outbox facts.
DO $$
BEGIN
  IF EXISTS (SELECT 1 FROM hhy.conversations)
     OR EXISTS (SELECT 1 FROM hhy.conversation_members)
     OR EXISTS (SELECT 1 FROM hhy.chat_messages)
     OR EXISTS (SELECT 1 FROM hhy.chat_message_attachments)
     OR EXISTS (SELECT 1 FROM hhy.chat_read_receipts)
     OR EXISTS (SELECT 1 FROM hhy.user_blocks)
     OR EXISTS (SELECT 1 FROM hhy.chat_reports) THEN
    RAISE EXCEPTION 'R14_U043_BUSINESS_FACTS_PRESENT' USING ERRCODE='55000';
  END IF;
END;
$$;

DROP TRIGGER IF EXISTS trg_r14_report_media_commit ON hhy.media_objects;
DROP TRIGGER IF EXISTS trg_r14_report_attachment_commit ON hhy.chat_message_attachments;
DROP TRIGGER IF EXISTS trg_r14_report_message_commit ON hhy.chat_messages;
DROP TRIGGER IF EXISTS trg_r14_report_evidence_commit ON hhy.chat_reports;
DROP TRIGGER IF EXISTS trg_r14_chat_report_status_history ON hhy.chat_reports;
DROP TRIGGER IF EXISTS trg_r14_chat_report_guard ON hhy.chat_reports;
DROP TRIGGER IF EXISTS trg_r14_user_block_guard ON hhy.user_blocks;
DROP TRIGGER IF EXISTS trg_r14_read_receipt_guard ON hhy.chat_read_receipts;
DROP TRIGGER IF EXISTS trg_r14_chat_attachment_guard ON hhy.chat_message_attachments;
DROP TRIGGER IF EXISTS trg_r14_chat_message_status_history ON hhy.chat_messages;
DROP TRIGGER IF EXISTS trg_r14_chat_message_guard ON hhy.chat_messages;
DROP TRIGGER IF EXISTS trg_r14_direct_message_commit ON hhy.chat_messages;
DROP TRIGGER IF EXISTS trg_r14_direct_member_commit ON hhy.conversation_members;
DROP TRIGGER IF EXISTS trg_r14_direct_conversation_commit ON hhy.conversations;
DROP TRIGGER IF EXISTS trg_r14_conversation_member_guard ON hhy.conversation_members;
DROP TRIGGER IF EXISTS trg_r14_sync_direct_pair ON hhy.conversation_members;

DROP INDEX IF EXISTS hhy.ix_r14_chat_report_media_gin;
DROP INDEX IF EXISTS hhy.ix_r14_chat_report_messages_gin;
DROP INDEX IF EXISTS hhy.ix_r14_chat_report_status_version;
DROP INDEX IF EXISTS hhy.ix_r14_chat_message_conversation_sequence;
DROP INDEX IF EXISTS hhy.ix_r14_conversation_member_visible;
DROP INDEX IF EXISTS hhy.uq_r14_conversation_direct_pair;

ALTER TABLE hhy.chat_reports
  DROP CONSTRAINT IF EXISTS ck_r14_chat_report_evidence_media_ids,
  DROP CONSTRAINT IF EXISTS ck_r14_chat_report_message_ids,
  DROP CONSTRAINT IF EXISTS ck_r14_chat_report_version,
  DROP CONSTRAINT IF EXISTS ck_r14_chat_report_status,
  DROP CONSTRAINT IF EXISTS ck_r14_chat_report_text,
  DROP CONSTRAINT IF EXISTS fk_r14_chat_report_reporter;
ALTER TABLE hhy.user_blocks
  DROP CONSTRAINT IF EXISTS ck_r14_user_block_reason,
  DROP CONSTRAINT IF EXISTS ck_r14_user_block_not_self;
ALTER TABLE hhy.chat_message_attachments
  DROP CONSTRAINT IF EXISTS uq_r14_chat_attachment_order,
  DROP CONSTRAINT IF EXISTS uq_r14_chat_attachment_media,
  DROP CONSTRAINT IF EXISTS ck_r14_chat_attachment_order;
ALTER TABLE hhy.chat_messages
  DROP CONSTRAINT IF EXISTS ck_r14_chat_message_payload,
  DROP CONSTRAINT IF EXISTS uq_chat_messages_sender_id_client_msg_id,
  DROP CONSTRAINT IF EXISTS ck_r14_chat_message_body_object,
  DROP CONSTRAINT IF EXISTS ck_r14_chat_message_status,
  DROP CONSTRAINT IF EXISTS ck_r14_chat_message_type,
  DROP CONSTRAINT IF EXISTS ck_r14_chat_message_client_id,
  DROP CONSTRAINT IF EXISTS fk_r14_chat_message_sender;
ALTER TABLE hhy.conversation_members
  DROP CONSTRAINT IF EXISTS ck_r14_conversation_member_unread;
ALTER TABLE hhy.conversations
  DROP CONSTRAINT IF EXISTS ck_r14_conversation_version,
  DROP CONSTRAINT IF EXISTS ck_r14_conversation_direct_pair,
  DROP CONSTRAINT IF EXISTS fk_r14_conversation_high_user,
  DROP CONSTRAINT IF EXISTS fk_r14_conversation_low_user;

ALTER TABLE hhy.chat_reports
  ALTER COLUMN target_user_id DROP NOT NULL,
  ALTER COLUMN conversation_id DROP NOT NULL,
  ALTER COLUMN message_ids DROP NOT NULL,
  ALTER COLUMN message_ids DROP DEFAULT,
  DROP COLUMN version,
  DROP COLUMN evidence_media_ids,
  DROP COLUMN description,
  DROP COLUMN reason_code;
ALTER TABLE hhy.chat_read_receipts ALTER COLUMN read_at DROP NOT NULL;
ALTER TABLE hhy.chat_message_attachments
  ALTER COLUMN media_id DROP NOT NULL,
  ALTER COLUMN sort_order DROP NOT NULL,
  ALTER COLUMN sort_order DROP DEFAULT;
ALTER TABLE hhy.chat_messages
  ALTER COLUMN type DROP NOT NULL,
  ALTER COLUMN body_json DROP NOT NULL,
  ALTER COLUMN client_msg_id DROP NOT NULL,
  ALTER COLUMN client_msg_id TYPE bigint USING client_msg_id::bigint,
  ADD CONSTRAINT uq_chat_messages_sender_id_client_msg_id UNIQUE (sender_id,client_msg_id);
ALTER TABLE hhy.conversation_members
  ALTER COLUMN unread_count DROP NOT NULL,
  ALTER COLUMN unread_count DROP DEFAULT,
  DROP COLUMN hidden_at;
ALTER TABLE hhy.conversations
  DROP COLUMN version,
  DROP COLUMN direct_user_high_id,
  DROP COLUMN direct_user_low_id;

DROP FUNCTION IF EXISTS hhy.check_r14_report_evidence();
DROP FUNCTION IF EXISTS hhy.guard_r14_chat_report();
DROP FUNCTION IF EXISTS hhy.assert_r14_report_evidence(bigint);
DROP FUNCTION IF EXISTS hhy.guard_r14_user_block();
DROP FUNCTION IF EXISTS hhy.guard_r14_read_receipt();
DROP FUNCTION IF EXISTS hhy.assert_r14_attachment_media(bigint);
DROP FUNCTION IF EXISTS hhy.guard_r14_chat_attachment();
DROP FUNCTION IF EXISTS hhy.guard_r14_chat_message();
DROP FUNCTION IF EXISTS hhy.assert_r14_direct_conversation();
DROP FUNCTION IF EXISTS hhy.guard_r14_conversation_member();
DROP FUNCTION IF EXISTS hhy.sync_r14_direct_pair();
DROP FUNCTION IF EXISTS hhy.r14_chat_payload_valid(varchar,jsonb);
DROP FUNCTION IF EXISTS hhy.r14_jsonb_id_array_valid(jsonb,integer);
