-- DEV/TEST only. Production rollback is a forward fix or a verified restore.
SET search_path TO hhy, public;

DO $$
BEGIN
  IF EXISTS (SELECT 1 FROM hhy.products)
     OR EXISTS (SELECT 1 FROM hhy.product_skus)
     OR EXISTS (SELECT 1 FROM hhy.sku_commission_policies)
     OR EXISTS (SELECT 1 FROM hhy.coupons)
     OR EXISTS (SELECT 1 FROM hhy.user_coupons)
     OR EXISTS (SELECT 1 FROM hhy.orders)
     OR EXISTS (SELECT 1 FROM hhy.order_items)
     OR EXISTS (SELECT 1 FROM hhy.order_price_snapshots)
     OR EXISTS (
       SELECT 1 FROM hhy.outbox_events
       WHERE event_type = 'platform.status.changed.v1'
         AND payload->>'entityType' = 'orders'
     ) THEN
    RAISE EXCEPTION 'R16_U045_BUSINESS_FACTS_PRESENT'
      USING ERRCODE = '55000';
  END IF;
END;
$$;

DROP TRIGGER IF EXISTS trg_r16_order_price_snapshot_commit ON hhy.order_price_snapshots;
DROP TRIGGER IF EXISTS trg_r16_orders_quote_commit ON hhy.orders;
DROP TRIGGER IF EXISTS trg_r16_order_price_snapshots_immutable ON hhy.order_price_snapshots;
DROP TRIGGER IF EXISTS trg_r16_order_items_immutable ON hhy.order_items;
DROP TRIGGER IF EXISTS trg_r16_orders_status_history ON hhy.orders;
DROP TRIGGER IF EXISTS trg_r16_orders_guard ON hhy.orders;

DROP FUNCTION IF EXISTS hhy.check_r16_order_quote();
DROP FUNCTION IF EXISTS hhy.assert_r16_order_quote(bigint);
DROP FUNCTION IF EXISTS hhy.guard_r16_order_mutation();

DROP INDEX IF EXISTS hhy.ix_r16_orders_paid_at;
DROP INDEX IF EXISTS hhy.uq_r16_orders_creation_idempotency;

ALTER TABLE hhy.order_price_snapshots
  DROP CONSTRAINT IF EXISTS ck_r16_order_price_snapshot_rules,
  DROP CONSTRAINT IF EXISTS ck_r16_order_price_snapshot_amounts,
  DROP COLUMN IF EXISTS rule_versions_json;

ALTER TABLE hhy.order_items
  DROP CONSTRAINT IF EXISTS ck_r16_order_items_subtotal,
  DROP CONSTRAINT IF EXISTS ck_r16_order_items_unit_price,
  DROP CONSTRAINT IF EXISTS ck_r16_order_items_quantity,
  DROP CONSTRAINT IF EXISTS ck_r16_order_items_name,
  ALTER COLUMN unit_price DROP NOT NULL,
  ALTER COLUMN quantity DROP NOT NULL,
  DROP COLUMN IF EXISTS subtotal_amount_cent,
  DROP COLUMN IF EXISTS item_name;

ALTER TABLE hhy.orders
  DROP CONSTRAINT IF EXISTS ck_r16_orders_payment_requires_no_refund,
  DROP CONSTRAINT IF EXISTS ck_r16_orders_creation_idempotency,
  DROP CONSTRAINT IF EXISTS ck_r16_orders_no_refund_evidence,
  DROP CONSTRAINT IF EXISTS ck_r16_orders_paid_pair,
  DROP CONSTRAINT IF EXISTS ck_r16_orders_paid_evidence,
  DROP CONSTRAINT IF EXISTS ck_r16_orders_currency,
  ALTER COLUMN amount_cent DROP NOT NULL,
  DROP COLUMN IF EXISTS legacy_without_idempotency,
  DROP COLUMN IF EXISTS request_hash,
  DROP COLUMN IF EXISTS idempotency_key,
  DROP COLUMN IF EXISTS no_refund_confirmed_at,
  DROP COLUMN IF EXISTS no_refund_agreement_version,
  DROP COLUMN IF EXISTS no_refund_confirmed,
  DROP COLUMN IF EXISTS paid_at,
  DROP COLUMN IF EXISTS paid_amount_cent,
  DROP COLUMN IF EXISTS currency;

ALTER TABLE hhy.user_coupons
  DROP CONSTRAINT IF EXISTS ck_r16_user_coupons_status;

ALTER TABLE hhy.coupons
  DROP CONSTRAINT IF EXISTS ck_r16_coupons_value,
  DROP CONSTRAINT IF EXISTS ck_r16_coupons_code,
  DROP CONSTRAINT IF EXISTS uq_r16_coupons_code;

ALTER TABLE hhy.sku_commission_policies
  DROP CONSTRAINT IF EXISTS ck_r16_sku_commission_policy_version,
  DROP CONSTRAINT IF EXISTS ck_r16_sku_commission_policy_total,
  DROP CONSTRAINT IF EXISTS uq_r16_sku_commission_policy_sku;

ALTER TABLE hhy.product_skus
  DROP CONSTRAINT IF EXISTS ck_r16_product_skus_version,
  DROP CONSTRAINT IF EXISTS ck_r16_product_skus_sale_window,
  DROP CONSTRAINT IF EXISTS ck_r16_product_skus_benefits,
  DROP CONSTRAINT IF EXISTS ck_r16_product_skus_duration,
  DROP CONSTRAINT IF EXISTS ck_r16_product_skus_member_price,
  DROP CONSTRAINT IF EXISTS ck_r16_product_skus_name,
  ALTER COLUMN benefits_json DROP NOT NULL,
  ALTER COLUMN name DROP NOT NULL,
  ALTER COLUMN price_cent DROP NOT NULL,
  DROP COLUMN IF EXISTS version,
  DROP COLUMN IF EXISTS sale_ends_at,
  DROP COLUMN IF EXISTS sale_starts_at,
  DROP COLUMN IF EXISTS benefits_json,
  DROP COLUMN IF EXISTS duration_days,
  DROP COLUMN IF EXISTS member_price_cent,
  DROP COLUMN IF EXISTS name;

ALTER TABLE hhy.products
  DROP CONSTRAINT IF EXISTS ck_r16_products_version,
  DROP CONSTRAINT IF EXISTS ck_r16_products_required_text,
  DROP CONSTRAINT IF EXISTS ck_r16_products_product_code,
  DROP CONSTRAINT IF EXISTS uq_r16_products_product_code,
  ALTER COLUMN name DROP NOT NULL,
  DROP COLUMN IF EXISTS version,
  DROP COLUMN IF EXISTS description,
  DROP COLUMN IF EXISTS product_code;

DROP FUNCTION IF EXISTS hhy.r16_benefits_valid(jsonb);
DROP FUNCTION IF EXISTS hhy.r16_rule_versions_valid(jsonb);
