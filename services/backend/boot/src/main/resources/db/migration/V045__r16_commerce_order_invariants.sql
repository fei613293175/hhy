-- R16 product, SKU, quote snapshot, and unified order invariants.
SET search_path TO hhy, public;

CREATE OR REPLACE FUNCTION hhy.r16_benefits_valid(p_benefits jsonb)
RETURNS boolean
LANGUAGE sql
IMMUTABLE
AS $$
  SELECT COALESCE(
    jsonb_typeof(p_benefits) = 'array'
    AND jsonb_array_length(p_benefits) <= 100
    AND NOT EXISTS (
      SELECT 1
      FROM jsonb_array_elements(p_benefits) AS benefit(value)
      WHERE jsonb_typeof(benefit.value) <> 'object'
         OR NOT benefit.value ?& ARRAY['benefitCode', 'name', 'value']
         OR benefit.value - ARRAY['benefitCode', 'name', 'value', 'unit']::text[] <> '{}'::jsonb
         OR jsonb_typeof(benefit.value->'benefitCode') <> 'string'
         OR char_length(btrim(benefit.value->>'benefitCode')) NOT BETWEEN 1 AND 64
         OR jsonb_typeof(benefit.value->'name') <> 'string'
         OR char_length(btrim(benefit.value->>'name')) NOT BETWEEN 1 AND 120
         OR (
           benefit.value ? 'unit'
           AND (
             jsonb_typeof(benefit.value->'unit') <> 'string'
             OR char_length(benefit.value->>'unit') > 32
           )
         )
    ),
    false
  );
$$;

