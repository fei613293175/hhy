-- Development/test rollback for R10 group promotion invariants. Business rows are preserved.
SET search_path TO hhy, public;

DROP TRIGGER IF EXISTS trg_r10_group_contact_terminal ON hhy.content_contacts;
DROP TRIGGER IF EXISTS trg_r10_group_detail_terminal ON hhy.group_details;
DROP TRIGGER IF EXISTS trg_r10_content_terminal ON hhy.content_posts;
DROP FUNCTION IF EXISTS hhy.assert_r10_group_terminal_state();
DROP TRIGGER IF EXISTS trg_r10_group_contact_parent_lock ON hhy.content_contacts;
DROP TRIGGER IF EXISTS trg_r10_group_detail_parent_lock ON hhy.group_details;
DROP FUNCTION IF EXISTS hhy.lock_r10_group_parent();
DROP INDEX IF EXISTS hhy.uq_r10_group_join_password;
DROP INDEX IF EXISTS hhy.ix_r10_group_platform;
DROP INDEX IF EXISTS hhy.ix_r10_group_public_list;

ALTER TABLE hhy.group_details
  DROP CONSTRAINT IF EXISTS fk_r10_group_qr_media,
  DROP CONSTRAINT IF EXISTS ck_r10_group_detail_values;
