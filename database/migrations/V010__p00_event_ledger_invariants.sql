-- P00 eventing, ledger, balance projection and reconciliation invariants.
-- Forward-only migration. Do not edit V001..V009 after they may have been applied.
SET search_path TO hhy, public;

CREATE EXTENSION IF NOT EXISTS btree_gist;

-- Basic value and lifecycle constraints that were documented but not executable.
ALTER TABLE hhy.outbox_events
  ADD CONSTRAINT ck_outbox_events_attempts_nonnegative CHECK (attempts >= 0),
  ADD CONSTRAINT ck_outbox_events_event_version_positive CHECK (event_version > 0),
  ADD CONSTRAINT ck_outbox_events_identity_nonblank CHECK (
    btrim(event_id) <> '' AND btrim(event_type) <> '' AND
    btrim(aggregate_id) <> '' AND btrim(aggregate_type) <> ''
  ),
  ADD CONSTRAINT ck_outbox_events_delivery_state CHECK (
    (status = 'PENDING' AND attempts = 0 AND published_at IS NULL AND last_error IS NULL) OR
    (status = 'PUBLISHING' AND attempts > 0 AND published_at IS NULL AND last_error IS NULL) OR
    (status = 'PUBLISHED' AND attempts > 0 AND published_at IS NOT NULL AND last_error IS NULL) OR
    (status IN ('RETRY_WAIT', 'DEAD_LETTER') AND attempts > 0 AND published_at IS NULL
      AND btrim(COALESCE(last_error, '')) <> '')
  );

ALTER TABLE hhy.inbox_messages
  ADD CONSTRAINT ck_inbox_messages_identity_nonblank CHECK (
    btrim(consumer) <> '' AND btrim(message_id) <> ''
  ),
  ADD CONSTRAINT ck_inbox_messages_payload_hash CHECK (payload_hash ~ '^[0-9a-f]{64}$');

ALTER TABLE hhy.ledger_accounts
  ADD CONSTRAINT ck_ledger_accounts_version_nonnegative CHECK (version >= 0),
  ADD CONSTRAINT ck_ledger_accounts_identity_nonblank CHECK (
    btrim(account_no) <> '' AND btrim(account_type) <> '' AND
    btrim(owner_id) <> '' AND btrim(owner_type) <> '' AND btrim(currency) <> ''
  ),
  ADD CONSTRAINT ck_ledger_accounts_status CHECK (status IN ('ACTIVE', 'FROZEN', 'CLOSED'));

ALTER TABLE hhy.accounting_transactions
  ADD CONSTRAINT ck_accounting_transactions_posted_fact CHECK (status = 'POSTED'),
  ADD CONSTRAINT ck_accounting_transactions_identity_nonblank CHECK (
    btrim(transaction_no) <> '' AND btrim(biz_type) <> '' AND
    btrim(biz_id) <> '' AND btrim(idempotency_key) <> ''
  ),
  ADD CONSTRAINT ck_accounting_transactions_reversal_not_self CHECK (
    reversal_of_id IS NULL OR reversal_of_id <> id
  );

-- A posted fact may be fully reversed at most once. A second correction must
-- reverse the previous correction instead of duplicating the original amount.
CREATE UNIQUE INDEX uq_accounting_transactions_reversal_of_id
  ON hhy.accounting_transactions (reversal_of_id)
  WHERE reversal_of_id IS NOT NULL;

ALTER TABLE hhy.reconciliation_runs
  ADD CONSTRAINT ck_reconciliation_runs_period CHECK (period_start < period_end),
  ADD CONSTRAINT ck_reconciliation_runs_status CHECK (
    status IN ('CREATED', 'RUNNING', 'SUCCEEDED', 'FAILED', 'CANCELLED')
  ),
  ADD CONSTRAINT ck_reconciliation_runs_lifecycle_time CHECK (
    (status = 'CREATED' AND started_at IS NULL AND finished_at IS NULL) OR
    (status = 'RUNNING' AND started_at IS NOT NULL AND finished_at IS NULL) OR
    (status IN ('SUCCEEDED', 'FAILED', 'CANCELLED') AND started_at IS NOT NULL
      AND finished_at IS NOT NULL AND finished_at >= started_at)
  ),
  ADD CONSTRAINT ex_reconciliation_runs_active_window
    EXCLUDE USING gist (
      scene WITH =,
      tstzrange(period_start, period_end, '[)') WITH &&
    ) WHERE (status IN ('CREATED', 'RUNNING'));

