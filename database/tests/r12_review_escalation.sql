SET search_path TO hhy, public;

CREATE TEMP TABLE r12_review_escalation_ids (
  owner_id bigint NOT NULL,
  first_reviewer_id bigint NOT NULL,
  second_reviewer_id bigint NOT NULL,
  wrong_reviewer_id bigint NOT NULL,
  content_id bigint NOT NULL,
  draft_snapshot_id bigint NOT NULL,
  submitted_snapshot_id bigint NOT NULL
) ON COMMIT PRESERVE ROWS;

CREATE OR REPLACE FUNCTION pg_temp.r12_escalation_payload(
  p_content_id bigint,
  p_snapshot_id bigint,
  p_actor_id bigint,
  p_command_id text,
  p_after_version bigint,
  p_result text DEFAULT 'SUCCESS'
) RETURNS jsonb
LANGUAGE sql
AS $$
  SELECT jsonb_build_object(
    'actorId', p_actor_id,
    'actorUsername', 'r12-first-reviewer',
    'permission', 'review.decide',
    'contentId', p_content_id::text,
    'reviewId', p_content_id::text,
    'submittedSnapshotId', p_snapshot_id,
    'submittedSnapshotVersion', '1',
    'beforeStatus', 'REVIEWING',
    'beforeVersion', p_after_version - 1,
    'afterVersion', p_after_version,
    'contentVersion', p_after_version,
    'status', 'REVIEWING',
    'reason', '需要二审确认',
    'evidenceIds', jsonb_build_array('evidence-a','evidence-b'),
    'requestId', 'r12-review-escalation-test',
    'ip', '127.0.0.1',
    'device', 'r12-device-fingerprint',
    'result', p_result,
    'commandId', p_command_id,
    'occurredAt', '2026-07-25T00:00:00Z'
  );
$$;

-- Build one submitted immutable snapshot and enter first review in committed
-- transactions. Later negative cases therefore cannot reuse a stale GUC from
-- fixture creation to impersonate the required parent version write.
BEGIN;
DO $$
DECLARE
  owner_id bigint;
  first_reviewer_id bigint;
  second_reviewer_id bigint;
  wrong_reviewer_id bigint;
  content_id bigint;
  draft_snapshot_id bigint;
BEGIN
  INSERT INTO hhy.users(phone,status,invite_code)
  VALUES ('13900004012','ACTIVE','R12ESCALATE') RETURNING id INTO owner_id;
  INSERT INTO hhy.admin_users(username,password_hash,status)
  VALUES ('r12-first-reviewer',repeat('a',60),'ACTIVE') RETURNING id INTO first_reviewer_id;
  INSERT INTO hhy.admin_users(username,password_hash,status)
  VALUES ('r12-second-reviewer',repeat('b',60),'ACTIVE') RETURNING id INTO second_reviewer_id;
  INSERT INTO hhy.admin_users(username,password_hash,status)
  VALUES ('r12-wrong-reviewer',repeat('c',60),'ACTIVE') RETURNING id INTO wrong_reviewer_id;

  INSERT INTO hhy.content_posts(owner_id,type,title,summary,status,version)
  VALUES (owner_id,'PROJECT','R12 review escalation','immutable review fixture','DRAFT',0)
  RETURNING id INTO content_id;
  INSERT INTO hhy.project_details(content_id,cooperation,conditions,region)
  VALUES (content_id,'联合运营','资料真实','CN-44');
  INSERT INTO hhy.content_versions(content_id,version_no,snapshot_json,created_by)
  VALUES (content_id,'0','{"title":"R12 review escalation"}'::jsonb,'user:r12')
  RETURNING id INTO draft_snapshot_id;

  INSERT INTO r12_review_escalation_ids(
    owner_id,first_reviewer_id,second_reviewer_id,wrong_reviewer_id,
    content_id,draft_snapshot_id,submitted_snapshot_id
  ) VALUES (
    owner_id,first_reviewer_id,second_reviewer_id,wrong_reviewer_id,
    content_id,draft_snapshot_id,0
  );
END;
$$;
COMMIT;

BEGIN;
DO $$
DECLARE
  ids r12_review_escalation_ids%ROWTYPE;
  snapshot_id bigint;
