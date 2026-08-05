-- R21 owner lifecycle, quote-version concurrency, and red-packet payment fulfillment.
SET search_path TO hhy, public;

ALTER TABLE hhy.red_packet_campaigns
  ADD COLUMN owner_action varchar(32),
  ADD COLUMN owner_action_reason varchar(2000),
  ADD COLUMN owner_action_at timestamptz;

ALTER TABLE hhy.red_packet_campaign_versions
  ADD COLUMN change_kind varchar(32),
  ADD COLUMN source_quote_id bigint;

ALTER TABLE hhy.red_packet_campaign_versions
  ADD CONSTRAINT ck_r21_campaign_version_change_kind CHECK (
    change_kind IS NULL OR change_kind IN ('INITIAL','EDIT','INCREASE','LIFECYCLE')),
  ADD CONSTRAINT fk_r21_campaign_version_source_quote
    FOREIGN KEY (source_quote_id) REFERENCES hhy.red_packet_quotes(id) ON DELETE RESTRICT,
  ADD CONSTRAINT ck_r21_campaign_version_source_quote CHECK (
    (change_kind = 'INCREASE' AND source_quote_id IS NOT NULL)
    OR (change_kind IS DISTINCT FROM 'INCREASE' AND source_quote_id IS NULL)
  );

ALTER TABLE hhy.red_packet_quotes
  ADD CONSTRAINT ck_r21_quote_type CHECK (
    quote_type IS NULL OR quote_type IN ('INITIAL','INCREASE'));

-- A quote is immutable history. Expired INCREASE quotes may be replaced; the
-- order relation is the durable single-pending-order guard for a campaign/version.
DROP INDEX IF EXISTS uq_r21_increase_quote_campaign_version;
CREATE UNIQUE INDEX uq_r21_increase_order_campaign_version
  ON hhy.red_packet_orders(campaign_id, version)
  WHERE type = 'INCREASE';
CREATE INDEX ix_r21_campaign_versions_source_quote
  ON hhy.red_packet_campaign_versions(source_quote_id)
  WHERE source_quote_id IS NOT NULL;

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
      (OLD.status = 'PAUSED_BY_OWNER' AND NEW.status IN ('ACTIVE','TERMINATED_BY_PLATFORM')) OR
      (OLD.status IN ('PAUSED_BY_CONTENT_OFFLINE','PAUSED_BY_RISK') AND NEW.status IN ('ACTIVE','TERMINATED_BY_PLATFORM')) OR
      (OLD.status IN ('DRAFT','PRE_REVIEWING','PRE_REVIEW_REJECTED','PRE_REVIEW_APPROVED','WAITING_PAYMENT','PAYMENT_PROCESSING') AND NEW.status = 'TERMINATED_BY_PLATFORM')
    ) THEN
      RAISE EXCEPTION 'R21_CAMPAIGN_INVALID_TRANSITION from=% to=%', OLD.status, NEW.status
        USING ERRCODE = '23514';
    END IF;
  END IF;
  RETURN NEW;
END;
$$;

CREATE OR REPLACE FUNCTION hhy.r21_fulfill_red_packet_order()
RETURNS trigger LANGUAGE plpgsql AS $$
DECLARE
  v_relation hhy.red_packet_orders%ROWTYPE;
  v_quote hhy.red_packet_quotes%ROWTYPE;
  v_campaign hhy.red_packet_campaigns%ROWTYPE;
  v_stock hhy.red_packet_stock%ROWTYPE;
  v_remaining bigint;
  v_delta bigint;
  v_increment_principal bigint;
  v_increment_fee bigint;
  v_target_principal bigint;
  v_target_fee bigint;
