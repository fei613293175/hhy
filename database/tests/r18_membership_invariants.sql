BEGIN;
SET search_path TO hhy, public;

DO $$
DECLARE
  v_user_id bigint;
  v_product_id bigint;
  v_sku_id bigint;
  v_plan_id bigint;
  v_membership_sku_id bigint;
  v_benefit_id bigint;
  v_membership_id bigint;
  v_order_id bigint;
  v_segment_id bigint;
  v_quote_id bigint;
  v_history_before bigint;
  v_history_after bigint;
BEGIN
  INSERT INTO hhy.users(phone, status, invite_code)
  VALUES ('13900005118', 'ACTIVE', 'R18MEM') RETURNING id INTO v_user_id;

  INSERT INTO hhy.products(
    product_code, type, name, description, status, display_order, version
  ) VALUES (
    'MEMBERSHIP-R18', 'MEMBERSHIP', 'Pro会员', 'R18 invariant fixture',
    'ACTIVE', 1, 0
  ) RETURNING id INTO v_product_id;

  INSERT INTO hhy.product_skus(
    product_id, code, name, price_cent, duration_days, benefits_json, status, version
  ) VALUES (
    v_product_id, 'MEMBERSHIP-R18-MONTH', 'Pro月卡', 9800, 30,
    '[{"benefitCode":"PUBLISH_LIMIT","name":"发布额度","value":10,"unit":"COUNT"}]',
    'ACTIVE', 0
  ) RETURNING id INTO v_sku_id;

  INSERT INTO hhy.membership_plans(code, name, public_badge)
  VALUES ('PRO', 'Pro会员', 'Pro') RETURNING id INTO v_plan_id;

  INSERT INTO hhy.membership_skus(plan_id, sku_id, term_type, term_seconds)
  VALUES (v_plan_id, v_sku_id, 'MONTH', 2592000) RETURNING id INTO v_membership_sku_id;

  INSERT INTO hhy.membership_benefits(code, value_type, description)
  VALUES ('PUBLISH_LIMIT', 1, '发布额度') RETURNING id INTO v_benefit_id;

  INSERT INTO hhy.membership_sku_benefits(
    membership_sku_id, benefit_id, value_json, enabled
  ) VALUES (v_membership_sku_id, v_benefit_id, '10', true);

  INSERT INTO hhy.user_memberships(
    user_id, plan_id, status, starts_at, ends_at, version
  ) VALUES (
    v_user_id, v_plan_id, 'ACTIVE', clock_timestamp(),
    clock_timestamp() + interval '30 days', 0
  ) RETURNING id INTO v_membership_id;

  INSERT INTO hhy.orders(
    order_no, user_id, biz_type, biz_id, amount_cent, status, currency,
    no_refund_confirmed, no_refund_agreement_version, no_refund_confirmed_at,
    idempotency_key, request_hash, version
  ) VALUES (
    'R18-MEMBERSHIP-ORDER-0001', v_user_id, 'MEMBERSHIP', v_membership_sku_id,
    9800, 'PENDING_PAYMENT', 'CNY', true, 'membership-no-refund-v1', clock_timestamp(),
    'r18-order-idempotency-0001', repeat('a', 64), 0
  ) RETURNING id INTO v_order_id;

  INSERT INTO hhy.order_items(
    order_id, sku_id, item_name, quantity, unit_price,
    subtotal_amount_cent, snapshot_json
  ) VALUES (
    v_order_id, v_sku_id, 'Pro月卡', 1, 9800, 9800,
    '{"name":"Pro月卡","skuCode":"MEMBERSHIP-R18-MONTH"}'
  );

  INSERT INTO hhy.order_price_snapshots(
    order_id, original, discount, service_fee, payable,
    rule_versions, rule_versions_json
  ) VALUES (
    v_order_id, 9800, 0, 0, 9800, 'membership-price-v1',
    '["membership-price-v1"]'
  );

  INSERT INTO hhy.membership_entitlement_segments(
    user_id, source_order_id, membership_sku_id, type, paid_amount,
    starts_at, ends_at, consumed, version
  ) VALUES (
    v_user_id, v_order_id, v_membership_sku_id, 'PAID', 9800,
    clock_timestamp(), clock_timestamp() + interval '30 days', 0, 0
  ) RETURNING id INTO v_segment_id;

  INSERT INTO hhy.membership_benefit_snapshots(
    order_id, benefits_json, reward_matrix_json
  ) VALUES (
    v_order_id,
    '[{"benefitCode":"PUBLISH_LIMIT","name":"发布额度","value":10,"unit":"COUNT"}]',
    '{}'::jsonb
  );

  INSERT INTO hhy.membership_upgrade_quotes(
    user_id, target_sku_id, remaining_value, payable, expires_at,
    membership_version, idempotency_key, request_hash
  ) VALUES (
    v_user_id, v_membership_sku_id, 4900, 4900,
    clock_timestamp() + interval '15 minutes', 0,
    'r18-quote-idempotency-0001', repeat('b', 64)
  ) RETURNING id INTO v_quote_id;

  INSERT INTO hhy.membership_value_conversions(
    upgrade_order_id, source_segment_id, converted_value, extra_seconds
  ) VALUES (v_order_id, v_segment_id, 4900, 1296000);

  BEGIN
    INSERT INTO hhy.membership_skus(plan_id, sku_id, term_type, term_seconds)
    VALUES (v_plan_id, v_sku_id, 'MONTH', 2592000);
    RAISE EXCEPTION 'R18_DUPLICATE_MEMBERSHIP_SKU_ACCEPTED';
  EXCEPTION WHEN unique_violation THEN NULL;
  END;

  BEGIN
    INSERT INTO hhy.user_memberships(
      user_id, plan_id, status, starts_at, ends_at
    ) VALUES (
      v_user_id, v_plan_id, 'ACTIVE', clock_timestamp(),
      clock_timestamp() + interval '30 days'
    );
    RAISE EXCEPTION 'R18_DUPLICATE_CURRENT_MEMBERSHIP_ACCEPTED';
  EXCEPTION WHEN unique_violation THEN NULL;
  END;

  BEGIN
    INSERT INTO hhy.membership_entitlement_segments(
      user_id, membership_sku_id, type, paid_amount, starts_at, ends_at, consumed
    ) VALUES (
      v_user_id, v_membership_sku_id, 'GIFT', 1,
      clock_timestamp(), clock_timestamp() + interval '1 day', 0
    );
    RAISE EXCEPTION 'R18_GIFT_WITH_PAID_VALUE_ACCEPTED';
  EXCEPTION WHEN check_violation THEN NULL;
  END;

  BEGIN
    INSERT INTO hhy.membership_upgrade_quotes(
      user_id, target_sku_id, remaining_value, payable, expires_at,
      idempotency_key, request_hash
    ) VALUES (
      v_user_id, v_membership_sku_id, 0, 1,
      clock_timestamp() + interval '15 minutes', NULL, repeat('c', 64)
    );
    RAISE EXCEPTION 'R18_PARTIAL_QUOTE_IDEMPOTENCY_ACCEPTED';
  EXCEPTION WHEN check_violation THEN NULL;
  END;

  BEGIN
    INSERT INTO hhy.membership_upgrade_quotes(
      user_id, target_sku_id, remaining_value, payable, expires_at,
      idempotency_key, request_hash
    ) VALUES (
      v_user_id, v_membership_sku_id, 4900, 4900,
      clock_timestamp() + interval '15 minutes',
      'r18-quote-idempotency-0001', repeat('b', 64)
    );
    RAISE EXCEPTION 'R18_DUPLICATE_QUOTE_IDEMPOTENCY_ACCEPTED';
  EXCEPTION WHEN unique_violation THEN NULL;
  END;

  BEGIN
    UPDATE hhy.membership_benefit_snapshots
    SET benefits_json = '[]'::jsonb WHERE order_id = v_order_id;
    RAISE EXCEPTION 'R18_BENEFIT_SNAPSHOT_MUTATION_ACCEPTED';
  EXCEPTION WHEN object_not_in_prerequisite_state THEN NULL;
  END;

  BEGIN
    UPDATE hhy.membership_value_conversions
    SET converted_value = 0 WHERE source_segment_id = v_segment_id;
    RAISE EXCEPTION 'R18_VALUE_CONVERSION_MUTATION_ACCEPTED';
  EXCEPTION WHEN object_not_in_prerequisite_state THEN NULL;
  END;

  SELECT count(*) INTO v_history_before
  FROM hhy.outbox_events
  WHERE aggregate_type = 'user_memberships'
    AND aggregate_id = v_membership_id::text
    AND event_type = 'platform.status.changed.v1';

  UPDATE hhy.user_memberships
  SET status = 'EXPIRED', version = version + 1
  WHERE id = v_membership_id AND version = 0;
  IF NOT FOUND THEN RAISE EXCEPTION 'R18_MEMBERSHIP_OPTIMISTIC_UPDATE_FAILED'; END IF;

  SELECT count(*) INTO v_history_after
  FROM hhy.outbox_events
  WHERE aggregate_type = 'user_memberships'
    AND aggregate_id = v_membership_id::text
    AND event_type = 'platform.status.changed.v1';
  IF v_history_after <> v_history_before + 1 THEN
    RAISE EXCEPTION 'R18_MEMBERSHIP_STATUS_HISTORY_MISSING';
  END IF;

  IF (SELECT status FROM hhy.membership_upgrade_quotes WHERE id = v_quote_id) <> 'OPEN' THEN
    RAISE EXCEPTION 'R18_QUOTE_INITIAL_STATUS_INVALID';
  END IF;

  UPDATE hhy.orders SET status='PAYMENT_PROCESSING',version=version+1 WHERE id=v_order_id;
  UPDATE hhy.orders
  SET status='PAID',paid_amount_cent=amount_cent,paid_at=clock_timestamp(),version=version+1
  WHERE id=v_order_id;
  IF (SELECT status FROM hhy.orders WHERE id=v_order_id) <> 'COMPLETED' THEN
    RAISE EXCEPTION 'R18_PAID_ORDER_NOT_FULFILLED';
  END IF;
  IF (SELECT count(*) FROM hhy.membership_entitlement_segments
      WHERE user_id=v_user_id AND source_order_id=v_order_id) <> 2 THEN
    RAISE EXCEPTION 'R18_PAID_ENTITLEMENT_SEGMENT_MISSING';
  END IF;

  IF (SELECT count(*) FROM hhy.admin_permissions
      WHERE code IN ('membership.read', 'membership.write', 'membership.grant')) <> 3 THEN
    RAISE EXCEPTION 'R18_MEMBERSHIP_PERMISSION_SET_INCOMPLETE';
  END IF;

  IF (SELECT count(*)
      FROM hhy.admin_role_permissions grant_row
      JOIN hhy.admin_roles role ON role.id = grant_row.role_id
      JOIN hhy.admin_permissions permission ON permission.id = grant_row.permission_id
      WHERE role.code = 'SUPER_ADMIN'
        AND role.status = 'ACTIVE'
        AND permission.code IN ('membership.read', 'membership.write', 'membership.grant')) <> 3 THEN
    RAISE EXCEPTION 'R18_SUPER_ADMIN_MEMBERSHIP_PERMISSIONS_INCOMPLETE';
  END IF;
END;
$$;

SELECT 'R18_MEMBERSHIP_INVARIANTS PASS' AS result;
ROLLBACK;
