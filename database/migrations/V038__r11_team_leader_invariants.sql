-- R11 team-leader profile and terminal-state invariants.
SET search_path TO hhy, public;

LOCK TABLE hhy.content_posts, hhy.team_leader_details, hhy.content_contacts, hhy.media_objects
  IN SHARE ROW EXCLUSIVE MODE;

DO $$
BEGIN
  IF NOT EXISTS (
    SELECT 1 FROM pg_indexes
    WHERE schemaname='hhy' AND indexname='uq_r06_team_leader_content'
  ) THEN
    RAISE EXCEPTION 'R11_TEAM_LEADER_UNIQUENESS_PREREQUISITE_MISSING' USING ERRCODE='55000';
  END IF;
  IF NOT EXISTS (
    SELECT 1 FROM pg_indexes
    WHERE schemaname='hhy' AND indexname='uq_r06_team_leader_owner_active'
  ) THEN
    RAISE EXCEPTION 'R11_TEAM_LEADER_OWNER_UNIQUENESS_PREREQUISITE_MISSING' USING ERRCODE='55000';
  END IF;
END;
$$;

ALTER TABLE hhy.team_leader_details
  ADD COLUMN nickname varchar(255),
  ADD COLUMN logo_media_id bigint,
  ADD COLUMN region varchar(255),
  ADD COLUMN personal_intro text,
  ADD COLUMN team_intro text,
  ADD COLUMN cooperation_requirement text,
  ADD COLUMN past_cases jsonb,
  ADD COLUMN accept_private_chat boolean;

DO $$
BEGIN
  IF EXISTS (
    SELECT 1
    FROM hhy.team_leader_details detail
    LEFT JOIN hhy.media_objects media ON media.id=detail.logo_media_id
    WHERE detail.logo_media_id IS NOT NULL AND media.id IS NULL
  ) THEN
    RAISE EXCEPTION 'R11_TEAM_LEADER_LOGO_ORPHAN_REQUIRES_REVIEW' USING ERRCODE='23503';
  END IF;
  IF EXISTS (
    SELECT 1
    FROM hhy.content_posts content
    WHERE content.type='TEAM_LEADER' AND content.status <> 'DELETED'
      AND NOT EXISTS (
        SELECT 1 FROM hhy.team_leader_details detail WHERE detail.content_id=content.id
      )
  ) THEN
    RAISE EXCEPTION 'R11_LEGACY_TEAM_LEADER_DETAIL_MISSING_REQUIRES_REVIEW' USING ERRCODE='23514';
  END IF;
  IF EXISTS (
    SELECT 1
    FROM hhy.content_posts content
    JOIN hhy.team_leader_details detail ON detail.content_id=content.id
    WHERE content.type='TEAM_LEADER'
      AND content.status IN ('PENDING_REVIEW','REVIEWING','APPROVED','ONLINE','OFFLINE_BY_OWNER','OFFLINE_BY_PLATFORM')
      AND (
        detail.region IS NULL OR btrim(detail.region)=''
        OR detail.nickname IS NULL OR btrim(detail.nickname)=''
        OR btrim(detail.team_name)=''
        OR detail.logo_media_id IS NULL
        OR detail.personal_intro IS NULL OR btrim(detail.personal_intro)=''
        OR detail.team_intro IS NULL OR btrim(detail.team_intro)=''
        OR detail.size_range IS NULL OR btrim(detail.size_range)=''
        OR detail.skills IS NULL OR btrim(detail.skills)=''
        OR detail.cooperation_types IS NULL OR btrim(detail.cooperation_types)=''
        OR detail.cooperation_requirement IS NULL OR btrim(detail.cooperation_requirement)=''
        OR detail.past_cases IS NULL
        OR CASE WHEN jsonb_typeof(detail.past_cases)='array'
          THEN jsonb_array_length(detail.past_cases)=0 ELSE true END
        OR detail.accept_private_chat IS NULL
      )
  ) THEN
    RAISE EXCEPTION 'R11_LEGACY_REVIEWABLE_TEAM_LEADER_INCOMPLETE_REQUIRES_REVIEW' USING ERRCODE='23514';
  END IF;
END;
$$;

