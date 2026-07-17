\set ON_ERROR_STOP on
SET search_path TO hhy, public;
BEGIN;

CREATE OR REPLACE FUNCTION pg_temp.assert_true(p_condition boolean, p_message text)
RETURNS void
LANGUAGE plpgsql
AS $$
BEGIN
  IF p_condition IS NOT TRUE THEN
    RAISE EXCEPTION 'ASSERTION_FAILED: %', p_message;
  END IF;
END;
$$;

CREATE OR REPLACE FUNCTION pg_temp.expect_sqlstate(
  p_sql text,
  p_expected_state text,
  p_message text
)
RETURNS void
LANGUAGE plpgsql
AS $$
DECLARE
  v_state text;
BEGIN
  BEGIN
    EXECUTE p_sql;
  EXCEPTION WHEN OTHERS THEN
    GET STACKED DIAGNOSTICS v_state = RETURNED_SQLSTATE;
    IF v_state <> p_expected_state THEN
      RAISE EXCEPTION 'ASSERTION_FAILED: %, expected SQLSTATE %, got %',
        p_message, p_expected_state, v_state;
    END IF;
    RETURN;
  END;
  RAISE EXCEPTION 'ASSERTION_FAILED: %, statement unexpectedly succeeded', p_message;
END;
$$;

-- Outbox delivery state, immutability, dead-letter replay and status history.
INSERT INTO hhy.outbox_events(
  aggregate_id, aggregate_type, event_id, event_type, payload
) VALUES (
  'P00-OUTBOX-AGG-1', 'P00_TEST', 'P00-OUTBOX-EVENT-1', 'p00.test.v1', '{"value":1}'
);

UPDATE hhy.outbox_events
SET status = 'PUBLISHING', attempts = attempts + 1
WHERE event_id = 'P00-OUTBOX-EVENT-1';
SELECT pg_temp.expect_sqlstate(
  $$UPDATE hhy.outbox_events SET status = 'PENDING' WHERE event_id = 'P00-OUTBOX-EVENT-1'$$,
  '23514', 'outbox must reject an invalid transition'
);
UPDATE hhy.outbox_events
SET status = 'RETRY_WAIT', last_error = 'temporary failure', available_at = clock_timestamp()
WHERE event_id = 'P00-OUTBOX-EVENT-1';
UPDATE hhy.outbox_events
SET status = 'PUBLISHING', attempts = attempts + 1, last_error = NULL
WHERE event_id = 'P00-OUTBOX-EVENT-1';
UPDATE hhy.outbox_events
SET status = 'PUBLISHED', published_at = clock_timestamp()
WHERE event_id = 'P00-OUTBOX-EVENT-1';
SELECT pg_temp.expect_sqlstate(
  $$UPDATE hhy.outbox_events SET payload = '{"value":2}' WHERE event_id = 'P00-OUTBOX-EVENT-1'$$,
  '55000', 'published outbox event must be immutable'
);
SELECT pg_temp.expect_sqlstate(
  $$DELETE FROM hhy.outbox_events WHERE event_id = 'P00-OUTBOX-EVENT-1'$$,
  '55000', 'outbox event facts and status history must not be deleted'
);
SELECT pg_temp.assert_true(
  (SELECT attempts = 2 AND published_at IS NOT NULL
   FROM hhy.outbox_events WHERE event_id = 'P00-OUTBOX-EVENT-1'),
  'outbox delivery metadata did not reach the published state'
);

