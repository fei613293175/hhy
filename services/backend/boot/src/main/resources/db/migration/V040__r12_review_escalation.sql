-- R12 content-review escalation persistence and transactional evidence.
SET search_path TO hhy, public;

LOCK TABLE hhy.content_posts IN SHARE ROW EXCLUSIVE MODE;
LOCK TABLE hhy.content_versions IN SHARE ROW EXCLUSIVE MODE;
LOCK TABLE hhy.content_status_logs IN SHARE ROW EXCLUSIVE MODE;
LOCK TABLE hhy.content_review_records IN SHARE ROW EXCLUSIVE MODE;
LOCK TABLE hhy.admin_sessions IN SHARE ROW EXCLUSIVE MODE;
LOCK TABLE hhy.admin_operation_logs IN SHARE ROW EXCLUSIVE MODE;
LOCK TABLE hhy.outbox_events IN SHARE ROW EXCLUSIVE MODE;

ALTER TABLE hhy.content_review_records
  DROP CONSTRAINT ck_r12_content_review_action;
ALTER TABLE hhy.content_review_records
  ADD CONSTRAINT ck_r12_content_review_action
    CHECK (
      snapshot_version_id IS NULL
      OR decision IN ('CLAIM','ASSIGN','APPROVE','REJECT','ESCALATE')
    ) NOT VALID;
ALTER TABLE hhy.content_review_records
  VALIDATE CONSTRAINT ck_r12_content_review_action;

CREATE UNIQUE INDEX uq_r12_content_review_escalation_snapshot
  ON hhy.content_review_records(snapshot_version_id)
  WHERE decision='ESCALATE';

CREATE OR REPLACE FUNCTION hhy.guard_r12_content_review_insert()
RETURNS trigger LANGUAGE plpgsql AS $$
DECLARE
  snapshot_content_id bigint;
  snapshot_version varchar(255);
  current_status varchar(64);
  current_version bigint;
  submitted_snapshot_id bigint;
  escalation_id bigint;
  second_reviewer_id bigint;
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
  IF NEW.decision NOT IN ('CLAIM','ASSIGN','APPROVE','REJECT','ESCALATE')
     OR (NEW.decision IN ('REJECT','ESCALATE') AND btrim(COALESCE(NEW.reason,''))='') THEN
    RAISE EXCEPTION 'R12_CONTENT_REVIEW_ACTION_INVALID content_id=% decision=%',
      NEW.content_id,NEW.decision USING ERRCODE='23514';
  END IF;
  SELECT status,version INTO current_status,current_version
  FROM hhy.content_posts WHERE id=NEW.content_id FOR KEY SHARE;
  IF current_setting('hhy.content_version_'||NEW.content_id::text,true)
       IS DISTINCT FROM current_version::text THEN
    RAISE EXCEPTION 'R12_CONTENT_REVIEW_REQUIRES_VERSIONED_WRITE content_id=%',NEW.content_id
      USING ERRCODE='23514';
  END IF;

  IF NEW.decision='ESCALATE' THEN
    IF current_status<>'REVIEWING' THEN
      RAISE EXCEPTION 'R12_CONTENT_REVIEW_ESCALATE_REQUIRES_REVIEWING content_id=% status=%',
        NEW.content_id,current_status USING ERRCODE='23514';
    END IF;
    SELECT version.id INTO submitted_snapshot_id
    FROM hhy.content_status_logs AS submitted
    JOIN hhy.content_versions AS version
      ON version.content_id=submitted.content_id
     AND version.version_no=submitted.transition_version::text
    WHERE submitted.content_id=NEW.content_id
      AND submitted.to_status='PENDING_REVIEW'
      AND submitted.transition_version<=current_version
    ORDER BY submitted.transition_version DESC,version.id DESC LIMIT 1;
    IF submitted_snapshot_id IS NULL OR submitted_snapshot_id<>NEW.snapshot_version_id THEN
      RAISE EXCEPTION 'R12_CONTENT_REVIEW_ESCALATE_SNAPSHOT_NOT_LATEST content_id=% snapshot_id=%',
        NEW.content_id,NEW.snapshot_version_id USING ERRCODE='23514';
    END IF;
  ELSIF NEW.decision IN ('APPROVE','REJECT') THEN
    SELECT id INTO escalation_id
    FROM hhy.content_review_records
    WHERE snapshot_version_id=NEW.snapshot_version_id AND decision='ESCALATE'
    ORDER BY id DESC LIMIT 1;
    IF escalation_id IS NOT NULL THEN
      SELECT admin_id INTO second_reviewer_id
      FROM hhy.content_review_records
      WHERE snapshot_version_id=NEW.snapshot_version_id
        AND decision='ASSIGN' AND id>escalation_id
      ORDER BY id DESC LIMIT 1;
      IF second_reviewer_id IS NULL OR NEW.admin_id IS DISTINCT FROM second_reviewer_id THEN
        RAISE EXCEPTION 'R12_CONTENT_SECOND_REVIEWER_REQUIRED content_id=% expected_admin=% actual_admin=%',
          NEW.content_id,second_reviewer_id,NEW.admin_id USING ERRCODE='23514';
      END IF;
    END IF;
  END IF;
  RETURN NEW;
