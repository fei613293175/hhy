SET search_path TO hhy, public;

DELETE FROM hhy.system_configs
WHERE scope='GLOBAL' AND key IN (
  'auth.security_challenge.ttl_seconds','auth.security_challenge.max_attempts',
  'auth.password.min_length','auth.password.max_length','auth.password.max_failures',
  'auth.password.lock_seconds','auth.password.require_letters','auth.password.require_digits',
  'sms.code.length','sms.code.ttl_seconds','sms.send.cooldown_seconds',
  'sms.send.daily_phone_limit','sms.send.daily_ip_limit','sms.verify.max_attempts'
);

DROP INDEX IF EXISTS hhy.ix_sms_code_active_lookup;
ALTER TABLE hhy.sms_verification_codes DROP CONSTRAINT IF EXISTS ck_sms_code_expiry;
ALTER TABLE hhy.sms_verification_codes DROP CONSTRAINT IF EXISTS ck_sms_code_attempts;
ALTER TABLE hhy.sms_verification_codes DROP COLUMN IF EXISTS max_attempts;
ALTER TABLE hhy.sms_verification_codes DROP COLUMN IF EXISTS used_at;
ALTER TABLE hhy.sms_verification_codes ALTER COLUMN attempts DROP NOT NULL;
ALTER TABLE hhy.sms_verification_codes ALTER COLUMN attempts DROP DEFAULT;

DROP INDEX IF EXISTS hhy.ix_auth_challenge_nonce;
ALTER TABLE hhy.auth_security_challenges DROP CONSTRAINT IF EXISTS ck_auth_challenge_expiry;
ALTER TABLE hhy.auth_security_challenges DROP CONSTRAINT IF EXISTS ck_auth_challenge_attempts;
ALTER TABLE hhy.auth_security_challenges DROP COLUMN IF EXISTS max_attempts;
ALTER TABLE hhy.auth_security_challenges DROP COLUMN IF EXISTS attempts;
ALTER TABLE hhy.auth_security_challenges DROP COLUMN IF EXISTS device_fingerprint_hash;
ALTER TABLE hhy.auth_security_challenges DROP COLUMN IF EXISTS client_nonce_hash;

ALTER TABLE hhy.user_credentials ALTER COLUMN failed_count DROP NOT NULL;
ALTER TABLE hhy.user_credentials ALTER COLUMN failed_count DROP DEFAULT;

