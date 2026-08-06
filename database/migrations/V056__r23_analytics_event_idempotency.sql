-- R23 unified analytics: durable event identity plus daily source-separated KPI rollups.
SET search_path TO hhy, public;

ALTER TABLE hhy.analytics_events
  ADD COLUMN IF NOT EXISTS event_key varchar(128);

CREATE UNIQUE INDEX IF NOT EXISTS uq_r23_analytics_events_event_key
  ON hhy.analytics_events(event_key)
  WHERE event_key IS NOT NULL;

ALTER TABLE hhy.analytics_events
  ADD CONSTRAINT ck_r23_analytics_traffic_type CHECK (
    traffic_type IS NULL OR traffic_type IN (
      'ORGANIC_TRAFFIC',
      'INCENTIVIZED_RED_PACKET_TRAFFIC',
      'INCENTIVIZED_TASK_TRAFFIC'
    )
  ) NOT VALID;

COMMENT ON COLUMN hhy.analytics_events.event_key IS 'R23 analytics event idempotency identity; one accepted business event has one row';