ALTER TABLE hhy.reconciliation_differences
  ADD CONSTRAINT ck_reconciliation_differences_status CHECK (
    status IN ('OPEN', 'RESOLVED', 'IGNORED')
  ),
  ADD CONSTRAINT ck_reconciliation_differences_resolution CHECK (
    (status = 'OPEN' AND resolution IS NULL) OR
    (status IN ('RESOLVED', 'IGNORED') AND btrim(COALESCE(resolution, '')) <> '')
  );

-- Outbox rows are event facts. Delivery metadata may change only through a
-- deliberately small state machine; identity and payload are immutable.
CREATE OR REPLACE FUNCTION hhy.guard_outbox_event_mutation()
RETURNS trigger
LANGUAGE plpgsql
AS $$
BEGIN
  IF TG_OP = 'INSERT' THEN
    IF NEW.status <> 'PENDING' OR NEW.attempts <> 0
       OR NEW.published_at IS NOT NULL OR NEW.last_error IS NOT NULL THEN
      RAISE EXCEPTION 'OUTBOX_EVENT_MUST_START_PENDING event_id=%', NEW.event_id
        USING ERRCODE = '23514';
    END IF;
    RETURN NEW;
  END IF;

  IF TG_OP = 'DELETE' THEN
    RAISE EXCEPTION 'OUTBOX_EVENT_IMMUTABLE event_id=%', OLD.event_id
      USING ERRCODE = '55000';
  END IF;

  IF ROW(
       NEW.created_at, NEW.aggregate_id, NEW.aggregate_type, NEW.event_id,
       NEW.event_type, NEW.event_version, NEW.headers, NEW.payload
     ) IS DISTINCT FROM ROW(
       OLD.created_at, OLD.aggregate_id, OLD.aggregate_type, OLD.event_id,
       OLD.event_type, OLD.event_version, OLD.headers, OLD.payload
     ) THEN
    RAISE EXCEPTION 'OUTBOX_EVENT_IMMUTABLE event_id=%', OLD.event_id
      USING ERRCODE = '55000';
  END IF;

  IF NEW.status IS DISTINCT FROM OLD.status AND NOT (
    (OLD.status = 'PENDING' AND NEW.status = 'PUBLISHING') OR
    (OLD.status = 'PUBLISHING' AND NEW.status IN ('PUBLISHED', 'RETRY_WAIT', 'DEAD_LETTER')) OR
    (OLD.status = 'RETRY_WAIT' AND NEW.status IN ('PUBLISHING', 'DEAD_LETTER')) OR
    (OLD.status = 'DEAD_LETTER' AND NEW.status = 'PENDING')
  ) THEN
    RAISE EXCEPTION 'OUTBOX_INVALID_TRANSITION event_id=% from=% to=%',
      OLD.event_id, OLD.status, NEW.status USING ERRCODE = '23514';
  END IF;

  IF NEW.status = 'PUBLISHING' AND OLD.status <> 'PUBLISHING'
     AND NEW.attempts <> OLD.attempts + 1 THEN
    RAISE EXCEPTION 'OUTBOX_ATTEMPT_MUST_INCREMENT event_id=% old=% new=%',
      OLD.event_id, OLD.attempts, NEW.attempts USING ERRCODE = '23514';
  END IF;

  IF NEW.attempts IS DISTINCT FROM OLD.attempts
     AND NOT (NEW.status = 'PUBLISHING' AND OLD.status <> 'PUBLISHING')
     AND NOT (OLD.status = 'DEAD_LETTER' AND NEW.status = 'PENDING' AND NEW.attempts = 0) THEN
    RAISE EXCEPTION 'OUTBOX_ATTEMPT_CHANGE_FORBIDDEN event_id=%', OLD.event_id
      USING ERRCODE = '23514';
  END IF;

  IF OLD.status = 'DEAD_LETTER' AND NEW.status = 'PENDING'
     AND (NEW.attempts <> 0 OR NEW.last_error IS NOT NULL OR NEW.published_at IS NOT NULL) THEN
    RAISE EXCEPTION 'OUTBOX_REPLAY_MUST_RESET event_id=%', OLD.event_id
      USING ERRCODE = '23514';
  END IF;

  IF OLD.status = 'PUBLISHED' AND NEW IS DISTINCT FROM OLD THEN
    RAISE EXCEPTION 'OUTBOX_PUBLISHED_EVENT_TERMINAL event_id=%', OLD.event_id
      USING ERRCODE = '55000';
  END IF;
  RETURN NEW;
