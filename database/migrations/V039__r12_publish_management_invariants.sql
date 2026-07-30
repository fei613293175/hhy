-- R12 unified publishing state machine, version history, review and Outbox invariants.
SET search_path TO hhy, public;

-- Lock the parent first and every dependent fact table in a stable order so an
-- upgrade observes one coherent legacy snapshot and cannot deadlock with R12 writes.
LOCK TABLE hhy.content_posts IN SHARE ROW EXCLUSIVE MODE;
LOCK TABLE hhy.project_details IN SHARE ROW EXCLUSIVE MODE;
LOCK TABLE hhy.app_details IN SHARE ROW EXCLUSIVE MODE;
LOCK TABLE hhy.group_details IN SHARE ROW EXCLUSIVE MODE;
LOCK TABLE hhy.team_leader_details IN SHARE ROW EXCLUSIVE MODE;
LOCK TABLE hhy.content_media IN SHARE ROW EXCLUSIVE MODE;
LOCK TABLE hhy.content_contacts IN SHARE ROW EXCLUSIVE MODE;
LOCK TABLE hhy.content_versions IN SHARE ROW EXCLUSIVE MODE;
LOCK TABLE hhy.content_status_logs IN SHARE ROW EXCLUSIVE MODE;
LOCK TABLE hhy.content_review_records IN SHARE ROW EXCLUSIVE MODE;
LOCK TABLE hhy.outbox_events IN SHARE ROW EXCLUSIVE MODE;
LOCK TABLE hhy.media_objects IN SHARE ROW EXCLUSIVE MODE;

