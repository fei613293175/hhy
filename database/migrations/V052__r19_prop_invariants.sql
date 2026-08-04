-- R19 upgrades the prop tables that were introduced as permissive baseline
-- tables in V004. This migration never recreates those tables: existing rows
-- with facts that cannot be derived safely are rejected before constraints are
-- tightened.
SET search_path TO hhy, public;

DO $$
DECLARE
  required_table text;
BEGIN
  FOREACH required_table IN ARRAY ARRAY[
    'prop_products', 'prop_skus', 'user_props',
    'content_exposure_entitlements', 'content_refresh_records',
    'headline_slots', 'headline_slot_bookings', 'color_themes',
    'prop_execution_logs'
  ] LOOP
    IF to_regclass('hhy.' || required_table) IS NULL THEN
      RAISE EXCEPTION 'R19_BASELINE_TABLE_MISSING table=%', required_table
        USING ERRCODE = '42P01';
    END IF;
  END LOOP;
END;
$$;

-- Definitions and sellable prop SKU bindings.
ALTER TABLE hhy.prop_products
  ADD COLUMN IF NOT EXISTS status varchar(64) DEFAULT 'ACTIVE' NOT NULL,
  ADD COLUMN IF NOT EXISTS version bigint DEFAULT 0 NOT NULL;

DO $$
BEGIN
  IF EXISTS (
    SELECT 1 FROM hhy.prop_products
    WHERE type NOT IN ('REFRESH', 'TOP', 'HEADLINE', 'COLOR')
       OR name IS NULL OR btrim(name) = ''
       OR duration_seconds IS NULL
       OR duration_seconds !~ '^[0-9]+$'
       OR duration_seconds::numeric <= 0
       OR execution_type IS NULL OR btrim(execution_type) = ''
       OR status NOT IN ('ACTIVE', 'INACTIVE')
  ) THEN
    RAISE EXCEPTION 'R19_PROP_PRODUCT_LEGACY_FACTS_INVALID' USING ERRCODE = '23514';
  END IF;
END;
$$;

ALTER TABLE hhy.prop_products
  ALTER COLUMN name SET NOT NULL,
  ALTER COLUMN duration_seconds SET NOT NULL,
  ALTER COLUMN execution_type SET NOT NULL,
  ADD CONSTRAINT ck_r19_prop_product_type CHECK (type IN ('REFRESH', 'TOP', 'HEADLINE', 'COLOR')),
  ADD CONSTRAINT ck_r19_prop_product_name CHECK (btrim(name) <> ''),
  ADD CONSTRAINT ck_r19_prop_product_duration CHECK (
    duration_seconds ~ '^[0-9]+$' AND duration_seconds::numeric > 0
  ),
  ADD CONSTRAINT ck_r19_prop_product_execution CHECK (btrim(execution_type) <> ''),
  ADD CONSTRAINT ck_r19_prop_product_status CHECK (status IN ('ACTIVE', 'INACTIVE')),
  ADD CONSTRAINT ck_r19_prop_product_version CHECK (version >= 0);

ALTER TABLE hhy.prop_skus
  ADD COLUMN IF NOT EXISTS status varchar(64) DEFAULT 'ACTIVE' NOT NULL,
  ADD COLUMN IF NOT EXISTS version bigint DEFAULT 0 NOT NULL;
UPDATE hhy.prop_skus SET scope_json = '{}'::jsonb WHERE scope_json IS NULL;

DO $$
BEGIN
  IF EXISTS (SELECT 1 FROM hhy.prop_skus WHERE product_sku_id IS NULL)
     OR EXISTS (SELECT 1 FROM hhy.prop_skus WHERE scope_json IS NULL OR jsonb_typeof(scope_json) <> 'object')
     OR EXISTS (SELECT 1 FROM hhy.prop_skus WHERE status NOT IN ('ACTIVE', 'INACTIVE')) THEN
    RAISE EXCEPTION 'R19_PROP_SKU_LEGACY_FACTS_INVALID' USING ERRCODE = '23514';
  END IF;
END;
$$;

