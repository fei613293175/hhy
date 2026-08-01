-- R15 notification, announcement-read, and support-ticket invariants.
SET search_path TO hhy, public;

DO $$
BEGIN
  IF EXISTS (
    SELECT 1
    FROM hhy.support_tickets
    WHERE status NOT IN ('OPEN', 'PENDING', 'RESOLVED', 'CLOSED')
  ) THEN
    RAISE EXCEPTION 'R15_DIRTY_UPGRADE_SUPPORT_STATUS_INVALID'
      USING ERRCODE = '23514';
  END IF;

  IF EXISTS (
    SELECT 1
    FROM hhy.ticket_status_logs
    WHERE from_status IS NOT NULL
      AND to_status IS NOT NULL
      AND NOT (
           (from_status = 'OPEN' AND to_status = 'PENDING')
        OR (from_status = 'PENDING' AND to_status = 'RESOLVED')
        OR (from_status = 'RESOLVED' AND to_status IN ('PENDING', 'CLOSED'))
      )
  ) THEN
    RAISE EXCEPTION 'R15_DIRTY_UPGRADE_SUPPORT_STATUS_HISTORY_INVALID'
      USING ERRCODE = '23514';
  END IF;
END;
$$;

ALTER TABLE hhy.notifications
  ADD COLUMN idempotency_key varchar(128),
  ADD COLUMN request_hash varchar(64),
  ADD COLUMN legacy_without_idempotency boolean DEFAULT false NOT NULL;

ALTER TABLE hhy.notification_deliveries
  ADD COLUMN idempotency_key varchar(128),
  ADD COLUMN request_hash varchar(64),
  ADD COLUMN legacy_without_idempotency boolean DEFAULT false NOT NULL;

ALTER TABLE hhy.announcement_reads
  ADD COLUMN idempotency_key varchar(128),
  ADD COLUMN request_hash varchar(64),
  ADD COLUMN legacy_without_idempotency boolean DEFAULT false NOT NULL;

UPDATE hhy.notifications
SET legacy_without_idempotency = true;

UPDATE hhy.notification_deliveries
SET legacy_without_idempotency = true;

UPDATE hhy.announcement_reads
SET legacy_without_idempotency = true;

ALTER TABLE hhy.notifications
  ADD CONSTRAINT ck_r15_notifications_idempotency CHECK (
    (
      legacy_without_idempotency = true
      AND idempotency_key IS NULL
      AND request_hash IS NULL
    ) OR (
      legacy_without_idempotency = false
      AND idempotency_key IS NOT NULL
      AND request_hash IS NOT NULL
      AND char_length(idempotency_key) BETWEEN 16 AND 128
      AND request_hash ~ '^[0-9a-f]{64}$'
    )
  ),
  ADD CONSTRAINT ck_r15_notifications_biz_identity CHECK (
    biz_type IS NULL OR btrim(biz_type) <> ''
  );

ALTER TABLE hhy.notification_deliveries
  ADD CONSTRAINT ck_r15_notification_deliveries_idempotency CHECK (
    (
      legacy_without_idempotency = true
      AND idempotency_key IS NULL
      AND request_hash IS NULL
    ) OR (
      legacy_without_idempotency = false
      AND idempotency_key IS NOT NULL
      AND request_hash IS NOT NULL
      AND char_length(idempotency_key) BETWEEN 16 AND 128
      AND request_hash ~ '^[0-9a-f]{64}$'
    )
  ),
  ADD CONSTRAINT ck_r15_notification_deliveries_channel CHECK (
    channel IS NULL OR btrim(channel) <> ''
  );

ALTER TABLE hhy.announcement_reads
  ADD CONSTRAINT ck_r15_announcement_reads_idempotency CHECK (
    (
      legacy_without_idempotency = true
      AND idempotency_key IS NULL
      AND request_hash IS NULL
    ) OR (
      legacy_without_idempotency = false
      AND idempotency_key IS NOT NULL
      AND request_hash IS NOT NULL
      AND char_length(idempotency_key) BETWEEN 16 AND 128
      AND request_hash ~ '^[0-9a-f]{64}$'
    )
  );

ALTER TABLE hhy.support_tickets
  ADD CONSTRAINT ck_r15_support_ticket_status CHECK (
    status IN ('OPEN', 'PENDING', 'RESOLVED', 'CLOSED')
  );

