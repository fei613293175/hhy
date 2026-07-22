-- R08 project publishing, listing, detail, and edit invariants.
SET search_path TO hhy, public;

DO $$
BEGIN
  IF NOT EXISTS (
    SELECT 1 FROM pg_constraint
    WHERE connamespace = 'hhy'::regnamespace
      AND conname = 'uq_idempotency_records_scope_idem_key'
  ) THEN
    RAISE EXCEPTION 'R08_IDEMPOTENCY_PREREQUISITE_MISSING' USING ERRCODE = '55000';
  END IF;
  IF NOT EXISTS (
    SELECT 1 FROM pg_constraint
    WHERE connamespace = 'hhy'::regnamespace
      AND conname = 'uq_project_details_content_id'
  ) THEN
    RAISE EXCEPTION 'R08_PROJECT_UNIQUENESS_PREREQUISITE_MISSING' USING ERRCODE = '55000';
  END IF;
END;
$$;

ALTER TABLE hhy.project_details
  ADD CONSTRAINT ck_r08_project_detail_values
    CHECK (
      btrim(cooperation) <> ''
      AND (conditions IS NULL OR btrim(conditions) <> '')
      AND (region IS NULL OR btrim(region) <> '')
      AND (website IS NULL OR btrim(website) <> '')
    ) NOT VALID;

ALTER TABLE hhy.content_stats
  ADD CONSTRAINT ck_r08_content_stats_numeric
    CHECK (
      organic_views ~ '^[0-9]+$'
      AND (redpacket_views IS NULL OR redpacket_views ~ '^[0-9]+$')
      AND (task_views IS NULL OR task_views ~ '^[0-9]+$')
      AND (favorites IS NULL OR favorites ~ '^[0-9]+$')
      AND (chats IS NULL OR chats ~ '^[0-9]+$')
      AND (contacts IS NULL OR contacts ~ '^[0-9]+$')
    ) NOT VALID;

ALTER TABLE hhy.content_versions
  ADD CONSTRAINT ck_r08_content_version_number
    CHECK (version_no ~ '^(0|[1-9][0-9]*)$') NOT VALID;

CREATE INDEX ix_r08_project_public_list
  ON hhy.content_posts (status, created_at DESC, id DESC)
  WHERE type = 'PROJECT' AND status <> 'DELETED';
CREATE INDEX ix_r08_project_region
  ON hhy.project_details (region, content_id)
  WHERE region IS NOT NULL;

CREATE OR REPLACE FUNCTION hhy.guard_r08_project_detail_delete()
RETURNS trigger LANGUAGE plpgsql AS $$
DECLARE
  content_status varchar(64);
BEGIN
  SELECT status INTO content_status
  FROM hhy.content_posts
  WHERE id = OLD.content_id
  FOR KEY SHARE;
  IF content_status IS NULL OR content_status <> 'DELETED' THEN
    RAISE EXCEPTION 'R08_PROJECT_DETAIL_DELETE_REQUIRES_SOFT_DELETE content_id=%', OLD.content_id
      USING ERRCODE = '55000';
  END IF;
  RETURN OLD;
END;
$$;

CREATE TRIGGER trg_r08_project_detail_delete
  BEFORE DELETE ON hhy.project_details
  FOR EACH ROW EXECUTE FUNCTION hhy.guard_r08_project_detail_delete();