ALTER TABLE hhy.prop_skus
  ALTER COLUMN product_sku_id SET NOT NULL,
  ALTER COLUMN scope_json SET NOT NULL,
  ADD CONSTRAINT fk_r19_prop_skus_product FOREIGN KEY (prop_id)
    REFERENCES hhy.prop_products(id) ON DELETE RESTRICT,
  ADD CONSTRAINT fk_r19_prop_skus_product_sku FOREIGN KEY (product_sku_id)
    REFERENCES hhy.product_skus(id) ON DELETE RESTRICT,
  ADD CONSTRAINT uq_r19_prop_sku_binding UNIQUE (prop_id, product_sku_id),
  ADD CONSTRAINT ck_r19_prop_sku_scope CHECK (jsonb_typeof(scope_json) = 'object'),
  ADD CONSTRAINT ck_r19_prop_sku_status CHECK (status IN ('ACTIVE', 'INACTIVE')),
  ADD CONSTRAINT ck_r19_prop_sku_version CHECK (version >= 0);

-- User inventory. A non-empty V004 inventory without a prop SKU is not
-- guessable, so dirty upgrades stop before the NOT NULL/FK boundary.
ALTER TABLE hhy.user_props
  ADD COLUMN IF NOT EXISTS quantity bigint DEFAULT 1 NOT NULL,
  ADD COLUMN IF NOT EXISTS version bigint DEFAULT 0 NOT NULL;

DO $$
BEGIN
  IF EXISTS (
    SELECT 1 FROM hhy.user_props
    WHERE prop_sku_id IS NULL
       OR status NOT IN ('AVAILABLE', 'RESERVED', 'CONSUMED', 'EXPIRED')
       OR quantity < 0
       OR (expires_at IS NOT NULL AND expires_at <= created_at)
  ) THEN
    RAISE EXCEPTION 'R19_USER_PROP_LEGACY_FACTS_INVALID' USING ERRCODE = '23514';
  END IF;
END;
$$;

ALTER TABLE hhy.user_props
  ALTER COLUMN prop_sku_id SET NOT NULL,
  ADD CONSTRAINT fk_r19_user_props_user FOREIGN KEY (user_id)
    REFERENCES hhy.users(id) ON DELETE RESTRICT,
  ADD CONSTRAINT fk_r19_user_props_sku FOREIGN KEY (prop_sku_id)
    REFERENCES hhy.prop_skus(id) ON DELETE RESTRICT,
  ADD CONSTRAINT fk_r19_user_props_order FOREIGN KEY (acquired_order_id)
    REFERENCES hhy.orders(id) ON DELETE RESTRICT,
  ADD CONSTRAINT ck_r19_user_prop_quantity CHECK (quantity >= 0),
  ADD CONSTRAINT ck_r19_user_prop_status CHECK (
    status IN ('AVAILABLE', 'RESERVED', 'CONSUMED', 'EXPIRED')
  ),
  ADD CONSTRAINT ck_r19_user_prop_expiry CHECK (expires_at IS NULL OR expires_at > created_at),
  ADD CONSTRAINT ck_r19_user_prop_version CHECK (version >= 0);

-- Exposure entitlements. V004 had no owner inventory link; only an empty or
-- fully explicit legacy table can be upgraded without inventing ownership.
ALTER TABLE hhy.content_exposure_entitlements
  ADD COLUMN IF NOT EXISTS user_prop_id bigint;

DO $$
BEGIN
  IF EXISTS (
    SELECT 1 FROM hhy.content_exposure_entitlements
    WHERE user_prop_id IS NULL OR type NOT IN ('TOP', 'HEADLINE', 'COLOR')
       OR starts_at IS NULL OR ends_at IS NULL OR ends_at <= starts_at
       OR status NOT IN ('SCHEDULED', 'ACTIVE', 'EXPIRED', 'REVOKED', 'CANCELLED')
  ) THEN
    RAISE EXCEPTION 'R19_EXPOSURE_LEGACY_FACTS_INVALID' USING ERRCODE = '23514';
  END IF;
END;
$$;