INSERT INTO hhy.outbox_events(
  aggregate_id, aggregate_type, event_id, event_type, payload
) VALUES (
  'P00-OUTBOX-AGG-2', 'P00_TEST', 'P00-OUTBOX-EVENT-2', 'p00.test.v1', '{}'
);
UPDATE hhy.outbox_events
SET status = 'PUBLISHING', attempts = attempts + 1
WHERE event_id = 'P00-OUTBOX-EVENT-2';
UPDATE hhy.outbox_events
SET status = 'DEAD_LETTER', last_error = 'permanent failure'
WHERE event_id = 'P00-OUTBOX-EVENT-2';
UPDATE hhy.outbox_events
SET status = 'PENDING', attempts = 0, last_error = NULL, published_at = NULL
WHERE event_id = 'P00-OUTBOX-EVENT-2';
SELECT pg_temp.expect_sqlstate(
  $$INSERT INTO hhy.outbox_events(
      aggregate_id, aggregate_type, event_id, event_type, status, attempts
    ) VALUES ('bad', 'P00_TEST', 'P00-OUTBOX-BAD', 'p00.test.v1', 'PENDING', 1)$$,
  '23514', 'pending outbox inserts must start with zero attempts'
);
SELECT pg_temp.expect_sqlstate(
  $$INSERT INTO hhy.outbox_events(
      aggregate_id, aggregate_type, event_id, event_type, status, attempts, published_at
    ) VALUES (
      'bad', 'P00_TEST', 'P00-OUTBOX-PREPUBLISHED', 'p00.test.v1',
      'PUBLISHED', 1, clock_timestamp()
    )$$,
  '23514', 'outbox inserts must not bypass the initial pending state'
);
SELECT pg_temp.assert_true(
  (SELECT count(*) >= 7 FROM hhy.outbox_events
   WHERE event_type = 'platform.status.changed.v1'
     AND aggregate_type = 'outbox_events'),
  'outbox status history events were not retained'
);

-- Inbox claim, replay and conflicting-payload/result protection.
DO $$
DECLARE
  v_inbox_id bigint;
  v_replay_id bigint;
  v_result jsonb;
BEGIN
  SELECT (hhy.claim_inbox_message(
    'P00-CONSUMER', 'P00-MESSAGE-1', repeat('a', 64)
  )).id INTO v_inbox_id;
  SELECT (hhy.claim_inbox_message(
    'P00-CONSUMER', 'P00-MESSAGE-1', repeat('a', 64)
  )).id INTO v_replay_id;
  IF v_inbox_id IS DISTINCT FROM v_replay_id THEN
    RAISE EXCEPTION 'ASSERTION_FAILED: inbox replay returned a different row';
  END IF;
  SELECT (hhy.complete_inbox_message(v_inbox_id, '{"ok":true}'::jsonb)).result
  INTO v_result;
  IF v_result IS DISTINCT FROM '{"ok":true}'::jsonb THEN
    RAISE EXCEPTION 'ASSERTION_FAILED: inbox completion result mismatch';
  END IF;
  PERFORM hhy.complete_inbox_message(v_inbox_id, '{"ok":true}'::jsonb);
END;
$$;
SELECT pg_temp.expect_sqlstate(
  $$SELECT hhy.claim_inbox_message('P00-CONSUMER', 'P00-MESSAGE-1', repeat('b', 64))$$,
  '23505', 'inbox replay with a different payload hash must fail'
);
SELECT pg_temp.expect_sqlstate(
  $$SELECT hhy.complete_inbox_message(
      (SELECT id FROM hhy.inbox_messages
       WHERE consumer = 'P00-CONSUMER' AND message_id = 'P00-MESSAGE-1'),
      '{"ok":false}'::jsonb
    )$$,
  '23505', 'completed inbox result must not be overwritten'
);
SELECT pg_temp.expect_sqlstate(
  $$UPDATE hhy.inbox_messages SET payload_hash = repeat('c', 64)
    WHERE consumer = 'P00-CONSUMER' AND message_id = 'P00-MESSAGE-1'$$,
  '55000', 'inbox payload identity must be immutable'
);
SELECT pg_temp.expect_sqlstate(
  $$DELETE FROM hhy.inbox_messages
    WHERE consumer = 'P00-CONSUMER' AND message_id = 'P00-MESSAGE-1'$$,
  '55000', 'completed inbox claims must not be deleted and replayed'
);
SELECT pg_temp.expect_sqlstate(
  $$INSERT INTO hhy.inbox_messages(consumer, message_id, payload_hash, result)
    VALUES ('P00-CONSUMER', 'P00-MESSAGE-PRECOMPLETED', repeat('d', 64), '{"ok":true}')$$,
  '23514', 'inbox inserts must not bypass claim/completion'
);

