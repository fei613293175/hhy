-- R09 App promotion, external-link, and no-APK invariants.
SET search_path TO hhy, public;

DO $$
BEGIN
  IF NOT EXISTS (
    SELECT 1 FROM pg_constraint
    WHERE connamespace = 'hhy'::regnamespace
      AND conname = 'uq_idempotency_records_scope_idem_key'
  ) THEN
    RAISE EXCEPTION 'R09_IDEMPOTENCY_PREREQUISITE_MISSING' USING ERRCODE = '55000';
  END IF;
  IF NOT EXISTS (
    SELECT 1 FROM pg_constraint
    WHERE connamespace = 'hhy'::regnamespace
      AND conname = 'uq_app_details_content_id'
  ) THEN
    RAISE EXCEPTION 'R09_APP_UNIQUENESS_PREREQUISITE_MISSING' USING ERRCODE = '55000';
  END IF;
  IF EXISTS (
    SELECT 1
    FROM hhy.content_media link
    JOIN hhy.content_posts content ON content.id = link.content_id
    JOIN hhy.media_objects media ON media.id = link.media_id
    WHERE content.type = 'APP'
      AND (
        upper(btrim(COALESCE(link.media_type, ''))) IN
          ('APK', 'ANDROID_PACKAGE', 'APPLICATION/VND.ANDROID.PACKAGE-ARCHIVE')
        OR lower(btrim(COALESCE(media.mime, ''))) = 'application/vnd.android.package-archive'
        OR COALESCE(media.object_key, '') ~* '\\.apk($|[?#])'
      )
  ) THEN
    RAISE EXCEPTION 'R09_LEGACY_APP_APK_REQUIRES_REVIEW' USING ERRCODE = '23514';
  END IF;
END;
$$;

ALTER TABLE hhy.app_details
  ADD CONSTRAINT ck_r09_app_detail_values
    CHECK (
      btrim(app_name) <> ''
      AND (platform IS NULL OR btrim(platform) <> '')
      AND (version_text IS NULL OR btrim(version_text) <> '')
      AND (download_url IS NULL OR btrim(download_url) <> '')
      AND (website IS NULL OR btrim(website) <> '')
    ) NOT VALID;

CREATE INDEX ix_r09_app_public_list
  ON hhy.content_posts (status, created_at DESC, id DESC)
  WHERE type = 'APP' AND status <> 'DELETED';
CREATE INDEX ix_r09_app_platform
  ON hhy.app_details (platform, content_id)
  WHERE platform IS NOT NULL;

CREATE OR REPLACE FUNCTION hhy.assert_r09_app_detail_required()
RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
  IF NEW.type = 'APP' AND NEW.status <> 'DELETED'
     AND NOT EXISTS (SELECT 1 FROM hhy.app_details WHERE content_id = NEW.id) THEN
    RAISE EXCEPTION 'R09_APP_DETAIL_REQUIRED content_id=%', NEW.id USING ERRCODE = '23514';
  END IF;
  RETURN NEW;
END;
$$;

CREATE CONSTRAINT TRIGGER trg_r09_app_detail_required
  AFTER INSERT OR UPDATE OF type, status ON hhy.content_posts
  DEFERRABLE INITIALLY DEFERRED
  FOR EACH ROW EXECUTE FUNCTION hhy.assert_r09_app_detail_required();

CREATE OR REPLACE FUNCTION hhy.guard_r09_app_detail_delete()
RETURNS trigger LANGUAGE plpgsql AS $$
DECLARE
  content_status varchar(64);
BEGIN
  SELECT status INTO content_status
  FROM hhy.content_posts
  WHERE id = OLD.content_id
  FOR KEY SHARE;
  IF content_status IS NULL OR content_status <> 'DELETED' THEN
    RAISE EXCEPTION 'R09_APP_DETAIL_DELETE_REQUIRES_SOFT_DELETE content_id=%', OLD.content_id
      USING ERRCODE = '55000';
  END IF;
  RETURN OLD;
END;
$$;

CREATE TRIGGER trg_r09_app_detail_delete
  BEFORE DELETE ON hhy.app_details
  FOR EACH ROW EXECUTE FUNCTION hhy.guard_r09_app_detail_delete();

CREATE OR REPLACE FUNCTION hhy.assert_r09_app_media_safe()
RETURNS trigger LANGUAGE plpgsql AS $$
DECLARE
  content_type varchar(64);
  media_mime varchar(255);
  media_key text;
BEGIN
  SELECT type INTO content_type FROM hhy.content_posts WHERE id = NEW.content_id FOR KEY SHARE;
  IF content_type = 'APP' THEN
    SELECT mime, object_key INTO media_mime, media_key
    FROM hhy.media_objects WHERE id = NEW.media_id FOR KEY SHARE;
    IF upper(btrim(COALESCE(NEW.media_type, ''))) IN
         ('APK', 'ANDROID_PACKAGE', 'APPLICATION/VND.ANDROID.PACKAGE-ARCHIVE')
       OR lower(btrim(COALESCE(media_mime, ''))) = 'application/vnd.android.package-archive'
       OR COALESCE(media_key, '') ~* '\\.apk($|[?#])' THEN
      RAISE EXCEPTION 'R09_APP_APK_MEDIA_FORBIDDEN content_id=%, media_id=%',
        NEW.content_id, NEW.media_id USING ERRCODE = '23514';
    END IF;
  END IF;
  RETURN NEW;
END;
$$;

CREATE TRIGGER trg_r09_app_media_safe
  BEFORE INSERT OR UPDATE OF content_id, media_id, media_type ON hhy.content_media
  FOR EACH ROW EXECUTE FUNCTION hhy.assert_r09_app_media_safe();

CREATE OR REPLACE FUNCTION hhy.guard_r09_media_object_apk_mutation()
RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
  IF EXISTS (
    SELECT 1
    FROM hhy.content_media link
    JOIN hhy.content_posts content ON content.id = link.content_id
    WHERE link.media_id = NEW.id AND content.type = 'APP'
  ) AND (
    lower(btrim(COALESCE(NEW.mime, ''))) = 'application/vnd.android.package-archive'
    OR COALESCE(NEW.object_key, '') ~* '\\.apk($|[?#])'
  ) THEN
    RAISE EXCEPTION 'R09_LINKED_APP_MEDIA_CANNOT_BECOME_APK media_id=%', NEW.id
      USING ERRCODE = '23514';
  END IF;
  RETURN NEW;
END;
$$;

CREATE TRIGGER trg_r09_media_object_apk_mutation
  BEFORE UPDATE OF mime, object_key ON hhy.media_objects
  FOR EACH ROW EXECUTE FUNCTION hhy.guard_r09_media_object_apk_mutation();
