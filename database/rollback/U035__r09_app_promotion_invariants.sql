-- Development/test rollback for R09 App promotion invariants. Business rows are preserved.
SET search_path TO hhy, public;

DROP TRIGGER IF EXISTS trg_r09_media_object_apk_mutation ON hhy.media_objects;
DROP FUNCTION IF EXISTS hhy.guard_r09_media_object_apk_mutation();
DROP TRIGGER IF EXISTS trg_r09_app_media_safe ON hhy.content_media;
DROP FUNCTION IF EXISTS hhy.assert_r09_app_media_safe();
DROP TRIGGER IF EXISTS trg_r09_app_detail_delete ON hhy.app_details;
DROP FUNCTION IF EXISTS hhy.guard_r09_app_detail_delete();
DROP TRIGGER IF EXISTS trg_r09_app_detail_required ON hhy.content_posts;
DROP FUNCTION IF EXISTS hhy.assert_r09_app_detail_required();
DROP INDEX IF EXISTS hhy.ix_r09_app_platform;
DROP INDEX IF EXISTS hhy.ix_r09_app_public_list;

ALTER TABLE hhy.app_details
  DROP CONSTRAINT IF EXISTS ck_r09_app_detail_values;