-- Ledger account state/version protection.
INSERT INTO hhy.ledger_accounts(
  account_no, owner_type, owner_id, account_type, currency, status
) VALUES
  ('P00-ACCOUNT-CASH', 'PLATFORM', 'P00-PLATFORM', 'CASH', 'CNY', 'ACTIVE'),
  ('P00-ACCOUNT-USER', 'USER', 'P00-USER', 'BALANCE', 'CNY', 'ACTIVE'),
  ('P00-ACCOUNT-USD', 'PLATFORM', 'P00-USD', 'CASH', 'USD', 'ACTIVE'),
  ('P00-ACCOUNT-STATE', 'USER', 'P00-STATE', 'BALANCE', 'CNY', 'ACTIVE');
UPDATE hhy.ledger_accounts
SET status = 'FROZEN', version = version + 1
WHERE account_no = 'P00-ACCOUNT-STATE';
SELECT pg_temp.expect_sqlstate(
  $$UPDATE hhy.ledger_accounts SET status = 'ACTIVE', version = version + 2
    WHERE account_no = 'P00-ACCOUNT-STATE'$$,
  '23514', 'ledger state update must increment version exactly once'
);
UPDATE hhy.ledger_accounts
SET status = 'ACTIVE', version = version + 1
WHERE account_no = 'P00-ACCOUNT-STATE';
UPDATE hhy.ledger_accounts
SET status = 'CLOSED', version = version + 1
WHERE account_no = 'P00-ACCOUNT-STATE';
SELECT pg_temp.expect_sqlstate(
  $$UPDATE hhy.ledger_accounts SET status = 'ACTIVE', version = version + 1
    WHERE account_no = 'P00-ACCOUNT-STATE'$$,
  '23514', 'closed ledger account must not reopen'
);
SELECT pg_temp.expect_sqlstate(
  $$INSERT INTO hhy.ledger_accounts(
      account_no, owner_type, owner_id, account_type, currency, status
    ) VALUES ('P00-ACCOUNT-PRECLOSED', 'USER', 'P00-PRECLOSED', 'BALANCE', 'CNY', 'CLOSED')$$,
  '23514', 'ledger account inserts must start active'
);

-- Valid posted fact, idempotency, immutability and reversal symmetry.
DO $$
DECLARE
  v_transaction_id bigint;
  v_reversal_id bigint;
  v_cash_id bigint;
  v_user_id bigint;
BEGIN
  SELECT id INTO v_cash_id FROM hhy.ledger_accounts WHERE account_no = 'P00-ACCOUNT-CASH';
  SELECT id INTO v_user_id FROM hhy.ledger_accounts WHERE account_no = 'P00-ACCOUNT-USER';
  INSERT INTO hhy.accounting_transactions(
    transaction_no, biz_type, biz_id, idempotency_key, status
  ) VALUES (
    'P00-TX-VALID', 'P00_TEST', 'P00-BIZ-VALID', 'P00-IDEM-VALID', 'POSTED'
  ) RETURNING id INTO v_transaction_id;
  INSERT INTO hhy.accounting_entries(
    transaction_id, account_id, direction, amount_cent, currency, sequence
  ) VALUES
    (v_transaction_id, v_cash_id, 'DEBIT', 100, 'CNY', 1),
    (v_transaction_id, v_user_id, 'CREDIT', 100, 'CNY', 2);
  SET CONSTRAINTS ALL IMMEDIATE;
  SET CONSTRAINTS ALL DEFERRED;

  INSERT INTO hhy.accounting_transactions(
    transaction_no, biz_type, biz_id, idempotency_key, reversal_of_id, status
  ) VALUES (
    'P00-TX-REVERSAL', 'P00_TEST', 'P00-BIZ-REVERSAL', 'P00-IDEM-REVERSAL',
    v_transaction_id, 'POSTED'
  ) RETURNING id INTO v_reversal_id;
  INSERT INTO hhy.accounting_entries(
    transaction_id, account_id, direction, amount_cent, currency, sequence
  ) VALUES
    (v_reversal_id, v_user_id, 'DEBIT', 100, 'CNY', 1),
    (v_reversal_id, v_cash_id, 'CREDIT', 100, 'CNY', 2);
  SET CONSTRAINTS ALL IMMEDIATE;
  SET CONSTRAINTS ALL DEFERRED;