END;
$$;

CREATE TRIGGER trg_outbox_events_guard
BEFORE INSERT OR UPDATE OR DELETE ON hhy.outbox_events
FOR EACH ROW EXECUTE FUNCTION hhy.guard_outbox_event_mutation();

-- Inbox claims are serialized by consumer+message_id. The payload identity is
-- immutable and a completed result may only be replayed, never overwritten.
CREATE OR REPLACE FUNCTION hhy.guard_inbox_message_mutation()
RETURNS trigger
LANGUAGE plpgsql
AS $$
BEGIN
  IF TG_OP = 'INSERT' THEN
    IF NEW.result IS NOT NULL THEN
      RAISE EXCEPTION 'INBOX_MESSAGE_MUST_START_UNPROCESSED consumer=% message_id=%',
        NEW.consumer, NEW.message_id USING ERRCODE = '23514';
    END IF;
    RETURN NEW;
  END IF;


  IF TG_OP = 'DELETE' THEN
    RAISE EXCEPTION 'INBOX_MESSAGE_IMMUTABLE consumer=% message_id=%',
      OLD.consumer, OLD.message_id USING ERRCODE = '55000';
  END IF;

  IF ROW(NEW.created_at, NEW.consumer, NEW.message_id, NEW.payload_hash)
     IS DISTINCT FROM ROW(OLD.created_at, OLD.consumer, OLD.message_id, OLD.payload_hash) THEN
    RAISE EXCEPTION 'INBOX_IDENTITY_IMMUTABLE consumer=% message_id=%',
      OLD.consumer, OLD.message_id USING ERRCODE = '55000';
  END IF;
  IF OLD.result IS NOT NULL AND
     (NEW.result IS DISTINCT FROM OLD.result OR NEW.processed_at IS DISTINCT FROM OLD.processed_at) THEN
    RAISE EXCEPTION 'INBOX_RESULT_IMMUTABLE consumer=% message_id=%',
      OLD.consumer, OLD.message_id USING ERRCODE = '55000';
  END IF;
  IF OLD.result IS NULL AND NEW.result IS NULL THEN
    RAISE EXCEPTION 'INBOX_ONLY_COMPLETION_UPDATE_ALLOWED consumer=% message_id=%',
      OLD.consumer, OLD.message_id USING ERRCODE = '55000';
  END IF;
  RETURN NEW;
END;
$$;

CREATE TRIGGER trg_inbox_messages_guard
BEFORE INSERT OR UPDATE OR DELETE ON hhy.inbox_messages
FOR EACH ROW EXECUTE FUNCTION hhy.guard_inbox_message_mutation();

CREATE OR REPLACE FUNCTION hhy.claim_inbox_message(
  p_consumer varchar,
  p_message_id varchar,
  p_payload_hash varchar
)
RETURNS hhy.inbox_messages
LANGUAGE plpgsql
AS $$
DECLARE
  v_row hhy.inbox_messages%ROWTYPE;