ALTER TABLE hhy.content_exposure_entitlements
  ALTER COLUMN user_prop_id SET NOT NULL,
  ALTER COLUMN type SET NOT NULL,
  ALTER COLUMN starts_at SET NOT NULL,
  ALTER COLUMN ends_at SET NOT NULL,
  ADD CONSTRAINT fk_r19_exposure_content FOREIGN KEY (content_id)
    REFERENCES hhy.content_posts(id) ON DELETE RESTRICT,
  ADD CONSTRAINT fk_r19_exposure_user_prop FOREIGN KEY (user_prop_id)
    REFERENCES hhy.user_props(id) ON DELETE RESTRICT,
  ADD CONSTRAINT ck_r19_exposure_type CHECK (type IN ('TOP', 'HEADLINE', 'COLOR')),
  ADD CONSTRAINT ck_r19_exposure_window CHECK (ends_at > starts_at),
  ADD CONSTRAINT ck_r19_exposure_status CHECK (
    status IN ('SCHEDULED', 'ACTIVE', 'EXPIRED', 'REVOKED', 'CANCELLED')
  ),
  ADD CONSTRAINT ck_r19_exposure_version CHECK (version >= 0);

-- Refresh facts and execution logs were permissive in V004. Their idempotency
-- material cannot be reconstructed for existing rows, so upgrades fail rather
-- than assigning synthetic request keys or hashes.
DO $$
BEGIN
  IF EXISTS (SELECT 1 FROM hhy.content_refresh_records)
     OR EXISTS (SELECT 1 FROM hhy.prop_execution_logs) THEN
    RAISE EXCEPTION 'R19_IMPLICIT_IDEMPOTENCY_FACTS_REQUIRED'
      USING ERRCODE = '23514';
  END IF;
END;
$$;

ALTER TABLE hhy.content_refresh_records
  ADD COLUMN IF NOT EXISTS idempotency_key varchar(128),
  ADD COLUMN IF NOT EXISTS request_hash varchar(64);

ALTER TABLE hhy.content_refresh_records
  ALTER COLUMN user_prop_id SET NOT NULL,
  ALTER COLUMN layers SET NOT NULL,
  ALTER COLUMN executed_at SET NOT NULL,
  ALTER COLUMN idempotency_key SET NOT NULL,
  ALTER COLUMN request_hash SET NOT NULL,
  ADD CONSTRAINT fk_r19_refresh_content FOREIGN KEY (content_id)
    REFERENCES hhy.content_posts(id) ON DELETE RESTRICT,
  ADD CONSTRAINT fk_r19_refresh_user_prop FOREIGN KEY (user_prop_id)
    REFERENCES hhy.user_props(id) ON DELETE RESTRICT,
  ADD CONSTRAINT uq_r19_refresh_idempotency UNIQUE (user_prop_id, idempotency_key),
  ADD CONSTRAINT ck_r19_refresh_layers CHECK (btrim(layers) <> ''),
  ADD CONSTRAINT ck_r19_refresh_idempotency_key CHECK (char_length(idempotency_key) BETWEEN 16 AND 128),
  ADD CONSTRAINT ck_r19_refresh_request_hash CHECK (request_hash ~ '^[0-9a-f]{64}$');

DO $$
BEGIN
  IF EXISTS (
    SELECT 1 FROM hhy.headline_slots
    WHERE capacity IS NULL OR btrim(capacity) = '' OR btrim(capacity) !~ '^[0-9]+$'
  ) THEN
    RAISE EXCEPTION 'R19_HEADLINE_SLOT_LEGACY_CAPACITY_INVALID' USING ERRCODE = '23514';
  END IF;
END;
$$;

ALTER TABLE hhy.headline_slots
  ALTER COLUMN capacity TYPE integer USING NULLIF(btrim(capacity), '')::integer,
  ADD COLUMN IF NOT EXISTS version bigint DEFAULT 0 NOT NULL;

DO $$
BEGIN
  IF EXISTS (
    SELECT 1 FROM hhy.headline_slots
    WHERE page_code IS NULL OR btrim(page_code) = ''
       OR slot_code IS NULL OR btrim(slot_code) = ''
       OR capacity IS NULL OR capacity < 1
       OR status NOT IN ('ACTIVE', 'INACTIVE')
  ) THEN
    RAISE EXCEPTION 'R19_HEADLINE_SLOT_LEGACY_FACTS_INVALID' USING ERRCODE = '23514';
  END IF;
END;
$$;