END;
$$;
SELECT pg_temp.expect_sqlstate(
  $$INSERT INTO hhy.accounting_transactions(
      transaction_no, biz_type, biz_id, idempotency_key, status
    ) VALUES ('P00-TX-BAD-STATUS', 'P00_TEST', 'P00-BIZ-BAD-STATUS',
      'P00-IDEM-BAD-STATUS', 'DRAFT')$$,
  '23514', 'non-posted status must not bypass accounting checks'
);
SELECT pg_temp.expect_sqlstate(
  $$INSERT INTO hhy.accounting_transactions(
      transaction_no, biz_type, biz_id, idempotency_key, status
    ) VALUES ('P00-TX-DUP-IDEM', 'P00_TEST', 'P00-BIZ-DUP-IDEM',
      'P00-IDEM-VALID', 'POSTED')$$,
  '23505', 'accounting idempotency key must be unique within biz type'
);
SELECT pg_temp.expect_sqlstate(
  $$INSERT INTO hhy.accounting_transactions(
      transaction_no, biz_type, biz_id, idempotency_key, reversal_of_id, status
    ) VALUES (
      'P00-TX-DUP-REVERSAL', 'P00_TEST', 'P00-BIZ-DUP-REVERSAL',
      'P00-IDEM-DUP-REVERSAL',
      (SELECT id FROM hhy.accounting_transactions WHERE transaction_no = 'P00-TX-VALID'),
      'POSTED'
    )$$,
  '23505', 'an accounting transaction may be reversed only once'
);
SELECT set_config('hhy.allow_immutable_mutation', 'on', true);
SELECT pg_temp.expect_sqlstate(
  $$UPDATE hhy.accounting_transactions SET description = 'tampered'
    WHERE transaction_no = 'P00-TX-VALID'$$,
  '55000', 'application GUC must not bypass immutable accounting facts'
);

DO $$
DECLARE
  v_transaction_id bigint;
  v_cash_id bigint;
  v_closed_id bigint;
  v_failed boolean := false;
BEGIN
  SELECT id INTO v_cash_id FROM hhy.ledger_accounts WHERE account_no = 'P00-ACCOUNT-CASH';
  SELECT id INTO v_closed_id FROM hhy.ledger_accounts WHERE account_no = 'P00-ACCOUNT-STATE';
  BEGIN
    INSERT INTO hhy.accounting_transactions(
      transaction_no, biz_type, biz_id, idempotency_key
    ) VALUES (
      'P00-TX-CLOSED-ACCOUNT', 'P00_TEST_BAD', 'P00-BIZ-CLOSED-ACCOUNT',
      'P00-IDEM-CLOSED-ACCOUNT'
    ) RETURNING id INTO v_transaction_id;
    INSERT INTO hhy.accounting_entries(
      transaction_id, account_id, direction, amount_cent, currency, sequence
    ) VALUES
      (v_transaction_id, v_cash_id, 'DEBIT', 100, 'CNY', 1),
      (v_transaction_id, v_closed_id, 'CREDIT', 100, 'CNY', 2);
  EXCEPTION WHEN check_violation THEN
    v_failed := true;
  END;
  IF NOT v_failed THEN
    RAISE EXCEPTION 'ASSERTION_FAILED: posting to a closed account succeeded';
  END IF;
