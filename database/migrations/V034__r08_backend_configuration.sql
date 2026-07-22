-- R08 active content/chat limits and H5 share host. Existing active values win.
SET search_path TO hhy, public;

ALTER TABLE hhy.content_posts
  ALTER COLUMN title TYPE varchar(2000);
ALTER TABLE hhy.project_details
  ALTER COLUMN cooperation TYPE varchar(2000),
  ALTER COLUMN conditions TYPE varchar(2000),
  ALTER COLUMN region TYPE varchar(2000),
  ALTER COLUMN website TYPE varchar(2000);

INSERT INTO hhy.system_configs(key,value_json,scope)
VALUES
  ('content.limit.normal.online','3'::jsonb,'GLOBAL'),
  ('content.limit.month.online','10'::jsonb,'GLOBAL'),
  ('content.limit.quarter.online','25'::jsonb,'GLOBAL'),
  ('content.limit.year.online','60'::jsonb,'GLOBAL'),
  ('content.team_leader_per_account','1'::jsonb,'GLOBAL'),
  ('content.limit.normal.pending','3'::jsonb,'GLOBAL'),
  ('content.limit.normal.drafts','10'::jsonb,'GLOBAL'),
  ('content.limit.normal.daily_submissions','10'::jsonb,'GLOBAL'),
  ('chat.stranger.daily_conversation_limit','30'::jsonb,'GLOBAL'),
  ('chat.message.per_minute_limit','60'::jsonb,'GLOBAL'),
  ('chat.image.max_mb','10'::jsonb,'GLOBAL'),
  ('chat.history.retention_days','1095'::jsonb,'GLOBAL'),
  ('domain.h5.host','"h5.orbexa.cc"'::jsonb,'GLOBAL')
ON CONFLICT (key,scope) DO NOTHING;
