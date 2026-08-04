-- R19 rollback restores the permissive V004 shape. It refuses to discard
-- purchased inventory or execution facts implicitly.
SET search_path TO hhy, public;

DO $$
BEGIN
  IF EXISTS (SELECT 1 FROM hhy.user_props WHERE quantity <> 1 OR acquired_order_id IS NOT NULL)
     OR EXISTS (SELECT 1 FROM hhy.content_exposure_entitlements)
     OR EXISTS (SELECT 1 FROM hhy.content_refresh_records)
     OR EXISTS (SELECT 1 FROM hhy.headline_slot_bookings)
     OR EXISTS (SELECT 1 FROM hhy.prop_execution_logs) THEN
    RAISE EXCEPTION 'R19_ROLLBACK_REQUIRES_EMPTY_RUNTIME_FACTS' USING ERRCODE = '55000';
  END IF;
END;
$$;

DROP TRIGGER IF EXISTS trg_r19_prop_order_fulfillment ON hhy.orders;
DROP TRIGGER IF EXISTS trg_r19_refresh_immutable ON hhy.content_refresh_records;
DROP TRIGGER IF EXISTS trg_r19_headline_slot_capacity ON hhy.headline_slot_bookings;
DROP TRIGGER IF EXISTS trg_r19_exposure_history ON hhy.content_exposure_entitlements;
DROP TRIGGER IF EXISTS trg_r19_user_props_history ON hhy.user_props;
DROP TRIGGER IF EXISTS trg_r19_exposure_status ON hhy.content_exposure_entitlements;
DROP TRIGGER IF EXISTS trg_r19_user_props_status ON hhy.user_props;
DROP TRIGGER IF EXISTS trg_r19_prop_products_updated_at ON hhy.prop_products;

DROP FUNCTION IF EXISTS hhy.fulfill_r19_prop_order();
DROP FUNCTION IF EXISTS hhy.guard_r19_prop_status();
DROP FUNCTION IF EXISTS hhy.guard_r19_headline_slot_capacity();

DROP INDEX IF EXISTS hhy.ix_r19_user_props_user_status;
DROP INDEX IF EXISTS hhy.ix_r19_exposure_content_window;
DROP INDEX IF EXISTS hhy.ix_r19_exposure_active;
DROP INDEX IF EXISTS hhy.ix_r19_refresh_content_created;
DROP INDEX IF EXISTS hhy.ix_r19_headline_bookings_slot_window;
DROP INDEX IF EXISTS hhy.ix_r19_execution_content_created;

DELETE FROM hhy.system_configs
WHERE scope = 'GLOBAL' AND key IN (
  'props.top_duration_seconds',
  'props.headline_duration_seconds',
  'props.color_duration_seconds',
  'props.refresh_cooldown_seconds',
  'props.same_type.stack_duration',
  'props.same_page.deduplicate_content',
  'props.refresh.daily_limit.normal',
  'props.refresh.daily_limit.month',
  'props.refresh.daily_limit.quarter',
  'props.refresh.daily_limit.year',
  'props.max_stack_days'
);

DELETE FROM hhy.admin_permissions
WHERE code IN ('prop.read', 'prop.write', 'prop.slot.read', 'prop.slot.write');

ALTER TABLE hhy.prop_execution_logs
  DROP CONSTRAINT IF EXISTS fk_r19_execution_user_prop,
  DROP CONSTRAINT IF EXISTS fk_r19_execution_content,
  DROP CONSTRAINT IF EXISTS uq_r19_execution_idempotency,
  DROP CONSTRAINT IF EXISTS ck_r19_execution_status,
  DROP CONSTRAINT IF EXISTS ck_r19_execution_operation,
  DROP CONSTRAINT IF EXISTS ck_r19_execution_idempotency_key,
  DROP CONSTRAINT IF EXISTS ck_r19_execution_request_hash,
  DROP CONSTRAINT IF EXISTS ck_r19_execution_version,
  DROP CONSTRAINT IF EXISTS ck_r19_execution_result,
  DROP COLUMN IF EXISTS status,
  DROP COLUMN IF EXISTS operation_id,
  DROP COLUMN IF EXISTS idempotency_key,
  DROP COLUMN IF EXISTS request_hash,
  DROP COLUMN IF EXISTS version;

ALTER TABLE hhy.color_themes
  DROP CONSTRAINT IF EXISTS ck_r19_color_theme_code,
  DROP CONSTRAINT IF EXISTS ck_r19_color_theme_tokens,
  DROP CONSTRAINT IF EXISTS ck_r19_color_theme_version,
  ALTER COLUMN token_json DROP NOT NULL,
  ALTER COLUMN token_json DROP DEFAULT,
  DROP COLUMN IF EXISTS version;

ALTER TABLE hhy.headline_slot_bookings
  DROP CONSTRAINT IF EXISTS fk_r19_booking_slot,
  DROP CONSTRAINT IF EXISTS fk_r19_booking_content,
  DROP CONSTRAINT IF EXISTS fk_r19_booking_entitlement,
  DROP CONSTRAINT IF EXISTS ck_r19_headline_booking_window,
  DROP CONSTRAINT IF EXISTS ck_r19_headline_booking_status,
  DROP CONSTRAINT IF EXISTS ck_r19_headline_booking_version,
  ALTER COLUMN content_id DROP NOT NULL,
  ALTER COLUMN entitlement_id DROP NOT NULL,
  ALTER COLUMN starts_at DROP NOT NULL,
  ALTER COLUMN ends_at DROP NOT NULL,
  DROP COLUMN IF EXISTS status,
  DROP COLUMN IF EXISTS version;