END;
$$;

DO $$
DECLARE
  v_transaction_id bigint;
  v_cash_id bigint;
  v_user_id bigint;
  v_failed boolean := false;
BEGIN
  SELECT id INTO v_cash_id FROM hhy.ledger_accounts WHERE account_no = 'P00-ACCOUNT-CASH';
  SELECT id INTO v_user_id FROM hhy.ledger_accounts WHERE account_no = 'P00-ACCOUNT-USER';
  BEGIN
    INSERT INTO hhy.accounting_transactions(
      transaction_no, biz_type, biz_id, idempotency_key
    ) VALUES (
      'P00-TX-UNBALANCED', 'P00_TEST_BAD', 'P00-BIZ-UNBALANCED', 'P00-IDEM-UNBALANCED'
    ) RETURNING id INTO v_transaction_id;
    INSERT INTO hhy.accounting_entries(
      transaction_id, account_id, direction, amount_cent, currency, sequence
    ) VALUES
      (v_transaction_id, v_cash_id, 'DEBIT', 100, 'CNY', 1),
      (v_transaction_id, v_user_id, 'CREDIT', 99, 'CNY', 2);
    SET CONSTRAINTS ALL IMMEDIATE;
  EXCEPTION WHEN check_violation THEN
    v_failed := true;
  END;
  IF NOT v_failed THEN
    RAISE EXCEPTION 'ASSERTION_FAILED: unbalanced accounting transaction succeeded';
  END IF;
  SET CONSTRAINTS ALL DEFERRED;
END;
$$;

DO $$
DECLARE
  v_original_id bigint;
  v_reversal_id bigint;
  v_cash_id bigint;
  v_user_id bigint;
  v_failed boolean := false;
BEGIN
  INSERT INTO hhy.ledger_accounts(
    account_no, owner_type, owner_id, account_type, currency, status
  ) VALUES (
    'P00-ACCOUNT-BAD-REVERSAL-CASH', 'PLATFORM', 'P00-BAD-REVERSAL-CASH',
    'CASH', 'CNY', 'ACTIVE'
  ) RETURNING id INTO v_cash_id;
  INSERT INTO hhy.ledger_accounts(
    account_no, owner_type, owner_id, account_type, currency, status
  ) VALUES (
    'P00-ACCOUNT-BAD-REVERSAL-USER', 'USER', 'P00-BAD-REVERSAL-USER',
    'BALANCE', 'CNY', 'ACTIVE'
  ) RETURNING id INTO v_user_id;
  INSERT INTO hhy.accounting_transactions(
    transaction_no, biz_type, biz_id, idempotency_key, status
  ) VALUES (
    'P00-TX-BAD-REVERSAL-BASE', 'P00_TEST_BAD', 'P00-BIZ-BAD-REVERSAL-BASE',
    'P00-IDEM-BAD-REVERSAL-BASE', 'POSTED'
  ) RETURNING id INTO v_original_id;
  INSERT INTO hhy.accounting_entries(
    transaction_id, account_id, direction, amount_cent, currency, sequence
  ) VALUES
    (v_original_id, v_cash_id, 'DEBIT', 100, 'CNY', 1),
    (v_original_id, v_user_id, 'CREDIT', 100, 'CNY', 2);
  SET CONSTRAINTS ALL IMMEDIATE;
  SET CONSTRAINTS ALL DEFERRED;
  BEGIN
    INSERT INTO hhy.accounting_transactions(
      transaction_no, biz_type, biz_id, idempotency_key, reversal_of_id
    ) VALUES (
      'P00-TX-BAD-REVERSAL', 'P00_TEST_BAD', 'P00-BIZ-BAD-REVERSAL',
      'P00-IDEM-BAD-REVERSAL', v_original_id
    ) RETURNING id INTO v_reversal_id;
    INSERT INTO hhy.accounting_entries(
      transaction_id, account_id, direction, amount_cent, currency, sequence
    ) VALUES
      (v_reversal_id, v_cash_id, 'DEBIT', 100, 'CNY', 1),
      (v_reversal_id, v_user_id, 'CREDIT', 100, 'CNY', 2);
    SET CONSTRAINTS ALL IMMEDIATE;
  EXCEPTION WHEN check_violation THEN
    v_failed := true;
  END;
  IF NOT v_failed THEN
    RAISE EXCEPTION 'ASSERTION_FAILED: non-opposite accounting reversal succeeded';
  END IF;
  SET CONSTRAINTS ALL DEFERRED;
