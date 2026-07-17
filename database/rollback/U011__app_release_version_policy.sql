-- Development-only rollback for V011.

DROP INDEX IF EXISTS hhy.ix_app_release_records_policy_lookup;

ALTER TABLE hhy.app_release_channels
  DROP CONSTRAINT IF EXISTS uq_app_release_channels_code_environment;

ALTER TABLE hhy.app_release_channels
  ADD CONSTRAINT uq_app_release_channels_code UNIQUE (code);

ALTER TABLE hhy.app_release_records
  DROP CONSTRAINT IF EXISTS ck_app_release_records_release_notes_length;

ALTER TABLE hhy.app_release_records
  DROP CONSTRAINT IF EXISTS ck_app_release_records_min_supported_version_code;

ALTER TABLE hhy.app_release_records
  DROP COLUMN IF EXISTS release_notes;

ALTER TABLE hhy.app_release_records
  DROP COLUMN IF EXISTS min_supported_version_code;

ALTER TABLE hhy.app_release_records
  ALTER COLUMN version_code TYPE integer USING version_code::integer;

ALTER TABLE hhy.app_build_jobs
  ALTER COLUMN version_code TYPE integer USING version_code::integer;

ALTER TABLE hhy.app_versions
  ALTER COLUMN min_version TYPE integer USING min_version::integer,
  ALTER COLUMN version_code TYPE integer USING version_code::integer;
