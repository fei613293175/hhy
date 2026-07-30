-- R06 unified content and home invariants.
SET search_path TO hhy, public;

ALTER TABLE hhy.content_posts
  ALTER COLUMN refresh_times SET DEFAULT 0;

ALTER TABLE hhy.content_posts
  ADD CONSTRAINT ck_r06_content_type
    CHECK (type IN ('PROJECT','APP','GROUP','TEAM_LEADER')) NOT VALID,
  ADD CONSTRAINT ck_r06_content_title
    CHECK (title IS NOT NULL AND btrim(title) <> '') NOT VALID,
  ADD CONSTRAINT ck_r06_content_counter_version
    CHECK (COALESCE(refresh_times, 0) >= 0 AND version >= 0) NOT VALID;

CREATE UNIQUE INDEX uq_r06_team_leader_content
  ON hhy.team_leader_details (content_id);
CREATE UNIQUE INDEX uq_r06_team_leader_owner_active
  ON hhy.content_posts (owner_id)
  WHERE type = 'TEAM_LEADER' AND status <> 'DELETED';

CREATE OR REPLACE FUNCTION hhy.assert_r06_content_detail_type()
RETURNS trigger LANGUAGE plpgsql AS $$
DECLARE
  expected_type varchar(64);
  actual_type varchar(64);
BEGIN
  expected_type := CASE TG_TABLE_NAME
    WHEN 'project_details' THEN 'PROJECT'
    WHEN 'app_details' THEN 'APP'
    WHEN 'group_details' THEN 'GROUP'
    WHEN 'team_leader_details' THEN 'TEAM_LEADER'
  END;
  SELECT type INTO actual_type FROM hhy.content_posts WHERE id = NEW.content_id FOR KEY SHARE;
  IF actual_type IS NULL OR actual_type <> expected_type THEN
    RAISE EXCEPTION 'R06_CONTENT_DETAIL_TYPE_MISMATCH table=%, expected=%, actual=%',
      TG_TABLE_NAME, expected_type, actual_type USING ERRCODE = '23514';
  END IF;
  RETURN NEW;
END;
$$;

CREATE TRIGGER trg_r06_project_detail_type
  BEFORE INSERT OR UPDATE ON hhy.project_details
  FOR EACH ROW EXECUTE FUNCTION hhy.assert_r06_content_detail_type();
CREATE TRIGGER trg_r06_app_detail_type
  BEFORE INSERT OR UPDATE ON hhy.app_details
  FOR EACH ROW EXECUTE FUNCTION hhy.assert_r06_content_detail_type();
CREATE TRIGGER trg_r06_group_detail_type
  BEFORE INSERT OR UPDATE ON hhy.group_details
  FOR EACH ROW EXECUTE FUNCTION hhy.assert_r06_content_detail_type();
CREATE TRIGGER trg_r06_team_leader_detail_type
  BEFORE INSERT OR UPDATE ON hhy.team_leader_details
  FOR EACH ROW EXECUTE FUNCTION hhy.assert_r06_content_detail_type();

CREATE OR REPLACE FUNCTION hhy.guard_r06_content_type_change()
RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
  IF OLD.type IS DISTINCT FROM NEW.type AND (
    EXISTS (SELECT 1 FROM hhy.project_details WHERE content_id = OLD.id)
    OR EXISTS (SELECT 1 FROM hhy.app_details WHERE content_id = OLD.id)
    OR EXISTS (SELECT 1 FROM hhy.group_details WHERE content_id = OLD.id)
    OR EXISTS (SELECT 1 FROM hhy.team_leader_details WHERE content_id = OLD.id)
  ) THEN
    RAISE EXCEPTION 'R06_CONTENT_TYPE_CHANGE_WITH_DETAIL content_id=%', OLD.id USING ERRCODE = '23514';
  END IF;
  RETURN NEW;
END;
$$;

CREATE TRIGGER trg_r06_content_type_change
  BEFORE UPDATE OF type ON hhy.content_posts
  FOR EACH ROW EXECUTE FUNCTION hhy.guard_r06_content_type_change();

