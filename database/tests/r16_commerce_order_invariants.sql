BEGIN;
SET search_path TO hhy, public;

DO $$
DECLARE
  actor_id bigint;
  product_id bigint;
  sku_id bigint;
  created_order_id bigint;
  history_before bigint;
  history_after bigint;
BEGIN
  INSERT INTO hhy.users(phone, status, invite_code)
  VALUES ('13900004501', 'ACTIVE', 'R16DATA')
  RETURNING id INTO actor_id;

  INSERT INTO hhy.products(
    product_code, type, name, description, status, display_order, version
  ) VALUES (
    'MEMBERSHIP-R16', 'MEMBERSHIP', '会员服务', 'R16 invariant fixture',
    'ACTIVE', 1, 0
  ) RETURNING id INTO product_id;

  INSERT INTO hhy.product_skus(
    product_id, code, name, price_cent, member_price_cent, duration_days,
    benefits_json, status, version
  ) VALUES (
    product_id, 'MEMBERSHIP-R16-MONTH', '月度会员', 1900, 1500, 30,
    '[{"benefitCode":"MEMBER_DAYS","name":"会员天数","value":30,"unit":"DAY"}]',
    'ACTIVE', 0
  ) RETURNING id INTO sku_id;

  INSERT INTO hhy.sku_commission_policies(
    sku_id, level1_bps, level2_bps, enabled, version
  ) VALUES (sku_id, 1000, 500, true, 0);

  BEGIN
    INSERT INTO hhy.product_skus(
      product_id, code, name, price_cent, benefits_json, status, version
    ) VALUES (
      product_id, 'MEMBERSHIP-R16-BAD', '错误权益', 100,
      '[{"benefitCode":"BROKEN","name":"错误","extra":true}]', 'ACTIVE', 0
    );
    RAISE EXCEPTION 'R16_INVALID_BENEFIT_SHAPE_WAS_ACCEPTED';
  EXCEPTION WHEN check_violation THEN NULL;
  END;

  INSERT INTO hhy.orders(
    order_no, user_id, biz_type, biz_id, amount_cent, status, currency,
    idempotency_key, request_hash, version
  ) VALUES (
    'R16-ORDER-0001', actor_id, 'MEMBERSHIP', product_id, 1900,
    'PENDING_PAYMENT', 'CNY', 'r16-order-idem-0001',
    repeat('a', 64), 0
  ) RETURNING id INTO created_order_id;

  INSERT INTO hhy.order_items(
    order_id, sku_id, item_name, quantity, unit_price,
    subtotal_amount_cent, snapshot_json
  ) VALUES (
    created_order_id, sku_id, '月度会员', 1, 1900, 1900,
    '{"name":"月度会员","skuCode":"MEMBERSHIP-R16-MONTH"}'
  );

  INSERT INTO hhy.order_price_snapshots(
    order_id, original, discount, service_fee, payable,
    rule_versions, rule_versions_json
  ) VALUES (
    created_order_id, 2000, 100, 0, 1900, 'membership-price-v1',
    '["membership-price-v1"]'
  );

  BEGIN
    INSERT INTO hhy.orders(
      order_no, user_id, biz_type, amount_cent, status, currency,
      idempotency_key, request_hash
    ) VALUES (
      'R16-ORDER-INVALID-INITIAL', actor_id, 'MEMBERSHIP', 1900,
      'PAID', 'CNY', 'r16-order-idem-0002', repeat('b', 64)
    );
    RAISE EXCEPTION 'R16_INVALID_INITIAL_STATUS_WAS_ACCEPTED';
  EXCEPTION WHEN check_violation THEN NULL;
  END;

  BEGIN
    INSERT INTO hhy.orders(
      order_no, user_id, biz_type, amount_cent, status, currency,
      idempotency_key, request_hash
    ) VALUES (
      'R16-ORDER-DUPLICATE-IDEMPOTENCY', actor_id, 'MEMBERSHIP', 1900,
      'PENDING_PAYMENT', 'CNY', 'r16-order-idem-0001', repeat('b', 64)
    );
    RAISE EXCEPTION 'R16_DUPLICATE_IDEMPOTENCY_WAS_ACCEPTED';
  EXCEPTION WHEN unique_violation THEN NULL;
  END;

  BEGIN
    UPDATE hhy.orders
    SET status = 'PAYMENT_PROCESSING', version = version + 1
    WHERE id = created_order_id;
    RAISE EXCEPTION 'R16_PAYMENT_WITHOUT_NO_REFUND_EVIDENCE_WAS_ACCEPTED';
  EXCEPTION WHEN check_violation THEN NULL;
  END;

  UPDATE hhy.orders
  SET no_refund_confirmed = true,
      no_refund_agreement_version = 'no-refund-v1',
      no_refund_confirmed_at = clock_timestamp(),
      version = version + 1
  WHERE id = created_order_id;

  SELECT count(*) INTO history_before
  FROM hhy.outbox_events
  WHERE event_type = 'platform.status.changed.v1'
    AND payload->>'entityType' = 'orders'
    AND payload->>'entityId' = created_order_id::text;

  UPDATE hhy.orders
  SET status = 'PAYMENT_PROCESSING', version = version + 1
  WHERE id = created_order_id;

  SELECT count(*) INTO history_after
  FROM hhy.outbox_events
  WHERE event_type = 'platform.status.changed.v1'
    AND payload->>'entityType' = 'orders'
    AND payload->>'entityId' = created_order_id::text;
  IF history_after <> history_before + 1 THEN
    RAISE EXCEPTION 'R16_ORDER_STATUS_HISTORY_CARDINALITY expected=% actual=%',
      history_before + 1, history_after;
  END IF;

  BEGIN
    UPDATE hhy.orders
    SET status = 'COMPLETED', version = version + 1
    WHERE id = created_order_id;
    RAISE EXCEPTION 'R16_ILLEGAL_ORDER_TRANSITION_WAS_ACCEPTED';
  EXCEPTION WHEN check_violation THEN NULL;
  END;

  IF (SELECT status FROM hhy.orders WHERE id = created_order_id) <> 'PAYMENT_PROCESSING'
     OR (
       SELECT count(*) FROM hhy.outbox_events
       WHERE event_type = 'platform.status.changed.v1'
         AND payload->>'entityType' = 'orders'
         AND payload->>'entityId' = created_order_id::text
     ) <> history_after THEN
    RAISE EXCEPTION 'R16_FAILED_TRANSITION_LEFT_HISTORY';
  END IF;

  BEGIN
    UPDATE hhy.orders
    SET status = 'PAID',
        paid_amount_cent = 1900,
        paid_at = clock_timestamp(),
        version = version + 1
    WHERE id = created_order_id;
    RAISE EXCEPTION 'force nested transaction rollback';
  EXCEPTION WHEN raise_exception THEN NULL;
  END;

  IF (SELECT status FROM hhy.orders WHERE id = created_order_id) <> 'PAYMENT_PROCESSING'
     OR (
       SELECT count(*) FROM hhy.outbox_events
       WHERE event_type = 'platform.status.changed.v1'
         AND payload->>'entityType' = 'orders'
         AND payload->>'entityId' = created_order_id::text
     ) <> history_after THEN
    RAISE EXCEPTION 'R16_ROLLED_BACK_TRANSITION_LEFT_HISTORY';
  END IF;

  UPDATE hhy.orders
  SET status = 'PAID',
      paid_amount_cent = 1900,
      paid_at = clock_timestamp(),
      version = version + 1
  WHERE id = created_order_id;
  UPDATE hhy.orders SET status = 'FULFILLING', version = version + 1 WHERE id = created_order_id;
  UPDATE hhy.orders SET status = 'COMPLETED', version = version + 1 WHERE id = created_order_id;

  SELECT count(*) INTO history_after
  FROM hhy.outbox_events
  WHERE event_type = 'platform.status.changed.v1'
    AND payload->>'entityType' = 'orders'
    AND payload->>'entityId' = created_order_id::text;
  IF history_after <> history_before + 4 THEN
    RAISE EXCEPTION 'R16_ORDER_COMPLETE_HISTORY_CARDINALITY expected=% actual=%',
      history_before + 4, history_after;
  END IF;

  BEGIN
    UPDATE hhy.orders SET status = 'PAID', version = version + 1 WHERE id = created_order_id;
    RAISE EXCEPTION 'R16_TERMINAL_ORDER_MUTATION_WAS_ACCEPTED';
  EXCEPTION WHEN check_violation THEN NULL;
  END;

  BEGIN
    UPDATE hhy.order_items
    SET subtotal_amount_cent = subtotal_amount_cent + 1
    WHERE order_items.order_id = created_order_id;
    RAISE EXCEPTION 'R16_ORDER_ITEM_MUTATION_WAS_ACCEPTED';
  EXCEPTION WHEN object_not_in_prerequisite_state THEN NULL;
  END;

  BEGIN
    UPDATE hhy.order_price_snapshots
    SET payable = payable + 1
    WHERE order_price_snapshots.order_id = created_order_id;
    RAISE EXCEPTION 'R16_PRICE_SNAPSHOT_MUTATION_WAS_ACCEPTED';
  EXCEPTION WHEN object_not_in_prerequisite_state THEN NULL;
  END;
END;
$$;

SELECT 'R16_COMMERCE_ORDER_INVARIANT_PROPERTY_MATRIX PASS' AS result;
ROLLBACK;
