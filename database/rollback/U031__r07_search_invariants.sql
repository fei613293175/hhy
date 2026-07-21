-- Development/test rollback for R07 search invariants.
SET search_path TO hhy, public;

DROP TRIGGER IF EXISTS trg_r07_contact_access_immutable ON hhy.content_contact_access_logs;
DROP INDEX IF EXISTS hhy.ix_r07_contact_access_recent;
ALTER TABLE hhy.content_contact_access_logs
  DROP CONSTRAINT IF EXISTS ck_r07_contact_access_complete;

DROP INDEX IF EXISTS hhy.ix_r07_hot_active_rank;
DROP INDEX IF EXISTS hhy.uq_r07_hot_keyword;
ALTER TABLE hhy.hot_search_terms
  DROP CONSTRAINT IF EXISTS ck_r07_hot_schedule,
  DROP CONSTRAINT IF EXISTS ck_r07_hot_keyword_weight,
  ALTER COLUMN weight DROP DEFAULT;

DROP INDEX IF EXISTS hhy.ix_r07_search_history_user_recent;
ALTER TABLE hhy.search_histories
  DROP CONSTRAINT IF EXISTS ck_r07_search_history_keyword;