ALTER TABLE hhy.content_media
  ADD CONSTRAINT ck_r06_content_media
    CHECK (
      media_type IS NOT NULL AND btrim(media_type) <> ''
      AND sort_order IS NOT NULL AND sort_order >= 0
    ) NOT VALID;

ALTER TABLE hhy.content_contacts
  ADD CONSTRAINT ck_r06_content_contact
    CHECK (
      channel IS NOT NULL AND btrim(channel) <> ''
      AND value_cipher IS NOT NULL
      AND display_mask IS NOT NULL AND btrim(display_mask) <> ''
      AND sort_order IS NOT NULL AND sort_order >= 0
    ) NOT VALID;
CREATE UNIQUE INDEX uq_r06_content_contact_order
  ON hhy.content_contacts (content_id, channel, sort_order);

ALTER TABLE hhy.content_versions
  ADD CONSTRAINT ck_r06_content_version_snapshot
    CHECK (
      btrim(version_no) <> ''
      AND snapshot_json IS NOT NULL
      AND jsonb_typeof(snapshot_json) = 'object'
    ) NOT VALID;
CREATE TRIGGER trg_r06_content_version_immutable
  BEFORE UPDATE OR DELETE ON hhy.content_versions
  FOR EACH ROW EXECUTE FUNCTION hhy.prevent_immutable_mutation();

ALTER TABLE hhy.content_status_logs
  ADD CONSTRAINT ck_r06_content_status_history
    CHECK (
      to_status IS NOT NULL AND btrim(to_status) <> ''
      AND (from_status IS NULL OR btrim(from_status) <> '')
      AND from_status IS DISTINCT FROM to_status
      AND operator IS NOT NULL AND btrim(operator) <> ''
    ) NOT VALID;
CREATE INDEX ix_r06_content_status_history
  ON hhy.content_status_logs (content_id, created_at DESC, id DESC);
CREATE TRIGGER trg_r06_content_status_immutable
  BEFORE UPDATE OR DELETE ON hhy.content_status_logs
  FOR EACH ROW EXECUTE FUNCTION hhy.prevent_immutable_mutation();

ALTER TABLE hhy.content_review_records
  ADD CONSTRAINT fk_r06_content_review_admin
    FOREIGN KEY (admin_id) REFERENCES hhy.admin_users(id) ON DELETE RESTRICT NOT VALID,
  ADD CONSTRAINT ck_r06_content_review_decision
    CHECK (decision IS NOT NULL AND btrim(decision) <> '') NOT VALID,
  ADD CONSTRAINT ck_r06_content_review_reason
    CHECK (
      decision NOT IN ('REJECT','BAN','OFFLINE')
      OR (reason IS NOT NULL AND btrim(reason) <> '')
    ) NOT VALID;
CREATE INDEX ix_r06_content_review_version
  ON hhy.content_review_records (content_id, version_no, created_at DESC);
CREATE TRIGGER trg_r06_content_review_immutable
  BEFORE UPDATE OR DELETE ON hhy.content_review_records
  FOR EACH ROW EXECUTE FUNCTION hhy.prevent_immutable_mutation();

ALTER TABLE hhy.home_modules
  ADD CONSTRAINT ck_r06_home_module_config
    CHECK (
      btrim(code) <> '' AND btrim(title) <> ''
      AND display_order IS NOT NULL AND display_order >= 0
      AND (config_json IS NULL OR jsonb_typeof(config_json) = 'object')
    ) NOT VALID;
CREATE INDEX ix_r06_home_module_enabled_order
  ON hhy.home_modules (enabled, display_order, id);

ALTER TABLE hhy.banners
  ADD CONSTRAINT ck_r06_banner_schedule
    CHECK (
      btrim(position) <> ''
      AND (starts_at IS NULL OR ends_at IS NULL OR ends_at > starts_at)
    ) NOT VALID;
CREATE INDEX ix_r06_banner_schedule
  ON hhy.banners (position, starts_at, ends_at);