BEGIN
  INSERT INTO hhy.inbox_messages(consumer, message_id, payload_hash)
  VALUES (p_consumer, p_message_id, p_payload_hash)
  ON CONFLICT (consumer, message_id) DO NOTHING;

  SELECT * INTO v_row
  FROM hhy.inbox_messages
  WHERE consumer = p_consumer AND message_id = p_message_id
  FOR UPDATE;

  IF v_row.payload_hash IS DISTINCT FROM p_payload_hash THEN
    RAISE EXCEPTION 'INBOX_PAYLOAD_HASH_CONFLICT consumer=% message_id=%',
      p_consumer, p_message_id USING ERRCODE = '23505';
  END IF;
  RETURN v_row;
END;
$$;

CREATE OR REPLACE FUNCTION hhy.complete_inbox_message(
  p_inbox_id bigint,
  p_result jsonb
)
RETURNS hhy.inbox_messages
LANGUAGE plpgsql
AS $$
DECLARE
  v_row hhy.inbox_messages%ROWTYPE;
BEGIN
  SELECT * INTO v_row FROM hhy.inbox_messages WHERE id = p_inbox_id FOR UPDATE;
  IF NOT FOUND THEN
    RAISE EXCEPTION 'INBOX_MESSAGE_NOT_FOUND id=%', p_inbox_id USING ERRCODE = 'P0002';
  END IF;
  IF v_row.result IS NULL THEN
    UPDATE hhy.inbox_messages
    SET result = p_result, processed_at = clock_timestamp()
    WHERE id = p_inbox_id
    RETURNING * INTO v_row;
  ELSIF v_row.result IS DISTINCT FROM p_result THEN
    RAISE EXCEPTION 'INBOX_RESULT_CONFLICT id=%', p_inbox_id USING ERRCODE = '23505';
  END IF;
  RETURN v_row;
END;
$$;

-- A ledger account has immutable identity. Only status and optimistic version
-- may change, and every state change increments version exactly once.
CREATE OR REPLACE FUNCTION hhy.guard_ledger_account_mutation()
RETURNS trigger
LANGUAGE plpgsql
AS $$
BEGIN
  IF TG_OP = 'INSERT' THEN
    IF NEW.status <> 'ACTIVE' OR NEW.version <> 0 THEN
      RAISE EXCEPTION 'LEDGER_ACCOUNT_MUST_START_ACTIVE account_no=%', NEW.account_no
        USING ERRCODE = '23514';
    END IF;
    RETURN NEW;
  END IF;

  IF ROW(NEW.created_at, NEW.account_no, NEW.account_type, NEW.currency, NEW.owner_id, NEW.owner_type)
     IS DISTINCT FROM ROW(OLD.created_at, OLD.account_no, OLD.account_type, OLD.currency, OLD.owner_id, OLD.owner_type) THEN
    RAISE EXCEPTION 'LEDGER_ACCOUNT_IDENTITY_IMMUTABLE account_no=%', OLD.account_no
      USING ERRCODE = '55000';
  END IF;
  IF NEW.status IS NOT DISTINCT FROM OLD.status OR NEW.version <> OLD.version + 1 THEN
    RAISE EXCEPTION 'LEDGER_ACCOUNT_VERSION_CONFLICT account_no=% expected_version=%',
      OLD.account_no, OLD.version USING ERRCODE = '23514';
  END IF;
  IF NOT (
    (OLD.status = 'ACTIVE' AND NEW.status IN ('FROZEN', 'CLOSED')) OR
    (OLD.status = 'FROZEN' AND NEW.status IN ('ACTIVE', 'CLOSED'))
  ) THEN
    RAISE EXCEPTION 'LEDGER_ACCOUNT_INVALID_TRANSITION account_no=% from=% to=%',
      OLD.account_no, OLD.status, NEW.status USING ERRCODE = '23514';
  END IF;
  RETURN NEW;
