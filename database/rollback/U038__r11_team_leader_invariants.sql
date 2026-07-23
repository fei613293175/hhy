-- Development/test rollback for R11 team-leader invariants.
SET search_path TO hhy, public;

DO $$
BEGIN
  IF EXISTS (
    SELECT 1 FROM hhy.team_leader_details
    WHERE nickname IS NOT NULL OR logo_media_id IS NOT NULL OR region IS NOT NULL OR personal_intro IS NOT NULL
      OR team_intro IS NOT NULL OR cooperation_requirement IS NOT NULL
      OR past_cases IS NOT NULL OR accept_private_chat IS NOT NULL
  ) THEN
    RAISE EXCEPTION 'R11_TEAM_LEADER_ROLLBACK_BLOCKED_NEW_VALUES_EXIST' USING ERRCODE='55000';
  END IF;
END;
$$;

DROP TRIGGER IF EXISTS trg_r11_team_leader_contact_terminal ON hhy.content_contacts;
DROP TRIGGER IF EXISTS trg_r11_team_leader_detail_terminal ON hhy.team_leader_details;
DROP TRIGGER IF EXISTS trg_r11_content_terminal ON hhy.content_posts;
DROP FUNCTION IF EXISTS hhy.assert_r11_team_leader_terminal_state();
DROP TRIGGER IF EXISTS trg_r11_team_leader_contact_parent_lock ON hhy.content_contacts;
DROP TRIGGER IF EXISTS trg_r11_team_leader_detail_parent_lock ON hhy.team_leader_details;
DROP FUNCTION IF EXISTS hhy.lock_r11_team_leader_parent();
DROP INDEX IF EXISTS hhy.ix_r11_team_leader_region_size;
DROP INDEX IF EXISTS hhy.ix_r11_team_leader_public_list;
ALTER TABLE hhy.team_leader_details
  DROP CONSTRAINT IF EXISTS fk_r11_team_leader_logo_media,
  DROP CONSTRAINT IF EXISTS ck_r11_team_leader_values,
  DROP COLUMN IF EXISTS accept_private_chat,
  DROP COLUMN IF EXISTS past_cases,
  DROP COLUMN IF EXISTS cooperation_requirement,
  DROP COLUMN IF EXISTS team_intro,
  DROP COLUMN IF EXISTS personal_intro,
  DROP COLUMN IF EXISTS logo_media_id,
  DROP COLUMN IF EXISTS region,
  DROP COLUMN IF EXISTS nickname;
