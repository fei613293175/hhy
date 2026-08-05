-- R20 红包活动创建、预审核与初始订单不变量。
SET search_path TO hhy, public;

-- Legacy V004 only had a partial campaign shape. Keep upgrades explicit: the
-- new facts are nullable where old rows cannot be reconstructed, while every
-- new write is guarded by the checks below and by the application service.
ALTER TABLE hhy.red_packet_campaigns
  ADD COLUMN amount_per_claim_cent bigint,
  ADD COLUMN start_at timestamptz,
  ADD COLUMN end_at timestamptz,
  ADD COLUMN targeting_json jsonb DEFAULT '{}'::jsonb,
  ADD COLUMN principal_cent bigint,
  ADD COLUMN service_fee_cent bigint DEFAULT 0,
  ADD COLUMN review_decision varchar(32),
  ADD COLUMN review_reason varchar(2000),
  ADD COLUMN review_evidence_json jsonb,
  ADD COLUMN reviewed_by bigint,
  ADD COLUMN reviewed_at timestamptz;

UPDATE hhy.red_packet_campaigns
SET amount_per_claim_cent = COALESCE(amount_per_claim_cent, current_amount, 0),
    targeting_json = COALESCE(targeting_json, '{}'::jsonb),
    service_fee_cent = COALESCE(service_fee_cent, 0);

ALTER TABLE hhy.red_packet_campaigns
  ALTER COLUMN amount_per_claim_cent SET DEFAULT 0,
  ALTER COLUMN principal_cent SET DEFAULT 0,
  ALTER COLUMN service_fee_cent SET NOT NULL,
  ALTER COLUMN targeting_json SET NOT NULL,
  ADD CONSTRAINT ck_r20_campaign_status CHECK (
    status IN ('DRAFT','PRE_REVIEWING','PRE_REVIEW_REJECTED','PRE_REVIEW_APPROVED',
               'WAITING_PAYMENT','PAYMENT_PROCESSING','ACTIVE',
               'PAUSED_BY_CONTENT_OFFLINE','PAUSED_BY_OWNER','PAUSED_BY_RISK',
               'SOLD_OUT','CLOSED_BY_OWNER','TERMINATED_BY_PLATFORM')),
  ADD CONSTRAINT ck_r20_campaign_count CHECK (total_count >= 0),
  ADD CONSTRAINT ck_r20_campaign_amount CHECK (amount_per_claim_cent >= 0),
  ADD CONSTRAINT ck_r20_campaign_principal CHECK (principal_cent IS NULL OR principal_cent >= 0),
  ADD CONSTRAINT ck_r20_campaign_fee CHECK (service_fee_cent >= 0),
  ADD CONSTRAINT ck_r20_campaign_window CHECK (
    start_at IS NULL OR end_at IS NULL OR end_at > start_at),
  ADD CONSTRAINT ck_r20_campaign_targeting CHECK (jsonb_typeof(targeting_json) = 'object'),
  ADD CONSTRAINT ck_r20_campaign_review_decision CHECK (
    review_decision IS NULL OR review_decision IN ('APPROVE','REJECT','ESCALATE'));

ALTER TABLE hhy.red_packet_campaign_versions
  ADD COLUMN total_count integer,
  ADD COLUMN amount_per_claim_cent bigint,
  ADD COLUMN targeting_json jsonb DEFAULT '{}'::jsonb,
  ADD COLUMN request_hash varchar(64);

ALTER TABLE hhy.red_packet_quotes
  ADD COLUMN total_count integer,
  ADD COLUMN amount_per_claim_cent bigint,
  ADD COLUMN idempotency_key varchar(128),
  ADD COLUMN request_hash varchar(64),
  ADD COLUMN version bigint DEFAULT 0 NOT NULL;

ALTER TABLE hhy.red_packet_orders
  ADD COLUMN idempotency_key varchar(128),
  ADD COLUMN request_hash varchar(64);

ALTER TABLE hhy.red_packet_stock
  ADD CONSTRAINT ck_r20_stock_nonnegative CHECK (total >= 0 AND claimed >= 0 AND reserved >= 0),
  ADD CONSTRAINT ck_r20_stock_bounds CHECK (claimed + reserved <= total);

ALTER TABLE hhy.red_packet_reservations
  ADD COLUMN idempotency_key varchar(128),
  ADD COLUMN request_hash varchar(64);

-- New writes use the durable shared idempotency table. These indexes prevent
-- duplicate quotes/orders even if a caller retries across process restarts.
CREATE UNIQUE INDEX uq_r20_initial_quote_campaign
  ON hhy.red_packet_quotes(campaign_id)
  WHERE quote_type = 'INITIAL';
CREATE UNIQUE INDEX uq_r20_red_packet_order_quote
  ON hhy.red_packet_orders(quote_id)
  WHERE quote_id IS NOT NULL;
CREATE UNIQUE INDEX uq_r20_red_packet_reservation_key
  ON hhy.red_packet_reservations(campaign_id, user_id, idempotency_key)
  WHERE idempotency_key IS NOT NULL;
CREATE INDEX ix_r20_campaign_owner_status
  ON hhy.red_packet_campaigns(owner_id, status, created_at DESC);
CREATE INDEX ix_r20_campaign_status_created
  ON hhy.red_packet_campaigns(status, created_at DESC);
