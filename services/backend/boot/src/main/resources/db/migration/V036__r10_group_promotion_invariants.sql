-- R10 group promotion, join-channel, and owner-contact invariants.
SET search_path TO hhy, public;

-- Keep upgrade validation stable while legacy rows and prerequisite objects are inspected.
LOCK TABLE hhy.content_posts, hhy.group_details, hhy.content_contacts, hhy.media_objects
  IN SHARE ROW EXCLUSIVE MODE;

DO $$
BEGIN
  IF NOT EXISTS (
    SELECT 1 FROM pg_constraint
    WHERE connamespace = 'hhy'::regnamespace
      AND conname = 'uq_idempotency_records_scope_idem_key'
  ) THEN
    RAISE EXCEPTION 'R10_IDEMPOTENCY_PREREQUISITE_MISSING' USING ERRCODE = '55000';
  END IF;
  IF NOT EXISTS (
    SELECT 1 FROM pg_constraint
    WHERE connamespace = 'hhy'::regnamespace
      AND conname = 'uq_group_details_content_id'
  ) THEN
    RAISE EXCEPTION 'R10_GROUP_UNIQUENESS_PREREQUISITE_MISSING' USING ERRCODE = '55000';
  END IF;
  IF NOT EXISTS (
    SELECT 1 FROM pg_constraint
    WHERE connamespace = 'hhy'::regnamespace
      AND conname = 'fk_content_contacts_content_id_content_posts'
  ) THEN
    RAISE EXCEPTION 'R10_CONTENT_CONTACT_PREREQUISITE_MISSING' USING ERRCODE = '55000';
  END IF;

  IF EXISTS (
    SELECT 1
    FROM hhy.group_details detail
    LEFT JOIN hhy.media_objects media ON media.id = detail.qr_media_id
    WHERE detail.qr_media_id IS NOT NULL AND media.id IS NULL
  ) THEN
    RAISE EXCEPTION 'R10_GROUP_QR_MEDIA_ORPHAN_REQUIRES_REVIEW' USING ERRCODE = '23503';
  END IF;
  IF EXISTS (
    SELECT 1
    FROM hhy.content_posts content
    WHERE content.type = 'GROUP' AND content.status <> 'DELETED'
      AND NOT EXISTS (
        SELECT 1 FROM hhy.group_details detail WHERE detail.content_id = content.id
      )
  ) THEN
    RAISE EXCEPTION 'R10_LEGACY_GROUP_DETAIL_MISSING_REQUIRES_REVIEW' USING ERRCODE = '23514';
  END IF;
  IF EXISTS (
    SELECT 1
    FROM hhy.content_contacts contact
    JOIN hhy.content_posts content ON content.id = contact.content_id
    WHERE contact.channel = 'JOIN_PASSWORD' AND content.type <> 'GROUP'
  ) THEN
    RAISE EXCEPTION 'R10_LEGACY_JOIN_PASSWORD_NON_GROUP_REQUIRES_REVIEW'
      USING ERRCODE = '23514';
  END IF;
  IF EXISTS (
    SELECT 1
    FROM hhy.content_contacts contact
    WHERE contact.channel = 'JOIN_PASSWORD'
    GROUP BY contact.content_id
    HAVING count(*) > 1
  ) THEN
    RAISE EXCEPTION 'R10_LEGACY_JOIN_PASSWORD_DUPLICATE_REQUIRES_REVIEW'
      USING ERRCODE = '23514';
  END IF;
  IF EXISTS (
    SELECT 1
    FROM hhy.content_posts content
    JOIN hhy.group_details detail ON detail.content_id = content.id
    WHERE content.type = 'GROUP' AND content.status = 'ONLINE'
      AND (
        detail.platform <> btrim(detail.platform) OR btrim(detail.platform) = ''
        OR (
          detail.size_range IS NOT NULL
          AND (detail.size_range <> btrim(detail.size_range) OR btrim(detail.size_range) = '')
        )
        OR (
          detail.join_requirement IS NOT NULL
          AND (
            detail.join_requirement <> btrim(detail.join_requirement)
            OR btrim(detail.join_requirement) = ''
          )
        )
        OR (
          detail.group_link IS NOT NULL
          AND (detail.group_link <> btrim(detail.group_link) OR btrim(detail.group_link) = '')
        )
        OR (
          detail.group_no IS NOT NULL
          AND (detail.group_no <> btrim(detail.group_no) OR btrim(detail.group_no) = '')
        )
      )
  ) THEN
    RAISE EXCEPTION 'R10_LEGACY_ONLINE_GROUP_DETAIL_INVALID_REQUIRES_REVIEW'
      USING ERRCODE = '23514';
  END IF;
  IF EXISTS (
    SELECT 1
    FROM hhy.content_posts content
    JOIN hhy.group_details detail ON detail.content_id = content.id
    WHERE content.type = 'GROUP' AND content.status = 'ONLINE'
      AND detail.qr_media_id IS NULL
      AND detail.group_link IS NULL
      AND detail.group_no IS NULL
      AND NOT EXISTS (
        SELECT 1
        FROM hhy.content_contacts contact
        WHERE contact.content_id = content.id
          AND contact.channel = 'JOIN_PASSWORD'
          AND contact.value_cipher IS NOT NULL AND btrim(contact.value_cipher) <> ''
          AND contact.display_mask IS NOT NULL AND btrim(contact.display_mask) <> ''
          AND contact.sort_order IS NOT NULL AND contact.sort_order >= 0
      )
  ) THEN
    RAISE EXCEPTION 'R10_LEGACY_ONLINE_GROUP_ENTRY_CHANNEL_MISSING_REQUIRES_REVIEW'
      USING ERRCODE = '23514';
  END IF;
  IF EXISTS (
    SELECT 1
    FROM hhy.content_posts content
    WHERE content.type = 'GROUP' AND content.status = 'ONLINE'
      AND NOT EXISTS (
        SELECT 1
        FROM hhy.content_contacts contact
        WHERE contact.content_id = content.id
          AND contact.channel IN ('WECHAT', 'PHONE', 'QQ', 'EMAIL')
          AND contact.value_cipher IS NOT NULL AND btrim(contact.value_cipher) <> ''
          AND contact.display_mask IS NOT NULL AND btrim(contact.display_mask) <> ''
          AND contact.sort_order IS NOT NULL AND contact.sort_order >= 0
      )
  ) THEN
    RAISE EXCEPTION 'R10_LEGACY_ONLINE_GROUP_OWNER_CONTACT_MISSING_REQUIRES_REVIEW'
      USING ERRCODE = '23514';
  END IF;
