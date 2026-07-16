-- State, money and accounting invariants.
SET search_path TO hhy, public;

ALTER TABLE hhy.content_posts ADD CONSTRAINT ck_content_posts_status CHECK (status IN ('DRAFT', 'PENDING_REVIEW', 'REVIEWING', 'REJECTED', 'APPROVED', 'ONLINE', 'OFFLINE_BY_OWNER', 'OFFLINE_BY_PLATFORM', 'RECTIFICATION', 'BANNED', 'DELETED'));
ALTER TABLE hhy.red_packet_campaigns ADD CONSTRAINT ck_red_packet_campaigns_status CHECK (status IN ('DRAFT', 'PRE_REVIEWING', 'PRE_REVIEW_REJECTED', 'PRE_REVIEW_APPROVED', 'WAITING_PAYMENT', 'PAYMENT_PROCESSING', 'ACTIVE', 'PAUSED_BY_CONTENT_OFFLINE', 'PAUSED_BY_OWNER', 'PAUSED_BY_RISK', 'SOLD_OUT', 'CLOSED_BY_OWNER', 'TERMINATED_BY_PLATFORM'));
ALTER TABLE hhy.orders ADD CONSTRAINT ck_orders_status CHECK (status IN ('PENDING_PAYMENT', 'PAYMENT_PROCESSING', 'PAID', 'FULFILLING', 'COMPLETED', 'PAYMENT_FAILED', 'CLOSED', 'CHANNEL_REVERSAL'));
ALTER TABLE hhy.withdrawal_requests ADD CONSTRAINT ck_withdrawal_requests_status CHECK (status IN ('CREATED', 'RISK_REVIEWING', 'FINANCE_REVIEWING', 'APPROVED', 'PAYOUT_PROCESSING', 'SUCCEEDED', 'FAILED', 'REJECTED', 'CANCELLED'));
ALTER TABLE hhy.identity_profiles ADD CONSTRAINT ck_identity_profiles_status CHECK (status IN ('NOT_STARTED', 'SESSION_CREATED', 'LIVENESS_PENDING', 'PROVIDER_PROCESSING', 'MANUAL_REVIEW', 'VERIFIED', 'REJECTED', 'EXPIRED'));
ALTER TABLE hhy.identity_verification_sessions ADD CONSTRAINT ck_identity_verification_sessions_status CHECK (status IN ('NOT_STARTED', 'SESSION_CREATED', 'LIVENESS_PENDING', 'PROVIDER_PROCESSING', 'MANUAL_REVIEW', 'VERIFIED', 'REJECTED', 'EXPIRED'));
ALTER TABLE hhy.app_build_jobs ADD CONSTRAINT ck_app_build_jobs_status CHECK (status IN ('QUEUED', 'PREPARING', 'BUILDING', 'TESTING', 'SIGNING', 'UPLOADING', 'SUCCEEDED', 'FAILED', 'CANCELLED'));
ALTER TABLE hhy.provider_config_versions ADD CONSTRAINT ck_provider_config_versions_status CHECK (status IN ('DRAFT', 'VALIDATED', 'CONNECTION_TESTED', 'PENDING_APPROVAL', 'ACTIVE', 'SUPERSEDED', 'REJECTED', 'ROLLED_BACK'));
ALTER TABLE hhy.outbox_events ADD CONSTRAINT ck_outbox_events_status CHECK (status IN ('PENDING', 'PUBLISHING', 'PUBLISHED', 'RETRY_WAIT', 'DEAD_LETTER'));
ALTER TABLE hhy.user_credentials ADD CONSTRAINT ck_user_credentials_failed_count_nonnegative CHECK (failed_count >= 0);
ALTER TABLE hhy.conversation_members ADD CONSTRAINT ck_conversation_members_unread_count_nonnegative CHECK (unread_count >= 0);
ALTER TABLE hhy.product_skus ADD CONSTRAINT ck_product_skus_price_cent_nonnegative CHECK (price_cent >= 0);
ALTER TABLE hhy.sku_commission_policies ADD CONSTRAINT ck_sku_commission_policies_level1_bps_bps CHECK (level1_bps BETWEEN 0 AND 10000);
ALTER TABLE hhy.sku_commission_policies ADD CONSTRAINT ck_sku_commission_policies_level2_bps_bps CHECK (level2_bps BETWEEN 0 AND 10000);
ALTER TABLE hhy.orders ADD CONSTRAINT ck_orders_amount_cent_nonnegative CHECK (amount_cent >= 0);
ALTER TABLE hhy.order_items ADD CONSTRAINT ck_order_items_quantity_nonnegative CHECK (quantity >= 0);
ALTER TABLE hhy.membership_entitlement_segments ADD CONSTRAINT ck_membership_entitlement_segments_paid_amount_nonnegative CHECK (paid_amount >= 0);
ALTER TABLE hhy.red_packet_campaigns ADD CONSTRAINT ck_red_packet_campaigns_current_amount_nonnegative CHECK (current_amount >= 0);
ALTER TABLE hhy.red_packet_campaigns ADD CONSTRAINT ck_red_packet_campaigns_total_count_nonnegative CHECK (total_count >= 0);
ALTER TABLE hhy.red_packet_stock ADD CONSTRAINT ck_red_packet_stock_total_nonnegative CHECK (total >= 0);
ALTER TABLE hhy.red_packet_stock ADD CONSTRAINT ck_red_packet_stock_claimed_nonnegative CHECK (claimed >= 0);
ALTER TABLE hhy.red_packet_stock ADD CONSTRAINT ck_red_packet_stock_reserved_nonnegative CHECK (reserved >= 0);
ALTER TABLE hhy.reward_accounts ADD CONSTRAINT ck_reward_accounts_pending_nonnegative CHECK (pending >= 0);
ALTER TABLE hhy.reward_accounts ADD CONSTRAINT ck_reward_accounts_available_nonnegative CHECK (available >= 0);
ALTER TABLE hhy.reward_accounts ADD CONSTRAINT ck_reward_accounts_frozen_nonnegative CHECK (frozen >= 0);
ALTER TABLE hhy.reward_accounts ADD CONSTRAINT ck_reward_accounts_withdrawing_nonnegative CHECK (withdrawing >= 0);
ALTER TABLE hhy.reward_accounts ADD CONSTRAINT ck_reward_accounts_withdrawn_nonnegative CHECK (withdrawn >= 0);
ALTER TABLE hhy.withdrawal_requests ADD CONSTRAINT ck_withdrawal_requests_net_amount_nonnegative CHECK (net_amount >= 0);
ALTER TABLE hhy.referral_reward_matrix ADD CONSTRAINT ck_referral_reward_matrix_reward_amount_nonnegative CHECK (reward_amount >= 0);
ALTER TABLE hhy.storage_migration_jobs ADD CONSTRAINT ck_storage_migration_jobs_total_nonnegative CHECK (total >= 0);
ALTER TABLE hhy.accounting_entries ADD CONSTRAINT ck_accounting_entries_amount_cent_nonnegative CHECK (amount_cent >= 0);
ALTER TABLE hhy.balance_snapshots ADD CONSTRAINT ck_balance_snapshots_available_cent_nonnegative CHECK (available_cent >= 0);
ALTER TABLE hhy.balance_snapshots ADD CONSTRAINT ck_balance_snapshots_frozen_cent_nonnegative CHECK (frozen_cent >= 0);
ALTER TABLE hhy.reconciliation_differences ADD CONSTRAINT ck_reconciliation_differences_actual_cent_nonnegative CHECK (actual_cent >= 0);
ALTER TABLE hhy.reconciliation_differences ADD CONSTRAINT ck_reconciliation_differences_expected_cent_nonnegative CHECK (expected_cent >= 0);
ALTER TABLE hhy.red_packet_stock ADD CONSTRAINT ck_red_packet_stock_capacity CHECK (claimed + reserved <= total);
ALTER TABLE hhy.accounting_entries ADD CONSTRAINT ck_accounting_entries_amount CHECK (amount_cent > 0);
ALTER TABLE hhy.accounting_entries ADD CONSTRAINT ck_accounting_entries_direction CHECK (direction IN ('DEBIT','CREDIT'));

