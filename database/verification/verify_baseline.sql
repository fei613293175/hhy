SET search_path TO hhy, public;
DO $$ DECLARE v_count integer; BEGIN
 SELECT count(*) INTO v_count FROM information_schema.tables WHERE table_schema='hhy' AND table_type='BASE TABLE';
 IF v_count <> 188 THEN RAISE EXCEPTION 'Expected 188 tables, found %',v_count; END IF;
 IF EXISTS (SELECT 1 FROM hhy.accounting_transactions t LEFT JOIN hhy.accounting_entries e ON e.transaction_id=t.id WHERE t.status='POSTED' GROUP BY t.id HAVING COALESCE(sum(e.amount_cent) FILTER(WHERE e.direction='DEBIT'),0) <> COALESCE(sum(e.amount_cent) FILTER(WHERE e.direction='CREDIT'),0)) THEN RAISE EXCEPTION 'Unbalanced accounting data'; END IF;
 IF EXISTS (SELECT 1 FROM hhy.red_packet_stock WHERE claimed+reserved>total) THEN RAISE EXCEPTION 'Invalid red packet stock'; END IF;
END $$;
SELECT 'baseline-ok' AS result, count(*) AS table_count FROM information_schema.tables WHERE table_schema='hhy' AND table_type='BASE TABLE';
