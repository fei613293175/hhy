#!/usr/bin/env bash
set -euo pipefail

: "${HHY_HOME_CMS_ENVIRONMENT:?HHY_HOME_CMS_ENVIRONMENT is required}"
: "${HHY_HOME_CMS_POSTGRES_CONTAINER:?HHY_HOME_CMS_POSTGRES_CONTAINER is required}"

if [[ "$HHY_HOME_CMS_ENVIRONMENT" != "STAGING" ]]; then
  echo HOME_CMS_FIXTURE_REJECTED_NON_STAGING >&2
  exit 1
fi

postgres="$HHY_HOME_CMS_POSTGRES_CONTAINER"
banner_url=${HHY_HOME_CMS_BANNER_URL:-https://download.orbexa.cc/r09-candidate-media/attempt-2/01-app-list.png}

docker inspect "$postgres" >/dev/null
pg_user=$(docker exec "$postgres" printenv POSTGRES_USER)
pg_database=$(docker exec "$postgres" printenv POSTGRES_DB)
test -n "$pg_user" && test -n "$pg_database"
curl -fsSI --max-time 20 "$banner_url" >/dev/null

docker exec -i "$postgres" psql -U "$pg_user" -d "$pg_database" \
  -v ON_ERROR_STOP=1 -v banner_url="$banner_url" <<'SQL'
BEGIN;

DO $$
BEGIN
  IF NOT EXISTS (SELECT 1 FROM hhy.content_posts WHERE type='PROJECT' AND status='ONLINE') THEN
    RAISE EXCEPTION 'HOME_CMS_STAGING_REQUIRES_ONLINE_PROJECT';
  END IF;
  IF NOT EXISTS (SELECT 1 FROM hhy.content_posts WHERE type='APP' AND status='ONLINE') THEN
    RAISE EXCEPTION 'HOME_CMS_STAGING_REQUIRES_ONLINE_APP';
  END IF;
END;
$$;

INSERT INTO hhy.home_modules(code,title,source_type,config_json,display_order,enabled)
VALUES (
  'home_notice','平台公告','NOTICE',
  jsonb_build_object(
    'layoutType','notice',
    'items',jsonb_build_array(jsonb_build_object(
      'id','home-notice-current','itemType','NOTICE','title','合伙云 Pro 持续完善真实合作内容与服务体验',
      'target',jsonb_build_object('targetType','NONE','requiresLogin',false)))),
  10,true)
ON CONFLICT (code) DO UPDATE SET
  title=EXCLUDED.title,source_type=EXCLUDED.source_type,config_json=EXCLUDED.config_json,
  display_order=EXCLUDED.display_order,enabled=EXCLUDED.enabled,updated_at=clock_timestamp();

INSERT INTO hhy.home_modules(code,title,source_type,config_json,display_order,enabled)
VALUES (
  'home_banner','平台精选','BANNER',
  jsonb_build_object(
    'layoutType','carousel',
    'items',jsonb_build_array(jsonb_build_object(
      'id','home-banner-app','itemType','BANNER','title','发现平台最新 App',
      'subtitle','查看真实发布的 App 内容与产品信息','coverUrl',:'banner_url',
      'target',jsonb_build_object('targetType','IN_APP_ROUTE','route','/content/apps','requiresLogin',true))),
    'moreTarget',jsonb_build_object('targetType','IN_APP_ROUTE','route','/content/apps','requiresLogin',true)),
  20,true)
ON CONFLICT (code) DO UPDATE SET
  title=EXCLUDED.title,source_type=EXCLUDED.source_type,config_json=EXCLUDED.config_json,
  display_order=EXCLUDED.display_order,enabled=EXCLUDED.enabled,updated_at=clock_timestamp();

INSERT INTO hhy.home_modules(code,title,source_type,config_json,display_order,enabled)
SELECT
  'home_latest_projects','最新发布','VERTICAL_LIST',
  jsonb_build_object(
    'subtitle','平台最新上线的真实项目','layoutType','vertical_list',
    'dataSource','LATEST_PROJECTS','limit',4,
    'items',COALESCE(jsonb_agg(jsonb_build_object(
      'id',p.id::text,'contentId',p.id::text,'itemType','CONTENT','title',p.title,
      'subtitle',p.summary,'badges',jsonb_build_array('项目'),
      'target',jsonb_build_object('targetType','IN_APP_ROUTE','route','/content/project/' || p.id,'requiresLogin',true))
      ORDER BY p.created_at DESC,p.id DESC),'[]'::jsonb),
    'moreTarget',jsonb_build_object('targetType','IN_APP_ROUTE','route','/content/projects','requiresLogin',true)),
  30,true
FROM (SELECT * FROM hhy.content_posts WHERE type='PROJECT' AND status='ONLINE'
      ORDER BY created_at DESC,id DESC LIMIT 4) p
ON CONFLICT (code) DO UPDATE SET
  title=EXCLUDED.title,source_type=EXCLUDED.source_type,config_json=EXCLUDED.config_json,
  display_order=EXCLUDED.display_order,enabled=EXCLUDED.enabled,updated_at=clock_timestamp();

INSERT INTO hhy.home_modules(code,title,source_type,config_json,display_order,enabled)
SELECT
  'home_recommended_apps','为你推荐','HORIZONTAL_LIST',
  jsonb_build_object(
    'subtitle','平台精选的真实 App 内容','layoutType','horizontal_list',
    'dataSource','LATEST_APPS','limit',4,
    'items',COALESCE(jsonb_agg(jsonb_strip_nulls(jsonb_build_object(
      'id',p.id::text,'contentId',p.id::text,'itemType','CONTENT','title',p.title,
      'subtitle',p.summary,'coverUrl',p.cover_url,'badges',jsonb_build_array('App'),
      'target',jsonb_build_object('targetType','IN_APP_ROUTE','route','/content/app/' || p.id,'requiresLogin',true)))
      ORDER BY p.created_at DESC,p.id DESC),'[]'::jsonb),
    'moreTarget',jsonb_build_object('targetType','IN_APP_ROUTE','route','/content/apps','requiresLogin',true)),
  40,true
FROM (
  SELECT post.id,post.title,post.summary,post.created_at,
         CASE WHEN binding.public_domain IS NULL OR media.object_key IS NULL THEN NULL
              ELSE rtrim(binding.public_domain,'/') || '/' || ltrim(media.object_key,'/') END cover_url
  FROM hhy.content_posts post
  LEFT JOIN LATERAL (
    SELECT object.id,object.object_key,object.storage_binding_id
    FROM hhy.content_media link
    JOIN hhy.media_objects object ON object.id=link.media_id AND object.status='READY'
    WHERE link.content_id=post.id ORDER BY link.sort_order,link.id LIMIT 1
  ) media ON true
  LEFT JOIN hhy.storage_scope_bindings binding ON binding.id=media.storage_binding_id AND binding.status='ACTIVE'
  WHERE post.type='APP' AND post.status='ONLINE'
  ORDER BY post.created_at DESC,post.id DESC LIMIT 4
) p
ON CONFLICT (code) DO UPDATE SET
  title=EXCLUDED.title,source_type=EXCLUDED.source_type,config_json=EXCLUDED.config_json,
  display_order=EXCLUDED.display_order,enabled=EXCLUDED.enabled,updated_at=clock_timestamp();

COMMIT;

SELECT code || '|' || source_type || '|' || display_order || '|' ||
       jsonb_array_length(config_json->'items')
FROM hhy.home_modules
WHERE code IN ('home_notice','home_banner','home_latest_projects','home_recommended_apps')
  AND enabled=true
ORDER BY display_order;
SQL

echo HOME_CMS_STAGING_FIXTURE_OK
