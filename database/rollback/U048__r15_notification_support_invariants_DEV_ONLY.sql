-- DEV/TEST only: remove R15 invariant objects when no V048 business facts exist.
SET search_path TO hhy, public;

DO $$
BEGIN
  IF EXISTS (
    SELECT 1 FROM hhy.notifications WHERE legacy_without_idempotency = false
  ) OR EXISTS (
    SELECT 1 FROM hhy.notification_deliveries WHERE legacy_without_idempotency = false
  ) OR EXISTS (
    SELECT 1 FROM hhy.announcement_reads WHERE legacy_without_idempotency = false
  ) OR EXISTS (
    SELECT 1 FROM hhy.outbox_events
    WHERE event_type = 'platform.status.changed.v1'
      AND aggregate_type = 'support_tickets'
  ) THEN
    RAISE EXCEPTION 'R15_U048_BUSINESS_FACTS_PRESENT'
      USING ERRCODE = '55000';
  END IF;
END;
$$;

DROP TRIGGER IF EXISTS trg_r15_support_tickets_status_history ON hhy.support_tickets;
DROP TRIGGER IF EXISTS trg_r15_support_tickets_guard ON hhy.support_tickets;
DROP TRIGGER IF EXISTS trg_r15_announcement_reads_guard ON hhy.announcement_reads;
DROP TRIGGER IF EXISTS trg_r15_notification_deliveries_guard ON hhy.notification_deliveries;
DROP TRIGGER IF EXISTS trg_r15_notifications_guard ON hhy.notifications;

DROP FUNCTION IF EXISTS hhy.guard_r15_support_ticket_mutation();
DROP FUNCTION IF EXISTS hhy.guard_r15_announcement_read_mutation();
DROP FUNCTION IF EXISTS hhy.guard_r15_notification_delivery_mutation();
DROP FUNCTION IF EXISTS hhy.guard_r15_notification_mutation();

DROP INDEX IF EXISTS hhy.uq_r15_announcement_reads_idempotency;
DROP INDEX IF EXISTS hhy.uq_r15_notification_deliveries_idempotency;
DROP INDEX IF EXISTS hhy.uq_r15_notifications_idempotency;

ALTER TABLE hhy.support_tickets
  DROP CONSTRAINT IF EXISTS ck_r15_support_ticket_status;

ALTER TABLE hhy.announcement_reads
  DROP CONSTRAINT IF EXISTS ck_r15_announcement_reads_idempotency,
  DROP COLUMN IF EXISTS legacy_without_idempotency,
  DROP COLUMN IF EXISTS request_hash,
  DROP COLUMN IF EXISTS idempotency_key;

ALTER TABLE hhy.notification_deliveries
  DROP CONSTRAINT IF EXISTS ck_r15_notification_deliveries_channel,
  DROP CONSTRAINT IF EXISTS ck_r15_notification_deliveries_idempotency,
  DROP COLUMN IF EXISTS legacy_without_idempotency,
  DROP COLUMN IF EXISTS request_hash,
  DROP COLUMN IF EXISTS idempotency_key;

ALTER TABLE hhy.notifications
  DROP CONSTRAINT IF EXISTS ck_r15_notifications_biz_identity,
  DROP CONSTRAINT IF EXISTS ck_r15_notifications_idempotency,
  DROP COLUMN IF EXISTS legacy_without_idempotency,
  DROP COLUMN IF EXISTS request_hash,
  DROP COLUMN IF EXISTS idempotency_key;
