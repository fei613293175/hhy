BEGIN;
SET search_path TO hhy, public;

DO $$
DECLARE
  user_id bigint;
  order_id bigint;
  payment_id bigint;
  admin_id bigint;
  exception_id bigint;
BEGIN
  INSERT INTO hhy.users(phone, status, invite_code)
  VALUES ('13900005017', 'ACTIVE', 'R17PAY') RETURNING id INTO user_id;
  INSERT INTO hhy.admin_users(username, password_hash, status)
  VALUES ('r17-payment-test', repeat('a', 64), 'ACTIVE') RETURNING id INTO admin_id;

  INSERT INTO hhy.orders(
    order_no, user_id, biz_type, amount_cent, status, currency,
    idempotency_key, request_hash, no_refund_confirmed,
    no_refund_agreement_version, no_refund_confirmed_at
  ) VALUES (
    'R17-PAY-ORDER-001', user_id, 'MEMBERSHIP', 1990, 'PENDING_PAYMENT', 'CNY',
    'r17-order-idempotency-0001', repeat('a', 64), true,
    'no-refund-v1', clock_timestamp()
  ) RETURNING id INTO order_id;
  INSERT INTO hhy.order_price_snapshots(
    order_id, original, discount, service_fee, payable, rule_versions_json
  ) VALUES (order_id, 2090, 100, 0, 1990, '["r17-price-v1"]');

  INSERT INTO hhy.payment_transactions(
    order_id, gateway, amount, status, currency, reconciliation_status,
    idempotency_key, request_hash
  ) VALUES (
    order_id, 'ALIPAY', 1990, 'PENDING', 'CNY', 'NOT_RECONCILED',
    'r17-payment-idempotency-0001', repeat('b', 64)
  ) RETURNING id INTO payment_id;

  BEGIN
    INSERT INTO hhy.payment_transactions(
      order_id, gateway, amount, status, currency,
      idempotency_key, request_hash
    ) VALUES (
      order_id, 'ALIPAY', -1, 'PENDING', 'CNY',
      'r17-payment-idempotency-0002', repeat('c', 64)
    );
    RAISE EXCEPTION 'R17_NEGATIVE_PAYMENT_ACCEPTED';
  EXCEPTION WHEN check_violation THEN NULL;
  END;

  BEGIN
    UPDATE hhy.payment_transactions SET status = 'PAID' WHERE id = payment_id;
    RAISE EXCEPTION 'R17_PAID_WITHOUT_TIMESTAMP_ACCEPTED';
  EXCEPTION WHEN check_violation THEN NULL;
  END;

  INSERT INTO hhy.payment_callbacks(
    gateway, payload_cipher, signature_valid, processed, notification_id,
    event_type, order_no, amount_cent, currency, callback_status,
    payload_sha256, provider_timestamp, provider_nonce, processed_at
  ) VALUES (
    'ALIPAY', 'ciphertext-envelope', true, true, 'notify-r17-001',
    'PAYMENT_SUCCEEDED', 'R17-PAY-ORDER-001', 1990, 'CNY', 'PAID',
    repeat('d', 64), clock_timestamp(), 'nonce-r17-001', clock_timestamp()
  );

  BEGIN
    INSERT INTO hhy.payment_callbacks(
      gateway, payload_cipher, signature_valid, processed, notification_id,
      event_type, order_no, callback_status, payload_sha256,
      provider_timestamp, provider_nonce, processed_at
    ) VALUES (
      'ALIPAY', 'duplicate', true, true, 'notify-r17-001',
      'PAYMENT_SUCCEEDED', 'R17-PAY-ORDER-001', 'PAID', repeat('e', 64),
      clock_timestamp(), 'nonce-r17-002', clock_timestamp()
    );
    RAISE EXCEPTION 'R17_DUPLICATE_NOTIFICATION_ACCEPTED';
  EXCEPTION WHEN unique_violation THEN NULL;
  END;

  BEGIN
    INSERT INTO hhy.payment_callbacks(
      gateway, payload_cipher, signature_valid, processed, notification_id,
      event_type, order_no, callback_status, payload_sha256,
      provider_timestamp, provider_nonce
    ) VALUES (
      'ALIPAY', 'invalid-processing-pair', true, true, 'notify-r17-002',
      'PAYMENT_SUCCEEDED', 'R17-PAY-ORDER-001', 'PAID', repeat('f', 64),
      clock_timestamp(), 'nonce-r17-003'
    );
    RAISE EXCEPTION 'R17_CALLBACK_PROCESSING_PAIR_ACCEPTED';
  EXCEPTION WHEN check_violation THEN NULL;
  END;

  INSERT INTO hhy.payment_exception_orders(order_id, type, status)
  VALUES (order_id, 'AMOUNT_MISMATCH', 'OPEN') RETURNING id INTO exception_id;
  UPDATE hhy.payment_exception_orders
  SET status = 'RESOLVED', resolution = '确认供应商入账金额正确',
      reason = '人工复核订单与回调原始证据一致', resolved_by_admin_id = admin_id,
      resolved_at = clock_timestamp(), version = version + 1
  WHERE id = exception_id AND version = 0;
  IF NOT FOUND THEN RAISE EXCEPTION 'R17_EXCEPTION_OPTIMISTIC_UPDATE_FAILED'; END IF;

  BEGIN
    INSERT INTO hhy.payment_reconciliation_records(
      recon_date, gateway, result, diff_json, status,
      transaction_count, difference_count, initiated_by_admin_id, completed_at
    ) VALUES (
      current_date, 'ALIPAY', '{}'::jsonb, '[]'::jsonb, 'COMPLETED',
      1, 2, admin_id, clock_timestamp()
    );
    RAISE EXCEPTION 'R17_INVALID_RECONCILIATION_COUNTS_ACCEPTED';
  EXCEPTION WHEN check_violation THEN NULL;
  END;

  IF (SELECT count(*) FROM hhy.admin_permissions WHERE code IN (
      'payment.read', 'payment.manage', 'payment.reconcile', 'payment.query',
      'payment.exception.read', 'payment.exception.resolve')) <> 6 THEN
    RAISE EXCEPTION 'R17_PAYMENT_PERMISSION_SET_INCOMPLETE';
  END IF;
END;
$$;

SELECT 'R17_PAYMENT_INVARIANTS PASS' AS result;
ROLLBACK;
