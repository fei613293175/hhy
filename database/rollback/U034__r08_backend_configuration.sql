-- Development/test rollback. Refuse to erase an activated or changed value.
SET search_path TO hhy, public;

DO $$
BEGIN
  IF EXISTS (SELECT 1 FROM hhy.content_posts WHERE char_length(title)>255)
     OR EXISTS (SELECT 1 FROM hhy.project_details WHERE char_length(cooperation)>255
                 OR char_length(conditions)>255 OR char_length(region)>255 OR char_length(website)>255) THEN
    RAISE EXCEPTION 'R08_SCHEMA_ROLLBACK_LONG_VALUE' USING ERRCODE='55000';
  END IF;
END;
$$;

DO $$
DECLARE
  changed_key text;
BEGIN
  SELECT current.key INTO changed_key
  FROM hhy.system_configs current
  JOIN (VALUES
    ('content.limit.normal.online','3'::jsonb),
    ('content.limit.month.online','10'::jsonb),
    ('content.limit.quarter.online','25'::jsonb),
    ('content.limit.year.online','60'::jsonb),
    ('content.team_leader_per_account','1'::jsonb),
    ('content.limit.normal.pending','3'::jsonb),
    ('content.limit.normal.drafts','10'::jsonb),
    ('content.limit.normal.daily_submissions','10'::jsonb),
    ('chat.stranger.daily_conversation_limit','30'::jsonb),
    ('chat.message.per_minute_limit','60'::jsonb),
    ('chat.image.max_mb','10'::jsonb),
    ('chat.history.retention_days','1095'::jsonb),
    ('domain.h5.host','"h5.orbexa.cc"'::jsonb)
  ) expected(key,value_json)
    ON current.key=expected.key AND current.scope='GLOBAL'
  WHERE current.version<>0 OR current.value_json<>expected.value_json
  LIMIT 1;
  IF changed_key IS NOT NULL THEN
    RAISE EXCEPTION 'R08_CONFIG_ROLLBACK_CHANGED_VALUE key=%', changed_key USING ERRCODE='55000';
  END IF;
END;
$$;

DELETE FROM hhy.system_configs current
USING (VALUES
  ('content.limit.normal.online','3'::jsonb),
  ('content.limit.month.online','10'::jsonb),
  ('content.limit.quarter.online','25'::jsonb),
  ('content.limit.year.online','60'::jsonb),
  ('content.team_leader_per_account','1'::jsonb),
  ('content.limit.normal.pending','3'::jsonb),
  ('content.limit.normal.drafts','10'::jsonb),
  ('content.limit.normal.daily_submissions','10'::jsonb),
  ('chat.stranger.daily_conversation_limit','30'::jsonb),
  ('chat.message.per_minute_limit','60'::jsonb),
  ('chat.image.max_mb','10'::jsonb),
  ('chat.history.retention_days','1095'::jsonb),
  ('domain.h5.host','"h5.orbexa.cc"'::jsonb)
) expected(key,value_json)
WHERE current.key=expected.key AND current.scope='GLOBAL'
  AND current.version=0 AND current.value_json=expected.value_json;

ALTER TABLE hhy.project_details
  ALTER COLUMN cooperation TYPE varchar(255),
  ALTER COLUMN conditions TYPE varchar(255),
  ALTER COLUMN region TYPE varchar(255),
  ALTER COLUMN website TYPE varchar(255);
ALTER TABLE hhy.content_posts
  ALTER COLUMN title TYPE varchar(255);