END;
$$;

CREATE OR REPLACE FUNCTION hhy.assert_r12_review_escalation_commit()
RETURNS trigger LANGUAGE plpgsql AS $$
DECLARE
  current_status varchar(64);
  current_version bigint;
  audit_count integer;
  event_count integer;
BEGIN
  IF NEW.decision<>'ESCALATE' THEN RETURN NULL; END IF;
  SELECT status,version INTO current_status,current_version
  FROM hhy.content_posts WHERE id=NEW.content_id;
  IF current_status<>'REVIEWING' THEN
    RAISE EXCEPTION 'R12_CONTENT_REVIEW_ESCALATION_STATUS_DRIFT content_id=% status=%',
      NEW.content_id,current_status USING ERRCODE='23514';
  END IF;

  SELECT count(*) INTO audit_count
  FROM hhy.admin_operation_logs AS audit
  WHERE audit.resource='CONTENT_REVIEW'
    AND audit.resource_id=NEW.content_id
    AND audit.admin_id=NEW.admin_id
    AND audit.action='REVIEW_DECIDED'
    AND audit.after_json->>'commandId'=NEW.command_id
    AND audit.after_json->>'afterVersion'=current_version::text
    AND audit.after_json->>'result'='SUCCESS'
    AND audit.after_json->>'actorId'=NEW.admin_id::text
    AND audit.after_json->>'permission'='review.decide'
    AND audit.after_json->>'status'='REVIEWING'
    AND audit.after_json->>'reason'=NEW.reason
    AND audit.after_json ? 'actorUsername'
    AND audit.after_json ? 'evidenceIds'
    AND audit.after_json ? 'requestId'
    AND audit.after_json ? 'device';
  IF audit_count<>1 THEN
    RAISE EXCEPTION 'R12_CONTENT_REVIEW_ESCALATION_AUDIT_REQUIRED content_id=% command_id=% count=%',
      NEW.content_id,NEW.command_id,audit_count USING ERRCODE='23514';
  END IF;

  SELECT count(*) INTO event_count
  FROM hhy.outbox_events AS event
  WHERE event.aggregate_type='CONTENT'
    AND event.aggregate_id=NEW.content_id::text
    AND event.event_type='content.review.escalated.v1'
    AND event.payload->>'commandId'=NEW.command_id
    AND event.payload->>'contentId'=NEW.content_id::text
    AND event.payload->>'submittedSnapshotId'=NEW.snapshot_version_id::text
    AND event.payload->>'submittedSnapshotVersion'=NEW.version_no
    AND event.payload->>'beforeVersion'=(current_version-1)::text
    AND event.payload->>'afterVersion'=current_version::text
    AND event.payload->>'contentVersion'=current_version::text
    AND event.payload->>'status'='REVIEWING'
    AND event.payload->>'actorId'=NEW.admin_id::text
    AND event.payload->>'permission'='review.decide'
    AND event.payload->>'reason'=NEW.reason
    AND jsonb_typeof(event.payload->'evidenceIds')='array'
    AND event.payload ? 'actorUsername'
    AND event.payload ? 'requestId'
    AND event.payload ? 'device'
    AND event.payload ? 'occurredAt';
  IF event_count<>1 THEN
    RAISE EXCEPTION 'R12_CONTENT_REVIEW_ESCALATION_OUTBOX_REQUIRED content_id=% command_id=% count=%',
      NEW.content_id,NEW.command_id,event_count USING ERRCODE='23514';
  END IF;
  RETURN NULL;
END;
$$;

CREATE CONSTRAINT TRIGGER trg_r12_review_escalation_commit
AFTER INSERT ON hhy.content_review_records
DEFERRABLE INITIALLY DEFERRED
FOR EACH ROW EXECUTE FUNCTION hhy.assert_r12_review_escalation_commit();
