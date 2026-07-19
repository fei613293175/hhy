-- A provider return state is a one-time browser handoff, never a reusable query credential.
ALTER TABLE hhy.identity_verification_sessions
  ADD COLUMN callback_consumed_at timestamptz;

CREATE INDEX ix_r05_identity_callback_unconsumed
  ON hhy.identity_verification_sessions (state, expires_at)
  WHERE callback_consumed_at IS NULL;