ALTER TABLE hhy.headline_slots
  ALTER COLUMN slot_code SET NOT NULL,
  ALTER COLUMN capacity SET DEFAULT 1,
  ALTER COLUMN capacity SET NOT NULL,
  ADD CONSTRAINT uq_r19_headline_slot_code UNIQUE (page_code, slot_code),
  ADD CONSTRAINT ck_r19_headline_slot_identity CHECK (
    btrim(page_code) <> '' AND btrim(slot_code) <> ''
  ),
  ADD CONSTRAINT ck_r19_headline_slot_capacity CHECK (capacity > 0),
  ADD CONSTRAINT ck_r19_headline_slot_status CHECK (status IN ('ACTIVE', 'INACTIVE')),
  ADD CONSTRAINT ck_r19_headline_slot_version CHECK (version >= 0);

ALTER TABLE hhy.headline_slot_bookings
  ADD COLUMN IF NOT EXISTS status varchar(64) DEFAULT 'BOOKED' NOT NULL,
  ADD COLUMN IF NOT EXISTS version bigint DEFAULT 0 NOT NULL;

DO $$
BEGIN
  IF EXISTS (
    SELECT 1 FROM hhy.headline_slot_bookings
    WHERE content_id IS NULL OR entitlement_id IS NULL OR starts_at IS NULL OR ends_at IS NULL
       OR ends_at <= starts_at
       OR status NOT IN ('BOOKED', 'ACTIVE', 'EXPIRED', 'CANCELLED')
  ) THEN
    RAISE EXCEPTION 'R19_HEADLINE_BOOKING_LEGACY_FACTS_INVALID' USING ERRCODE = '23514';
  END IF;
END;
$$;

ALTER TABLE hhy.headline_slot_bookings
  ALTER COLUMN content_id SET NOT NULL,
  ALTER COLUMN entitlement_id SET NOT NULL,
  ALTER COLUMN starts_at SET NOT NULL,
  ALTER COLUMN ends_at SET NOT NULL,
  ADD CONSTRAINT fk_r19_booking_slot FOREIGN KEY (slot_id)
    REFERENCES hhy.headline_slots(id) ON DELETE RESTRICT,
  ADD CONSTRAINT fk_r19_booking_content FOREIGN KEY (content_id)
    REFERENCES hhy.content_posts(id) ON DELETE RESTRICT,
  ADD CONSTRAINT fk_r19_booking_entitlement FOREIGN KEY (entitlement_id)
    REFERENCES hhy.content_exposure_entitlements(id) ON DELETE RESTRICT,
  ADD CONSTRAINT ck_r19_headline_booking_window CHECK (ends_at > starts_at),
  ADD CONSTRAINT ck_r19_headline_booking_status CHECK (
    status IN ('BOOKED', 'ACTIVE', 'EXPIRED', 'CANCELLED')
  ),
  ADD CONSTRAINT ck_r19_headline_booking_version CHECK (version >= 0);

ALTER TABLE hhy.color_themes
  ADD COLUMN IF NOT EXISTS version bigint DEFAULT 0 NOT NULL;
UPDATE hhy.color_themes SET token_json = '{}'::jsonb WHERE token_json IS NULL;
ALTER TABLE hhy.color_themes
  ALTER COLUMN token_json SET DEFAULT '{}'::jsonb,
  ALTER COLUMN token_json SET NOT NULL,
  ADD CONSTRAINT ck_r19_color_theme_code CHECK (btrim(code) <> ''),
  ADD CONSTRAINT ck_r19_color_theme_tokens CHECK (jsonb_typeof(token_json) = 'object'),
  ADD CONSTRAINT ck_r19_color_theme_version CHECK (version >= 0);

ALTER TABLE hhy.prop_execution_logs
  ADD COLUMN IF NOT EXISTS status varchar(64) DEFAULT 'SUCCEEDED' NOT NULL,
  ADD COLUMN IF NOT EXISTS operation_id varchar(128),
  ADD COLUMN IF NOT EXISTS idempotency_key varchar(128),
  ADD COLUMN IF NOT EXISTS request_hash varchar(64),
  ADD COLUMN IF NOT EXISTS version bigint DEFAULT 0 NOT NULL;

