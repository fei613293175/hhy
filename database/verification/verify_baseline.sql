SET search_path TO hhy, public;
DO $$
DECLARE
 v_count integer;
 v_transaction record;
BEGIN
 SELECT count(*) INTO v_count FROM information_schema.tables WHERE table_schema='hhy' AND table_type='BASE TABLE';
 IF v_count NOT IN (198,199) THEN RAISE EXCEPTION 'Expected a compatible 198/199-table schema, found %',v_count; END IF;
 FOR v_transaction IN SELECT id FROM hhy.accounting_transactions LOOP
   PERFORM hhy.assert_balanced_transaction(v_transaction.id);
 END LOOP;
 IF EXISTS (
   SELECT 1 FROM hhy.outbox_events
   WHERE attempts < 0 OR event_version <= 0
     OR (status = 'PUBLISHED' AND published_at IS NULL)
     OR (status <> 'PUBLISHED' AND published_at IS NOT NULL)
 ) THEN RAISE EXCEPTION 'Invalid outbox delivery data'; END IF;
 IF EXISTS (
   SELECT 1 FROM hhy.inbox_messages
   WHERE payload_hash !~ '^[0-9a-f]{64}$'
 ) THEN RAISE EXCEPTION 'Invalid inbox payload hash'; END IF;
 IF EXISTS (
   SELECT 1
   FROM hhy.balance_snapshots s
   LEFT JOIN hhy.accounting_entries cursor_entry ON cursor_entry.id = s.as_of_entry_id
   WHERE (s.as_of_entry_id <> 0 AND cursor_entry.account_id IS DISTINCT FROM s.account_id)
      OR (s.available_cent::numeric + s.frozen_cent::numeric) <> abs(COALESCE((
        SELECT sum(CASE WHEN e.direction='DEBIT' THEN e.amount_cent ELSE -e.amount_cent END)
        FROM hhy.accounting_entries e
        WHERE e.account_id=s.account_id AND e.id<=s.as_of_entry_id
      ), 0))
 ) THEN RAISE EXCEPTION 'Invalid balance snapshot data'; END IF;
 IF EXISTS (
   SELECT 1 FROM hhy.reconciliation_runs
   WHERE period_start >= period_end
      OR (status IN ('CREATED','RUNNING') AND finished_at IS NOT NULL)
      OR (status IN ('SUCCEEDED','FAILED','CANCELLED') AND finished_at IS NULL)
 ) THEN RAISE EXCEPTION 'Invalid reconciliation run data'; END IF;
 IF EXISTS (SELECT 1 FROM hhy.red_packet_stock WHERE claimed+reserved>total) THEN RAISE EXCEPTION 'Invalid red packet stock'; END IF;
 IF EXISTS (SELECT 1 FROM hhy.admin_users WHERE mfa_secret_ref IS NOT NULL OR version < 0)
   THEN RAISE EXCEPTION 'Invalid administrator MFA source or version'; END IF;
 IF EXISTS (
   SELECT 1 FROM hhy.admin_sessions
   WHERE btrim(access_jti)='' OR (refresh_hash IS NOT NULL AND btrim(refresh_hash)='')
      OR version < 0 OR expires_at <= created_at
      OR (revoked_at IS NOT NULL AND revoked_at < created_at)
      OR (last_active_at IS NOT NULL AND last_active_at < created_at)
      OR (revoked_at IS NOT NULL AND last_active_at IS NOT NULL AND revoked_at < last_active_at)
 ) THEN RAISE EXCEPTION 'Invalid administrator session data'; END IF;
 IF EXISTS (
   SELECT 1 FROM hhy.admin_mfa_methods
   WHERE btrim(method)='' OR version < 0 OR NOT (
     (status='PENDING' AND btrim(COALESCE(secret_ref,''))<>'' AND confirmed_at IS NULL AND disabled_at IS NULL) OR
     (status='ACTIVE' AND btrim(COALESCE(secret_ref,''))<>'' AND confirmed_at>=created_at AND disabled_at IS NULL) OR
     (status='DISABLED' AND secret_ref IS NULL AND disabled_at>=created_at)
   )
 ) THEN RAISE EXCEPTION 'Invalid administrator MFA method data'; END IF;
 IF EXISTS (
   SELECT 1 FROM hhy.admin_recovery_codes
   WHERE btrim(code_hash)='' OR (used_at IS NOT NULL AND used_at < created_at)
      OR (expires_at IS NOT NULL AND expires_at <= created_at)
      OR (used_at IS NOT NULL AND expires_at IS NOT NULL AND used_at > expires_at)
 ) THEN RAISE EXCEPTION 'Invalid administrator recovery-code data'; END IF;
 IF EXISTS (
   SELECT 1 FROM hhy.admin_login_logs
   WHERE btrim(event_type)='' OR btrim(result)=''
      OR (request_id IS NOT NULL AND btrim(request_id)='')
      OR (upper(result) IN ('SUCCESS','SUCCEEDED') AND failure_code IS NOT NULL)
 ) THEN RAISE EXCEPTION 'Invalid administrator login log data'; END IF;
 IF to_regprocedure('hhy.consume_admin_recovery_code(bigint,character varying)') IS NULL
   THEN RAISE EXCEPTION 'Missing administrator recovery-code consumer'; END IF;
END $$;
SELECT 'baseline-ok' AS result, count(*) AS table_count FROM information_schema.tables WHERE table_schema='hhy' AND table_type='BASE TABLE';
