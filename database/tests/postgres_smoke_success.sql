\set ON_ERROR_STOP on
SET search_path TO hhy, public;

INSERT INTO ledger_accounts(account_no, owner_type, owner_id, account_type, currency, status)
VALUES
  ('SMOKE-CASH', 'PLATFORM', 'smoke-platform', 'CASH', 'CNY', 'ACTIVE'),
  ('SMOKE-REWARD', 'USER', 'smoke-user', 'REWARD', 'CNY', 'ACTIVE');

DO $$
DECLARE
  tx_id bigint;
  debit_account bigint;
  credit_account bigint;
BEGIN
  SELECT id INTO debit_account FROM ledger_accounts WHERE account_no = 'SMOKE-CASH';
  SELECT id INTO credit_account FROM ledger_accounts WHERE account_no = 'SMOKE-REWARD';
  INSERT INTO accounting_transactions(transaction_no, biz_type, biz_id, idempotency_key, status)
  VALUES ('SMOKE-TX-BALANCED', 'SMOKE_TEST', 'SMOKE-BIZ-1', 'SMOKE-IDEM-1', 'POSTED')
  RETURNING id INTO tx_id;
  INSERT INTO accounting_entries(transaction_id, account_id, direction, amount_cent, currency, sequence)
  VALUES
    (tx_id, debit_account, 'DEBIT', 100, 'CNY', 1),
    (tx_id, credit_account, 'CREDIT', 100, 'CNY', 2);
  SET CONSTRAINTS ALL IMMEDIATE;
END $$;
