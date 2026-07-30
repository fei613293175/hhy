-- Development/test rollback for R12 review escalation. Existing escalation facts are never deleted.
BEGIN;
SET search_path TO hhy, public;

LOCK TABLE hhy.content_review_records IN SHARE ROW EXCLUSIVE MODE;
DO $$
BEGIN
  IF EXISTS (SELECT 1 FROM hhy.content_review_records WHERE decision='ESCALATE') THEN
    RAISE EXCEPTION 'R12_U040_ESCALATION_FACTS_PRESENT'
      USING ERRCODE='55000';
  END IF;
END;
$$;

DROP TRIGGER IF EXISTS trg_r12_review_escalation_commit ON hhy.content_review_records;
DROP FUNCTION IF EXISTS hhy.assert_r12_review_escalation_commit();
DROP INDEX IF EXISTS hhy.uq_r12_content_review_escalation_snapshot;

ALTER TABLE hhy.content_review_records
  DROP CONSTRAINT ck_r12_content_review_action;
ALTER TABLE hhy.content_review_records
  ADD CONSTRAINT ck_r12_content_review_action
    CHECK (
      snapshot_version_id IS NULL
      OR decision IN ('CLAIM','ASSIGN','APPROVE','REJECT')
    );

CREATE OR REPLACE FUNCTION hhy.guard_r12_content_review_insert()
RETURNS trigger LANGUAGE plpgsql AS $$
DECLARE
  snapshot_content_id bigint;
  snapshot_version varchar(255);
  current_version bigint;
BEGIN
  IF NEW.snapshot_version_id IS NULL OR NEW.command_id IS NULL OR btrim(NEW.command_id)='' THEN
    RAISE EXCEPTION 'R12_CONTENT_REVIEW_BINDING_REQUIRED content_id=%',NEW.content_id
      USING ERRCODE='23514';
  END IF;
  SELECT content_id,version_no INTO snapshot_content_id,snapshot_version
  FROM hhy.content_versions WHERE id=NEW.snapshot_version_id FOR KEY SHARE;
  IF snapshot_content_id IS NULL OR snapshot_content_id<>NEW.content_id THEN
    RAISE EXCEPTION 'R12_CONTENT_REVIEW_SNAPSHOT_MISMATCH content_id=% snapshot_id=%',
      NEW.content_id,NEW.snapshot_version_id USING ERRCODE='23503';
  END IF;
  IF NEW.version_no IS NULL THEN NEW.version_no:=snapshot_version; END IF;
  IF NEW.version_no<>snapshot_version THEN
    RAISE EXCEPTION 'R12_CONTENT_REVIEW_VERSION_MISMATCH content_id=%',NEW.content_id
      USING ERRCODE='23514';
  END IF;
  IF NEW.decision NOT IN ('CLAIM','ASSIGN','APPROVE','REJECT')
     OR (NEW.decision='REJECT' AND btrim(COALESCE(NEW.reason,''))='') THEN
    RAISE EXCEPTION 'R12_CONTENT_REVIEW_ACTION_INVALID content_id=% decision=%',
      NEW.content_id,NEW.decision USING ERRCODE='23514';
  END IF;
  SELECT version INTO current_version FROM hhy.content_posts WHERE id=NEW.content_id FOR KEY SHARE;
  IF current_setting('hhy.content_version_'||NEW.content_id::text,true)
       IS DISTINCT FROM current_version::text THEN
    RAISE EXCEPTION 'R12_CONTENT_REVIEW_REQUIRES_VERSIONED_WRITE content_id=%',NEW.content_id
      USING ERRCODE='23514';
  END IF;
  RETURN NEW;
END;
$$;

COMMIT;