BEGIN
  IF NEW.status <> 'PAID' OR OLD.status = 'PAID' OR NEW.biz_type <> 'RED_PACKET' THEN
    RETURN NEW;
  END IF;

  SELECT * INTO v_relation
  FROM hhy.red_packet_orders
  WHERE order_id = NEW.id
  FOR UPDATE;
  IF NOT FOUND OR v_relation.quote_id IS NULL THEN
    RAISE EXCEPTION 'R21_RED_PACKET_RELATION_MISSING order_id=%', NEW.id
      USING ERRCODE = '23514';
  END IF;

  SELECT * INTO v_quote
  FROM hhy.red_packet_quotes
  WHERE id = v_relation.quote_id AND campaign_id = v_relation.campaign_id
  FOR UPDATE;
  IF NOT FOUND OR v_quote.quote_type IS NULL THEN
    RAISE EXCEPTION 'R21_RED_PACKET_QUOTE_MISSING order_id=%', NEW.id
      USING ERRCODE = '23514';
  END IF;

  SELECT * INTO v_campaign
  FROM hhy.red_packet_campaigns
  WHERE id = v_relation.campaign_id AND owner_id = NEW.user_id
  FOR UPDATE;
  IF NOT FOUND THEN
    RAISE EXCEPTION 'R21_RED_PACKET_OWNER_MISMATCH order_id=%', NEW.id
      USING ERRCODE = '23514';
  END IF;
  IF NEW.amount_cent IS DISTINCT FROM v_quote.payable
     OR NEW.paid_amount_cent IS DISTINCT FROM NEW.amount_cent THEN
    RAISE EXCEPTION 'R21_RED_PACKET_AMOUNT_MISMATCH order_id=%', NEW.id
      USING ERRCODE = '23514';
  END IF;
  IF v_quote.version IS DISTINCT FROM v_relation.version THEN
    RAISE EXCEPTION 'R21_RED_PACKET_QUOTE_VERSION_MISMATCH order_id=%', NEW.id
      USING ERRCODE = '40001';
  END IF;

  IF v_relation.type = 'INITIAL' THEN
    IF v_quote.quote_type <> 'INITIAL'
       OR v_campaign.status <> 'WAITING_PAYMENT'
       OR v_campaign.version <> v_relation.version + 1
       OR v_quote.total_count IS DISTINCT FROM v_campaign.total_count
       OR v_quote.amount_per_claim_cent IS DISTINCT FROM v_campaign.amount_per_claim_cent THEN
      RAISE EXCEPTION 'R21_RED_PACKET_INITIAL_STATE_CONFLICT order_id=%', NEW.id
        USING ERRCODE = '40001';
    END IF;
    SELECT * INTO v_stock FROM hhy.red_packet_stock WHERE campaign_id=v_campaign.id FOR UPDATE;
    IF NOT FOUND OR v_stock.claimed <> 0 OR v_stock.reserved <> 0
       OR v_stock.total IS DISTINCT FROM v_quote.total_count THEN
      RAISE EXCEPTION 'R21_RED_PACKET_INITIAL_STOCK_CONFLICT order_id=%', NEW.id
        USING ERRCODE = '40001';
    END IF;
    UPDATE hhy.red_packet_campaigns
    SET status='PAYMENT_PROCESSING',
        current_amount=v_quote.amount_per_claim_cent,
        amount_per_claim_cent=v_quote.amount_per_claim_cent,
        principal_cent=v_quote.principal,
        service_fee_cent=v_quote.service_fee,
        version=version+1,updated_at=clock_timestamp()
    WHERE id=v_campaign.id AND status='WAITING_PAYMENT' AND version=v_relation.version+1;
    IF NOT FOUND THEN
      RAISE EXCEPTION 'R21_RED_PACKET_INITIAL_CAMPAIGN_CONFLICT order_id=%', NEW.id
        USING ERRCODE = '40001';
    END IF;
    UPDATE hhy.red_packet_campaigns
    SET status='ACTIVE',version=version+1,updated_at=clock_timestamp()
    WHERE id=v_campaign.id AND status='PAYMENT_PROCESSING';
    IF NOT FOUND THEN
      RAISE EXCEPTION 'R21_RED_PACKET_INITIAL_ACTIVATION_CONFLICT order_id=%', NEW.id
        USING ERRCODE = '40001';
    END IF;
  ELSIF v_relation.type = 'INCREASE' THEN
    IF v_quote.quote_type <> 'INCREASE'
       OR v_campaign.status NOT IN ('ACTIVE','PAUSED_BY_OWNER','PAUSED_BY_CONTENT_OFFLINE','PAUSED_BY_RISK')
       OR v_campaign.version <> v_relation.version
       OR v_quote.amount_per_claim_cent <= v_campaign.amount_per_claim_cent THEN
      RAISE EXCEPTION 'R21_RED_PACKET_INCREASE_STATE_CONFLICT order_id=%', NEW.id
        USING ERRCODE = '40001';
    END IF;
    SELECT * INTO v_stock FROM hhy.red_packet_stock WHERE campaign_id=v_campaign.id FOR UPDATE;
    IF NOT FOUND THEN
      RAISE EXCEPTION 'R21_RED_PACKET_STOCK_MISSING order_id=%', NEW.id
        USING ERRCODE = '23514';
    END IF;
    v_remaining := v_stock.total - v_stock.claimed - v_stock.reserved;
    IF v_remaining < 1 OR v_quote.total_count IS DISTINCT FROM v_remaining THEN
      RAISE EXCEPTION 'R21_RED_PACKET_INCREASE_REMAINING_CHANGED order_id=%', NEW.id
        USING ERRCODE = '40001';
    END IF;
    v_delta := v_quote.amount_per_claim_cent - v_campaign.amount_per_claim_cent;
    v_increment_principal := v_remaining * v_delta;
    v_increment_fee := floor(v_increment_principal * 500 / 10000);
    IF v_quote.principal IS DISTINCT FROM v_increment_principal
       OR v_quote.service_fee IS DISTINCT FROM v_increment_fee
       OR v_quote.payable IS DISTINCT FROM v_increment_principal + v_increment_fee THEN
      RAISE EXCEPTION 'R21_RED_PACKET_INCREASE_QUOTE_CHANGED order_id=%', NEW.id
        USING ERRCODE = '40001';
    END IF;
    v_target_principal := COALESCE(v_campaign.principal_cent, 0) + v_increment_principal;
    v_target_fee := COALESCE(v_campaign.service_fee_cent, 0) + v_increment_fee;
    UPDATE hhy.red_packet_campaigns
    SET current_amount=v_quote.amount_per_claim_cent,
        amount_per_claim_cent=v_quote.amount_per_claim_cent,
        principal_cent=v_target_principal,
        service_fee_cent=v_target_fee,
        version=version+1,updated_at=clock_timestamp()
    WHERE id=v_campaign.id AND version=v_relation.version
      AND status IN ('ACTIVE','PAUSED_BY_OWNER','PAUSED_BY_CONTENT_OFFLINE','PAUSED_BY_RISK');
    IF NOT FOUND THEN
      RAISE EXCEPTION 'R21_RED_PACKET_INCREASE_CAMPAIGN_CONFLICT order_id=%', NEW.id
        USING ERRCODE = '40001';
    END IF;
    INSERT INTO hhy.red_packet_campaign_versions(
      campaign_id,version_no,amount,total_count,amount_per_claim_cent,
      targeting_json,request_hash,change_kind,source_quote_id)
    VALUES (v_campaign.id,(v_relation.version+1)::text,v_target_principal,
            v_campaign.total_count,v_quote.amount_per_claim_cent,
            v_campaign.targeting_json,v_quote.request_hash,'INCREASE',v_quote.id);
  ELSE
    RAISE EXCEPTION 'R21_RED_PACKET_ORDER_TYPE_INVALID order_id=%', NEW.id
      USING ERRCODE = '23514';
  END IF;

  UPDATE hhy.orders SET status='FULFILLING',version=version+1 WHERE id=NEW.id;
  UPDATE hhy.orders SET status='COMPLETED',version=version+1 WHERE id=NEW.id;
  RETURN NEW;
END;
$$;

DROP TRIGGER IF EXISTS trg_r21_red_packet_order_fulfillment ON hhy.orders;
CREATE TRIGGER trg_r21_red_packet_order_fulfillment
AFTER UPDATE OF status ON hhy.orders
FOR EACH ROW EXECUTE FUNCTION hhy.r21_fulfill_red_packet_order();

UPDATE hhy.system_configs
SET value_json = 'true'::jsonb
WHERE key = 'red_packet.allow_raise_unit_amount' AND scope = 'GLOBAL';

COMMENT ON TABLE hhy.red_packet_campaigns IS 'R21 红包活动生命周期与加价事实';
COMMENT ON TABLE hhy.red_packet_quotes IS 'R21 初始及加价报价事实';