ALTER TABLE hhy.team_leader_details
  ADD CONSTRAINT ck_r11_team_leader_values CHECK (
    team_name=btrim(team_name) AND team_name <> ''
    AND (nickname IS NULL OR (nickname=btrim(nickname) AND nickname <> ''))
    AND (region IS NULL OR (region=btrim(region) AND region <> ''))
    AND (size_range IS NULL OR (size_range=btrim(size_range) AND size_range <> ''))
    AND (skills IS NULL OR (skills=btrim(skills) AND skills <> ''))
    AND (cooperation_types IS NULL OR (cooperation_types=btrim(cooperation_types) AND cooperation_types <> ''))
    AND (personal_intro IS NULL OR (personal_intro=btrim(personal_intro) AND personal_intro <> ''))
    AND (team_intro IS NULL OR (team_intro=btrim(team_intro) AND team_intro <> ''))
    AND (
      cooperation_requirement IS NULL
      OR (cooperation_requirement=btrim(cooperation_requirement) AND cooperation_requirement <> '')
    )
    AND (
      past_cases IS NULL
      OR CASE WHEN jsonb_typeof(past_cases)='array'
        THEN jsonb_array_length(past_cases) BETWEEN 1 AND 20 ELSE false END
    )
  ) NOT VALID,
  ADD CONSTRAINT fk_r11_team_leader_logo_media
    FOREIGN KEY (logo_media_id) REFERENCES hhy.media_objects(id) ON DELETE RESTRICT;

CREATE INDEX ix_r11_team_leader_public_list
  ON hhy.content_posts (status, created_at DESC, id DESC)
  WHERE type='TEAM_LEADER' AND status <> 'DELETED';
CREATE INDEX ix_r11_team_leader_region_size
  ON hhy.team_leader_details (region, size_range, content_id);

CREATE OR REPLACE FUNCTION hhy.lock_r11_team_leader_parent()
RETURNS trigger LANGUAGE plpgsql AS $$
DECLARE
  old_content_id bigint;
  new_content_id bigint;
BEGIN
  IF TG_OP <> 'INSERT' THEN old_content_id := OLD.content_id; END IF;
  IF TG_OP <> 'DELETE' THEN new_content_id := NEW.content_id; END IF;
  PERFORM 1 FROM hhy.content_posts content
    WHERE content.id IN (old_content_id,new_content_id)
    ORDER BY content.id FOR UPDATE;
  IF TG_OP='DELETE' THEN RETURN OLD; END IF;
  RETURN NEW;
END;
$$;

CREATE TRIGGER trg_r11_team_leader_detail_parent_lock
  BEFORE INSERT OR UPDATE OR DELETE ON hhy.team_leader_details
  FOR EACH ROW EXECUTE FUNCTION hhy.lock_r11_team_leader_parent();
CREATE TRIGGER trg_r11_team_leader_contact_parent_lock
  BEFORE INSERT OR UPDATE OR DELETE ON hhy.content_contacts
  FOR EACH ROW EXECUTE FUNCTION hhy.lock_r11_team_leader_parent();

CREATE OR REPLACE FUNCTION hhy.assert_r11_team_leader_terminal_state()
RETURNS trigger LANGUAGE plpgsql AS $$
DECLARE
  old_content_id bigint;
  new_content_id bigint;
  checked_content_id bigint;
  content_type varchar(64);
  content_status varchar(64);
