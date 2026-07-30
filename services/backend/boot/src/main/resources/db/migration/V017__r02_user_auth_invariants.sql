SET search_path TO hhy, public;

ALTER TABLE hhy.auth_security_challenges
  ADD COLUMN client_nonce_hash varchar(128),
  ADD COLUMN device_fingerprint_hash varchar(128),
  ADD COLUMN attempts integer NOT NULL DEFAULT 0,
  ADD COLUMN max_attempts integer NOT NULL DEFAULT 5;

ALTER TABLE hhy.auth_security_challenges
  ADD CONSTRAINT ck_auth_challenge_attempts CHECK (attempts >= 0 AND max_attempts BETWEEN 1 AND 10),
  ADD CONSTRAINT ck_auth_challenge_expiry CHECK (expires_at IS NULL OR expires_at > created_at);

CREATE INDEX ix_auth_challenge_nonce
  ON hhy.auth_security_challenges (client_nonce_hash, created_at DESC);

ALTER TABLE hhy.sms_verification_codes
  ADD COLUMN used_at timestamptz,
  ADD COLUMN max_attempts integer NOT NULL DEFAULT 5;

UPDATE hhy.sms_verification_codes SET attempts=0 WHERE attempts IS NULL;
ALTER TABLE hhy.sms_verification_codes ALTER COLUMN attempts SET DEFAULT 0;
ALTER TABLE hhy.sms_verification_codes ALTER COLUMN attempts SET NOT NULL;

ALTER TABLE hhy.sms_verification_codes
  ADD CONSTRAINT ck_sms_code_attempts CHECK (attempts >= 0 AND max_attempts BETWEEN 1 AND 10),
  ADD CONSTRAINT ck_sms_code_expiry CHECK (expires_at IS NULL OR expires_at > created_at);

CREATE INDEX ix_sms_code_active_lookup
  ON hhy.sms_verification_codes (phone, scene, created_at DESC)
  WHERE used_at IS NULL;

UPDATE hhy.user_credentials SET failed_count=0 WHERE failed_count IS NULL;
ALTER TABLE hhy.user_credentials ALTER COLUMN failed_count SET DEFAULT 0;
ALTER TABLE hhy.user_credentials ALTER COLUMN failed_count SET NOT NULL;

INSERT INTO hhy.system_configs(key,value_json,scope)
VALUES
  ('auth.security_challenge.ttl_seconds','120'::jsonb,'GLOBAL'),
  ('auth.security_challenge.max_attempts','5'::jsonb,'GLOBAL'),
  ('auth.password.min_length','8'::jsonb,'GLOBAL'),
  ('auth.password.max_length','72'::jsonb,'GLOBAL'),
  ('auth.password.max_failures','5'::jsonb,'GLOBAL'),
  ('auth.password.lock_seconds','900'::jsonb,'GLOBAL'),
  ('auth.password.require_letters','true'::jsonb,'GLOBAL'),
  ('auth.password.require_digits','true'::jsonb,'GLOBAL'),
  ('sms.code.length','6'::jsonb,'GLOBAL'),
  ('sms.code.ttl_seconds','300'::jsonb,'GLOBAL'),
  ('sms.send.cooldown_seconds','60'::jsonb,'GLOBAL'),
  ('sms.send.daily_phone_limit','10'::jsonb,'GLOBAL'),
  ('sms.send.daily_ip_limit','50'::jsonb,'GLOBAL'),
  ('sms.verify.max_attempts','5'::jsonb,'GLOBAL')
ON CONFLICT (key,scope) DO NOTHING;