END;
$$;

CREATE TRIGGER trg_ledger_accounts_guard
BEFORE INSERT OR UPDATE ON hhy.ledger_accounts
FOR EACH ROW EXECUTE FUNCTION hhy.guard_ledger_account_mutation();

-- New postings require active accounts. A reversal remains possible after an
-- account is frozen or closed so immutable facts can still be corrected.
CREATE OR REPLACE FUNCTION hhy.guard_accounting_entry_insert()
RETURNS trigger
LANGUAGE plpgsql
AS $$
DECLARE
  v_account_status varchar;
  v_account_currency varchar;
  v_reversal_of_id bigint;
BEGIN
  SELECT status, currency INTO v_account_status, v_account_currency
  FROM hhy.ledger_accounts WHERE id = NEW.account_id;
  SELECT reversal_of_id INTO v_reversal_of_id
  FROM hhy.accounting_transactions WHERE id = NEW.transaction_id;
  IF v_account_status IS NULL OR NOT FOUND THEN
    RETURN NEW;
  END IF;
  IF NEW.currency IS DISTINCT FROM v_account_currency THEN
    RAISE EXCEPTION 'ACCOUNTING_ENTRY_CURRENCY_MISMATCH account_id=% account_currency=% entry_currency=%',
      NEW.account_id, v_account_currency, NEW.currency USING ERRCODE = '23514';
  END IF;
  IF v_reversal_of_id IS NULL AND v_account_status <> 'ACTIVE' THEN
    RAISE EXCEPTION 'ACCOUNTING_ACCOUNT_NOT_ACTIVE account_id=% status=%',
      NEW.account_id, v_account_status USING ERRCODE = '23514';
  END IF;
  RETURN NEW;
END;
$$;

CREATE TRIGGER trg_accounting_entries_insert_guard
BEFORE INSERT ON hhy.accounting_entries
FOR EACH ROW EXECUTE FUNCTION hhy.guard_accounting_entry_insert();

-- Every posted transaction must have at least two entries, one currency,
-- equal debit/credit totals and entry currencies matching their accounts.
CREATE OR REPLACE FUNCTION hhy.assert_balanced_transaction(p_transaction_id bigint)
RETURNS void
LANGUAGE plpgsql
AS $$
DECLARE
  v_debit numeric;
  v_credit numeric;
  v_entry_count bigint;
  v_currency_count bigint;
  v_currency_mismatch bigint;
  v_reversal_of_id bigint;
  v_reversal_mismatch bigint;
BEGIN
  SELECT reversal_of_id INTO v_reversal_of_id
  FROM hhy.accounting_transactions
  WHERE id = p_transaction_id AND status = 'POSTED';
  IF NOT FOUND THEN
    RETURN;
  END IF;

  SELECT count(*),
         COALESCE(sum(e.amount_cent) FILTER (WHERE e.direction = 'DEBIT'), 0),
         COALESCE(sum(e.amount_cent) FILTER (WHERE e.direction = 'CREDIT'), 0),
         count(DISTINCT e.currency),
         count(*) FILTER (WHERE a.currency IS DISTINCT FROM e.currency)
  INTO v_entry_count, v_debit, v_credit, v_currency_count, v_currency_mismatch
  FROM hhy.accounting_entries e
  JOIN hhy.ledger_accounts a ON a.id = e.account_id
  WHERE e.transaction_id = p_transaction_id;

  IF v_entry_count < 2 OR v_debit <> v_credit OR v_currency_count <> 1 OR v_currency_mismatch <> 0 THEN
    RAISE EXCEPTION
      'ACCOUNTING_UNBALANCED transaction_id=%, entries=%, debit=%, credit=%, currencies=%, currency_mismatch=%',
      p_transaction_id, v_entry_count, v_debit, v_credit, v_currency_count, v_currency_mismatch
      USING ERRCODE = '23514';
  END IF;

  IF v_reversal_of_id IS NOT NULL THEN
    WITH current_entries AS (
      SELECT account_id, currency, direction, sum(amount_cent)::numeric AS total
      FROM hhy.accounting_entries
      WHERE transaction_id = p_transaction_id
      GROUP BY account_id, currency, direction
    ), original_reversed AS (
      SELECT account_id, currency,
             CASE direction WHEN 'DEBIT' THEN 'CREDIT' ELSE 'DEBIT' END::varchar AS direction,
             sum(amount_cent)::numeric AS total
      FROM hhy.accounting_entries
      WHERE transaction_id = v_reversal_of_id
      GROUP BY account_id, currency, direction
    )
    SELECT count(*) INTO v_reversal_mismatch
    FROM (
      (SELECT * FROM current_entries EXCEPT SELECT * FROM original_reversed)
      UNION ALL
      (SELECT * FROM original_reversed EXCEPT SELECT * FROM current_entries)
    ) mismatch;
    IF v_reversal_mismatch <> 0 THEN
      RAISE EXCEPTION 'ACCOUNTING_REVERSAL_MISMATCH transaction_id=% original_transaction_id=%',
        p_transaction_id, v_reversal_of_id USING ERRCODE = '23514';
    END IF;
  END IF;