BEGIN
  IF TG_TABLE_NAME='content_posts' THEN
    IF TG_OP <> 'INSERT' THEN old_content_id := OLD.id; END IF;
    IF TG_OP <> 'DELETE' THEN new_content_id := NEW.id; END IF;
  ELSE
    IF TG_OP <> 'INSERT' THEN old_content_id := OLD.content_id; END IF;
    IF TG_OP <> 'DELETE' THEN new_content_id := NEW.content_id; END IF;
  END IF;

  FOR checked_content_id IN
    SELECT DISTINCT candidate.content_id
    FROM (VALUES(old_content_id),(new_content_id)) candidate(content_id)
    WHERE candidate.content_id IS NOT NULL ORDER BY candidate.content_id
  LOOP
    SELECT type,status INTO content_type,content_status
    FROM hhy.content_posts WHERE id=checked_content_id FOR UPDATE;
    IF NOT FOUND OR content_type <> 'TEAM_LEADER' OR content_status='DELETED' THEN CONTINUE; END IF;

    IF NOT EXISTS (
      SELECT 1 FROM hhy.team_leader_details detail WHERE detail.content_id=checked_content_id
    ) THEN
      RAISE EXCEPTION 'R11_TEAM_LEADER_DETAIL_REQUIRED content_id=%',checked_content_id USING ERRCODE='23514';
    END IF;

    IF content_status IN ('PENDING_REVIEW','REVIEWING','APPROVED','ONLINE','OFFLINE_BY_OWNER','OFFLINE_BY_PLATFORM')
      AND NOT EXISTS (
        SELECT 1
        FROM hhy.team_leader_details detail
        JOIN hhy.content_posts content ON content.id=detail.content_id
        WHERE detail.content_id=checked_content_id
          AND detail.region IS NOT NULL AND btrim(detail.region) <> ''
          AND detail.nickname IS NOT NULL AND btrim(detail.nickname) <> ''
          AND btrim(detail.team_name) <> ''
          AND detail.logo_media_id IS NOT NULL
          AND detail.personal_intro IS NOT NULL AND btrim(detail.personal_intro) <> ''
          AND detail.team_intro IS NOT NULL AND btrim(detail.team_intro) <> ''
          AND detail.size_range IS NOT NULL AND btrim(detail.size_range) <> ''
          AND detail.skills IS NOT NULL AND btrim(detail.skills) <> ''
          AND detail.cooperation_types IS NOT NULL AND btrim(detail.cooperation_types) <> ''
          AND detail.cooperation_requirement IS NOT NULL AND btrim(detail.cooperation_requirement) <> ''
          AND detail.past_cases IS NOT NULL
          AND CASE WHEN jsonb_typeof(detail.past_cases)='array'
            THEN jsonb_array_length(detail.past_cases) BETWEEN 1 AND 20 ELSE false END
          AND detail.accept_private_chat IS NOT NULL
      ) THEN
        RAISE EXCEPTION 'R11_REVIEWABLE_TEAM_LEADER_PROFILE_REQUIRED content_id=%',checked_content_id USING ERRCODE='23514';
    END IF;

    IF content_status='ONLINE' AND NOT EXISTS (
      SELECT 1 FROM hhy.content_contacts contact
      WHERE contact.content_id=checked_content_id
        AND contact.channel IN ('WECHAT','PHONE','QQ','EMAIL')
        AND contact.value_cipher IS NOT NULL AND btrim(contact.value_cipher) <> ''
        AND contact.display_mask IS NOT NULL AND btrim(contact.display_mask) <> ''
        AND contact.sort_order IS NOT NULL AND contact.sort_order >= 0
    ) THEN
      RAISE EXCEPTION 'R11_ONLINE_TEAM_LEADER_CONTACT_REQUIRED content_id=%',checked_content_id USING ERRCODE='23514';
    END IF;
  END LOOP;
  IF TG_OP='DELETE' THEN RETURN OLD; END IF;
  RETURN NEW;
END;
$$;

CREATE CONSTRAINT TRIGGER trg_r11_content_terminal
  AFTER INSERT OR UPDATE OR DELETE ON hhy.content_posts
  DEFERRABLE INITIALLY DEFERRED FOR EACH ROW
  EXECUTE FUNCTION hhy.assert_r11_team_leader_terminal_state();
CREATE CONSTRAINT TRIGGER trg_r11_team_leader_detail_terminal
  AFTER INSERT OR UPDATE OR DELETE ON hhy.team_leader_details
  DEFERRABLE INITIALLY DEFERRED FOR EACH ROW
  EXECUTE FUNCTION hhy.assert_r11_team_leader_terminal_state();
CREATE CONSTRAINT TRIGGER trg_r11_team_leader_contact_terminal
  AFTER INSERT OR UPDATE OR DELETE ON hhy.content_contacts
  DEFERRABLE INITIALLY DEFERRED FOR EACH ROW
  EXECUTE FUNCTION hhy.assert_r11_team_leader_terminal_state();
