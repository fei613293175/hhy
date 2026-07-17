-- A successfully accepted TOTP timestep is single-use across administrator
-- login and all high-risk self-service operations.
ALTER TABLE hhy.admin_mfa_methods
  ADD COLUMN last_accepted_step bigint;

ALTER TABLE hhy.admin_mfa_methods
  ADD CONSTRAINT ck_admin_mfa_methods_last_accepted_step_nonnegative
  CHECK (last_accepted_step IS NULL OR last_accepted_step >= 0);

COMMENT ON COLUMN hhy.admin_mfa_methods.last_accepted_step IS
  'Highest successfully consumed RFC 6238 timestep; lower or equal steps are replay';