CREATE UNIQUE INDEX uq_r15_notifications_idempotency
  ON hhy.notifications (
    user_id,
    (COALESCE(biz_type, '')),
    (COALESCE(biz_id, -1)),
    idempotency_key
  )
  WHERE legacy_without_idempotency = false;

CREATE UNIQUE INDEX uq_r15_notification_deliveries_idempotency
  ON hhy.notification_deliveries (
    notification_id,
    (COALESCE(channel, '')),
    idempotency_key
  )
  WHERE legacy_without_idempotency = false;

CREATE UNIQUE INDEX uq_r15_announcement_reads_idempotency
  ON hhy.announcement_reads (
    announcement_id,
    user_id,
    idempotency_key
  )
  WHERE legacy_without_idempotency = false;

CREATE OR REPLACE FUNCTION hhy.guard_r15_notification_mutation()
RETURNS trigger
LANGUAGE plpgsql
AS $$
BEGIN
  IF TG_OP = 'INSERT' THEN
    IF NEW.legacy_without_idempotency THEN
      RAISE EXCEPTION 'R15_NOTIFICATION_LEGACY_MARKER_FORBIDDEN'
        USING ERRCODE = '23514';
    END IF;
    RETURN NEW;
  END IF;

  IF NEW.legacy_without_idempotency IS DISTINCT FROM OLD.legacy_without_idempotency
     OR NEW.idempotency_key IS DISTINCT FROM OLD.idempotency_key
     OR NEW.request_hash IS DISTINCT FROM OLD.request_hash
     OR NEW.user_id IS DISTINCT FROM OLD.user_id
     OR NEW.biz_type IS DISTINCT FROM OLD.biz_type
     OR NEW.biz_id IS DISTINCT FROM OLD.biz_id
     OR NEW.created_at IS DISTINCT FROM OLD.created_at THEN
    RAISE EXCEPTION 'R15_NOTIFICATION_IDENTITY_IMMUTABLE notification_id=%', OLD.id
      USING ERRCODE = '55000';
  END IF;
  RETURN NEW;
END;
$$;

CREATE OR REPLACE FUNCTION hhy.guard_r15_notification_delivery_mutation()
RETURNS trigger
LANGUAGE plpgsql
AS $$
BEGIN
  IF TG_OP = 'INSERT' THEN
    IF NEW.legacy_without_idempotency THEN
      RAISE EXCEPTION 'R15_NOTIFICATION_DELIVERY_LEGACY_MARKER_FORBIDDEN'
        USING ERRCODE = '23514';
    END IF;
    RETURN NEW;
  END IF;

  IF NEW.legacy_without_idempotency IS DISTINCT FROM OLD.legacy_without_idempotency
     OR NEW.idempotency_key IS DISTINCT FROM OLD.idempotency_key
     OR NEW.request_hash IS DISTINCT FROM OLD.request_hash
     OR NEW.notification_id IS DISTINCT FROM OLD.notification_id
     OR NEW.channel IS DISTINCT FROM OLD.channel
     OR NEW.created_at IS DISTINCT FROM OLD.created_at THEN
    RAISE EXCEPTION 'R15_NOTIFICATION_DELIVERY_IDENTITY_IMMUTABLE delivery_id=%', OLD.id
      USING ERRCODE = '55000';
  END IF;
  RETURN NEW;
END;
$$;

CREATE OR REPLACE FUNCTION hhy.guard_r15_announcement_read_mutation()
RETURNS trigger
LANGUAGE plpgsql
AS $$
BEGIN
  IF TG_OP = 'INSERT' THEN
    IF NEW.legacy_without_idempotency THEN
      RAISE EXCEPTION 'R15_ANNOUNCEMENT_READ_LEGACY_MARKER_FORBIDDEN'
        USING ERRCODE = '23514';
    END IF;
    RETURN NEW;
  END IF;

  IF NEW.legacy_without_idempotency IS DISTINCT FROM OLD.legacy_without_idempotency
     OR NEW.idempotency_key IS DISTINCT FROM OLD.idempotency_key
     OR NEW.request_hash IS DISTINCT FROM OLD.request_hash
     OR NEW.announcement_id IS DISTINCT FROM OLD.announcement_id
     OR NEW.user_id IS DISTINCT FROM OLD.user_id
     OR NEW.read_at IS DISTINCT FROM OLD.read_at
     OR NEW.created_at IS DISTINCT FROM OLD.created_at THEN
    RAISE EXCEPTION 'R15_ANNOUNCEMENT_READ_IDENTITY_IMMUTABLE read_id=%', OLD.id
      USING ERRCODE = '55000';
  END IF;
  RETURN NEW;
