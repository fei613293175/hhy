-- Development/test rollback for R08 project invariants. Business rows are preserved.
SET search_path TO hhy, public;

DROP TRIGGER IF EXISTS trg_r08_project_detail_delete ON hhy.project_details;
DROP FUNCTION IF EXISTS hhy.guard_r08_project_detail_delete();
DROP INDEX IF EXISTS hhy.ix_r08_project_region;
DROP INDEX IF EXISTS hhy.ix_r08_project_public_list;

ALTER TABLE hhy.content_versions
  DROP CONSTRAINT IF EXISTS ck_r08_content_version_number;
ALTER TABLE hhy.content_stats
  DROP CONSTRAINT IF EXISTS ck_r08_content_stats_numeric;
ALTER TABLE hhy.project_details
  DROP CONSTRAINT IF EXISTS ck_r08_project_detail_values;
