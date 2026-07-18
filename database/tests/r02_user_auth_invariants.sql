DO $$
BEGIN
  IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname='ck_auth_challenge_attempts') THEN
    RAISE EXCEPTION 'missing challenge attempt invariant';
  END IF;
  IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname='ck_sms_code_attempts') THEN
    RAISE EXCEPTION 'missing SMS attempt invariant';
  END IF;
  IF (SELECT count(*) FROM hhy.system_configs WHERE scope='GLOBAL' AND key LIKE 'auth.%') < 8 THEN
    RAISE EXCEPTION 'missing frozen authentication policy seeds';
  END IF;
END $$;

