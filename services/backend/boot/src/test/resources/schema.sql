CREATE SCHEMA IF NOT EXISTS hhy;

CREATE TABLE hhy.app_build_artifacts (
  id bigint PRIMARY KEY,
  object_key varchar(2048),
  sha256 varchar(128)
);

CREATE TABLE hhy.app_release_channels (
  id bigint PRIMARY KEY,
  code varchar(64) NOT NULL,
  environment varchar(64) NOT NULL,
  download_domain varchar(255),
  status varchar(64) NOT NULL,
  CONSTRAINT uq_test_app_release_channels UNIQUE (code, environment)
);

CREATE TABLE hhy.app_release_records (
  id bigint PRIMARY KEY,
  artifact_id bigint NOT NULL,
  channel_id bigint NOT NULL,
  version_name varchar(255),
  version_code bigint,
  update_type varchar(255),
  status varchar(64) NOT NULL,
  published_at timestamp with time zone,
  min_supported_version_code bigint,
  release_notes varchar(2000)
);

CREATE TABLE hhy.outbox_events (
  id bigint PRIMARY KEY,
  status varchar(64) NOT NULL
);

CREATE TABLE hhy.ledger_accounts (
  id bigint PRIMARY KEY,
  currency varchar(8) NOT NULL
);

CREATE TABLE hhy.accounting_transactions (
  id bigint PRIMARY KEY,
  status varchar(64) NOT NULL
);

CREATE TABLE hhy.accounting_entries (
  id bigint PRIMARY KEY,
  transaction_id bigint NOT NULL,
  account_id bigint NOT NULL,
  amount_cent bigint NOT NULL,
  currency varchar(8) NOT NULL,
  direction varchar(64) NOT NULL
);

CREATE TABLE hhy.reconciliation_differences (
  id bigint PRIMARY KEY,
  status varchar(64) NOT NULL
);
