\set ON_ERROR_STOP on
SET search_path TO hhy, public;
BEGIN;

DO $$
DECLARE
  v_user_id bigint;
BEGIN
  INSERT INTO hhy.users(phone, status, invite_code)
  VALUES ('13900005724', 'ACTIVE', 'R24TEST')
  RETURNING id INTO v_user_id;

  BEGIN
    INSERT INTO hhy.reward_accounts(user_id, pending, available, frozen, withdrawing, withdrawn)
    VALUES (v_user_id, -1, 0, 0, 0, 0);
    RAISE EXCEPTION 'ASSERTION_FAILED: negative reward balance unexpectedly succeeded';
  EXCEPTION WHEN check_violation THEN NULL;
  END;

  INSERT INTO hhy.reward_accounts(user_id, pending, available, frozen, withdrawing, withdrawn)
  VALUES (v_user_id, 0, 1000, 0, 0, 0);

  INSERT INTO hhy.payout_accounts(user_id, account_cipher, account_hash, account_masked, real_name, status)
  VALUES (v_user_id, 'hhy-r24-payout-v1.test.test', repeat('a', 64), '138****0000', '测试账户', 'ACTIVE');

  BEGIN
    INSERT INTO hhy.payout_accounts(user_id, account_cipher, account_hash, account_masked, real_name, status)
    VALUES (v_user_id, 'hhy-r24-payout-v1.test.other', repeat('b', 64), '139****0000', '重复账户', 'ACTIVE');
    RAISE EXCEPTION 'ASSERTION_FAILED: duplicate payout account unexpectedly succeeded';
  EXCEPTION WHEN unique_violation THEN NULL;
  END;

  BEGIN
    INSERT INTO hhy.reward_accounts(user_id) VALUES (v_user_id);
    RAISE EXCEPTION 'ASSERTION_FAILED: duplicate reward account unexpectedly succeeded';
  EXCEPTION WHEN unique_violation THEN NULL;
  END;

  INSERT INTO hhy.reward_ledger(user_id, source_type, amount, status, biz_id, balance_after)
  VALUES (v_user_id, 'R24_TEST', 1000, 'AVAILABLE', 900001, 1000);

  BEGIN
    INSERT INTO hhy.reward_ledger(user_id, source_type, amount, status, biz_id, balance_after)
    VALUES (v_user_id, 'R24_TEST', 1000, 'AVAILABLE', 900001, 2000);
    RAISE EXCEPTION 'ASSERTION_FAILED: duplicate reward ledger key unexpectedly succeeded';
  EXCEPTION WHEN unique_violation THEN NULL;
  END;

  BEGIN
    INSERT INTO hhy.reward_ledger(user_id, source_type, amount, status, biz_id, balance_after)
    VALUES (v_user_id, 'R24_TEST', 0, 'AVAILABLE', 900002, 1000);
    RAISE EXCEPTION 'ASSERTION_FAILED: zero reward amount unexpectedly succeeded';
  EXCEPTION WHEN check_violation THEN NULL;
  END;

  INSERT INTO hhy.withdrawal_requests(withdraw_no, user_id, amount, fee, net_amount, status)
  VALUES ('R24-TEST-001', v_user_id, 1000, 100, 900, 'CREATED');

  BEGIN
    INSERT INTO hhy.withdrawal_requests(withdraw_no, user_id, amount, fee, net_amount, status)
    VALUES ('R24-TEST-002', v_user_id, 1000, 100, 901, 'CREATED');
    RAISE EXCEPTION 'ASSERTION_FAILED: inconsistent withdrawal net amount unexpectedly succeeded';
  EXCEPTION WHEN check_violation THEN NULL;
  END;
END;
$$;

ROLLBACK;
