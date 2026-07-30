DROP INDEX IF EXISTS hhy.ix_r05_identity_callback_unconsumed;

ALTER TABLE hhy.identity_verification_sessions
  DROP COLUMN IF EXISTS callback_consumed_at;