CREATE OR REPLACE FUNCTION hhy.r16_rule_versions_valid(p_rule_versions jsonb)
RETURNS boolean
LANGUAGE sql
IMMUTABLE
AS $$
  SELECT COALESCE(
    jsonb_typeof(p_rule_versions) = 'array'
    AND jsonb_array_length(p_rule_versions) <= 100
    AND NOT EXISTS (
      SELECT 1
      FROM jsonb_array_elements(p_rule_versions) AS rule(value)
      WHERE jsonb_typeof(rule.value) <> 'string'
         OR char_length(btrim(rule.value #>> '{}')) NOT BETWEEN 1 AND 64
    ),
    false
  );
$$;

-- Abort dirty upgrades before adding any column or constraint. Every check is
-- deliberately based on existing facts; V045 never invents display content.
DO $$
BEGIN
  IF EXISTS (
    SELECT 1 FROM hhy.products
    WHERE name IS NULL OR btrim(name) = '' OR btrim(type) = '' OR btrim(status) = ''
  ) THEN
    RAISE EXCEPTION 'R16_DIRTY_UPGRADE_PRODUCT_REQUIRED_FACT_MISSING'
      USING ERRCODE = '23514';
  END IF;

  IF EXISTS (
    SELECT 1 FROM hhy.product_skus
    WHERE price_cent IS NULL
       OR price_cent < 0
       OR attributes_json IS NULL
       OR jsonb_typeof(attributes_json) <> 'object'
       OR jsonb_typeof(attributes_json->'name') <> 'string'
       OR btrim(attributes_json->>'name') = ''
       OR NOT hhy.r16_benefits_valid(attributes_json->'benefits')
  ) THEN
    RAISE EXCEPTION 'R16_DIRTY_UPGRADE_SKU_EXPLICIT_FACT_MISSING'
      USING ERRCODE = '23514';
  END IF;

  IF EXISTS (
    SELECT 1
    FROM hhy.sku_commission_policies
    GROUP BY sku_id
    HAVING count(*) > 1
       OR max(COALESCE(level1_bps, 0) + COALESCE(level2_bps, 0)) > 10000
  ) THEN
    RAISE EXCEPTION 'R16_DIRTY_UPGRADE_COMMISSION_POLICY_INVALID'
      USING ERRCODE = '23514';
  END IF;

  IF EXISTS (
    SELECT 1 FROM hhy.coupons
    WHERE btrim(code) = '' OR (value IS NOT NULL AND value < 0)
  ) OR EXISTS (
    SELECT 1 FROM hhy.coupons GROUP BY code HAVING count(*) > 1
  ) THEN
    RAISE EXCEPTION 'R16_DIRTY_UPGRADE_COUPON_INVALID'
      USING ERRCODE = '23514';
  END IF;

  IF EXISTS (
    SELECT 1 FROM hhy.user_coupons WHERE btrim(status) = ''
  ) THEN
    RAISE EXCEPTION 'R16_DIRTY_UPGRADE_USER_COUPON_STATUS_MISSING'
      USING ERRCODE = '23514';
  END IF;

  IF EXISTS (
    SELECT 1 FROM hhy.order_items
    WHERE quantity IS NULL
       OR quantity < 1
       OR unit_price IS NULL
       OR unit_price < 0
       OR snapshot_json IS NULL
       OR jsonb_typeof(snapshot_json) <> 'object'
       OR jsonb_typeof(snapshot_json->'name') <> 'string'
       OR btrim(snapshot_json->>'name') = ''
  ) THEN
    RAISE EXCEPTION 'R16_DIRTY_UPGRADE_ORDER_ITEM_FACT_MISSING'
      USING ERRCODE = '23514';
  END IF;

  IF EXISTS (
    SELECT 1 FROM hhy.order_price_snapshots
    WHERE original < 0
       OR discount IS NULL
       OR discount < 0
       OR service_fee IS NULL
       OR service_fee < 0
       OR payable IS NULL
       OR payable < 0
       OR original - discount + service_fee <> payable
  ) THEN
    RAISE EXCEPTION 'R16_DIRTY_UPGRADE_PRICE_SNAPSHOT_INVALID'
      USING ERRCODE = '23514';
  END IF;

  IF EXISTS (
    SELECT 1
    FROM hhy.orders orders
    LEFT JOIN hhy.order_price_snapshots snapshot ON snapshot.order_id = orders.id
    GROUP BY orders.id, orders.amount_cent
    HAVING orders.amount_cent IS NULL
       OR count(snapshot.id) <> 1
       OR max(snapshot.payable) IS DISTINCT FROM orders.amount_cent
  ) THEN
    RAISE EXCEPTION 'R16_DIRTY_UPGRADE_ORDER_QUOTE_MISSING'
      USING ERRCODE = '23514';
  END IF;
END;
$$;

ALTER TABLE hhy.products
  ADD COLUMN product_code varchar(64),
  ADD COLUMN description varchar(2000),
  ADD COLUMN version bigint DEFAULT 0 NOT NULL;

UPDATE hhy.products
SET product_code = 'LEGACY-' || id::text;

ALTER TABLE hhy.products
  ALTER COLUMN product_code SET NOT NULL,
  ALTER COLUMN name SET NOT NULL,
  ADD CONSTRAINT uq_r16_products_product_code UNIQUE (product_code),
  ADD CONSTRAINT ck_r16_products_product_code CHECK (btrim(product_code) <> ''),
  ADD CONSTRAINT ck_r16_products_required_text CHECK (
    btrim(name) <> '' AND btrim(type) <> '' AND btrim(status) <> ''
  ),
  ADD CONSTRAINT ck_r16_products_version CHECK (version >= 0);

ALTER TABLE hhy.product_skus
  ADD COLUMN name varchar(255),
  ADD COLUMN member_price_cent bigint,
  ADD COLUMN duration_days integer,
  ADD COLUMN benefits_json jsonb,
  ADD COLUMN sale_starts_at timestamptz,
  ADD COLUMN sale_ends_at timestamptz,
  ADD COLUMN version bigint DEFAULT 0 NOT NULL;

UPDATE hhy.product_skus
SET name = attributes_json->>'name',
    benefits_json = attributes_json->'benefits',
    duration_days = duration;

ALTER TABLE hhy.product_skus
  ALTER COLUMN price_cent SET NOT NULL,
  ALTER COLUMN name SET NOT NULL,
  ALTER COLUMN benefits_json SET NOT NULL,
  ADD CONSTRAINT ck_r16_product_skus_name CHECK (btrim(name) <> ''),
  ADD CONSTRAINT ck_r16_product_skus_member_price CHECK (
    member_price_cent IS NULL OR member_price_cent >= 0
  ),
  ADD CONSTRAINT ck_r16_product_skus_duration CHECK (
    duration_days IS NULL OR duration_days > 0
  ),
  ADD CONSTRAINT ck_r16_product_skus_benefits CHECK (
    hhy.r16_benefits_valid(benefits_json)
  ),
  ADD CONSTRAINT ck_r16_product_skus_sale_window CHECK (
    sale_starts_at IS NULL OR sale_ends_at IS NULL OR sale_starts_at < sale_ends_at
  ),
  ADD CONSTRAINT ck_r16_product_skus_version CHECK (version >= 0);

ALTER TABLE hhy.sku_commission_policies
  ADD CONSTRAINT uq_r16_sku_commission_policy_sku UNIQUE (sku_id),
  ADD CONSTRAINT ck_r16_sku_commission_policy_total CHECK (
    COALESCE(level1_bps, 0) + COALESCE(level2_bps, 0) <= 10000
  ),
  ADD CONSTRAINT ck_r16_sku_commission_policy_version CHECK (version >= 0);

ALTER TABLE hhy.coupons
  ADD CONSTRAINT uq_r16_coupons_code UNIQUE (code),
  ADD CONSTRAINT ck_r16_coupons_code CHECK (btrim(code) <> ''),
  ADD CONSTRAINT ck_r16_coupons_value CHECK (value IS NULL OR value >= 0);

ALTER TABLE hhy.user_coupons
  ADD CONSTRAINT ck_r16_user_coupons_status CHECK (btrim(status) <> '');

ALTER TABLE hhy.orders
  ADD COLUMN currency varchar(3) DEFAULT 'CNY' NOT NULL,
  ADD COLUMN paid_amount_cent bigint,
  ADD COLUMN paid_at timestamptz,
  ADD COLUMN no_refund_confirmed boolean DEFAULT false NOT NULL,
  ADD COLUMN no_refund_agreement_version varchar(64),
  ADD COLUMN no_refund_confirmed_at timestamptz,
  ADD COLUMN idempotency_key varchar(128),
  ADD COLUMN request_hash varchar(64),
  ADD COLUMN legacy_without_idempotency boolean DEFAULT false NOT NULL;

UPDATE hhy.orders
SET legacy_without_idempotency = true;

ALTER TABLE hhy.orders
  ALTER COLUMN amount_cent SET NOT NULL,
  ADD CONSTRAINT ck_r16_orders_currency CHECK (currency ~ '^[A-Z]{3}$'),
  ADD CONSTRAINT ck_r16_orders_paid_evidence CHECK (
    paid_amount_cent IS NULL OR paid_amount_cent >= 0
  ),
  ADD CONSTRAINT ck_r16_orders_paid_pair CHECK (
    (paid_amount_cent IS NULL) = (paid_at IS NULL)
  ),
  ADD CONSTRAINT ck_r16_orders_no_refund_evidence CHECK (
    (
      no_refund_confirmed = false
      AND no_refund_agreement_version IS NULL
      AND no_refund_confirmed_at IS NULL
    ) OR (
      no_refund_confirmed = true
      AND no_refund_agreement_version IS NOT NULL
      AND btrim(no_refund_agreement_version) <> ''
      AND no_refund_confirmed_at IS NOT NULL
    )
  ),
  ADD CONSTRAINT ck_r16_orders_creation_idempotency CHECK (
    (
      legacy_without_idempotency = true
      AND idempotency_key IS NULL
      AND request_hash IS NULL
    ) OR (
      legacy_without_idempotency = false
      AND biz_type IS NOT NULL
      AND btrim(biz_type) <> ''
      AND char_length(idempotency_key) BETWEEN 16 AND 128
      AND request_hash ~ '^[0-9a-f]{64}$'
    )
  ),
  ADD CONSTRAINT ck_r16_orders_payment_requires_no_refund CHECK (
    status NOT IN (
      'PAYMENT_PROCESSING', 'PAID', 'FULFILLING', 'COMPLETED',
      'PAYMENT_FAILED', 'CHANNEL_REVERSAL'
    ) OR no_refund_confirmed
  ) NOT VALID;

CREATE UNIQUE INDEX uq_r16_orders_creation_idempotency
  ON hhy.orders(user_id, biz_type, idempotency_key)
  WHERE legacy_without_idempotency = false;

CREATE INDEX ix_r16_orders_paid_at ON hhy.orders(paid_at)
  WHERE paid_at IS NOT NULL;

ALTER TABLE hhy.order_items
  ADD COLUMN item_name varchar(255),
  ADD COLUMN subtotal_amount_cent bigint;

UPDATE hhy.order_items
SET item_name = snapshot_json->>'name',
    subtotal_amount_cent = quantity::bigint * unit_price;

ALTER TABLE hhy.order_items
  ALTER COLUMN quantity SET NOT NULL,
  ALTER COLUMN unit_price SET NOT NULL,
  ALTER COLUMN item_name SET NOT NULL,
  ALTER COLUMN subtotal_amount_cent SET NOT NULL,
  ADD CONSTRAINT ck_r16_order_items_name CHECK (btrim(item_name) <> ''),
  ADD CONSTRAINT ck_r16_order_items_quantity CHECK (quantity >= 1),
  ADD CONSTRAINT ck_r16_order_items_unit_price CHECK (unit_price >= 0),
  ADD CONSTRAINT ck_r16_order_items_subtotal CHECK (
    subtotal_amount_cent >= 0
    AND subtotal_amount_cent = quantity::bigint * unit_price
  );

ALTER TABLE hhy.order_price_snapshots
  ADD COLUMN rule_versions_json jsonb;

UPDATE hhy.order_price_snapshots
SET rule_versions_json = CASE
  WHEN rule_versions IS NULL OR btrim(rule_versions) = '' THEN '[]'::jsonb
  ELSE jsonb_build_array(rule_versions)
END;

ALTER TABLE hhy.order_price_snapshots
  ALTER COLUMN discount SET NOT NULL,
  ALTER COLUMN service_fee SET NOT NULL,
  ALTER COLUMN payable SET NOT NULL,
  ALTER COLUMN rule_versions_json SET NOT NULL,
  ADD CONSTRAINT ck_r16_order_price_snapshot_amounts CHECK (
    original >= 0
    AND discount >= 0
    AND service_fee >= 0
    AND payable >= 0
    AND original - discount + service_fee = payable
  ),
  ADD CONSTRAINT ck_r16_order_price_snapshot_rules CHECK (
    hhy.r16_rule_versions_valid(rule_versions_json)
  );

CREATE OR REPLACE FUNCTION hhy.guard_r16_order_mutation()
RETURNS trigger
LANGUAGE plpgsql
AS $$
BEGIN
  IF TG_OP = 'INSERT' THEN
    IF NEW.legacy_without_idempotency THEN
      RAISE EXCEPTION 'R16_ORDER_LEGACY_MARKER_FORBIDDEN'
        USING ERRCODE = '23514';
    END IF;
    IF NEW.status <> 'PENDING_PAYMENT' THEN
      RAISE EXCEPTION 'R16_ORDER_INITIAL_STATUS_INVALID status=%', NEW.status
        USING ERRCODE = '23514';
    END IF;
    RETURN NEW;
  END IF;

  IF NEW.legacy_without_idempotency IS DISTINCT FROM OLD.legacy_without_idempotency
     OR NEW.order_no IS DISTINCT FROM OLD.order_no
     OR NEW.user_id IS DISTINCT FROM OLD.user_id
     OR NEW.biz_type IS DISTINCT FROM OLD.biz_type
     OR NEW.biz_id IS DISTINCT FROM OLD.biz_id
     OR NEW.amount_cent IS DISTINCT FROM OLD.amount_cent
     OR NEW.currency IS DISTINCT FROM OLD.currency
     OR NEW.idempotency_key IS DISTINCT FROM OLD.idempotency_key
     OR NEW.request_hash IS DISTINCT FROM OLD.request_hash
     OR NEW.created_at IS DISTINCT FROM OLD.created_at THEN
    RAISE EXCEPTION 'R16_ORDER_CREATION_FACT_IMMUTABLE order_id=%', OLD.id
      USING ERRCODE = '55000';
  END IF;

  IF OLD.no_refund_confirmed AND (
    NOT NEW.no_refund_confirmed
    OR NEW.no_refund_agreement_version IS DISTINCT FROM OLD.no_refund_agreement_version
    OR NEW.no_refund_confirmed_at IS DISTINCT FROM OLD.no_refund_confirmed_at
  ) THEN
    RAISE EXCEPTION 'R16_ORDER_NO_REFUND_EVIDENCE_IMMUTABLE order_id=%', OLD.id
      USING ERRCODE = '55000';
  END IF;

  IF NEW.status IS NOT DISTINCT FROM OLD.status THEN
    RETURN NEW;
  END IF;

  IF NOT (
       (OLD.status = 'PENDING_PAYMENT' AND NEW.status IN ('PAYMENT_PROCESSING', 'CLOSED'))
    OR (OLD.status = 'PAYMENT_PROCESSING' AND NEW.status IN ('PAID', 'PAYMENT_FAILED'))
    OR (OLD.status = 'PAID' AND NEW.status IN ('FULFILLING', 'CHANNEL_REVERSAL'))
    OR (OLD.status = 'FULFILLING' AND NEW.status = 'COMPLETED')
  ) THEN
    RAISE EXCEPTION 'R16_ORDER_INVALID_TRANSITION order_id=% from=% to=%',
      OLD.id, OLD.status, NEW.status USING ERRCODE = '23514';
  END IF;

  IF NEW.status = 'PAYMENT_PROCESSING' AND NOT NEW.no_refund_confirmed THEN
    RAISE EXCEPTION 'R16_ORDER_NO_REFUND_EVIDENCE_REQUIRED order_id=%', OLD.id
      USING ERRCODE = '23514';
  END IF;
  RETURN NEW;
END;
$$;

CREATE TRIGGER trg_r16_orders_guard
BEFORE INSERT OR UPDATE ON hhy.orders
FOR EACH ROW EXECUTE FUNCTION hhy.guard_r16_order_mutation();

CREATE TRIGGER trg_r16_orders_status_history
AFTER UPDATE OF status ON hhy.orders
FOR EACH ROW EXECUTE FUNCTION hhy.record_platform_status_history();

CREATE TRIGGER trg_r16_order_items_immutable
BEFORE UPDATE OR DELETE ON hhy.order_items
FOR EACH ROW EXECUTE FUNCTION hhy.prevent_immutable_mutation();

CREATE TRIGGER trg_r16_order_price_snapshots_immutable
BEFORE UPDATE OR DELETE ON hhy.order_price_snapshots
FOR EACH ROW EXECUTE FUNCTION hhy.prevent_immutable_mutation();

CREATE OR REPLACE FUNCTION hhy.assert_r16_order_quote(p_order_id bigint)
RETURNS void
LANGUAGE plpgsql
AS $$
DECLARE
  v_amount bigint;
  v_snapshot_count integer;
  v_payable bigint;
BEGIN
  SELECT amount_cent INTO v_amount
  FROM hhy.orders
  WHERE id = p_order_id;
  IF NOT FOUND THEN
    RETURN;
  END IF;

  SELECT count(*), max(payable)
  INTO v_snapshot_count, v_payable
  FROM hhy.order_price_snapshots
  WHERE order_id = p_order_id;

  IF v_snapshot_count <> 1 OR v_payable IS DISTINCT FROM v_amount THEN
    RAISE EXCEPTION 'R16_ORDER_QUOTE_MISMATCH order_id=% snapshots=% amount=% payable=%',
      p_order_id, v_snapshot_count, v_amount, v_payable USING ERRCODE = '23514';
  END IF;
END;
$$;

CREATE OR REPLACE FUNCTION hhy.check_r16_order_quote()
RETURNS trigger
LANGUAGE plpgsql
AS $$
DECLARE
  v_order_id bigint;
BEGIN
  IF TG_TABLE_NAME = 'orders' THEN
    v_order_id := NEW.id;
  ELSIF TG_OP = 'DELETE' THEN
    v_order_id := OLD.order_id;
  ELSE
    v_order_id := NEW.order_id;
  END IF;
  PERFORM hhy.assert_r16_order_quote(v_order_id);
  RETURN NULL;
END;
$$;

CREATE CONSTRAINT TRIGGER trg_r16_orders_quote_commit
AFTER INSERT OR UPDATE ON hhy.orders
DEFERRABLE INITIALLY DEFERRED
FOR EACH ROW EXECUTE FUNCTION hhy.check_r16_order_quote();

CREATE CONSTRAINT TRIGGER trg_r16_order_price_snapshot_commit
AFTER INSERT OR UPDATE OR DELETE ON hhy.order_price_snapshots
DEFERRABLE INITIALLY DEFERRED
FOR EACH ROW EXECUTE FUNCTION hhy.check_r16_order_quote();

COMMENT ON COLUMN hhy.orders.legacy_without_idempotency IS
  'Migration-only fact for orders created before R16 creation idempotency; new inserts are rejected';
COMMENT ON COLUMN hhy.orders.request_hash IS
  'Lowercase SHA-256 of the canonical order creation request';
COMMENT ON COLUMN hhy.order_price_snapshots.rule_versions_json IS
  'Exact ordered rule version identifiers used by the server-side quote';