-- Refuse dirty legacy data before the first schema mutation. Flyway wraps this
-- migration in one transaction, so every failure leaves the V038 schema intact.
DO $$
BEGIN
  IF EXISTS (
    SELECT 1
    FROM hhy.content_posts AS content
    CROSS JOIN LATERAL (
      SELECT
        (SELECT count(*) FROM hhy.project_details d WHERE d.content_id=content.id) AS projects,
        (SELECT count(*) FROM hhy.app_details d WHERE d.content_id=content.id) AS apps,
        (SELECT count(*) FROM hhy.group_details d WHERE d.content_id=content.id) AS groups,
        (SELECT count(*) FROM hhy.team_leader_details d WHERE d.content_id=content.id) AS leaders
    ) AS detail
    WHERE content.status <> 'DELETED'
      AND (
        (content.type='PROJECT' AND (detail.projects<>1 OR detail.apps+detail.groups+detail.leaders<>0)) OR
        (content.type='APP' AND (detail.apps<>1 OR detail.projects+detail.groups+detail.leaders<>0)) OR
        (content.type='GROUP' AND (detail.groups<>1 OR detail.projects+detail.apps+detail.leaders<>0)) OR
        (content.type='TEAM_LEADER' AND (detail.leaders<>1 OR detail.projects+detail.apps+detail.groups<>0))
      )
  ) THEN
    RAISE EXCEPTION 'R12_DIRTY_UPGRADE_CONTENT_DETAIL_CARDINALITY' USING ERRCODE='23514';
  END IF;

  IF EXISTS (
    WITH allowed(from_status,to_status) AS (VALUES
      ('DRAFT','PENDING_REVIEW'),
      ('REJECTED','PENDING_REVIEW'),
      ('RECTIFICATION','PENDING_REVIEW'),
      ('PENDING_REVIEW','REVIEWING'),
      ('REVIEWING','APPROVED'),
      ('REVIEWING','REJECTED'),
      ('APPROVED','ONLINE'),
      ('ONLINE','OFFLINE_BY_OWNER'),
      ('ONLINE','OFFLINE_BY_PLATFORM'),
      ('OFFLINE_BY_OWNER','ONLINE'),
      ('OFFLINE_BY_PLATFORM','RECTIFICATION')
    ), nonterminal(status) AS (VALUES
      ('DRAFT'),('PENDING_REVIEW'),('REVIEWING'),('REJECTED'),('APPROVED'),
      ('ONLINE'),('OFFLINE_BY_OWNER'),('OFFLINE_BY_PLATFORM'),('RECTIFICATION')
    ), expanded AS (
      SELECT * FROM allowed
      UNION ALL SELECT status,'BANNED' FROM nonterminal
      UNION ALL SELECT status,'DELETED' FROM nonterminal
    )
    SELECT 1
    FROM hhy.content_status_logs AS log
    WHERE NOT (
      (log.from_status IS NULL AND log.to_status='DRAFT') OR
      EXISTS (SELECT 1 FROM expanded edge
              WHERE edge.from_status=log.from_status AND edge.to_status=log.to_status)
    )
  ) THEN
    RAISE EXCEPTION 'R12_DIRTY_UPGRADE_ILLEGAL_STATUS_HISTORY_EDGE' USING ERRCODE='23514';
  END IF;

  IF EXISTS (
    SELECT 1 FROM hhy.content_contacts
    WHERE channel IS NULL OR btrim(channel)='' OR value_cipher IS NULL OR btrim(value_cipher)=''
       OR display_mask IS NULL OR btrim(display_mask)='' OR sort_order IS NULL OR sort_order<0
  ) THEN
    RAISE EXCEPTION 'R12_DIRTY_UPGRADE_INVALID_CONTENT_CONTACT' USING ERRCODE='23514';
  END IF;
  IF EXISTS (
    SELECT 1 FROM hhy.content_media
    WHERE media_type IS NULL OR btrim(media_type)='' OR sort_order IS NULL OR sort_order<0
  ) THEN
    RAISE EXCEPTION 'R12_DIRTY_UPGRADE_INVALID_CONTENT_MEDIA' USING ERRCODE='23514';
  END IF;
  IF EXISTS (
    SELECT 1 FROM hhy.content_contacts GROUP BY content_id,sort_order HAVING count(*)>1
  ) THEN
    RAISE EXCEPTION 'R12_DIRTY_UPGRADE_DUPLICATE_CONTACT_ORDER' USING ERRCODE='23505';
  END IF;
  IF EXISTS (
    SELECT 1 FROM hhy.content_contacts GROUP BY content_id,channel HAVING count(*)>1
  ) THEN
    RAISE EXCEPTION 'R12_DIRTY_UPGRADE_DUPLICATE_CONTACT_CHANNEL' USING ERRCODE='23505';
  END IF;
  IF EXISTS (
    SELECT 1 FROM hhy.content_media GROUP BY content_id,media_id HAVING count(*)>1
  ) THEN
    RAISE EXCEPTION 'R12_DIRTY_UPGRADE_DUPLICATE_CONTENT_MEDIA' USING ERRCODE='23505';
  END IF;
  IF EXISTS (
    SELECT 1
    FROM hhy.content_versions AS version
    JOIN hhy.content_posts AS content ON content.id=version.content_id
    WHERE version.version_no !~ '^(0|[1-9][0-9]*)$'
       OR version.version_no::numeric > content.version
  ) THEN
    RAISE EXCEPTION 'R12_DIRTY_UPGRADE_INVALID_CONTENT_VERSION_SEQUENCE' USING ERRCODE='23514';
  END IF;
  IF EXISTS (
    SELECT 1
    FROM hhy.content_review_records AS review
    WHERE review.version_no IS NOT NULL AND btrim(review.version_no)<>''
      AND NOT EXISTS (
        SELECT 1 FROM hhy.content_versions AS version
        WHERE version.content_id=review.content_id AND version.version_no=review.version_no
      )
  ) THEN
    RAISE EXCEPTION 'R12_DIRTY_UPGRADE_REVIEW_VERSION_UNBINDABLE' USING ERRCODE='23503';
  END IF;
END;
$$;