END;
$$;

-- No application-controlled setting may bypass immutable accounting facts.
CREATE OR REPLACE FUNCTION hhy.prevent_immutable_mutation()
RETURNS trigger
LANGUAGE plpgsql
AS $$
BEGIN
  RAISE EXCEPTION 'IMMUTABLE_TABLE: %.%', TG_TABLE_SCHEMA, TG_TABLE_NAME
    USING ERRCODE = '55000';
END;
$$;

-- Snapshot totals are rebuildable at an entry cursor. The frozen schema does
-- not encode normal balance side, so the conservative invariant is the
-- absolute debit/credit delta; available/frozen is an allocation of that total.
CREATE OR REPLACE FUNCTION hhy.guard_balance_snapshot()
RETURNS trigger
LANGUAGE plpgsql
AS $$
DECLARE
  v_cursor_account bigint;
  v_rebuilt_total numeric;
BEGIN
  IF TG_OP = 'UPDATE' THEN
    IF NEW.account_id <> OLD.account_id THEN
      RAISE EXCEPTION 'BALANCE_SNAPSHOT_ACCOUNT_IMMUTABLE snapshot_id=%', OLD.id
        USING ERRCODE = '55000';
    END IF;
    IF NEW.as_of_entry_id < OLD.as_of_entry_id THEN
      RAISE EXCEPTION 'BALANCE_SNAPSHOT_CURSOR_REGRESSION account_id=% old=% new=%',
        OLD.account_id, OLD.as_of_entry_id, NEW.as_of_entry_id USING ERRCODE = '23514';
    END IF;
    IF NEW.as_of_entry_id = OLD.as_of_entry_id AND
       ROW(NEW.available_cent, NEW.frozen_cent) IS DISTINCT FROM
       ROW(OLD.available_cent, OLD.frozen_cent) THEN
      RAISE EXCEPTION 'BALANCE_SNAPSHOT_CURSOR_REWRITE account_id=% cursor=%',
        OLD.account_id, OLD.as_of_entry_id USING ERRCODE = '23514';
    END IF;
  END IF;

  IF NEW.as_of_entry_id = 0 THEN
    v_rebuilt_total := 0;
  ELSE
    SELECT account_id INTO v_cursor_account
    FROM hhy.accounting_entries WHERE id = NEW.as_of_entry_id;
    IF v_cursor_account IS DISTINCT FROM NEW.account_id THEN
      RAISE EXCEPTION 'BALANCE_SNAPSHOT_CURSOR_ACCOUNT_MISMATCH account_id=% cursor=%',
        NEW.account_id, NEW.as_of_entry_id USING ERRCODE = '23514';
    END IF;
    SELECT abs(COALESCE(sum(
      CASE WHEN direction = 'DEBIT' THEN amount_cent ELSE -amount_cent END
    ), 0))
    INTO v_rebuilt_total
    FROM hhy.accounting_entries
    WHERE account_id = NEW.account_id AND id <= NEW.as_of_entry_id;
  END IF;

  IF (NEW.available_cent::numeric + NEW.frozen_cent::numeric) <> v_rebuilt_total THEN
    RAISE EXCEPTION 'BALANCE_SNAPSHOT_MISMATCH account_id=% cursor=% expected_total=% actual_total=%',
      NEW.account_id, NEW.as_of_entry_id, v_rebuilt_total,
      NEW.available_cent::numeric + NEW.frozen_cent::numeric USING ERRCODE = '23514';
  END IF;
  RETURN NEW;
