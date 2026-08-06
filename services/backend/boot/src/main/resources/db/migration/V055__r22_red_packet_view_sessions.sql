-- R22 server-authoritative twenty-second browsing task and claim invariants.
SET search_path TO hhy, public;

ALTER TABLE hhy.red_packet_view_sessions
  ADD COLUMN IF NOT EXISTS client_nonce varchar(2000),
  ADD COLUMN IF NOT EXISTS device_context varchar(2000),
  ADD COLUMN IF NOT EXISTS accumulated_seconds integer DEFAULT 0,
  ADD COLUMN IF NOT EXISTS last_heartbeat_sequence bigint DEFAULT -1,
  ADD COLUMN IF NOT EXISTS last_server_time timestamptz,
  ADD COLUMN IF NOT EXISTS claimed_at timestamptz;

ALTER TABLE hhy.red_packet_reservations
  ADD COLUMN IF NOT EXISTS released_at timestamptz,
  ADD COLUMN IF NOT EXISTS claim_id bigint;

ALTER TABLE hhy.red_packet_claims
  ADD COLUMN IF NOT EXISTS session_id bigint,
  ADD COLUMN IF NOT EXISTS client_nonce varchar(2000),
  ADD COLUMN IF NOT EXISTS final_heartbeat_sequence bigint,
  ADD COLUMN IF NOT EXISTS request_hash varchar(64);

UPDATE hhy.red_packet_view_sessions
SET required = COALESCE(required, true),
    valid_seconds = COALESCE(valid_seconds, 20),
    accumulated_seconds = COALESCE(accumulated_seconds, 0),
    last_heartbeat_sequence = COALESCE(last_heartbeat_sequence, -1)
WHERE required IS NULL OR valid_seconds IS NULL OR accumulated_seconds IS NULL
   OR last_heartbeat_sequence IS NULL;

UPDATE hhy.red_packet_campaigns SET required_seconds = 20
WHERE required_seconds IS DISTINCT FROM 20;

INSERT INTO hhy.system_configs(key, value_json, scope)
VALUES ('red_packet.default_view_seconds', '20'::jsonb, 'GLOBAL'),
       ('red_packet.reservation_min_seconds', '90'::jsonb, 'GLOBAL')
ON CONFLICT (key, scope) DO UPDATE
SET value_json = EXCLUDED.value_json,
    version = hhy.system_configs.version + 1,
    updated_at = clock_timestamp();

DO $$ BEGIN
  ALTER TABLE hhy.red_packet_view_sessions
    ADD CONSTRAINT ck_r22_view_session_status CHECK (
      status IN ('VIEWING','VIEW_COMPLETE','CLAIMED','CANCELLED','EXPIRED'));
EXCEPTION WHEN duplicate_object THEN NULL; END $$;
DO $$ BEGIN
  ALTER TABLE hhy.red_packet_view_sessions
    ADD CONSTRAINT ck_r22_view_session_seconds CHECK (
      valid_seconds BETWEEN 1 AND 20 AND accumulated_seconds BETWEEN 0 AND valid_seconds);
EXCEPTION WHEN duplicate_object THEN NULL; END $$;
DO $$ BEGIN
  ALTER TABLE hhy.red_packet_reservations
    ADD CONSTRAINT ck_r22_reservation_status CHECK (
      status IN ('LOCKED','CLAIMED','RELEASED','EXPIRED'));
EXCEPTION WHEN duplicate_object THEN NULL; END $$;
DO $$ BEGIN
  ALTER TABLE hhy.red_packet_claims
    ADD CONSTRAINT ck_r22_claim_status CHECK (
      status IN ('PENDING','SETTLED','REJECTED','CANCELLED'));
EXCEPTION WHEN duplicate_object THEN NULL; END $$;
DO $$ BEGIN
  ALTER TABLE hhy.red_packet_claims
    ADD CONSTRAINT fk_r22_claim_session FOREIGN KEY (session_id)
      REFERENCES hhy.red_packet_view_sessions(id) ON DELETE RESTRICT;
EXCEPTION WHEN duplicate_object THEN NULL; END $$;
DO $$ BEGIN
  ALTER TABLE hhy.red_packet_reservations
    ADD CONSTRAINT fk_r22_reservation_claim FOREIGN KEY (claim_id)
      REFERENCES hhy.red_packet_claims(id) ON DELETE RESTRICT;
EXCEPTION WHEN duplicate_object THEN NULL; END $$;

CREATE UNIQUE INDEX IF NOT EXISTS uq_r22_active_view_session
  ON hhy.red_packet_view_sessions(campaign_id, user_id)
  WHERE status IN ('VIEWING','VIEW_COMPLETE');
CREATE UNIQUE INDEX IF NOT EXISTS uq_r22_claim_campaign_user
  ON hhy.red_packet_claims(campaign_id, user_id);
CREATE UNIQUE INDEX IF NOT EXISTS uq_r22_claim_request
  ON hhy.red_packet_claims(user_id, request_hash)
  WHERE request_hash IS NOT NULL;
CREATE INDEX IF NOT EXISTS ix_r22_view_session_expiry
  ON hhy.red_packet_view_sessions(status, expires_at);
CREATE INDEX IF NOT EXISTS ix_r22_reservation_expiry
  ON hhy.red_packet_reservations(status, expires_at);
CREATE INDEX IF NOT EXISTS ix_r22_claim_user_created
  ON hhy.red_packet_claims(user_id, created_at DESC);

COMMENT ON TABLE hhy.red_packet_view_sessions IS 'R22 服务端计时的红包浏览会话';
COMMENT ON TABLE hhy.red_packet_reservations IS 'R22 红包名额锁定、释放与领取关系';
COMMENT ON TABLE hhy.red_packet_claims IS 'R22 红包领取幂等与结算状态';