END;
$$;

ALTER TABLE hhy.group_details
  ADD CONSTRAINT ck_r10_group_detail_values
    CHECK (
      platform = btrim(platform) AND platform <> ''
      AND (size_range IS NULL OR (size_range = btrim(size_range) AND size_range <> ''))
      AND (
        join_requirement IS NULL
        OR (join_requirement = btrim(join_requirement) AND join_requirement <> '')
      )
      AND (group_link IS NULL OR (group_link = btrim(group_link) AND group_link <> ''))
      AND (group_no IS NULL OR (group_no = btrim(group_no) AND group_no <> ''))
    ) NOT VALID,
  ADD CONSTRAINT fk_r10_group_qr_media
    FOREIGN KEY (qr_media_id) REFERENCES hhy.media_objects(id) ON DELETE RESTRICT;

CREATE INDEX ix_r10_group_public_list
  ON hhy.content_posts (status, created_at DESC, id DESC)
  WHERE type = 'GROUP' AND status <> 'DELETED';
CREATE INDEX ix_r10_group_platform
  ON hhy.group_details (platform, content_id);
CREATE UNIQUE INDEX uq_r10_group_join_password
  ON hhy.content_contacts (content_id)
  WHERE channel = 'JOIN_PASSWORD';

-- Child writes take the parent row lock before mutation. This serializes terminal-state
-- checks without imposing an immediate business-order requirement on the transaction.
CREATE OR REPLACE FUNCTION hhy.lock_r10_group_parent()
RETURNS trigger LANGUAGE plpgsql AS $$
DECLARE
  old_content_id bigint;
  new_content_id bigint;
BEGIN
  IF TG_OP <> 'INSERT' THEN
    old_content_id := OLD.content_id;
  END IF;
  IF TG_OP <> 'DELETE' THEN
    new_content_id := NEW.content_id;
  END IF;

  PERFORM 1
  FROM hhy.content_posts content
  WHERE content.id IN (old_content_id, new_content_id)
  ORDER BY content.id
  FOR UPDATE;

  IF TG_OP = 'DELETE' THEN
    RETURN OLD;
  END IF;
  RETURN NEW;
END;
$$;

CREATE TRIGGER trg_r10_group_detail_parent_lock
  BEFORE INSERT OR UPDATE OR DELETE ON hhy.group_details
  FOR EACH ROW EXECUTE FUNCTION hhy.lock_r10_group_parent();
CREATE TRIGGER trg_r10_group_contact_parent_lock
  BEFORE INSERT OR UPDATE OR DELETE ON hhy.content_contacts
  FOR EACH ROW EXECUTE FUNCTION hhy.lock_r10_group_parent();

CREATE OR REPLACE FUNCTION hhy.assert_r10_group_terminal_state()
RETURNS trigger LANGUAGE plpgsql AS $$
DECLARE
  old_content_id bigint;
  new_content_id bigint;
  checked_content_id bigint;
  content_type varchar(64);
  content_status varchar(64);