ALTER TABLE hhy.prop_execution_logs
  ALTER COLUMN operation_id SET NOT NULL,
  ALTER COLUMN idempotency_key SET NOT NULL,
  ALTER COLUMN request_hash SET NOT NULL,
  ADD CONSTRAINT fk_r19_execution_user_prop FOREIGN KEY (user_prop_id)
    REFERENCES hhy.user_props(id) ON DELETE RESTRICT,
  ADD CONSTRAINT fk_r19_execution_content FOREIGN KEY (content_id)
    REFERENCES hhy.content_posts(id) ON DELETE RESTRICT,
  ADD CONSTRAINT uq_r19_execution_idempotency UNIQUE (user_prop_id, idempotency_key),
  ADD CONSTRAINT ck_r19_execution_status CHECK (status IN ('PENDING', 'SUCCEEDED', 'FAILED', 'RETRYING')),
  ADD CONSTRAINT ck_r19_execution_operation CHECK (btrim(operation_id) <> ''),
  ADD CONSTRAINT ck_r19_execution_idempotency_key CHECK (char_length(idempotency_key) BETWEEN 16 AND 128),
  ADD CONSTRAINT ck_r19_execution_request_hash CHECK (request_hash ~ '^[0-9a-f]{64}$'),
  ADD CONSTRAINT ck_r19_execution_version CHECK (version >= 0),
  ADD CONSTRAINT ck_r19_execution_result CHECK (result IS NULL OR jsonb_typeof(result) = 'object');

CREATE INDEX ix_r19_user_props_user_status ON hhy.user_props(user_id, status, expires_at);
CREATE INDEX ix_r19_exposure_content_window ON hhy.content_exposure_entitlements(content_id, starts_at, ends_at);
CREATE INDEX ix_r19_exposure_active ON hhy.content_exposure_entitlements(content_id, type, starts_at, ends_at)
  WHERE status IN ('SCHEDULED', 'ACTIVE');
CREATE INDEX ix_r19_refresh_content_created ON hhy.content_refresh_records(content_id, created_at DESC);
CREATE INDEX ix_r19_headline_bookings_slot_window ON hhy.headline_slot_bookings(slot_id, starts_at, ends_at);
CREATE INDEX ix_r19_execution_content_created ON hhy.prop_execution_logs(content_id, created_at DESC);

CREATE OR REPLACE FUNCTION hhy.guard_r19_headline_slot_capacity()
RETURNS trigger LANGUAGE plpgsql AS $$
DECLARE
  v_capacity integer;
  v_overlaps integer;
BEGIN
  IF NEW.status NOT IN ('BOOKED', 'ACTIVE') THEN RETURN NEW; END IF;
  SELECT capacity INTO v_capacity FROM hhy.headline_slots WHERE id = NEW.slot_id FOR UPDATE;
  IF v_capacity IS NULL THEN
    RAISE EXCEPTION 'R19_HEADLINE_SLOT_MISSING slot_id=%', NEW.slot_id USING ERRCODE = '23503';
  END IF;
  SELECT count(*) INTO v_overlaps
  FROM hhy.headline_slot_bookings booking
  WHERE booking.slot_id = NEW.slot_id
    AND booking.status IN ('BOOKED', 'ACTIVE')
    AND booking.starts_at < NEW.ends_at
    AND booking.ends_at > NEW.starts_at
    AND (TG_OP = 'INSERT' OR booking.id <> NEW.id);
  IF v_overlaps >= v_capacity THEN
    RAISE EXCEPTION 'R19_HEADLINE_SLOT_CAPACITY_EXCEEDED slot_id=%', NEW.slot_id
      USING ERRCODE = '23505';
  END IF;
  RETURN NEW;
END;
$$;

CREATE TRIGGER trg_r19_headline_slot_capacity
BEFORE INSERT OR UPDATE ON hhy.headline_slot_bookings
FOR EACH ROW EXECUTE FUNCTION hhy.guard_r19_headline_slot_capacity();