END;
$$;

CREATE TRIGGER trg_balance_snapshots_guard
BEFORE INSERT OR UPDATE ON hhy.balance_snapshots
FOR EACH ROW EXECUTE FUNCTION hhy.guard_balance_snapshot();

CREATE OR REPLACE FUNCTION hhy.guard_reconciliation_run_mutation()
RETURNS trigger
LANGUAGE plpgsql
AS $$
BEGIN
  IF TG_OP = 'INSERT' THEN
    IF NEW.status <> 'CREATED' OR NEW.started_at IS NOT NULL OR NEW.finished_at IS NOT NULL THEN
      RAISE EXCEPTION 'RECONCILIATION_RUN_MUST_START_CREATED run_no=%', NEW.run_no
        USING ERRCODE = '23514';
    END IF;
    RETURN NEW;
  END IF;

  IF ROW(NEW.created_at, NEW.run_no, NEW.scene, NEW.period_start, NEW.period_end)
     IS DISTINCT FROM ROW(OLD.created_at, OLD.run_no, OLD.scene, OLD.period_start, OLD.period_end) THEN
    RAISE EXCEPTION 'RECONCILIATION_RUN_IDENTITY_IMMUTABLE run_no=%', OLD.run_no
      USING ERRCODE = '55000';
  END IF;
  IF NEW.status IS DISTINCT FROM OLD.status AND NOT (
    (OLD.status = 'CREATED' AND NEW.status IN ('RUNNING', 'CANCELLED')) OR
    (OLD.status = 'RUNNING' AND NEW.status IN ('SUCCEEDED', 'FAILED', 'CANCELLED'))
  ) THEN
    RAISE EXCEPTION 'RECONCILIATION_RUN_INVALID_TRANSITION run_no=% from=% to=%',
      OLD.run_no, OLD.status, NEW.status USING ERRCODE = '23514';
  END IF;
  IF OLD.status IN ('SUCCEEDED', 'FAILED', 'CANCELLED') AND NEW IS DISTINCT FROM OLD THEN
    RAISE EXCEPTION 'RECONCILIATION_RUN_TERMINAL run_no=%', OLD.run_no
      USING ERRCODE = '55000';
  END IF;
  RETURN NEW;
END;
$$;

CREATE TRIGGER trg_reconciliation_runs_guard
BEFORE INSERT OR UPDATE ON hhy.reconciliation_runs
FOR EACH ROW EXECUTE FUNCTION hhy.guard_reconciliation_run_mutation();