CREATE INDEX ix_r20_quote_campaign_created
  ON hhy.red_packet_quotes(campaign_id, created_at DESC);

ALTER TABLE hhy.red_packet_campaigns
  DROP CONSTRAINT IF EXISTS uq_red_packet_campaigns_content_id;

CREATE UNIQUE INDEX uq_r20_active_content_campaign
  ON hhy.red_packet_campaigns(content_id)
  WHERE status IN ('ACTIVE','PAUSED_BY_CONTENT_OFFLINE','PAUSED_BY_OWNER','PAUSED_BY_RISK');

CREATE OR REPLACE FUNCTION hhy.guard_r20_campaign_status()
RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
  IF TG_OP = 'UPDATE' AND NEW.status IS DISTINCT FROM OLD.status THEN
    IF NOT (
      (OLD.status = 'DRAFT' AND NEW.status = 'PRE_REVIEWING') OR
      (OLD.status = 'PRE_REVIEWING' AND NEW.status IN ('PRE_REVIEW_APPROVED','PRE_REVIEW_REJECTED')) OR
      (OLD.status = 'PRE_REVIEW_REJECTED' AND NEW.status = 'PRE_REVIEWING') OR
      (OLD.status = 'PRE_REVIEW_APPROVED' AND NEW.status = 'WAITING_PAYMENT') OR
      (OLD.status = 'WAITING_PAYMENT' AND NEW.status = 'PAYMENT_PROCESSING') OR
      (OLD.status = 'PAYMENT_PROCESSING' AND NEW.status = 'ACTIVE') OR
      (OLD.status = 'ACTIVE' AND NEW.status IN ('PAUSED_BY_CONTENT_OFFLINE','PAUSED_BY_OWNER','PAUSED_BY_RISK','SOLD_OUT','CLOSED_BY_OWNER','TERMINATED_BY_PLATFORM')) OR
      (OLD.status IN ('PAUSED_BY_CONTENT_OFFLINE','PAUSED_BY_OWNER','PAUSED_BY_RISK') AND NEW.status IN ('ACTIVE','TERMINATED_BY_PLATFORM')) OR
      (OLD.status IN ('DRAFT','PRE_REVIEWING','PRE_REVIEW_REJECTED','PRE_REVIEW_APPROVED','WAITING_PAYMENT','PAYMENT_PROCESSING') AND NEW.status = 'TERMINATED_BY_PLATFORM')
    ) THEN
      RAISE EXCEPTION 'R20_CAMPAIGN_INVALID_TRANSITION from=% to=%', OLD.status, NEW.status
        USING ERRCODE = '23514';
    END IF;
  END IF;
  RETURN NEW;
END;
$$;

CREATE TRIGGER trg_r20_campaign_status
BEFORE UPDATE OF status ON hhy.red_packet_campaigns
FOR EACH ROW EXECUTE FUNCTION hhy.guard_r20_campaign_status();
CREATE TRIGGER trg_r20_campaign_status_history
AFTER UPDATE OF status ON hhy.red_packet_campaigns
FOR EACH ROW EXECUTE FUNCTION hhy.record_platform_status_history();

COMMENT ON TABLE hhy.red_packet_campaigns IS 'R20 红包活动及预审核状态事实';
COMMENT ON TABLE hhy.red_packet_quotes IS 'R20 红包初始报价事实';
COMMENT ON TABLE hhy.red_packet_orders IS 'R20 红包订单关联事实';

INSERT INTO hhy.system_configs(key, value_json, scope) VALUES
  ('red_packet.default_view_seconds', '10'::jsonb, 'GLOBAL'),
  ('red_packet.reservation_min_seconds', '30'::jsonb, 'GLOBAL'),
  ('red_packet.unit_amount_min_cent', '1'::jsonb, 'GLOBAL'),
  ('red_packet.unit_amount_max_cent', '100000000'::jsonb, 'GLOBAL'),
  ('red_packet.min_count', '1'::jsonb, 'GLOBAL'),
  ('red_packet.service_fee_bps', '500'::jsonb, 'GLOBAL'),
  ('red_packet.one_active_per_content', 'true'::jsonb, 'GLOBAL'),
  ('red_packet.allow_raise_unit_amount', 'false'::jsonb, 'GLOBAL')
ON CONFLICT (key, scope) DO NOTHING;

INSERT INTO hhy.admin_permissions(code, resource, action) VALUES
  ('redpacket.review', 'redpacket', 'review')
ON CONFLICT (code) DO UPDATE SET resource = EXCLUDED.resource, action = EXCLUDED.action;

INSERT INTO hhy.admin_role_permissions(role_id, permission_id)
SELECT role.id, permission.id
FROM hhy.admin_roles role
CROSS JOIN hhy.admin_permissions permission
WHERE role.code = 'SUPER_ADMIN' AND role.status = 'ACTIVE'
  AND permission.code = 'redpacket.review'
ON CONFLICT (role_id, permission_id) DO NOTHING;

INSERT INTO hhy.admin_role_permissions(role_id, permission_id)
SELECT legacy.role_id, review.id
FROM hhy.admin_role_permissions legacy
JOIN hhy.admin_permissions manage ON manage.id = legacy.permission_id
CROSS JOIN hhy.admin_permissions review
WHERE manage.code = 'redpacket.manage' AND review.code = 'redpacket.review'
ON CONFLICT (role_id, permission_id) DO NOTHING;
