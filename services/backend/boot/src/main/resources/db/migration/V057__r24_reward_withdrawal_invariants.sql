-- R24 reward account, withdrawal and accounting invariants.
SET search_path TO hhy, public;

DO $$
BEGIN
  IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'ck_r24_reward_accounts_total_nonnegative') THEN
    ALTER TABLE hhy.reward_accounts ADD CONSTRAINT ck_r24_reward_accounts_total_nonnegative
      CHECK (pending >= 0 AND available >= 0 AND frozen >= 0 AND withdrawing >= 0 AND withdrawn >= 0);
  END IF;
  IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'ck_r24_reward_accounts_version_nonnegative') THEN
    ALTER TABLE hhy.reward_accounts ADD CONSTRAINT ck_r24_reward_accounts_version_nonnegative CHECK (version >= 0);
  END IF;
  IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'ck_r24_reward_ledger_amount_nonzero') THEN
    ALTER TABLE hhy.reward_ledger ADD CONSTRAINT ck_r24_reward_ledger_amount_nonzero CHECK (amount IS NULL OR amount <> 0);
  END IF;
  IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'ck_r24_withdrawal_amounts') THEN
    ALTER TABLE hhy.withdrawal_requests ADD CONSTRAINT ck_r24_withdrawal_amounts CHECK (amount IS NULL OR amount > 0) NOT VALID;
  END IF;
  IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'ck_r24_withdrawal_fee_net') THEN
    ALTER TABLE hhy.withdrawal_requests ADD CONSTRAINT ck_r24_withdrawal_fee_net CHECK (fee IS NULL OR fee >= 0) NOT VALID;
  END IF;
  IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'ck_r24_withdrawal_net_consistent') THEN
    ALTER TABLE hhy.withdrawal_requests ADD CONSTRAINT ck_r24_withdrawal_net_consistent
      CHECK (amount IS NULL OR fee IS NULL OR net_amount IS NULL OR net_amount = amount - fee) NOT VALID;
  END IF;
END $$;

CREATE UNIQUE INDEX IF NOT EXISTS uq_r24_reward_ledger_business_key
  ON hhy.reward_ledger (user_id, source_type, biz_id)
  WHERE source_type IS NOT NULL AND biz_id IS NOT NULL;
CREATE UNIQUE INDEX IF NOT EXISTS uq_r24_payout_transactions_provider_order
  ON hhy.payout_transactions (gateway, provider_order_no)
  WHERE provider_order_no IS NOT NULL;
CREATE UNIQUE INDEX IF NOT EXISTS uq_r24_payout_callbacks_provider_order
  ON hhy.payout_callbacks (provider_order_no);
CREATE UNIQUE INDEX IF NOT EXISTS uq_r24_withdrawal_reconciliation_date
  ON hhy.payout_reconciliation_records (recon_date);
CREATE INDEX IF NOT EXISTS ix_r24_reward_ledger_user_created
  ON hhy.reward_ledger (user_id, created_at DESC, id DESC);
CREATE INDEX IF NOT EXISTS ix_r24_withdrawal_user_status_created
  ON hhy.withdrawal_requests (user_id, status, created_at DESC, id DESC);
CREATE INDEX IF NOT EXISTS ix_r24_payout_transactions_status_created
  ON hhy.payout_transactions (status, created_at DESC, id DESC);

COMMENT ON INDEX hhy.uq_r24_reward_ledger_business_key IS
  'R24 reward ledger idempotency: one source business event per user';
COMMENT ON INDEX hhy.uq_r24_payout_transactions_provider_order IS
  'R24 provider callback identity is unique per gateway order';