CREATE OR REPLACE FUNCTION hhy.assert_balanced_transaction(p_transaction_id bigint) RETURNS void LANGUAGE plpgsql AS $$
DECLARE v_debit bigint; v_credit bigint; v_entry_count bigint; v_currency_count bigint;
BEGIN
  IF NOT EXISTS (SELECT 1 FROM hhy.accounting_transactions WHERE id=p_transaction_id AND status='POSTED') THEN RETURN; END IF;
  SELECT count(*), COALESCE(sum(amount_cent) FILTER (WHERE direction='DEBIT'),0), COALESCE(sum(amount_cent) FILTER (WHERE direction='CREDIT'),0), count(DISTINCT currency)
    INTO v_entry_count,v_debit,v_credit,v_currency_count FROM hhy.accounting_entries WHERE transaction_id=p_transaction_id;
  IF v_entry_count < 2 OR v_debit <> v_credit OR v_currency_count <> 1 THEN
    RAISE EXCEPTION 'ACCOUNTING_UNBALANCED transaction_id=%, entries=%, debit=%, credit=%, currencies=%',p_transaction_id,v_entry_count,v_debit,v_credit,v_currency_count USING ERRCODE='23514';
  END IF;
END; $$;
CREATE OR REPLACE FUNCTION hhy.check_balanced_from_entry() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN PERFORM hhy.assert_balanced_transaction(COALESCE(NEW.transaction_id,OLD.transaction_id)); RETURN NULL; END; $$;
CREATE OR REPLACE FUNCTION hhy.check_balanced_from_transaction() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN PERFORM hhy.assert_balanced_transaction(COALESCE(NEW.id,OLD.id)); RETURN NULL; END; $$;
CREATE CONSTRAINT TRIGGER trg_accounting_entries_balance AFTER INSERT OR UPDATE OR DELETE ON hhy.accounting_entries DEFERRABLE INITIALLY DEFERRED FOR EACH ROW EXECUTE FUNCTION hhy.check_balanced_from_entry();
CREATE CONSTRAINT TRIGGER trg_accounting_transactions_balance AFTER INSERT OR UPDATE ON hhy.accounting_transactions DEFERRABLE INITIALLY DEFERRED FOR EACH ROW EXECUTE FUNCTION hhy.check_balanced_from_transaction();