-- Convert earlier NOT VALID content checks into verified upgrade prerequisites.
ALTER TABLE hhy.content_posts VALIDATE CONSTRAINT ck_r06_content_type;
ALTER TABLE hhy.content_posts VALIDATE CONSTRAINT ck_r06_content_title;
ALTER TABLE hhy.content_posts VALIDATE CONSTRAINT ck_r06_content_counter_version;
ALTER TABLE hhy.content_media VALIDATE CONSTRAINT ck_r06_content_media;
ALTER TABLE hhy.content_contacts VALIDATE CONSTRAINT ck_r06_content_contact;
ALTER TABLE hhy.content_contacts VALIDATE CONSTRAINT ck_r07_contact_cipher_envelope;
ALTER TABLE hhy.content_versions VALIDATE CONSTRAINT ck_r06_content_version_snapshot;
ALTER TABLE hhy.content_versions VALIDATE CONSTRAINT ck_r08_content_version_number;
ALTER TABLE hhy.content_status_logs VALIDATE CONSTRAINT ck_r06_content_status_history;
ALTER TABLE hhy.content_review_records VALIDATE CONSTRAINT ck_r06_content_review_decision;
ALTER TABLE hhy.content_review_records VALIDATE CONSTRAINT ck_r06_content_review_reason;
ALTER TABLE hhy.project_details VALIDATE CONSTRAINT ck_r08_project_detail_values;
ALTER TABLE hhy.app_details VALIDATE CONSTRAINT ck_r09_app_detail_values;
ALTER TABLE hhy.group_details VALIDATE CONSTRAINT ck_r10_group_detail_values;
ALTER TABLE hhy.team_leader_details VALIDATE CONSTRAINT ck_r11_team_leader_values;

ALTER TABLE hhy.content_status_logs
  ADD COLUMN IF NOT EXISTS transition_version bigint;
ALTER TABLE hhy.content_review_records
  ADD COLUMN IF NOT EXISTS snapshot_version_id bigint,
  ADD COLUMN IF NOT EXISTS command_id varchar(128);
ALTER TABLE hhy.content_media
  ADD COLUMN IF NOT EXISTS removed_at timestamptz;
ALTER TABLE hhy.content_contacts
  ADD COLUMN IF NOT EXISTS removed_at timestamptz;

ALTER TABLE hhy.content_status_logs
  ADD CONSTRAINT ck_r12_content_status_transition_version
    CHECK (transition_version IS NULL OR transition_version>=0);
ALTER TABLE hhy.content_review_records
  ADD CONSTRAINT fk_r12_content_review_snapshot
    FOREIGN KEY (snapshot_version_id) REFERENCES hhy.content_versions(id) ON DELETE RESTRICT,
  ADD CONSTRAINT ck_r12_content_review_command
    CHECK (command_id IS NULL OR btrim(command_id)<>''),
  ADD CONSTRAINT ck_r12_content_review_action
    CHECK (snapshot_version_id IS NULL OR decision IN ('CLAIM','ASSIGN','APPROVE','REJECT'));

CREATE UNIQUE INDEX uq_r12_content_status_transition_version
  ON hhy.content_status_logs(content_id,transition_version)
  WHERE transition_version IS NOT NULL;
CREATE UNIQUE INDEX uq_r12_content_review_command
  ON hhy.content_review_records(content_id,command_id)
  WHERE command_id IS NOT NULL;
CREATE INDEX ix_r12_content_review_snapshot
  ON hhy.content_review_records(snapshot_version_id,created_at,id);

DROP INDEX hhy.uq_r06_content_contact_order;
CREATE UNIQUE INDEX uq_r12_content_contact_order
  ON hhy.content_contacts(content_id,sort_order);
CREATE UNIQUE INDEX uq_r12_content_contact_channel
  ON hhy.content_contacts(content_id,channel);
CREATE UNIQUE INDEX uq_r12_content_media_identity
  ON hhy.content_media(content_id,media_id);

