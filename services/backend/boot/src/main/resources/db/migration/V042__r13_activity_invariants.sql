-- R13 favorites, history, share, contact-access, and report invariants.
SET search_path TO hhy, public;

LOCK TABLE hhy.content_favorites IN SHARE ROW EXCLUSIVE MODE;
LOCK TABLE hhy.content_view_logs IN SHARE ROW EXCLUSIVE MODE;
LOCK TABLE hhy.content_contact_access_logs IN SHARE ROW EXCLUSIVE MODE;
LOCK TABLE hhy.content_reports IN SHARE ROW EXCLUSIVE MODE;

DO $$
BEGIN
  IF NOT EXISTS (
    SELECT 1 FROM pg_constraint
    WHERE connamespace='hhy'::regnamespace
      AND conname='uq_content_favorites_user_id_content_id'
  ) THEN
    RAISE EXCEPTION 'R13_FAVORITE_UNIQUENESS_PREREQUISITE_MISSING'
      USING ERRCODE='55000';
  END IF;
  IF NOT EXISTS (
    SELECT 1 FROM pg_constraint
    WHERE connamespace='hhy'::regnamespace
      AND conname='uq_idempotency_records_scope_idem_key'
  ) THEN
    RAISE EXCEPTION 'R13_IDEMPOTENCY_PREREQUISITE_MISSING'
      USING ERRCODE='55000';
  END IF;
  IF NOT EXISTS (
    SELECT 1 FROM pg_trigger
    WHERE tgrelid='hhy.content_contact_access_logs'::regclass
      AND tgname='trg_r07_contact_access_immutable'
      AND NOT tgisinternal
  ) THEN
    RAISE EXCEPTION 'R13_CONTACT_AUDIT_IMMUTABILITY_PREREQUISITE_MISSING'
      USING ERRCODE='55000';
  END IF;
  IF NOT EXISTS (
    SELECT 1 FROM pg_trigger
    WHERE tgrelid='hhy.admin_operation_logs'::regclass
      AND tgname='trg_admin_operation_logs_immutable'
      AND NOT tgisinternal
  ) THEN
    RAISE EXCEPTION 'R13_ADMIN_AUDIT_PREREQUISITE_MISSING'
      USING ERRCODE='55000';
  END IF;
END;
$$;

ALTER TABLE hhy.content_view_logs
  ADD CONSTRAINT ck_r13_content_view_event
    CHECK (
      content_id IS NOT NULL
      AND traffic_type IN (
        'ORGANIC_TRAFFIC',
        'INCENTIVIZED_RED_PACKET_TRAFFIC',
        'INCENTIVIZED_TASK_TRAFFIC',
        'SHARE'
      )
      AND (duration IS NULL OR duration >= 0)
      AND (
        (traffic_type='SHARE'
          AND duration=0
          AND source IN ('WECHAT','WECHAT_MOMENTS','COPY_LINK','OTHER'))
        OR
        (traffic_type<>'SHARE'
          AND (source IS NULL OR (btrim(source)<>'' AND char_length(source)<=255)))
      )
    ) NOT VALID;

ALTER TABLE hhy.content_contact_access_logs
  ADD CONSTRAINT ck_r13_contact_access_contract
    CHECK (
      channel IN ('WECHAT','PHONE','QQ','EMAIL','LINK','QR_CODE','JOIN_PASSWORD')
      AND action IN ('VIEW','COPY','REPLAY','REJECTED_UNAVAILABLE','REJECTED_INVALID')
    ) NOT VALID;

ALTER TABLE hhy.content_reports
  ADD CONSTRAINT fk_r13_content_reports_reporter
    FOREIGN KEY (reporter_id) REFERENCES hhy.users(id) ON DELETE RESTRICT NOT VALID,
  ADD CONSTRAINT ck_r13_content_report_contract
    CHECK (
      content_id IS NOT NULL
      AND type IS NOT NULL AND btrim(type)<>'' AND char_length(type)<=64
      AND status IS NOT NULL AND btrim(status)<>'' AND char_length(status)<=64
      AND (description IS NULL OR (btrim(description)<>'' AND char_length(description)<=2000))
    ) NOT VALID;

ALTER TABLE hhy.content_view_logs VALIDATE CONSTRAINT ck_r13_content_view_event;
ALTER TABLE hhy.content_contact_access_logs VALIDATE CONSTRAINT ck_r13_contact_access_contract;
ALTER TABLE hhy.content_reports VALIDATE CONSTRAINT fk_r13_content_reports_reporter;
ALTER TABLE hhy.content_reports VALIDATE CONSTRAINT ck_r13_content_report_contract;

CREATE INDEX ix_r13_favorite_user_recent
  ON hhy.content_favorites(user_id,created_at DESC,id DESC);
CREATE INDEX ix_r13_history_user_recent
  ON hhy.content_view_logs(user_id,created_at DESC,id DESC)
  WHERE traffic_type<>'SHARE';
CREATE INDEX ix_r13_report_status_recent
  ON hhy.content_reports(status,created_at DESC,id DESC);

CREATE OR REPLACE FUNCTION hhy.prevent_r13_favorite_update()
RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
  RAISE EXCEPTION 'R13_FAVORITE_UPDATE_FORBIDDEN favorite_id=%',OLD.id
    USING ERRCODE='55000';
END;
$$;

CREATE TRIGGER trg_r13_favorite_no_update
  BEFORE UPDATE ON hhy.content_favorites
  FOR EACH ROW EXECUTE FUNCTION hhy.prevent_r13_favorite_update();

CREATE TRIGGER trg_r13_content_view_immutable
  BEFORE UPDATE OR DELETE ON hhy.content_view_logs
  FOR EACH ROW EXECUTE FUNCTION hhy.prevent_immutable_mutation();
