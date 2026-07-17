SET search_path TO hhy, public;
DO $$
DECLARE
 v_count integer;
 v_transaction record;
BEGIN
 SELECT count(*) INTO v_count FROM information_schema.tables WHERE table_schema='hhy' AND table_type='BASE TABLE';
 IF v_count <> 198 THEN RAISE EXCEPTION 'Expected 198 tables, found %',v_count; END IF;
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
END $$;
SELECT 'baseline-ok' AS result, count(*) AS table_count FROM information_schema.tables WHERE table_schema='hhy' AND table_type='BASE TABLE';
