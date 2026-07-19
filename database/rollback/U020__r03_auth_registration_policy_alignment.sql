-- Intentional no-op rollback.
-- The former value 72 was an erroneous seed that contradicted the frozen 8-20 password policy.
-- Restoring it would weaken the policy and could overwrite a legitimate operator value of 20.
SELECT 1;