-- BEGIN CONTENT_STATUS_SQL_PROJECTION
CREATE OR REPLACE FUNCTION hhy.r12_content_transition_allowed(
  from_status varchar, to_status varchar
) RETURNS boolean
LANGUAGE sql IMMUTABLE STRICT
AS $$
  SELECT (from_status,to_status) IN (VALUES
    ('DRAFT','PENDING_REVIEW'),
    ('REJECTED','PENDING_REVIEW'),
    ('RECTIFICATION','PENDING_REVIEW'),
    ('PENDING_REVIEW','REVIEWING'),
    ('REVIEWING','APPROVED'),
    ('REVIEWING','REJECTED'),
    ('APPROVED','ONLINE'),
    ('ONLINE','OFFLINE_BY_OWNER'),
    ('ONLINE','OFFLINE_BY_PLATFORM'),
    ('OFFLINE_BY_OWNER','ONLINE'),
    ('OFFLINE_BY_PLATFORM','RECTIFICATION'),
    ('DRAFT','BANNED'),
    ('PENDING_REVIEW','BANNED'),
    ('REVIEWING','BANNED'),
    ('REJECTED','BANNED'),
    ('APPROVED','BANNED'),
    ('ONLINE','BANNED'),
    ('OFFLINE_BY_OWNER','BANNED'),
    ('OFFLINE_BY_PLATFORM','BANNED'),
    ('RECTIFICATION','BANNED'),
    ('DRAFT','DELETED'),
    ('PENDING_REVIEW','DELETED'),
    ('REVIEWING','DELETED'),
    ('REJECTED','DELETED'),
    ('APPROVED','DELETED'),
    ('ONLINE','DELETED'),
    ('OFFLINE_BY_OWNER','DELETED'),
    ('OFFLINE_BY_PLATFORM','DELETED'),
    ('RECTIFICATION','DELETED')
  );
$$;
-- END CONTENT_STATUS_SQL_PROJECTION

CREATE OR REPLACE FUNCTION hhy.guard_r12_content_post()
RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
  IF TG_OP='INSERT' THEN
    IF NEW.status<>'DRAFT' OR NEW.version<>0 THEN
      RAISE EXCEPTION 'R12_CONTENT_MUST_START_DRAFT_VERSION_ZERO content_id=% status=% version=%',
        NEW.id,NEW.status,NEW.version USING ERRCODE='23514';
    END IF;
  ELSE
    IF NEW.version<>OLD.version+1 THEN
      RAISE EXCEPTION 'R12_CONTENT_VERSION_MUST_INCREMENT_ONCE content_id=% old=% new=%',
        OLD.id,OLD.version,NEW.version USING ERRCODE='40001';
    END IF;
    IF NEW.status IS DISTINCT FROM OLD.status
       AND NOT hhy.r12_content_transition_allowed(OLD.status,NEW.status) THEN
      RAISE EXCEPTION 'R12_CONTENT_STATUS_TRANSITION_FORBIDDEN content_id=% from=% to=%',
        OLD.id,OLD.status,NEW.status USING ERRCODE='23514';
    END IF;
  END IF;
  PERFORM set_config('hhy.content_version_'||NEW.id::text,NEW.version::text,true);
  RETURN NEW;
END;
$$;

CREATE TRIGGER trg_r12_content_post_guard
BEFORE INSERT OR UPDATE ON hhy.content_posts
FOR EACH ROW EXECUTE FUNCTION hhy.guard_r12_content_post();

CREATE OR REPLACE FUNCTION hhy.record_r12_content_write()
RETURNS trigger LANGUAGE plpgsql AS $$
DECLARE
  event_name varchar(128);
BEGIN
  IF TG_OP='INSERT' THEN
    INSERT INTO hhy.content_status_logs(
      content_id,from_status,to_status,reason,operator,transition_version
    ) VALUES (NEW.id,NULL,'DRAFT',NULL,'database:r12',0);
    event_name := 'content.created.v1';
  ELSIF NEW.status IS DISTINCT FROM OLD.status THEN
    event_name := 'content.status.changed.v1';
  ELSE
    event_name := 'content.updated.v1';
  END IF;
  INSERT INTO hhy.outbox_events(
    aggregate_id,aggregate_type,event_id,event_type,event_version,headers,payload
  ) VALUES (
    NEW.id::text,'CONTENT',gen_random_uuid()::text,event_name,1,
    '{"source":"r12-database"}'::jsonb,
    jsonb_build_object('resourceId',NEW.id::text,'status',NEW.status,'contentVersion',NEW.version)
  );
  RETURN NEW;
