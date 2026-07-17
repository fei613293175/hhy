-- P00 version-policy persistence hardening.
-- V010 is reserved for outbox/ledger invariants; this migration only touches App release tables.

ALTER TABLE hhy.app_versions
  ALTER COLUMN version_code TYPE bigint USING version_code::bigint,
  ALTER COLUMN min_version TYPE bigint USING min_version::bigint;

ALTER TABLE hhy.app_build_jobs
  ALTER COLUMN version_code TYPE bigint USING version_code::bigint;

ALTER TABLE hhy.app_release_records
  ALTER COLUMN version_code TYPE bigint USING version_code::bigint,
  ADD COLUMN min_supported_version_code bigint,
  ADD COLUMN release_notes text;

UPDATE hhy.app_release_records AS release_record
   SET min_supported_version_code = COALESCE(
           (
             SELECT app_version.min_version
               FROM hhy.app_versions AS app_version
              WHERE app_version.version_code = release_record.version_code
              ORDER BY app_version.created_at DESC, app_version.id DESC
              LIMIT 1
           ),
           release_record.version_code,
           1
       ),
       release_notes = '';

ALTER TABLE hhy.app_release_records
  ADD CONSTRAINT ck_app_release_records_min_supported_version_code
  CHECK (min_supported_version_code IS NULL OR min_supported_version_code > 0);

ALTER TABLE hhy.app_release_records
  ADD CONSTRAINT ck_app_release_records_release_notes_length
  CHECK (release_notes IS NULL OR char_length(release_notes) <= 2000);

ALTER TABLE hhy.app_release_channels
  DROP CONSTRAINT uq_app_release_channels_code;

ALTER TABLE hhy.app_release_channels
  ADD CONSTRAINT uq_app_release_channels_code_environment UNIQUE (code, environment);

CREATE INDEX ix_app_release_records_policy_lookup
  ON hhy.app_release_records (channel_id, status, published_at DESC, id DESC);
