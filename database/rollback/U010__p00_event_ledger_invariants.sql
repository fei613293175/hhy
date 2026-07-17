-- V010 rollback for disposable development/test databases only.
-- This intentionally restores V007 behavior and must never be used as a
-- production rollback; production correction is always a new forward migration.
SET search_path TO hhy, public;

DROP TRIGGER IF EXISTS trg_reconciliation_differences_status_history ON hhy.reconciliation_differences;
DROP TRIGGER IF EXISTS trg_reconciliation_runs_status_history ON hhy.reconciliation_runs;
DROP TRIGGER IF EXISTS trg_ledger_accounts_status_history ON hhy.ledger_accounts;
DROP TRIGGER IF EXISTS trg_outbox_events_status_history ON hhy.outbox_events;
DROP FUNCTION IF EXISTS hhy.record_platform_status_history();

DROP TRIGGER IF EXISTS trg_reconciliation_differences_guard ON hhy.reconciliation_differences;
DROP FUNCTION IF EXISTS hhy.guard_reconciliation_difference_mutation();
DROP TRIGGER IF EXISTS trg_reconciliation_runs_guard ON hhy.reconciliation_runs;
DROP FUNCTION IF EXISTS hhy.guard_reconciliation_run_mutation();
DROP TRIGGER IF EXISTS trg_balance_snapshots_guard ON hhy.balance_snapshots;
DROP FUNCTION IF EXISTS hhy.guard_balance_snapshot();
DROP TRIGGER IF EXISTS trg_accounting_entries_insert_guard ON hhy.accounting_entries;
DROP FUNCTION IF EXISTS hhy.guard_accounting_entry_insert();
DROP TRIGGER IF EXISTS trg_ledger_accounts_guard ON hhy.ledger_accounts;
DROP FUNCTION IF EXISTS hhy.guard_ledger_account_mutation();
DROP FUNCTION IF EXISTS hhy.complete_inbox_message(bigint, jsonb);
DROP FUNCTION IF EXISTS hhy.claim_inbox_message(varchar, varchar, varchar);
DROP TRIGGER IF EXISTS trg_inbox_messages_guard ON hhy.inbox_messages;
DROP FUNCTION IF EXISTS hhy.guard_inbox_message_mutation();
DROP TRIGGER IF EXISTS trg_outbox_events_guard ON hhy.outbox_events;
DROP FUNCTION IF EXISTS hhy.guard_outbox_event_mutation();

DROP INDEX IF EXISTS hhy.uq_accounting_transactions_reversal_of_id;

ALTER TABLE hhy.reconciliation_differences
  DROP CONSTRAINT IF EXISTS ck_reconciliation_differences_resolution,
  DROP CONSTRAINT IF EXISTS ck_reconciliation_differences_status;
ALTER TABLE hhy.reconciliation_runs
  DROP CONSTRAINT IF EXISTS ex_reconciliation_runs_active_window,
  DROP CONSTRAINT IF EXISTS ck_reconciliation_runs_lifecycle_time,
  DROP CONSTRAINT IF EXISTS ck_reconciliation_runs_status,
  DROP CONSTRAINT IF EXISTS ck_reconciliation_runs_period;
ALTER TABLE hhy.accounting_transactions
  DROP CONSTRAINT IF EXISTS ck_accounting_transactions_reversal_not_self,
  DROP CONSTRAINT IF EXISTS ck_accounting_transactions_identity_nonblank,
  DROP CONSTRAINT IF EXISTS ck_accounting_transactions_posted_fact;
ALTER TABLE hhy.ledger_accounts
  DROP CONSTRAINT IF EXISTS ck_ledger_accounts_status,
  DROP CONSTRAINT IF EXISTS ck_ledger_accounts_identity_nonblank,
  DROP CONSTRAINT IF EXISTS ck_ledger_accounts_version_nonnegative;
ALTER TABLE hhy.inbox_messages
  DROP CONSTRAINT IF EXISTS ck_inbox_messages_payload_hash,
  DROP CONSTRAINT IF EXISTS ck_inbox_messages_identity_nonblank;
ALTER TABLE hhy.outbox_events
  DROP CONSTRAINT IF EXISTS ck_outbox_events_delivery_state,
  DROP CONSTRAINT IF EXISTS ck_outbox_events_identity_nonblank,
  DROP CONSTRAINT IF EXISTS ck_outbox_events_event_version_positive,
  DROP CONSTRAINT IF EXISTS ck_outbox_events_attempts_nonnegative;

CREATE OR REPLACE FUNCTION hhy.assert_balanced_transaction(p_transaction_id bigint)
RETURNS void
LANGUAGE plpgsql
AS $$
DECLARE
  v_debit bigint;
  v_credit bigint;
  v_entry_count bigint;
  v_currency_count bigint;
BEGIN
  IF NOT EXISTS (
    SELECT 1 FROM hhy.accounting_transactions
    WHERE id = p_transaction_id AND status = 'POSTED'
  ) THEN
    RETURN;
  END IF;
  SELECT count(*),
         COALESCE(sum(amount_cent) FILTER (WHERE direction = 'DEBIT'), 0),
         COALESCE(sum(amount_cent) FILTER (WHERE direction = 'CREDIT'), 0),
         count(DISTINCT currency)
  INTO v_entry_count, v_debit, v_credit, v_currency_count
  FROM hhy.accounting_entries
  WHERE transaction_id = p_transaction_id;
  IF v_entry_count < 2 OR v_debit <> v_credit OR v_currency_count <> 1 THEN
    RAISE EXCEPTION
      'ACCOUNTING_UNBALANCED transaction_id=%, entries=%, debit=%, credit=%, currencies=%',
      p_transaction_id, v_entry_count, v_debit, v_credit, v_currency_count
      USING ERRCODE = '23514';
  END IF;
END;
$$;

CREATE OR REPLACE FUNCTION hhy.prevent_immutable_mutation()
RETURNS trigger
LANGUAGE plpgsql
AS $$
BEGIN
  IF current_setting('hhy.allow_immutable_mutation', true) = 'on' THEN
    RETURN COALESCE(NEW, OLD);
  END IF;
  RAISE EXCEPTION 'IMMUTABLE_TABLE: %.%', TG_TABLE_SCHEMA, TG_TABLE_NAME
    USING ERRCODE = '55000';
END;
$$;