BEGIN
  SELECT * INTO ids FROM r12_review_escalation_ids;
  UPDATE hhy.content_posts SET status='PENDING_REVIEW',version=version+1
  WHERE id=ids.content_id;
  INSERT INTO hhy.content_versions(content_id,version_no,snapshot_json,created_by)
  VALUES (ids.content_id,'1','{"title":"R12 review escalation","submitted":true}'::jsonb,'user:r12')
  RETURNING id INTO snapshot_id;
  INSERT INTO hhy.content_status_logs(
    content_id,from_status,to_status,operator,transition_version
  ) VALUES (ids.content_id,'DRAFT','PENDING_REVIEW','user:r12',1);
  UPDATE r12_review_escalation_ids SET submitted_snapshot_id=snapshot_id;
END;
$$;
COMMIT;

BEGIN;
DO $$
DECLARE ids r12_review_escalation_ids%ROWTYPE;
BEGIN
  SELECT * INTO ids FROM r12_review_escalation_ids;
  UPDATE hhy.content_posts SET status='REVIEWING',version=version+1 WHERE id=ids.content_id;
  INSERT INTO hhy.content_review_records(
    content_id,version_no,decision,admin_id,snapshot_version_id,command_id
  ) VALUES (ids.content_id,'1','ASSIGN',ids.first_reviewer_id,ids.submitted_snapshot_id,'r12-initial-assign');
  INSERT INTO hhy.content_review_records(
    content_id,version_no,decision,admin_id,snapshot_version_id,command_id
  ) VALUES (ids.content_id,'1','CLAIM',ids.first_reviewer_id,ids.submitted_snapshot_id,'r12-initial-claim');
  INSERT INTO hhy.content_status_logs(
    content_id,from_status,to_status,operator,transition_version
  ) VALUES (ids.content_id,'PENDING_REVIEW','REVIEWING','admin:r12',2);
END;
$$;
COMMIT;

-- Reject malformed or unbound escalation facts before they can reach commit.
DO $$
DECLARE ids r12_review_escalation_ids%ROWTYPE;
BEGIN
  SELECT * INTO ids FROM r12_review_escalation_ids;
  BEGIN
    UPDATE hhy.content_posts SET version=version+1 WHERE id=ids.content_id;
    INSERT INTO hhy.content_review_records(
      content_id,version_no,decision,reason,admin_id,snapshot_version_id,command_id
    ) VALUES (ids.content_id,'1','ESCALATE','',ids.first_reviewer_id,
              ids.submitted_snapshot_id,'r12-empty-reason');
    RAISE EXCEPTION 'R12_EMPTY_ESCALATION_REASON_ACCEPTED';
  EXCEPTION WHEN check_violation THEN
    IF SQLERRM NOT LIKE 'R12_CONTENT_REVIEW_ACTION_INVALID%' THEN RAISE; END IF;
  END;

  BEGIN
    UPDATE hhy.content_posts SET version=version+1 WHERE id=ids.content_id;
    INSERT INTO hhy.content_review_records(
      content_id,version_no,decision,reason,admin_id,snapshot_version_id,command_id
    ) VALUES (ids.content_id,'0','ESCALATE','需要二审确认',ids.first_reviewer_id,
              ids.draft_snapshot_id,'r12-wrong-snapshot');
    RAISE EXCEPTION 'R12_OLD_ESCALATION_SNAPSHOT_ACCEPTED';
  EXCEPTION WHEN check_violation THEN
    IF SQLERRM NOT LIKE 'R12_CONTENT_REVIEW_ESCALATE_SNAPSHOT_NOT_LATEST%' THEN RAISE; END IF;
  END;

  BEGIN
    INSERT INTO hhy.content_review_records(
      content_id,version_no,decision,reason,admin_id,snapshot_version_id,command_id
    ) VALUES (ids.content_id,'1','ESCALATE','需要二审确认',ids.first_reviewer_id,
              ids.submitted_snapshot_id,'r12-no-parent-write');
    RAISE EXCEPTION 'R12_ESCALATION_WITHOUT_PARENT_WRITE_ACCEPTED';
  EXCEPTION WHEN check_violation THEN
    IF SQLERRM NOT LIKE 'R12_CONTENT_REVIEW_REQUIRES_VERSIONED_WRITE%' THEN RAISE; END IF;
  END;
END;
$$;