ALTER TABLE hhy.headline_slots
  DROP CONSTRAINT IF EXISTS uq_r19_headline_slot_code,
  DROP CONSTRAINT IF EXISTS ck_r19_headline_slot_identity,
  DROP CONSTRAINT IF EXISTS ck_r19_headline_slot_capacity,
  DROP CONSTRAINT IF EXISTS ck_r19_headline_slot_status,
  DROP CONSTRAINT IF EXISTS ck_r19_headline_slot_version,
  ALTER COLUMN capacity TYPE varchar(255) USING capacity::varchar,
  ALTER COLUMN slot_code DROP NOT NULL,
  ALTER COLUMN capacity DROP NOT NULL,
  ALTER COLUMN capacity DROP DEFAULT,
  DROP COLUMN IF EXISTS version;

ALTER TABLE hhy.content_refresh_records
  DROP CONSTRAINT IF EXISTS fk_r19_refresh_content,
  DROP CONSTRAINT IF EXISTS fk_r19_refresh_user_prop,
  DROP CONSTRAINT IF EXISTS uq_r19_refresh_idempotency,
  DROP CONSTRAINT IF EXISTS ck_r19_refresh_layers,
  DROP CONSTRAINT IF EXISTS ck_r19_refresh_idempotency_key,
  DROP CONSTRAINT IF EXISTS ck_r19_refresh_request_hash,
  ALTER COLUMN user_prop_id DROP NOT NULL,
  ALTER COLUMN layers DROP NOT NULL,
  ALTER COLUMN executed_at DROP NOT NULL,
  DROP COLUMN IF EXISTS idempotency_key,
  DROP COLUMN IF EXISTS request_hash;

ALTER TABLE hhy.content_exposure_entitlements
  DROP CONSTRAINT IF EXISTS fk_r19_exposure_content,
  DROP CONSTRAINT IF EXISTS fk_r19_exposure_user_prop,
  DROP CONSTRAINT IF EXISTS ck_r19_exposure_type,
  DROP CONSTRAINT IF EXISTS ck_r19_exposure_window,
  DROP CONSTRAINT IF EXISTS ck_r19_exposure_status,
  DROP CONSTRAINT IF EXISTS ck_r19_exposure_version,
  ALTER COLUMN type DROP NOT NULL,
  ALTER COLUMN starts_at DROP NOT NULL,
  ALTER COLUMN ends_at DROP NOT NULL,
  DROP COLUMN IF EXISTS user_prop_id;

ALTER TABLE hhy.user_props
  DROP CONSTRAINT IF EXISTS fk_r19_user_props_user,
  DROP CONSTRAINT IF EXISTS fk_r19_user_props_sku,
  DROP CONSTRAINT IF EXISTS fk_r19_user_props_order,
  DROP CONSTRAINT IF EXISTS ck_r19_user_prop_quantity,
  DROP CONSTRAINT IF EXISTS ck_r19_user_prop_status,
  DROP CONSTRAINT IF EXISTS ck_r19_user_prop_expiry,
  DROP CONSTRAINT IF EXISTS ck_r19_user_prop_version,
  ALTER COLUMN prop_sku_id DROP NOT NULL,
  DROP COLUMN IF EXISTS quantity,
  DROP COLUMN IF EXISTS version;

ALTER TABLE hhy.prop_skus
  DROP CONSTRAINT IF EXISTS fk_r19_prop_skus_product,
  DROP CONSTRAINT IF EXISTS fk_r19_prop_skus_product_sku,
  DROP CONSTRAINT IF EXISTS uq_r19_prop_sku_binding,
  DROP CONSTRAINT IF EXISTS ck_r19_prop_sku_scope,
  DROP CONSTRAINT IF EXISTS ck_r19_prop_sku_status,
  DROP CONSTRAINT IF EXISTS ck_r19_prop_sku_version,
  ALTER COLUMN product_sku_id DROP NOT NULL,
  DROP COLUMN IF EXISTS status,
  DROP COLUMN IF EXISTS version;

ALTER TABLE hhy.prop_products
  DROP CONSTRAINT IF EXISTS ck_r19_prop_product_type,
  DROP CONSTRAINT IF EXISTS ck_r19_prop_product_name,
  DROP CONSTRAINT IF EXISTS ck_r19_prop_product_duration,
  DROP CONSTRAINT IF EXISTS ck_r19_prop_product_execution,
  DROP CONSTRAINT IF EXISTS ck_r19_prop_product_status,
  DROP CONSTRAINT IF EXISTS ck_r19_prop_product_version,
  ALTER COLUMN name DROP NOT NULL,
  ALTER COLUMN duration_seconds DROP NOT NULL,
  ALTER COLUMN execution_type DROP NOT NULL,
  DROP COLUMN IF EXISTS status,
  DROP COLUMN IF EXISTS version;