END;
$$;

CREATE TRIGGER trg_r12_content_write_record
AFTER INSERT OR UPDATE ON hhy.content_posts
FOR EACH ROW EXECUTE FUNCTION hhy.record_r12_content_write();

CREATE OR REPLACE FUNCTION hhy.guard_r12_content_status_log()
RETURNS trigger LANGUAGE plpgsql AS $$
DECLARE
  current_status varchar(64);
  current_version bigint;
BEGIN
  SELECT status,version INTO current_status,current_version
  FROM hhy.content_posts WHERE id=NEW.content_id FOR KEY SHARE;
  IF NOT FOUND THEN
    RAISE EXCEPTION 'R12_CONTENT_STATUS_LOG_PARENT_MISSING content_id=%',NEW.content_id
      USING ERRCODE='23503';
  END IF;
  IF NEW.transition_version IS NULL THEN
    NEW.transition_version := current_version;
  END IF;
  IF NEW.transition_version<>current_version OR NEW.to_status<>current_status THEN
    RAISE EXCEPTION 'R12_CONTENT_STATUS_LOG_VERSION_MISMATCH content_id=% expected=% actual=%',
      NEW.content_id,current_version,NEW.transition_version USING ERRCODE='23514';
  END IF;
  IF NOT (
    (NEW.from_status IS NULL AND NEW.to_status='DRAFT' AND NEW.transition_version=0) OR
    hhy.r12_content_transition_allowed(NEW.from_status,NEW.to_status)
  ) THEN
    RAISE EXCEPTION 'R12_CONTENT_STATUS_LOG_EDGE_FORBIDDEN content_id=% from=% to=%',
      NEW.content_id,NEW.from_status,NEW.to_status USING ERRCODE='23514';
  END IF;
  IF current_setting('hhy.content_version_'||NEW.content_id::text,true)
       IS DISTINCT FROM current_version::text THEN
    RAISE EXCEPTION 'R12_CONTENT_STATUS_LOG_REQUIRES_VERSIONED_WRITE content_id=%',NEW.content_id
      USING ERRCODE='23514';
  END IF;
  RETURN NEW;
END;
$$;

CREATE TRIGGER trg_r12_content_status_log_guard
BEFORE INSERT ON hhy.content_status_logs
FOR EACH ROW EXECUTE FUNCTION hhy.guard_r12_content_status_log();

CREATE OR REPLACE FUNCTION hhy.guard_r12_content_version_insert()
RETURNS trigger LANGUAGE plpgsql AS $$
DECLARE current_version bigint;
BEGIN
  SELECT version INTO current_version FROM hhy.content_posts
  WHERE id=NEW.content_id FOR KEY SHARE;
  IF current_version IS NULL OR NEW.version_no<>current_version::text THEN
    RAISE EXCEPTION 'R12_CONTENT_SNAPSHOT_VERSION_MISMATCH content_id=% expected=% actual=%',
      NEW.content_id,current_version,NEW.version_no USING ERRCODE='23514';
  END IF;
  IF current_setting('hhy.content_version_'||NEW.content_id::text,true)
       IS DISTINCT FROM current_version::text THEN
    RAISE EXCEPTION 'R12_CONTENT_SNAPSHOT_REQUIRES_VERSIONED_WRITE content_id=%',NEW.content_id
      USING ERRCODE='23514';
  END IF;
  RETURN NEW;
END;
$$;

CREATE TRIGGER trg_r12_content_version_insert_guard
BEFORE INSERT ON hhy.content_versions
FOR EACH ROW EXECUTE FUNCTION hhy.guard_r12_content_version_insert();

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

