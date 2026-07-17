-- Development-only rollback. Refuse to erase active replay-protection state.
DO $$
BEGIN
  IF EXISTS (
    SELECT 1 FROM hhy.admin_mfa_methods WHERE last_accepted_step IS NOT NULL
  ) THEN
    RAISE EXCEPTION 'R01_TOTP_REPLAY_GUARD_ROLLBACK_BLOCKED_CONSUMED_STEPS_EXIST'
      USING ERRCODE = '23514';
  END IF;
END;
$$;

ALTER TABLE hhy.admin_mfa_methods
  DROP CONSTRAINT ck_admin_mfa_methods_last_accepted_step_nonnegative;

ALTER TABLE hhy.admin_mfa_methods
  DROP COLUMN last_accepted_step;
