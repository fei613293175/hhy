-- Development/test rollback for R12 publishing invariants. Added columns and business rows are preserved.
SET search_path TO hhy, public;

DROP TRIGGER IF EXISTS trg_r12_content_commit ON hhy.content_posts;
DROP TRIGGER IF EXISTS trg_00_r12_content_contact_no_delete ON hhy.content_contacts;
DROP TRIGGER IF EXISTS trg_00_r12_content_media_no_delete ON hhy.content_media;
DROP TRIGGER IF EXISTS trg_00_r12_team_leader_detail_no_delete ON hhy.team_leader_details;
DROP TRIGGER IF EXISTS trg_00_r12_group_detail_no_delete ON hhy.group_details;
DROP TRIGGER IF EXISTS trg_00_r12_app_detail_no_delete ON hhy.app_details;
DROP TRIGGER IF EXISTS trg_00_r12_project_detail_no_delete ON hhy.project_details;
DROP TRIGGER IF EXISTS trg_00_r12_content_post_no_delete ON hhy.content_posts;
DROP TRIGGER IF EXISTS trg_r12_content_contact_write ON hhy.content_contacts;
DROP TRIGGER IF EXISTS trg_r12_content_media_write ON hhy.content_media;
DROP TRIGGER IF EXISTS trg_r12_team_leader_detail_write ON hhy.team_leader_details;
DROP TRIGGER IF EXISTS trg_r12_group_detail_write ON hhy.group_details;
DROP TRIGGER IF EXISTS trg_r12_app_detail_write ON hhy.app_details;
DROP TRIGGER IF EXISTS trg_r12_project_detail_write ON hhy.project_details;
DROP TRIGGER IF EXISTS trg_00_r12_content_outbox_enrich ON hhy.outbox_events;
DROP TRIGGER IF EXISTS trg_r12_content_review_insert_guard ON hhy.content_review_records;
DROP TRIGGER IF EXISTS trg_r12_content_version_insert_guard ON hhy.content_versions;
DROP TRIGGER IF EXISTS trg_r12_content_status_log_guard ON hhy.content_status_logs;
DROP TRIGGER IF EXISTS trg_r12_content_write_record ON hhy.content_posts;
DROP TRIGGER IF EXISTS trg_r12_content_post_guard ON hhy.content_posts;

DROP FUNCTION IF EXISTS hhy.assert_r12_content_commit();
DROP FUNCTION IF EXISTS hhy.prevent_r12_content_physical_delete();
DROP FUNCTION IF EXISTS hhy.guard_r12_content_child_write();
DROP FUNCTION IF EXISTS hhy.enrich_r12_content_outbox();
DROP FUNCTION IF EXISTS hhy.guard_r12_content_review_insert();
DROP FUNCTION IF EXISTS hhy.guard_r12_content_version_insert();
DROP FUNCTION IF EXISTS hhy.guard_r12_content_status_log();
DROP FUNCTION IF EXISTS hhy.record_r12_content_write();
DROP FUNCTION IF EXISTS hhy.guard_r12_content_post();
DROP FUNCTION IF EXISTS hhy.r12_content_transition_allowed(varchar,varchar);

DROP INDEX IF EXISTS hhy.uq_r12_content_media_identity;
DROP INDEX IF EXISTS hhy.uq_r12_content_contact_channel;
DROP INDEX IF EXISTS hhy.uq_r12_content_contact_order;
CREATE UNIQUE INDEX IF NOT EXISTS uq_r06_content_contact_order
  ON hhy.content_contacts(content_id,channel,sort_order);
DROP INDEX IF EXISTS hhy.ix_r12_content_review_snapshot;
DROP INDEX IF EXISTS hhy.uq_r12_content_review_command;
DROP INDEX IF EXISTS hhy.uq_r12_content_status_transition_version;

ALTER TABLE hhy.content_review_records
  DROP CONSTRAINT IF EXISTS ck_r12_content_review_action,
  DROP CONSTRAINT IF EXISTS ck_r12_content_review_command,
  DROP CONSTRAINT IF EXISTS fk_r12_content_review_snapshot;
ALTER TABLE hhy.content_status_logs
  DROP CONSTRAINT IF EXISTS ck_r12_content_status_transition_version;