CREATE OR REPLACE FUNCTION hhy.guard_r19_prop_status()
RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
  IF TG_OP = 'UPDATE' AND NEW.status IS DISTINCT FROM OLD.status THEN
    IF TG_TABLE_NAME = 'user_props' AND NOT (
      (OLD.status = 'AVAILABLE' AND NEW.status IN ('RESERVED', 'CONSUMED', 'EXPIRED')) OR
      (OLD.status = 'RESERVED' AND NEW.status IN ('AVAILABLE', 'CONSUMED', 'EXPIRED')) OR
      (OLD.status IN ('CONSUMED', 'EXPIRED') AND NEW.status = OLD.status)
    ) THEN
      RAISE EXCEPTION 'R19_USER_PROP_INVALID_TRANSITION from=% to=%', OLD.status, NEW.status
        USING ERRCODE = '23514';
    END IF;
    IF TG_TABLE_NAME = 'content_exposure_entitlements' AND NOT (
      (OLD.status = 'SCHEDULED' AND NEW.status IN ('ACTIVE', 'CANCELLED', 'REVOKED')) OR
      (OLD.status = 'ACTIVE' AND NEW.status IN ('EXPIRED', 'REVOKED', 'CANCELLED')) OR
      (OLD.status IN ('EXPIRED', 'REVOKED', 'CANCELLED') AND NEW.status = OLD.status)
    ) THEN
      RAISE EXCEPTION 'R19_EXPOSURE_INVALID_TRANSITION from=% to=%', OLD.status, NEW.status
        USING ERRCODE = '23514';
    END IF;
  END IF;
  RETURN NEW;
END;
$$;

CREATE TRIGGER trg_r19_user_props_status
BEFORE UPDATE OF status ON hhy.user_props
FOR EACH ROW EXECUTE FUNCTION hhy.guard_r19_prop_status();
CREATE TRIGGER trg_r19_exposure_status
BEFORE UPDATE OF status ON hhy.content_exposure_entitlements
FOR EACH ROW EXECUTE FUNCTION hhy.guard_r19_prop_status();
CREATE TRIGGER trg_r19_user_props_history
AFTER UPDATE OF status ON hhy.user_props
FOR EACH ROW EXECUTE FUNCTION hhy.record_platform_status_history();
CREATE TRIGGER trg_r19_exposure_history
AFTER UPDATE OF status ON hhy.content_exposure_entitlements
FOR EACH ROW EXECUTE FUNCTION hhy.record_platform_status_history();

-- V006 already attached set_updated_at() to every upgraded table except
-- prop_products. Avoid executing the same trigger twice on those tables.
CREATE TRIGGER trg_r19_prop_products_updated_at
BEFORE UPDATE ON hhy.prop_products
FOR EACH ROW EXECUTE FUNCTION hhy.set_updated_at();

CREATE TRIGGER trg_r19_refresh_immutable
BEFORE UPDATE OR DELETE ON hhy.content_refresh_records
FOR EACH ROW EXECUTE FUNCTION hhy.prevent_immutable_mutation();

-- Payment completion is the only point at which inventory is granted. The
-- trigger is idempotent because it runs only on a transition into PAID.
CREATE OR REPLACE FUNCTION hhy.fulfill_r19_prop_order()
RETURNS trigger LANGUAGE plpgsql AS $$
DECLARE
  v_prop_sku_id bigint;
  v_quantity bigint;
  v_duration bigint;
BEGIN
  IF NEW.status <> 'PAID' OR OLD.status = 'PAID' OR NEW.biz_type <> 'PROP' THEN
    RETURN NEW;
  END IF;
  v_prop_sku_id := NEW.biz_id;
  SELECT pp.duration_seconds::bigint INTO v_duration
  FROM hhy.prop_skus ps
  JOIN hhy.prop_products pp ON pp.id = ps.prop_id
  WHERE ps.id = v_prop_sku_id;
  IF v_duration IS NULL THEN
    RAISE EXCEPTION 'R19_PROP_FULFILLMENT_SKU_MISSING order_id=%', NEW.id
      USING ERRCODE = '23514';
  END IF;
  SELECT COALESCE(sum(quantity), 0)::bigint INTO v_quantity
  FROM hhy.order_items WHERE order_id = NEW.id;
  IF v_quantity < 1 THEN
    RAISE EXCEPTION 'R19_PROP_FULFILLMENT_QUANTITY_MISSING order_id=%', NEW.id
      USING ERRCODE = '23514';
  END IF;
  INSERT INTO hhy.user_props(
    user_id, prop_sku_id, quantity, status, acquired_order_id, expires_at, version)
  VALUES (
    NEW.user_id, v_prop_sku_id, v_quantity, 'AVAILABLE', NEW.id,
    clock_timestamp() + make_interval(secs => v_duration::double precision), 0);
  UPDATE hhy.orders SET status = 'FULFILLING', version = version + 1 WHERE id = NEW.id;
  UPDATE hhy.orders SET status = 'COMPLETED', version = version + 1 WHERE id = NEW.id;
  RETURN NEW;