CREATE OR REPLACE FUNCTION hhy.guard_reconciliation_difference_mutation()
RETURNS trigger
LANGUAGE plpgsql
AS $$
BEGIN
  IF TG_OP = 'INSERT' THEN
    IF NEW.status <> 'OPEN' OR NEW.resolution IS NOT NULL THEN
      RAISE EXCEPTION 'RECONCILIATION_DIFFERENCE_MUST_START_OPEN source_type=% source_id=%',
        NEW.source_type, NEW.source_id USING ERRCODE = '23514';
    END IF;
    RETURN NEW;
  END IF;

  IF ROW(NEW.created_at, NEW.run_id, NEW.source_type, NEW.source_id, NEW.expected_cent, NEW.actual_cent)
     IS DISTINCT FROM ROW(OLD.created_at, OLD.run_id, OLD.source_type, OLD.source_id, OLD.expected_cent, OLD.actual_cent) THEN
    RAISE EXCEPTION 'RECONCILIATION_DIFFERENCE_FACT_IMMUTABLE difference_id=%', OLD.id
      USING ERRCODE = '55000';
  END IF;
  IF NEW.status IS DISTINCT FROM OLD.status AND NOT (
    OLD.status = 'OPEN' AND NEW.status IN ('RESOLVED', 'IGNORED')
  ) THEN
    RAISE EXCEPTION 'RECONCILIATION_DIFFERENCE_INVALID_TRANSITION difference_id=% from=% to=%',
      OLD.id, OLD.status, NEW.status USING ERRCODE = '23514';
  END IF;
  IF OLD.status IN ('RESOLVED', 'IGNORED') AND NEW IS DISTINCT FROM OLD THEN
    RAISE EXCEPTION 'RECONCILIATION_DIFFERENCE_TERMINAL difference_id=%', OLD.id
      USING ERRCODE = '55000';
  END IF;
  RETURN NEW;
END;
$$;

CREATE TRIGGER trg_reconciliation_differences_guard
BEFORE INSERT OR UPDATE ON hhy.reconciliation_differences
FOR EACH ROW EXECUTE FUNCTION hhy.guard_reconciliation_difference_mutation();

-- Status changes are retained as immutable event payloads. This avoids adding
-- a second generic history table while keeping state history in the accepted
-- transactional Outbox fact layer.
CREATE OR REPLACE FUNCTION hhy.record_platform_status_history()
RETURNS trigger
LANGUAGE plpgsql
AS $$
BEGIN
  IF NEW.status IS NOT DISTINCT FROM OLD.status THEN
    RETURN NEW;
  END IF;
  -- NEW is a polymorphic record shared by four trigger tables. Access the
  -- Outbox-only event_type through JSON so PostgreSQL never resolves a field
  -- that does not exist on ledger/reconciliation rows.
  IF TG_TABLE_NAME = 'outbox_events'
     AND to_jsonb(NEW)->>'event_type' = 'platform.status.changed.v1' THEN
    RETURN NEW;
  END IF;
  INSERT INTO hhy.outbox_events(
    aggregate_id, aggregate_type, event_id, event_type, event_version,
    headers, payload, status, attempts, available_at
  ) VALUES (
    NEW.id::text,
    TG_TABLE_NAME,
    gen_random_uuid()::text,
    'platform.status.changed.v1',
    1,
    jsonb_build_object('history', true),
    jsonb_build_object(
      'entityType', TG_TABLE_NAME,
      'entityId', NEW.id,
      'fromStatus', OLD.status,
      'toStatus', NEW.status,
      'changedAt', clock_timestamp()
    ),
    'PENDING', 0, clock_timestamp()
  );
  RETURN NEW;
END;
$$;

CREATE TRIGGER trg_outbox_events_status_history
AFTER UPDATE OF status ON hhy.outbox_events
FOR EACH ROW EXECUTE FUNCTION hhy.record_platform_status_history();

CREATE TRIGGER trg_ledger_accounts_status_history
AFTER UPDATE OF status ON hhy.ledger_accounts
FOR EACH ROW EXECUTE FUNCTION hhy.record_platform_status_history();

CREATE TRIGGER trg_reconciliation_runs_status_history
AFTER UPDATE OF status ON hhy.reconciliation_runs
FOR EACH ROW EXECUTE FUNCTION hhy.record_platform_status_history();

CREATE TRIGGER trg_reconciliation_differences_status_history
AFTER UPDATE OF status ON hhy.reconciliation_differences
FOR EACH ROW EXECUTE FUNCTION hhy.record_platform_status_history();