CREATE TRIGGER trg_r12_content_review_insert_guard
BEFORE INSERT ON hhy.content_review_records
FOR EACH ROW EXECUTE FUNCTION hhy.guard_r12_content_review_insert();

CREATE OR REPLACE FUNCTION hhy.enrich_r12_content_outbox()
RETURNS trigger LANGUAGE plpgsql AS $$
DECLARE content_version bigint;
BEGIN
  IF NEW.aggregate_type<>'CONTENT' THEN RETURN NEW; END IF;
  IF NEW.aggregate_id !~ '^[1-9][0-9]*$' THEN
    RAISE EXCEPTION 'R12_CONTENT_OUTBOX_AGGREGATE_ID_INVALID aggregate_id=%',NEW.aggregate_id
      USING ERRCODE='23514';
  END IF;
  SELECT version INTO content_version FROM hhy.content_posts
  WHERE id=NEW.aggregate_id::bigint FOR KEY SHARE;
  IF content_version IS NULL THEN
    RAISE EXCEPTION 'R12_CONTENT_OUTBOX_PARENT_MISSING aggregate_id=%',NEW.aggregate_id
      USING ERRCODE='23503';
  END IF;
  IF jsonb_typeof(NEW.payload)<>'object' THEN
    RAISE EXCEPTION 'R12_CONTENT_OUTBOX_PAYLOAD_MUST_BE_OBJECT event_id=%',NEW.event_id
      USING ERRCODE='23514';
  END IF;
  IF NEW.payload ? 'contentVersion'
     AND NEW.payload->>'contentVersion'<>content_version::text THEN
    RAISE EXCEPTION 'R12_CONTENT_OUTBOX_VERSION_MISMATCH event_id=% expected=% actual=%',
      NEW.event_id,content_version,NEW.payload->>'contentVersion' USING ERRCODE='23514';
  END IF;
  NEW.payload:=NEW.payload||jsonb_build_object('contentVersion',content_version);
  RETURN NEW;
END;
$$;

CREATE TRIGGER trg_00_r12_content_outbox_enrich
BEFORE INSERT ON hhy.outbox_events
FOR EACH ROW EXECUTE FUNCTION hhy.enrich_r12_content_outbox();

CREATE OR REPLACE FUNCTION hhy.guard_r12_content_child_write()
RETURNS trigger LANGUAGE plpgsql AS $$
DECLARE
  parent_id bigint;
  current_version bigint;
BEGIN
  parent_id:=NEW.content_id;
  SELECT version INTO current_version FROM hhy.content_posts
  WHERE id=parent_id FOR KEY SHARE;
  IF current_version IS NULL THEN
    RAISE EXCEPTION 'R12_CONTENT_CHILD_PARENT_MISSING table=% content_id=%',TG_TABLE_NAME,parent_id
      USING ERRCODE='23503';
  END IF;
  IF current_setting('hhy.content_version_'||parent_id::text,true)
       IS DISTINCT FROM current_version::text THEN
    RAISE EXCEPTION 'R12_CONTENT_CHILD_REQUIRES_VERSIONED_WRITE table=% content_id=%',
      TG_TABLE_NAME,parent_id USING ERRCODE='23514';
  END IF;
  RETURN NEW;
END;
$$;