END;
$$;

-- Snapshot reconstruction, cursor ownership and monotonicity.
DO $$
DECLARE
  v_cash_id bigint;
  v_user_id bigint;
  v_first_cash_entry bigint;
  v_later_cash_entry bigint;
  v_transaction_id bigint;
BEGIN
  SELECT id INTO v_cash_id FROM hhy.ledger_accounts WHERE account_no = 'P00-ACCOUNT-CASH';
  SELECT id INTO v_user_id FROM hhy.ledger_accounts WHERE account_no = 'P00-ACCOUNT-USER';
  SELECT e.id INTO v_first_cash_entry
  FROM hhy.accounting_entries e
  JOIN hhy.accounting_transactions t ON t.id = e.transaction_id
  WHERE t.transaction_no = 'P00-TX-VALID' AND e.account_id = v_cash_id;
  INSERT INTO hhy.balance_snapshots(account_id, as_of_entry_id, available_cent, frozen_cent)
  VALUES (v_cash_id, v_first_cash_entry, 100, 0);

  INSERT INTO hhy.accounting_transactions(
    transaction_no, biz_type, biz_id, idempotency_key
  ) VALUES (
    'P00-TX-SNAPSHOT', 'P00_TEST', 'P00-BIZ-SNAPSHOT', 'P00-IDEM-SNAPSHOT'
  ) RETURNING id INTO v_transaction_id;
  INSERT INTO hhy.accounting_entries(
    transaction_id, account_id, direction, amount_cent, currency, sequence
  ) VALUES
    (v_transaction_id, v_cash_id, 'DEBIT', 50, 'CNY', 1),
    (v_transaction_id, v_user_id, 'CREDIT', 50, 'CNY', 2);
  SET CONSTRAINTS ALL IMMEDIATE;
  SET CONSTRAINTS ALL DEFERRED;
  SELECT id INTO v_later_cash_entry
  FROM hhy.accounting_entries
  WHERE transaction_id = v_transaction_id AND account_id = v_cash_id;
  UPDATE hhy.balance_snapshots
  SET as_of_entry_id = v_later_cash_entry, available_cent = 50, frozen_cent = 0
  WHERE account_id = v_cash_id;
END;
$$;
SELECT pg_temp.expect_sqlstate(
  $$UPDATE hhy.balance_snapshots SET available_cent = available_cent + 1
    WHERE account_id = (SELECT id FROM hhy.ledger_accounts WHERE account_no = 'P00-ACCOUNT-CASH')$$,
  '23514', 'snapshot amounts must not change at the same cursor'
);
SELECT pg_temp.expect_sqlstate(
  $$UPDATE hhy.balance_snapshots
    SET as_of_entry_id = (
      SELECT e.id FROM hhy.accounting_entries e
      JOIN hhy.accounting_transactions t ON t.id = e.transaction_id
      WHERE t.transaction_no = 'P00-TX-VALID'
        AND e.account_id = hhy.balance_snapshots.account_id
    ), available_cent = 100, frozen_cent = 0
    WHERE account_id = (SELECT id FROM hhy.ledger_accounts WHERE account_no = 'P00-ACCOUNT-CASH')$$,
  '23514', 'snapshot cursor must not regress'
);
SELECT pg_temp.expect_sqlstate(
  $$INSERT INTO hhy.balance_snapshots(account_id, as_of_entry_id, available_cent, frozen_cent)
    VALUES (
      (SELECT id FROM hhy.ledger_accounts WHERE account_no = 'P00-ACCOUNT-USD'),
      (SELECT e.id FROM hhy.accounting_entries e
       JOIN hhy.accounting_transactions t ON t.id = e.transaction_id
       WHERE t.transaction_no = 'P00-TX-VALID' LIMIT 1),
      0, 0
    )$$,
  '23514', 'snapshot cursor must belong to the same account'
);