CREATE OR REPLACE FUNCTION hhy.prevent_immutable_mutation() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
  IF current_setting('hhy.allow_immutable_mutation', true)='on' THEN RETURN COALESCE(NEW,OLD); END IF;
  RAISE EXCEPTION 'IMMUTABLE_TABLE: %.%',TG_TABLE_SCHEMA,TG_TABLE_NAME USING ERRCODE='55000';
END; $$;

CREATE TRIGGER trg_accounting_transactions_immutable BEFORE UPDATE OR DELETE ON hhy.accounting_transactions FOR EACH ROW EXECUTE FUNCTION hhy.prevent_immutable_mutation();
CREATE TRIGGER trg_accounting_entries_immutable BEFORE UPDATE OR DELETE ON hhy.accounting_entries FOR EACH ROW EXECUTE FUNCTION hhy.prevent_immutable_mutation();
CREATE TRIGGER trg_red_packet_ledger_immutable BEFORE UPDATE OR DELETE ON hhy.red_packet_ledger FOR EACH ROW EXECUTE FUNCTION hhy.prevent_immutable_mutation();
CREATE TRIGGER trg_user_point_ledger_immutable BEFORE UPDATE OR DELETE ON hhy.user_point_ledger FOR EACH ROW EXECUTE FUNCTION hhy.prevent_immutable_mutation();
CREATE TRIGGER trg_admin_operation_logs_immutable BEFORE UPDATE OR DELETE ON hhy.admin_operation_logs FOR EACH ROW EXECUTE FUNCTION hhy.prevent_immutable_mutation();
CREATE TRIGGER trg_sensitive_data_access_logs_immutable BEFORE UPDATE OR DELETE ON hhy.sensitive_data_access_logs FOR EACH ROW EXECUTE FUNCTION hhy.prevent_immutable_mutation();