END;
$$;

CREATE TRIGGER trg_r19_prop_order_fulfillment
AFTER UPDATE OF status ON hhy.orders
FOR EACH ROW EXECUTE FUNCTION hhy.fulfill_r19_prop_order();

INSERT INTO hhy.system_configs(key, value_json, scope) VALUES
  ('props.top_duration_seconds', '86400'::jsonb, 'GLOBAL'),
  ('props.headline_duration_seconds', '86400'::jsonb, 'GLOBAL'),
  ('props.color_duration_seconds', '86400'::jsonb, 'GLOBAL'),
  ('props.refresh_cooldown_seconds', '600'::jsonb, 'GLOBAL'),
  ('props.same_type.stack_duration', 'true'::jsonb, 'GLOBAL'),
  ('props.same_page.deduplicate_content', 'true'::jsonb, 'GLOBAL'),
  ('props.refresh.daily_limit.normal', '20'::jsonb, 'GLOBAL'),
  ('props.refresh.daily_limit.month', '50'::jsonb, 'GLOBAL'),
  ('props.refresh.daily_limit.quarter', '100'::jsonb, 'GLOBAL'),
  ('props.refresh.daily_limit.year', '200'::jsonb, 'GLOBAL'),
  ('props.max_stack_days', '365'::jsonb, 'GLOBAL')
ON CONFLICT (key, scope) DO NOTHING;

INSERT INTO hhy.admin_permissions(code, resource, action) VALUES
  ('prop.read', 'prop', 'read'),
  ('prop.write', 'prop', 'write'),
  ('prop.slot.read', 'prop', 'slot.read'),
  ('prop.slot.write', 'prop', 'slot.write')
ON CONFLICT (code) DO UPDATE
SET resource = EXCLUDED.resource, action = EXCLUDED.action;

INSERT INTO hhy.admin_role_permissions(role_id, permission_id)
SELECT legacy.role_id, granular.id
FROM hhy.admin_role_permissions legacy
JOIN hhy.admin_permissions legacy_permission ON legacy_permission.id = legacy.permission_id
CROSS JOIN hhy.admin_permissions granular
WHERE legacy_permission.code = 'prop.manage'
  AND granular.code IN ('prop.read', 'prop.write', 'prop.slot.read', 'prop.slot.write')
ON CONFLICT (role_id, permission_id) DO NOTHING;

INSERT INTO hhy.admin_role_permissions(role_id, permission_id)
SELECT role.id, permission.id
FROM hhy.admin_roles role
CROSS JOIN hhy.admin_permissions permission
WHERE role.code = 'SUPER_ADMIN' AND role.status = 'ACTIVE'
  AND permission.code IN ('prop.read', 'prop.write', 'prop.slot.read', 'prop.slot.write')
ON CONFLICT (role_id, permission_id) DO NOTHING;

DO $$
BEGIN
  IF (SELECT count(*) FROM hhy.admin_permissions
      WHERE code IN ('prop.read', 'prop.write', 'prop.slot.read', 'prop.slot.write')) <> 4 THEN
    RAISE EXCEPTION 'R19_PROP_GRANULAR_PERMISSIONS_INCOMPLETE' USING ERRCODE = '23514';
  END IF;
END;
$$;

COMMENT ON TABLE hhy.prop_products IS 'R19 道具商品定义';
COMMENT ON TABLE hhy.prop_skus IS 'R19 道具能力与商品SKU绑定';
COMMENT ON TABLE hhy.user_props IS 'R19 用户道具库存';
COMMENT ON TABLE hhy.content_exposure_entitlements IS 'R19 内容曝光权益';
COMMENT ON TABLE hhy.content_refresh_records IS 'R19 刷新执行事实';
COMMENT ON TABLE hhy.headline_slots IS 'R19 头条资源位';
COMMENT ON TABLE hhy.headline_slot_bookings IS 'R19 头条资源位排期';
COMMENT ON TABLE hhy.color_themes IS 'R19 变色设计Token';
COMMENT ON TABLE hhy.prop_execution_logs IS 'R19 道具执行审计日志';
