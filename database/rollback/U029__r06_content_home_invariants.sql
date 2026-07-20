-- Development/test rollback for R06 content and home invariants.
SET search_path TO hhy, public;

DROP INDEX IF EXISTS hhy.ix_r06_banner_schedule;
ALTER TABLE hhy.banners DROP CONSTRAINT IF EXISTS ck_r06_banner_schedule;

DROP INDEX IF EXISTS hhy.ix_r06_home_module_enabled_order;
ALTER TABLE hhy.home_modules DROP CONSTRAINT IF EXISTS ck_r06_home_module_config;

DROP TRIGGER IF EXISTS trg_r06_content_review_immutable ON hhy.content_review_records;
DROP INDEX IF EXISTS hhy.ix_r06_content_review_version;
ALTER TABLE hhy.content_review_records
  DROP CONSTRAINT IF EXISTS ck_r06_content_review_reason,
  DROP CONSTRAINT IF EXISTS ck_r06_content_review_decision,
  DROP CONSTRAINT IF EXISTS fk_r06_content_review_admin;

DROP TRIGGER IF EXISTS trg_r06_content_status_immutable ON hhy.content_status_logs;
DROP INDEX IF EXISTS hhy.ix_r06_content_status_history;
ALTER TABLE hhy.content_status_logs DROP CONSTRAINT IF EXISTS ck_r06_content_status_history;

DROP TRIGGER IF EXISTS trg_r06_content_version_immutable ON hhy.content_versions;
ALTER TABLE hhy.content_versions DROP CONSTRAINT IF EXISTS ck_r06_content_version_snapshot;

DROP INDEX IF EXISTS hhy.uq_r06_content_contact_order;
ALTER TABLE hhy.content_contacts DROP CONSTRAINT IF EXISTS ck_r06_content_contact;
ALTER TABLE hhy.content_media DROP CONSTRAINT IF EXISTS ck_r06_content_media;

DROP TRIGGER IF EXISTS trg_r06_content_type_change ON hhy.content_posts;
DROP TRIGGER IF EXISTS trg_r06_team_leader_detail_type ON hhy.team_leader_details;
DROP TRIGGER IF EXISTS trg_r06_group_detail_type ON hhy.group_details;
DROP TRIGGER IF EXISTS trg_r06_app_detail_type ON hhy.app_details;
DROP TRIGGER IF EXISTS trg_r06_project_detail_type ON hhy.project_details;
DROP FUNCTION IF EXISTS hhy.guard_r06_content_type_change();
DROP FUNCTION IF EXISTS hhy.assert_r06_content_detail_type();

DROP INDEX IF EXISTS hhy.uq_r06_team_leader_owner_active;
DROP INDEX IF EXISTS hhy.uq_r06_team_leader_content;
ALTER TABLE hhy.content_posts
  DROP CONSTRAINT IF EXISTS ck_r06_content_counter_version,
  DROP CONSTRAINT IF EXISTS ck_r06_content_title,
  DROP CONSTRAINT IF EXISTS ck_r06_content_type,
  ALTER COLUMN refresh_times DROP DEFAULT;
