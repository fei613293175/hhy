SET search_path TO hhy, public;
BEGIN;

DO $$
DECLARE
  title_size integer;
  detail_sizes integer;
BEGIN
  SELECT character_maximum_length INTO title_size
  FROM information_schema.columns
  WHERE table_schema='hhy' AND table_name='content_posts' AND column_name='title';
  SELECT count(*) INTO detail_sizes
  FROM information_schema.columns
  WHERE table_schema='hhy' AND table_name='project_details'
    AND column_name IN ('cooperation','conditions','region','website')
    AND character_maximum_length=2000;
  IF title_size<>2000 OR detail_sizes<>4 THEN
    RAISE EXCEPTION 'R08_SCHEMA_CAPACITY_INVALID title=% details=%', title_size, detail_sizes;
  END IF;
END;
$$;

DO $$
DECLARE
  configured integer;
  invalid integer;
BEGIN
  SELECT count(*) INTO configured FROM hhy.system_configs
  WHERE scope='GLOBAL' AND key IN (
    'content.limit.normal.online','content.limit.month.online','content.limit.quarter.online',
    'content.limit.year.online','content.team_leader_per_account','content.limit.normal.pending',
    'content.limit.normal.drafts','content.limit.normal.daily_submissions',
    'chat.stranger.daily_conversation_limit','chat.message.per_minute_limit','chat.image.max_mb',
    'chat.history.retention_days','domain.h5.host');
  IF configured <> 13 THEN
    RAISE EXCEPTION 'R08_CONFIG_COUNT_INVALID expected=13 actual=%', configured;
  END IF;
  SELECT count(*) INTO invalid FROM hhy.system_configs
  WHERE scope='GLOBAL' AND key LIKE 'content.%' AND key IN (
    'content.limit.normal.online','content.limit.month.online','content.limit.quarter.online',
    'content.limit.year.online','content.team_leader_per_account','content.limit.normal.pending',
    'content.limit.normal.drafts','content.limit.normal.daily_submissions')
    AND (value_json #>> '{}')::integer < 0;
  IF invalid <> 0 THEN RAISE EXCEPTION 'R08_CONFIG_NEGATIVE_VALUE'; END IF;
  IF (SELECT value_json #>> '{}' FROM hhy.system_configs
      WHERE key='domain.h5.host' AND scope='GLOBAL') <> 'h5.orbexa.cc' THEN
    RAISE EXCEPTION 'R08_H5_HOST_INVALID';
  END IF;
END;
$$;

ROLLBACK;