END;
$$;

CREATE OR REPLACE FUNCTION hhy.guard_r15_support_ticket_mutation()
RETURNS trigger
LANGUAGE plpgsql
AS $$
BEGIN
  IF TG_OP = 'INSERT' THEN
    IF NEW.status <> 'OPEN' THEN
      RAISE EXCEPTION 'R15_SUPPORT_TICKET_INITIAL_STATUS_INVALID status=%', NEW.status
        USING ERRCODE = '23514';
    END IF;
    RETURN NEW;
  END IF;

  IF NEW.ticket_no IS DISTINCT FROM OLD.ticket_no
     OR NEW.user_id IS DISTINCT FROM OLD.user_id
     OR NEW.created_at IS DISTINCT FROM OLD.created_at THEN
    RAISE EXCEPTION 'R15_SUPPORT_TICKET_IDENTITY_IMMUTABLE ticket_id=%', OLD.id
      USING ERRCODE = '55000';
  END IF;

  IF NEW.status IS NOT DISTINCT FROM OLD.status THEN
    RETURN NEW;
  END IF;

  IF NOT (
       (OLD.status = 'OPEN' AND NEW.status = 'PENDING')
    OR (OLD.status = 'PENDING' AND NEW.status = 'RESOLVED')
    OR (OLD.status = 'RESOLVED' AND NEW.status IN ('PENDING', 'CLOSED'))
  ) THEN
    RAISE EXCEPTION 'R15_SUPPORT_TICKET_INVALID_TRANSITION ticket_id=% from=% to=%',
      OLD.id, OLD.status, NEW.status USING ERRCODE = '23514';
  END IF;
  RETURN NEW;
END;
$$;

CREATE TRIGGER trg_r15_notifications_guard
BEFORE INSERT OR UPDATE ON hhy.notifications
FOR EACH ROW EXECUTE FUNCTION hhy.guard_r15_notification_mutation();

CREATE TRIGGER trg_r15_notification_deliveries_guard
BEFORE INSERT OR UPDATE ON hhy.notification_deliveries
FOR EACH ROW EXECUTE FUNCTION hhy.guard_r15_notification_delivery_mutation();

CREATE TRIGGER trg_r15_announcement_reads_guard
BEFORE INSERT OR UPDATE ON hhy.announcement_reads
FOR EACH ROW EXECUTE FUNCTION hhy.guard_r15_announcement_read_mutation();

CREATE TRIGGER trg_r15_support_tickets_guard
BEFORE INSERT OR UPDATE ON hhy.support_tickets
FOR EACH ROW EXECUTE FUNCTION hhy.guard_r15_support_ticket_mutation();

CREATE TRIGGER trg_r15_support_tickets_status_history
AFTER UPDATE OF status ON hhy.support_tickets
FOR EACH ROW EXECUTE FUNCTION hhy.record_platform_status_history();

COMMENT ON COLUMN hhy.notifications.legacy_without_idempotency IS
  'Migration-only fact for notifications created before R15 idempotency enforcement; new inserts are rejected';
COMMENT ON COLUMN hhy.notification_deliveries.legacy_without_idempotency IS
  'Migration-only fact for notification deliveries created before R15 idempotency enforcement; new inserts are rejected';
COMMENT ON COLUMN hhy.announcement_reads.legacy_without_idempotency IS
  'Migration-only fact for announcement reads created before R15 idempotency enforcement; new inserts are rejected';
COMMENT ON COLUMN hhy.notifications.request_hash IS
  'Lowercase SHA-256 of the canonical notification creation request';
COMMENT ON COLUMN hhy.notification_deliveries.request_hash IS
  'Lowercase SHA-256 of the canonical notification-delivery request';
COMMENT ON COLUMN hhy.announcement_reads.request_hash IS
  'Lowercase SHA-256 of the canonical announcement-read request';
