BEGIN;
SET search_path TO hhy, public;

DO $$
DECLARE
  v_user_id bigint;
  v_product_id bigint;
  v_product_sku_id bigint;
  v_refresh_product_id bigint;
  v_headline_product_id bigint;
  v_refresh_sku_id bigint;
  v_headline_sku_id bigint;
  v_refresh_inventory_id bigint;
  v_headline_inventory_id bigint;
  v_content_id bigint;
  v_slot_id bigint;
  v_entitlement_id bigint;
  v_second_entitlement_id bigint;
  v_order_id bigint;
  v_history_before bigint;
  v_history_after bigint;
  v_start timestamptz := clock_timestamp() + interval '1 hour';
  v_end timestamptz := clock_timestamp() + interval '25 hours';
BEGIN
  IF (SELECT count(*) FROM information_schema.tables
      WHERE table_schema = 'hhy'
        AND table_name IN (
          'prop_products', 'prop_skus', 'user_props',
          'content_exposure_entitlements', 'content_refresh_records',
          'headline_slots', 'headline_slot_bookings', 'color_themes',
          'prop_execution_logs')) <> 9 THEN
    RAISE EXCEPTION 'R19_PROP_TABLE_SET_INCOMPLETE';
  END IF;

  INSERT INTO hhy.users(phone, status, invite_code)
  VALUES ('13900005219', 'ACTIVE', 'R19PROP') RETURNING id INTO v_user_id;

  INSERT INTO hhy.products(
    product_code, type, name, description, status, display_order, version
  ) VALUES (
    'PROP-R19', 'PROP', 'R19道具', 'R19 invariant fixture', 'ACTIVE', 1, 0
  ) RETURNING id INTO v_product_id;

  INSERT INTO hhy.product_skus(
    product_id, code, name, price_cent, duration_days, benefits_json, status, version
  ) VALUES (
    v_product_id, 'PROP-R19-REFRESH', '刷新道具', 100, 1,
    '[{"benefitCode":"PROP_USE","name":"道具使用次数","value":1,"unit":"COUNT"}]',
    'ACTIVE', 0
  ) RETURNING id INTO v_product_sku_id;

  INSERT INTO hhy.prop_products(type, name, duration_seconds, execution_type, status, version)
  VALUES ('REFRESH', '刷新道具', '600', 'IMMEDIATE', 'ACTIVE', 0)
  RETURNING id INTO v_refresh_product_id;
  INSERT INTO hhy.prop_products(type, name, duration_seconds, execution_type, status, version)
  VALUES ('HEADLINE', '头条道具', '86400', 'SCHEDULED', 'ACTIVE', 0)
  RETURNING id INTO v_headline_product_id;

  INSERT INTO hhy.prop_skus(prop_id, product_sku_id, scope_json, status, version)
  VALUES (v_refresh_product_id, v_product_sku_id, '{}', 'ACTIVE', 0)
  RETURNING id INTO v_refresh_sku_id;

  INSERT INTO hhy.product_skus(
    product_id, code, name, price_cent, duration_days, benefits_json, status, version
  ) VALUES (
    v_product_id, 'PROP-R19-HEADLINE', '头条道具', 500, 1,
    '[{"benefitCode":"PROP_USE","name":"道具使用次数","value":1,"unit":"COUNT"}]',
    'ACTIVE', 0
  ) RETURNING id INTO v_product_sku_id;

  INSERT INTO hhy.prop_skus(prop_id, product_sku_id, scope_json, status, version)
  VALUES (v_headline_product_id, v_product_sku_id, '{}', 'ACTIVE', 0)
  RETURNING id INTO v_headline_sku_id;

  INSERT INTO hhy.user_props(
    user_id, prop_sku_id, quantity, status, expires_at, version
  ) VALUES (
    v_user_id, v_refresh_sku_id, 2, 'AVAILABLE', clock_timestamp() + interval '1 day', 0
  ) RETURNING id INTO v_refresh_inventory_id;
  INSERT INTO hhy.user_props(
    user_id, prop_sku_id, quantity, status, expires_at, version
  ) VALUES (
    v_user_id, v_headline_sku_id, 2, 'AVAILABLE', clock_timestamp() + interval '2 days', 0
  ) RETURNING id INTO v_headline_inventory_id;

  INSERT INTO hhy.content_posts(owner_id, type, title, status)
  VALUES (v_user_id, 'PROJECT', 'R19 prop target', 'DRAFT') RETURNING id INTO v_content_id;
  INSERT INTO hhy.project_details(content_id, cooperation)
  VALUES (v_content_id, 'R19 invariant fixture');

  BEGIN
    UPDATE hhy.user_props SET quantity = -1 WHERE id = v_refresh_inventory_id;
    RAISE EXCEPTION 'R19_NEGATIVE_INVENTORY_ACCEPTED';
  EXCEPTION WHEN check_violation THEN NULL;
  END;

  SELECT count(*) INTO v_history_before
  FROM hhy.outbox_events
  WHERE aggregate_type = 'user_props' AND aggregate_id = v_refresh_inventory_id::text
    AND event_type = 'platform.status.changed.v1';
  UPDATE hhy.user_props
  SET status = 'RESERVED', version = version + 1
  WHERE id = v_refresh_inventory_id AND version = 0;
  SELECT count(*) INTO v_history_after
  FROM hhy.outbox_events
  WHERE aggregate_type = 'user_props' AND aggregate_id = v_refresh_inventory_id::text
    AND event_type = 'platform.status.changed.v1';
  IF v_history_after <> v_history_before + 1 THEN
    RAISE EXCEPTION 'R19_USER_PROP_STATUS_HISTORY_MISSING';
  END IF;

  BEGIN
    UPDATE hhy.user_props
    SET status = 'UNKNOWN', version = version + 1
    WHERE id = v_refresh_inventory_id;
    RAISE EXCEPTION 'R19_INVALID_USER_PROP_TRANSITION_ACCEPTED';
  EXCEPTION WHEN check_violation THEN NULL;
  END;

  INSERT INTO hhy.content_refresh_records(
    content_id, user_prop_id, layers, executed_at, idempotency_key, request_hash
  ) VALUES (
    v_content_id, v_refresh_inventory_id, 'REFRESH', clock_timestamp(),
    'r19-refresh-idem-0001', repeat('a', 64)
  );

  BEGIN
    INSERT INTO hhy.content_refresh_records(
      content_id, user_prop_id, layers, executed_at, idempotency_key, request_hash
    ) VALUES (
      v_content_id, v_refresh_inventory_id, 'REFRESH', clock_timestamp(),
      'r19-refresh-idem-0001', repeat('a', 64)
    );
    RAISE EXCEPTION 'R19_DUPLICATE_REFRESH_IDEMPOTENCY_ACCEPTED';
  EXCEPTION WHEN unique_violation THEN NULL;
  END;

  BEGIN
    UPDATE hhy.content_refresh_records
    SET executed_at = clock_timestamp()
    WHERE user_prop_id = v_refresh_inventory_id;
    RAISE EXCEPTION 'R19_REFRESH_FACT_MUTATION_ACCEPTED';
  EXCEPTION WHEN object_not_in_prerequisite_state THEN NULL;
  END;

  INSERT INTO hhy.headline_slots(page_code, slot_code, capacity, status, version)
  VALUES ('HOME', 'TOP', 1, 'ACTIVE', 0) RETURNING id INTO v_slot_id;
  INSERT INTO hhy.content_exposure_entitlements(
    content_id, user_prop_id, type, starts_at, ends_at, status, version
  ) VALUES (
    v_content_id, v_headline_inventory_id, 'HEADLINE', v_start, v_end, 'SCHEDULED', 0
  ) RETURNING id INTO v_entitlement_id;
  INSERT INTO hhy.headline_slot_bookings(
    slot_id, content_id, entitlement_id, starts_at, ends_at, status, version
  ) VALUES (
    v_slot_id, v_content_id, v_entitlement_id, v_start, v_end, 'BOOKED', 0
  );

  INSERT INTO hhy.content_exposure_entitlements(
    content_id, user_prop_id, type, starts_at, ends_at, status, version
  ) VALUES (
    v_content_id, v_headline_inventory_id, 'HEADLINE', v_start, v_end, 'SCHEDULED', 0
  ) RETURNING id INTO v_second_entitlement_id;
  BEGIN
    INSERT INTO hhy.headline_slot_bookings(
      slot_id, content_id, entitlement_id, starts_at, ends_at, status, version
    ) VALUES (
      v_slot_id, v_content_id, v_second_entitlement_id, v_start, v_end, 'BOOKED', 0
    );
    RAISE EXCEPTION 'R19_HEADLINE_CAPACITY_OVERBOOK_ACCEPTED';
  EXCEPTION WHEN unique_violation THEN NULL;
  END;

  INSERT INTO hhy.prop_execution_logs(
    user_prop_id, content_id, result, status, operation_id,
    idempotency_key, request_hash, version
  ) VALUES (
    v_headline_inventory_id, v_content_id, '{"status":"SUCCEEDED"}', 'SUCCEEDED',
    'propPostMePropsByIdUse', 'r19-execution-idem-0001', repeat('b', 64), 0
  );
  BEGIN
    INSERT INTO hhy.prop_execution_logs(
      user_prop_id, content_id, result, status, operation_id,
      idempotency_key, request_hash, version
    ) VALUES (
      v_headline_inventory_id, v_content_id, '{"status":"SUCCEEDED"}', 'SUCCEEDED',
      'propPostMePropsByIdUse', 'r19-execution-idem-0001', repeat('b', 64), 0
    );
    RAISE EXCEPTION 'R19_DUPLICATE_EXECUTION_IDEMPOTENCY_ACCEPTED';
  EXCEPTION WHEN unique_violation THEN NULL;
  END;

  INSERT INTO hhy.orders(
    order_no, user_id, biz_type, biz_id, amount_cent, status, currency,
    no_refund_confirmed, no_refund_agreement_version, no_refund_confirmed_at,
    idempotency_key, request_hash, version
  ) VALUES (
    'R19-PROP-ORDER-0001', v_user_id, 'PROP', v_headline_sku_id, 500,
    'PENDING_PAYMENT', 'CNY', true, 'prop-no-refund-v1', clock_timestamp(),
    'r19-order-idempotency-0001', repeat('c', 64), 0
  ) RETURNING id INTO v_order_id;
  INSERT INTO hhy.order_items(
    order_id, sku_id, item_name, quantity, unit_price,
    subtotal_amount_cent, snapshot_json
  ) VALUES (
    v_order_id, v_product_sku_id, '头条道具', 1, 500, 500,
    '{"name":"头条道具","skuCode":"PROP-R19-HEADLINE"}'
  );
  INSERT INTO hhy.order_price_snapshots(
    order_id, original, discount, service_fee, payable,
    rule_versions, rule_versions_json
  ) VALUES (
    v_order_id, 500, 0, 0, 500, 'r19-prop-price-v1', '["r19-prop-price-v1"]'
  );
  UPDATE hhy.orders
  SET status = 'PAYMENT_PROCESSING', version = version + 1
  WHERE id = v_order_id;
  UPDATE hhy.orders
  SET status = 'PAID', paid_amount_cent = amount_cent,
      paid_at = clock_timestamp(), version = version + 1
  WHERE id = v_order_id;
  IF (SELECT status FROM hhy.orders WHERE id = v_order_id) <> 'COMPLETED' THEN
    RAISE EXCEPTION 'R19_PROP_ORDER_NOT_FULFILLED';
  END IF;
  IF (SELECT count(*) FROM hhy.user_props
      WHERE user_id = v_user_id AND prop_sku_id = v_headline_sku_id
        AND acquired_order_id = v_order_id AND quantity = 1 AND status = 'AVAILABLE') <> 1 THEN
    RAISE EXCEPTION 'R19_PROP_ORDER_INVENTORY_MISSING';
  END IF;

  IF (SELECT count(*) FROM hhy.system_configs
      WHERE scope = 'GLOBAL' AND key LIKE 'props.%') < 11 THEN
    RAISE EXCEPTION 'R19_PROP_CONFIG_DEFAULTS_INCOMPLETE';
  END IF;
  IF (SELECT count(*) FROM hhy.admin_permissions
      WHERE code IN ('prop.read', 'prop.write', 'prop.slot.read', 'prop.slot.write')) <> 4 THEN
    RAISE EXCEPTION 'R19_PROP_PERMISSION_SET_INCOMPLETE';
  END IF;
  IF (SELECT count(*)
      FROM hhy.admin_role_permissions grant_row
      JOIN hhy.admin_roles role ON role.id = grant_row.role_id
      JOIN hhy.admin_permissions permission ON permission.id = grant_row.permission_id
      WHERE role.code = 'SUPER_ADMIN' AND role.status = 'ACTIVE'
        AND permission.code IN ('prop.read', 'prop.write', 'prop.slot.read', 'prop.slot.write')) <> 4 THEN
    RAISE EXCEPTION 'R19_SUPER_ADMIN_PROP_PERMISSIONS_INCOMPLETE';
  END IF;
END;
$$;

SELECT 'R19_PROP_INVARIANT_PROPERTY_MATRIX PASS' AS result;
ROLLBACK;