-- Every committed escalation needs one exact audit fact and one exact
-- application Outbox event. Each expected failure rolls its subtransaction
-- back, leaving version 2 available for the next case.
DO $$
DECLARE ids r12_review_escalation_ids%ROWTYPE;
DECLARE payload jsonb;
BEGIN
  SELECT * INTO ids FROM r12_review_escalation_ids;

  BEGIN
    UPDATE hhy.content_posts SET version=version+1 WHERE id=ids.content_id;
    INSERT INTO hhy.content_review_records(
      content_id,version_no,decision,reason,admin_id,snapshot_version_id,command_id
    ) VALUES (ids.content_id,'1','ESCALATE','需要二审确认',ids.first_reviewer_id,
              ids.submitted_snapshot_id,'r12-missing-audit');
    payload:=pg_temp.r12_escalation_payload(
      ids.content_id,ids.submitted_snapshot_id,ids.first_reviewer_id,'r12-missing-audit',3);
    INSERT INTO hhy.outbox_events(
      aggregate_id,aggregate_type,event_id,event_type,event_version,headers,payload
    ) VALUES (ids.content_id::text,'CONTENT',gen_random_uuid()::text,
              'content.review.escalated.v1',1,'{}'::jsonb,payload);
    SET CONSTRAINTS ALL IMMEDIATE;
    RAISE EXCEPTION 'R12_ESCALATION_WITHOUT_AUDIT_ACCEPTED';
  EXCEPTION WHEN check_violation THEN
    IF SQLERRM NOT LIKE 'R12_CONTENT_REVIEW_ESCALATION_AUDIT_REQUIRED%' THEN RAISE; END IF;
  END;
  SET CONSTRAINTS ALL DEFERRED;

  BEGIN
    UPDATE hhy.content_posts SET version=version+1 WHERE id=ids.content_id;
    INSERT INTO hhy.content_review_records(
      content_id,version_no,decision,reason,admin_id,snapshot_version_id,command_id
    ) VALUES (ids.content_id,'1','ESCALATE','需要二审确认',ids.first_reviewer_id,
              ids.submitted_snapshot_id,'r12-audit-command');
    payload:=pg_temp.r12_escalation_payload(
      ids.content_id,ids.submitted_snapshot_id,ids.first_reviewer_id,'r12-wrong-command',3);
    INSERT INTO hhy.admin_operation_logs(
      admin_id,action,resource,resource_id,before_json,after_json,ip
    ) VALUES (ids.first_reviewer_id,'REVIEW_DECIDED','CONTENT_REVIEW',ids.content_id,
              '{}'::jsonb,payload,'127.0.0.1');
    payload:=pg_temp.r12_escalation_payload(
      ids.content_id,ids.submitted_snapshot_id,ids.first_reviewer_id,'r12-audit-command',3);
    INSERT INTO hhy.outbox_events(
      aggregate_id,aggregate_type,event_id,event_type,event_version,headers,payload
    ) VALUES (ids.content_id::text,'CONTENT',gen_random_uuid()::text,
              'content.review.escalated.v1',1,'{}'::jsonb,payload);
    SET CONSTRAINTS ALL IMMEDIATE;
    RAISE EXCEPTION 'R12_ESCALATION_WITH_MISMATCHED_AUDIT_COMMAND_ACCEPTED';
  EXCEPTION WHEN check_violation THEN
    IF SQLERRM NOT LIKE 'R12_CONTENT_REVIEW_ESCALATION_AUDIT_REQUIRED%' THEN RAISE; END IF;
  END;
  SET CONSTRAINTS ALL DEFERRED;

  BEGIN
    UPDATE hhy.content_posts SET version=version+1 WHERE id=ids.content_id;
    INSERT INTO hhy.content_review_records(
      content_id,version_no,decision,reason,admin_id,snapshot_version_id,command_id
    ) VALUES (ids.content_id,'1','ESCALATE','需要二审确认',ids.first_reviewer_id,
              ids.submitted_snapshot_id,'r12-audit-version');
    payload:=pg_temp.r12_escalation_payload(
      ids.content_id,ids.submitted_snapshot_id,ids.first_reviewer_id,'r12-audit-version',4);
    INSERT INTO hhy.admin_operation_logs(
      admin_id,action,resource,resource_id,before_json,after_json,ip
    ) VALUES (ids.first_reviewer_id,'REVIEW_DECIDED','CONTENT_REVIEW',ids.content_id,
              '{}'::jsonb,payload,'127.0.0.1');
    payload:=pg_temp.r12_escalation_payload(
      ids.content_id,ids.submitted_snapshot_id,ids.first_reviewer_id,'r12-audit-version',3);
    INSERT INTO hhy.outbox_events(
      aggregate_id,aggregate_type,event_id,event_type,event_version,headers,payload
    ) VALUES (ids.content_id::text,'CONTENT',gen_random_uuid()::text,
              'content.review.escalated.v1',1,'{}'::jsonb,payload);
    SET CONSTRAINTS ALL IMMEDIATE;
    RAISE EXCEPTION 'R12_ESCALATION_WITH_MISMATCHED_AUDIT_VERSION_ACCEPTED';
  EXCEPTION WHEN check_violation THEN
    IF SQLERRM NOT LIKE 'R12_CONTENT_REVIEW_ESCALATION_AUDIT_REQUIRED%' THEN RAISE; END IF;
  END;
  SET CONSTRAINTS ALL DEFERRED;

  BEGIN
    UPDATE hhy.content_posts SET version=version+1 WHERE id=ids.content_id;
    INSERT INTO hhy.content_review_records(
      content_id,version_no,decision,reason,admin_id,snapshot_version_id,command_id
    ) VALUES (ids.content_id,'1','ESCALATE','需要二审确认',ids.first_reviewer_id,
              ids.submitted_snapshot_id,'r12-audit-result');
    payload:=pg_temp.r12_escalation_payload(
      ids.content_id,ids.submitted_snapshot_id,ids.first_reviewer_id,'r12-audit-result',3,'FAILED');
    INSERT INTO hhy.admin_operation_logs(
      admin_id,action,resource,resource_id,before_json,after_json,ip
    ) VALUES (ids.first_reviewer_id,'REVIEW_DECIDED','CONTENT_REVIEW',ids.content_id,
              '{}'::jsonb,payload,'127.0.0.1');
    payload:=pg_temp.r12_escalation_payload(
      ids.content_id,ids.submitted_snapshot_id,ids.first_reviewer_id,'r12-audit-result',3);
    INSERT INTO hhy.outbox_events(
      aggregate_id,aggregate_type,event_id,event_type,event_version,headers,payload
    ) VALUES (ids.content_id::text,'CONTENT',gen_random_uuid()::text,
              'content.review.escalated.v1',1,'{}'::jsonb,payload);
    SET CONSTRAINTS ALL IMMEDIATE;
    RAISE EXCEPTION 'R12_ESCALATION_WITH_FAILED_AUDIT_ACCEPTED';
  EXCEPTION WHEN check_violation THEN
    IF SQLERRM NOT LIKE 'R12_CONTENT_REVIEW_ESCALATION_AUDIT_REQUIRED%' THEN RAISE; END IF;
  END;
  SET CONSTRAINTS ALL DEFERRED;

  BEGIN
    UPDATE hhy.content_posts SET version=version+1 WHERE id=ids.content_id;
    INSERT INTO hhy.content_review_records(
      content_id,version_no,decision,reason,admin_id,snapshot_version_id,command_id
    ) VALUES (ids.content_id,'1','ESCALATE','需要二审确认',ids.first_reviewer_id,
              ids.submitted_snapshot_id,'r12-missing-outbox');
    payload:=pg_temp.r12_escalation_payload(
      ids.content_id,ids.submitted_snapshot_id,ids.first_reviewer_id,'r12-missing-outbox',3);
    INSERT INTO hhy.admin_operation_logs(
      admin_id,action,resource,resource_id,before_json,after_json,ip
    ) VALUES (ids.first_reviewer_id,'REVIEW_DECIDED','CONTENT_REVIEW',ids.content_id,
              '{}'::jsonb,payload,'127.0.0.1');
    SET CONSTRAINTS ALL IMMEDIATE;
    RAISE EXCEPTION 'R12_ESCALATION_WITHOUT_APPLICATION_OUTBOX_ACCEPTED';
  EXCEPTION WHEN check_violation THEN
    IF SQLERRM NOT LIKE 'R12_CONTENT_REVIEW_ESCALATION_OUTBOX_REQUIRED%' THEN RAISE; END IF;
  END;
  SET CONSTRAINTS ALL DEFERRED;

  BEGIN
    UPDATE hhy.content_posts SET version=version+1 WHERE id=ids.content_id;
    INSERT INTO hhy.content_review_records(
      content_id,version_no,decision,reason,admin_id,snapshot_version_id,command_id
    ) VALUES (ids.content_id,'1','ESCALATE','需要二审确认',ids.first_reviewer_id,
              ids.submitted_snapshot_id,'r12-outbox-version');
    payload:=pg_temp.r12_escalation_payload(
      ids.content_id,ids.submitted_snapshot_id,ids.first_reviewer_id,'r12-outbox-version',3);
    INSERT INTO hhy.admin_operation_logs(
      admin_id,action,resource,resource_id,before_json,after_json,ip
    ) VALUES (ids.first_reviewer_id,'REVIEW_DECIDED','CONTENT_REVIEW',ids.content_id,
              '{}'::jsonb,payload,'127.0.0.1');
    payload:=jsonb_set(payload,'{contentVersion}','999'::jsonb);
    INSERT INTO hhy.outbox_events(
      aggregate_id,aggregate_type,event_id,event_type,event_version,headers,payload
    ) VALUES (ids.content_id::text,'CONTENT',gen_random_uuid()::text,
              'content.review.escalated.v1',1,'{}'::jsonb,payload);
    RAISE EXCEPTION 'R12_ESCALATION_WITH_MISMATCHED_OUTBOX_VERSION_ACCEPTED';
  EXCEPTION WHEN check_violation THEN
    IF SQLERRM NOT LIKE 'R12_CONTENT_OUTBOX_VERSION_MISMATCH%' THEN RAISE; END IF;
  END;