CREATE TRIGGER trg_r12_project_detail_write BEFORE INSERT OR UPDATE ON hhy.project_details
FOR EACH ROW EXECUTE FUNCTION hhy.guard_r12_content_child_write();
CREATE TRIGGER trg_r12_app_detail_write BEFORE INSERT OR UPDATE ON hhy.app_details
FOR EACH ROW EXECUTE FUNCTION hhy.guard_r12_content_child_write();
CREATE TRIGGER trg_r12_group_detail_write BEFORE INSERT OR UPDATE ON hhy.group_details
FOR EACH ROW EXECUTE FUNCTION hhy.guard_r12_content_child_write();
CREATE TRIGGER trg_r12_team_leader_detail_write BEFORE INSERT OR UPDATE ON hhy.team_leader_details
FOR EACH ROW EXECUTE FUNCTION hhy.guard_r12_content_child_write();
CREATE TRIGGER trg_r12_content_media_write BEFORE INSERT OR UPDATE ON hhy.content_media
FOR EACH ROW EXECUTE FUNCTION hhy.guard_r12_content_child_write();
CREATE TRIGGER trg_r12_content_contact_write BEFORE INSERT OR UPDATE ON hhy.content_contacts
FOR EACH ROW EXECUTE FUNCTION hhy.guard_r12_content_child_write();

CREATE OR REPLACE FUNCTION hhy.prevent_r12_content_physical_delete()
RETURNS trigger LANGUAGE plpgsql AS $$
DECLARE parent_id bigint;
BEGIN
  parent_id:=CASE WHEN TG_TABLE_NAME='content_posts' THEN OLD.id ELSE OLD.content_id END;
  IF TG_TABLE_NAME<>'content_posts' THEN
    PERFORM 1 FROM hhy.content_posts WHERE id=parent_id FOR UPDATE;
  END IF;
  RAISE EXCEPTION 'R12_CONTENT_PHYSICAL_DELETE_FORBIDDEN table=% content_id=%',
    TG_TABLE_NAME,parent_id USING ERRCODE='55000';
END;
$$;

CREATE TRIGGER trg_00_r12_content_post_no_delete BEFORE DELETE ON hhy.content_posts
FOR EACH ROW EXECUTE FUNCTION hhy.prevent_r12_content_physical_delete();
CREATE TRIGGER trg_00_r12_project_detail_no_delete BEFORE DELETE ON hhy.project_details
FOR EACH ROW EXECUTE FUNCTION hhy.prevent_r12_content_physical_delete();
CREATE TRIGGER trg_00_r12_app_detail_no_delete BEFORE DELETE ON hhy.app_details
FOR EACH ROW EXECUTE FUNCTION hhy.prevent_r12_content_physical_delete();
CREATE TRIGGER trg_00_r12_group_detail_no_delete BEFORE DELETE ON hhy.group_details
FOR EACH ROW EXECUTE FUNCTION hhy.prevent_r12_content_physical_delete();
CREATE TRIGGER trg_00_r12_team_leader_detail_no_delete BEFORE DELETE ON hhy.team_leader_details
FOR EACH ROW EXECUTE FUNCTION hhy.prevent_r12_content_physical_delete();
CREATE TRIGGER trg_00_r12_content_media_no_delete BEFORE DELETE ON hhy.content_media
FOR EACH ROW EXECUTE FUNCTION hhy.prevent_r12_content_physical_delete();
CREATE TRIGGER trg_00_r12_content_contact_no_delete BEFORE DELETE ON hhy.content_contacts
FOR EACH ROW EXECUTE FUNCTION hhy.prevent_r12_content_physical_delete();

CREATE OR REPLACE FUNCTION hhy.assert_r12_content_commit()
RETURNS trigger LANGUAGE plpgsql AS $$
DECLARE
  detail_count integer;
  snapshot_id bigint;
  required_decision varchar(64);
