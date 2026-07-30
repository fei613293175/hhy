-- Development/test rollback for R13 activity invariants. Business rows are preserved.
SET search_path TO hhy, public;

DROP TRIGGER IF EXISTS trg_r13_content_view_immutable ON hhy.content_view_logs;
DROP TRIGGER IF EXISTS trg_r13_favorite_no_update ON hhy.content_favorites;
DROP FUNCTION IF EXISTS hhy.prevent_r13_favorite_update();

DROP INDEX IF EXISTS hhy.ix_r13_report_status_recent;
DROP INDEX IF EXISTS hhy.ix_r13_history_user_recent;
DROP INDEX IF EXISTS hhy.ix_r13_favorite_user_recent;

ALTER TABLE hhy.content_reports
  DROP CONSTRAINT IF EXISTS ck_r13_content_report_contract,
  DROP CONSTRAINT IF EXISTS fk_r13_content_reports_reporter;
ALTER TABLE hhy.content_contact_access_logs
  DROP CONSTRAINT IF EXISTS ck_r13_contact_access_contract;
ALTER TABLE hhy.content_view_logs
  DROP CONSTRAINT IF EXISTS ck_r13_content_view_event;