END;
$$;

-- Commit the valid escalation with one exact audit and application Outbox.
BEGIN;
DO $$
DECLARE ids r12_review_escalation_ids%ROWTYPE;
DECLARE payload jsonb;
BEGIN
  SELECT * INTO ids FROM r12_review_escalation_ids;
  UPDATE hhy.content_posts SET version=version+1 WHERE id=ids.content_id;
  INSERT INTO hhy.content_review_records(
    content_id,version_no,decision,reason,admin_id,snapshot_version_id,command_id
  ) VALUES (ids.content_id,'1','ESCALATE','需要二审确认',ids.first_reviewer_id,
            ids.submitted_snapshot_id,'r12-escalate-success');
  payload:=pg_temp.r12_escalation_payload(
    ids.content_id,ids.submitted_snapshot_id,ids.first_reviewer_id,'r12-escalate-success',3);
  INSERT INTO hhy.admin_operation_logs(
    admin_id,action,resource,resource_id,before_json,after_json,ip
  ) VALUES (ids.first_reviewer_id,'REVIEW_DECIDED','CONTENT_REVIEW',ids.content_id,
            '{}'::jsonb,payload,'127.0.0.1');
  INSERT INTO hhy.outbox_events(
    aggregate_id,aggregate_type,event_id,event_type,event_version,headers,payload
  ) VALUES (ids.content_id::text,'CONTENT',gen_random_uuid()::text,
            'content.review.escalated.v1',1,'{}'::jsonb,payload);