BEGIN
  IF NEW.status<>'DELETED' THEN
    SELECT
      CASE NEW.type
        WHEN 'PROJECT' THEN (SELECT count(*) FROM hhy.project_details WHERE content_id=NEW.id)
        WHEN 'APP' THEN (SELECT count(*) FROM hhy.app_details WHERE content_id=NEW.id)
        WHEN 'GROUP' THEN (SELECT count(*) FROM hhy.group_details WHERE content_id=NEW.id)
        WHEN 'TEAM_LEADER' THEN (SELECT count(*) FROM hhy.team_leader_details WHERE content_id=NEW.id)
        ELSE 0
      END INTO detail_count;
    IF detail_count<>1 OR
       (SELECT count(*) FROM hhy.project_details WHERE content_id=NEW.id)+
       (SELECT count(*) FROM hhy.app_details WHERE content_id=NEW.id)+
       (SELECT count(*) FROM hhy.group_details WHERE content_id=NEW.id)+
       (SELECT count(*) FROM hhy.team_leader_details WHERE content_id=NEW.id)<>1 THEN
      RAISE EXCEPTION 'R12_CONTENT_DETAIL_CARDINALITY content_id=% type=%',NEW.id,NEW.type
        USING ERRCODE='23514';
    END IF;
  END IF;

  IF TG_OP='UPDATE' AND NEW.status IS DISTINCT FROM OLD.status THEN
    IF NOT EXISTS (
      SELECT 1 FROM hhy.content_status_logs
      WHERE content_id=NEW.id AND transition_version=NEW.version
        AND from_status=OLD.status AND to_status=NEW.status
    ) THEN
      RAISE EXCEPTION 'R12_CONTENT_STATUS_HISTORY_REQUIRED content_id=% version=%',NEW.id,NEW.version
        USING ERRCODE='23514';
    END IF;
    IF NEW.status='PENDING_REVIEW' AND NOT EXISTS (
      SELECT 1 FROM hhy.content_versions
      WHERE content_id=NEW.id AND version_no=NEW.version::text
    ) THEN
      RAISE EXCEPTION 'R12_CONTENT_SUBMISSION_SNAPSHOT_REQUIRED content_id=% version=%',NEW.id,NEW.version
        USING ERRCODE='23514';
    END IF;
    required_decision:=CASE
      WHEN OLD.status='PENDING_REVIEW' AND NEW.status='REVIEWING' THEN 'CLAIM'
      WHEN OLD.status='REVIEWING' AND NEW.status='APPROVED' THEN 'APPROVE'
      WHEN OLD.status='REVIEWING' AND NEW.status='REJECTED' THEN 'REJECT'
      ELSE NULL
    END;
    IF required_decision IS NOT NULL THEN
      SELECT version.id INTO snapshot_id
      FROM hhy.content_status_logs AS submitted
      JOIN hhy.content_versions AS version
        ON version.content_id=submitted.content_id
       AND version.version_no=submitted.transition_version::text
      WHERE submitted.content_id=NEW.id AND submitted.to_status='PENDING_REVIEW'
        AND submitted.transition_version<=OLD.version
      ORDER BY submitted.transition_version DESC LIMIT 1;
      IF snapshot_id IS NULL THEN
        SELECT id INTO snapshot_id FROM hhy.content_versions
        WHERE content_id=NEW.id AND version_no::bigint<=OLD.version
        ORDER BY version_no::bigint DESC LIMIT 1;
      END IF;
      IF snapshot_id IS NULL OR NOT EXISTS (
        SELECT 1 FROM hhy.content_review_records
        WHERE content_id=NEW.id AND snapshot_version_id=snapshot_id
          AND decision=required_decision
      ) THEN
        RAISE EXCEPTION 'R12_CONTENT_REVIEW_RECORD_REQUIRED content_id=% decision=%',
          NEW.id,required_decision USING ERRCODE='23514';
      END IF;
    END IF;
  END IF;

  IF NOT EXISTS (
    SELECT 1 FROM hhy.outbox_events
    WHERE aggregate_type='CONTENT' AND aggregate_id=NEW.id::text
      AND payload->>'contentVersion'=NEW.version::text
  ) THEN
    RAISE EXCEPTION 'R12_CONTENT_OUTBOX_REQUIRED content_id=% version=%',NEW.id,NEW.version
      USING ERRCODE='23514';
  END IF;
  RETURN NULL;
END;
$$;

CREATE CONSTRAINT TRIGGER trg_r12_content_commit
AFTER INSERT OR UPDATE ON hhy.content_posts
DEFERRABLE INITIALLY DEFERRED
FOR EACH ROW EXECUTE FUNCTION hhy.assert_r12_content_commit();