-- Reconciliation lifecycle, overlap exclusion, terminal rows and history.
INSERT INTO hhy.reconciliation_runs(
  run_no, scene, period_start, period_end, status
) VALUES (
  'P00-RECON-1', 'P00_SCENE', '2026-01-01T00:00:00Z', '2026-01-02T00:00:00Z', 'CREATED'
);
SELECT pg_temp.expect_sqlstate(
  $$INSERT INTO hhy.reconciliation_runs(
      run_no, scene, period_start, period_end, status, started_at
    ) VALUES (
      'P00-RECON-PRESTARTED', 'P00_OTHER_SCENE',
      '2026-01-01T00:00:00Z', '2026-01-02T00:00:00Z', 'RUNNING', clock_timestamp()
    )$$,
  '23514', 'reconciliation run inserts must start created'
);
UPDATE hhy.reconciliation_runs
SET status = 'RUNNING', started_at = clock_timestamp()
WHERE run_no = 'P00-RECON-1';
SELECT pg_temp.expect_sqlstate(
  $$INSERT INTO hhy.reconciliation_runs(
      run_no, scene, period_start, period_end, status
    ) VALUES (
      'P00-RECON-OVERLAP', 'P00_SCENE',
      '2026-01-01T12:00:00Z', '2026-01-03T00:00:00Z', 'CREATED'
    )$$,
  '23P01', 'active reconciliation windows must not overlap'
);
INSERT INTO hhy.reconciliation_differences(
  run_id, source_type, source_id, expected_cent, actual_cent, status
) VALUES (
  (SELECT id FROM hhy.reconciliation_runs WHERE run_no = 'P00-RECON-1'),
  'P00_SOURCE', 'P00-SOURCE-1', 100, 90, 'OPEN'
);
UPDATE hhy.reconciliation_differences
SET status = 'RESOLVED', resolution = 'verified adjustment'
WHERE source_type = 'P00_SOURCE' AND source_id = 'P00-SOURCE-1';
SELECT pg_temp.expect_sqlstate(
  $$UPDATE hhy.reconciliation_differences SET resolution = 'tampered'
    WHERE source_type = 'P00_SOURCE' AND source_id = 'P00-SOURCE-1'$$,
  '55000', 'resolved reconciliation difference must be terminal'
);
UPDATE hhy.reconciliation_runs
SET status = 'SUCCEEDED', finished_at = clock_timestamp()
WHERE run_no = 'P00-RECON-1';
SELECT pg_temp.expect_sqlstate(
  $$UPDATE hhy.reconciliation_runs SET finished_at = clock_timestamp()
    WHERE run_no = 'P00-RECON-1'$$,
  '55000', 'completed reconciliation run must be terminal'
);
INSERT INTO hhy.reconciliation_runs(
  run_no, scene, period_start, period_end, status
) VALUES (
  'P00-RECON-2', 'P00_SCENE', '2026-01-01T12:00:00Z', '2026-01-03T00:00:00Z', 'CREATED'
);
SELECT pg_temp.assert_true(
  (SELECT count(*) >= 6 FROM hhy.outbox_events
   WHERE event_type = 'platform.status.changed.v1'
     AND aggregate_type IN (
       'ledger_accounts', 'reconciliation_runs', 'reconciliation_differences'
     )),
  'ledger/reconciliation status history events were not retained'
);

ROLLBACK;
\echo 'P00_EVENT_LEDGER_INVARIANTS_OK'