END;
$$;
COMMIT;

DO $$
DECLARE ids r12_review_escalation_ids%ROWTYPE;
BEGIN
  SELECT * INTO ids FROM r12_review_escalation_ids;
  BEGIN
    UPDATE hhy.content_posts SET version=version+1 WHERE id=ids.content_id;
    INSERT INTO hhy.content_review_records(
      content_id,version_no,decision,reason,admin_id,snapshot_version_id,command_id
    ) VALUES (ids.content_id,'1','ESCALATE','再次二审',ids.first_reviewer_id,
              ids.submitted_snapshot_id,'r12-duplicate-escalate');
    RAISE EXCEPTION 'R12_DUPLICATE_SNAPSHOT_ESCALATION_ACCEPTED';
  EXCEPTION WHEN unique_violation THEN NULL;
  END;

  BEGIN
    UPDATE hhy.content_posts SET status='APPROVED',version=version+1 WHERE id=ids.content_id;
    INSERT INTO hhy.content_review_records(
      content_id,version_no,decision,admin_id,snapshot_version_id,command_id
    ) VALUES (ids.content_id,'1','APPROVE',ids.first_reviewer_id,
              ids.submitted_snapshot_id,'r12-direct-final');
    RAISE EXCEPTION 'R12_FINAL_DECISION_WITHOUT_SECOND_ASSIGNMENT_ACCEPTED';
  EXCEPTION WHEN check_violation THEN
    IF SQLERRM NOT LIKE 'R12_CONTENT_SECOND_REVIEWER_REQUIRED%' THEN RAISE; END IF;
  END;