BEGIN
  IF TG_TABLE_NAME = 'content_posts' THEN
    IF TG_OP <> 'INSERT' THEN
      old_content_id := OLD.id;
    END IF;
    IF TG_OP <> 'DELETE' THEN
      new_content_id := NEW.id;
    END IF;
  ELSE
    IF TG_OP <> 'INSERT' THEN
      old_content_id := OLD.content_id;
    END IF;
    IF TG_OP <> 'DELETE' THEN
      new_content_id := NEW.content_id;
    END IF;
  END IF;

  FOR checked_content_id IN
    SELECT DISTINCT candidate.content_id
    FROM (VALUES (old_content_id), (new_content_id)) AS candidate(content_id)
    WHERE candidate.content_id IS NOT NULL
    ORDER BY candidate.content_id
  LOOP
    SELECT content.type, content.status
      INTO content_type, content_status
    FROM hhy.content_posts content
    WHERE content.id = checked_content_id
    FOR UPDATE;

    IF NOT FOUND THEN
      CONTINUE;
    END IF;

    IF content_type <> 'GROUP' AND EXISTS (
      SELECT 1
      FROM hhy.content_contacts contact
      WHERE contact.content_id = checked_content_id
        AND contact.channel = 'JOIN_PASSWORD'
    ) THEN
      RAISE EXCEPTION 'R10_JOIN_PASSWORD_REQUIRES_GROUP content_id=%', checked_content_id
        USING ERRCODE = '23514';
    END IF;

    IF content_type <> 'GROUP' OR content_status = 'DELETED' THEN
      CONTINUE;
    END IF;

    IF NOT EXISTS (
      SELECT 1 FROM hhy.group_details detail WHERE detail.content_id = checked_content_id
    ) THEN
      RAISE EXCEPTION 'R10_GROUP_DETAIL_REQUIRED content_id=%', checked_content_id
        USING ERRCODE = '23514';
    END IF;

    IF content_status = 'ONLINE' THEN
      IF NOT EXISTS (
        SELECT 1
        FROM hhy.group_details detail
        WHERE detail.content_id = checked_content_id
          AND detail.platform = btrim(detail.platform) AND detail.platform <> ''
          AND (
            detail.size_range IS NULL
            OR (detail.size_range = btrim(detail.size_range) AND detail.size_range <> '')
          )
          AND (
            detail.join_requirement IS NULL
            OR (
              detail.join_requirement = btrim(detail.join_requirement)
              AND detail.join_requirement <> ''
            )
          )
          AND (
            detail.group_link IS NULL
            OR (detail.group_link = btrim(detail.group_link) AND detail.group_link <> '')
          )
          AND (
            detail.group_no IS NULL
            OR (detail.group_no = btrim(detail.group_no) AND detail.group_no <> '')
          )
      ) THEN
        RAISE EXCEPTION 'R10_ONLINE_GROUP_DETAIL_INVALID content_id=%', checked_content_id
          USING ERRCODE = '23514';
      END IF;

      IF NOT EXISTS (
        SELECT 1
        FROM hhy.group_details detail
        WHERE detail.content_id = checked_content_id
          AND (
            detail.qr_media_id IS NOT NULL
            OR detail.group_link IS NOT NULL
            OR detail.group_no IS NOT NULL
          )
      ) AND NOT EXISTS (
        SELECT 1
        FROM hhy.content_contacts contact
        WHERE contact.content_id = checked_content_id
          AND contact.channel = 'JOIN_PASSWORD'
          AND contact.value_cipher IS NOT NULL AND btrim(contact.value_cipher) <> ''
          AND contact.display_mask IS NOT NULL AND btrim(contact.display_mask) <> ''
          AND contact.sort_order IS NOT NULL AND contact.sort_order >= 0
      ) THEN
        RAISE EXCEPTION 'R10_ONLINE_GROUP_ENTRY_CHANNEL_REQUIRED content_id=%', checked_content_id
          USING ERRCODE = '23514';
      END IF;

      IF NOT EXISTS (
        SELECT 1
        FROM hhy.content_contacts contact
        WHERE contact.content_id = checked_content_id
          AND contact.channel IN ('WECHAT', 'PHONE', 'QQ', 'EMAIL')
          AND contact.value_cipher IS NOT NULL AND btrim(contact.value_cipher) <> ''
          AND contact.display_mask IS NOT NULL AND btrim(contact.display_mask) <> ''
          AND contact.sort_order IS NOT NULL AND contact.sort_order >= 0
      ) THEN
        RAISE EXCEPTION 'R10_ONLINE_GROUP_OWNER_CONTACT_REQUIRED content_id=%', checked_content_id
          USING ERRCODE = '23514';
      END IF;
    END IF;
  END LOOP;

  IF TG_OP = 'DELETE' THEN
    RETURN OLD;
  END IF;
  RETURN NEW;
END;
$$;

CREATE CONSTRAINT TRIGGER trg_r10_content_terminal
  AFTER INSERT OR UPDATE OR DELETE ON hhy.content_posts
  DEFERRABLE INITIALLY DEFERRED
  FOR EACH ROW EXECUTE FUNCTION hhy.assert_r10_group_terminal_state();
CREATE CONSTRAINT TRIGGER trg_r10_group_detail_terminal
  AFTER INSERT OR UPDATE OR DELETE ON hhy.group_details
  DEFERRABLE INITIALLY DEFERRED
  FOR EACH ROW EXECUTE FUNCTION hhy.assert_r10_group_terminal_state();
CREATE CONSTRAINT TRIGGER trg_r10_group_contact_terminal
  AFTER INSERT OR UPDATE OR DELETE ON hhy.content_contacts
  DEFERRABLE INITIALLY DEFERRED
  FOR EACH ROW EXECUTE FUNCTION hhy.assert_r10_group_terminal_state();