END;
$$;

BEGIN;
DO $$
DECLARE ids r12_review_escalation_ids%ROWTYPE;
BEGIN
  SELECT * INTO ids FROM r12_review_escalation_ids;
  UPDATE hhy.content_posts SET version=version+1 WHERE id=ids.content_id;
  INSERT INTO hhy.content_review_records(
    content_id,version_no,decision,reason,admin_id,snapshot_version_id,command_id
  ) VALUES (ids.content_id,'1','ASSIGN','分配二审员',ids.second_reviewer_id,
            ids.submitted_snapshot_id,'r12-second-assign');
END;
$$;
COMMIT;

DO $$
DECLARE ids r12_review_escalation_ids%ROWTYPE;
BEGIN
  SELECT * INTO ids FROM r12_review_escalation_ids;
  BEGIN
    UPDATE hhy.content_posts SET status='APPROVED',version=version+1 WHERE id=ids.content_id;
    INSERT INTO hhy.content_review_records(
      content_id,version_no,decision,admin_id,snapshot_version_id,command_id
    ) VALUES (ids.content_id,'1','APPROVE',ids.wrong_reviewer_id,
              ids.submitted_snapshot_id,'r12-wrong-final-reviewer');
    RAISE EXCEPTION 'R12_WRONG_SECOND_REVIEWER_ACCEPTED';
  EXCEPTION WHEN check_violation THEN
    IF SQLERRM NOT LIKE 'R12_CONTENT_SECOND_REVIEWER_REQUIRED%' THEN RAISE; END IF;
  END;
END;
$$;

BEGIN;
DO $$
DECLARE ids r12_review_escalation_ids%ROWTYPE;
BEGIN
  SELECT * INTO ids FROM r12_review_escalation_ids;
  UPDATE hhy.content_posts SET status='APPROVED',version=version+1 WHERE id=ids.content_id;
  INSERT INTO hhy.content_review_records(
    content_id,version_no,decision,admin_id,snapshot_version_id,command_id
  ) VALUES (ids.content_id,'1','APPROVE',ids.second_reviewer_id,
            ids.submitted_snapshot_id,'r12-second-final');
  INSERT INTO hhy.content_status_logs(
    content_id,from_status,to_status,operator,transition_version
  ) VALUES (ids.content_id,'REVIEWING','APPROVED','admin:r12-second-reviewer',5);
END;
$$;
COMMIT;

DO $$
DECLARE ids r12_review_escalation_ids%ROWTYPE;
BEGIN
  SELECT * INTO ids FROM r12_review_escalation_ids;
  IF (SELECT status FROM hhy.content_posts WHERE id=ids.content_id)<>'APPROVED'
     OR (SELECT version FROM hhy.content_posts WHERE id=ids.content_id)<>5 THEN
    RAISE EXCEPTION 'R12_SECOND_REVIEW_FINAL_STATE_INVALID';
  END IF;
  IF (SELECT count(*) FROM hhy.content_review_records
      WHERE snapshot_version_id=ids.submitted_snapshot_id AND decision='ESCALATE')<>1 THEN
    RAISE EXCEPTION 'R12_ESCALATION_FACT_COUNT_INVALID';
  END IF;
  IF NOT EXISTS (
    SELECT 1 FROM hhy.content_review_records
    WHERE snapshot_version_id=ids.submitted_snapshot_id
      AND decision='APPROVE' AND admin_id=ids.second_reviewer_id
  ) THEN
    RAISE EXCEPTION 'R12_SECOND_REVIEW_FINAL_DECISION_MISSING';
  END IF;
  IF (SELECT count(*) FROM hhy.content_status_logs
      WHERE content_id=ids.content_id AND from_status='REVIEWING' AND to_status='REVIEWING')<>0 THEN
    RAISE EXCEPTION 'R12_FALSE_REVIEWING_STATUS_HISTORY_PRESENT';
  END IF;
END;
$$;

SELECT 'R12_REVIEW_ESCALATION_INVARIANTS PASS';
