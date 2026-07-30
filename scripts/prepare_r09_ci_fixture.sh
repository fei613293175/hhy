#!/usr/bin/env bash
set -euo pipefail

if [[ "${HHY_R09_CI_FIXTURE_CONFIRM:-}" != "YES" ]]; then
  echo "Set HHY_R09_CI_FIXTURE_CONFIRM=YES for the dedicated R09 staging CI fixture" >&2
  exit 2
fi

backend=${HHY_R09_CI_BACKEND_CONTAINER:?HHY_R09_CI_BACKEND_CONTAINER is required}
postgres=${HHY_R09_CI_POSTGRES_CONTAINER:?HHY_R09_CI_POSTGRES_CONTAINER is required}
case "$backend" in hhy-r09-ci-candidate-*) ;; *) echo "Unsafe R09 backend container name" >&2; exit 2 ;; esac
case "$postgres" in hhy-*-postgres-1) ;; *) echo "Unsafe staging PostgreSQL container name" >&2; exit 2 ;; esac

docker inspect "$backend" "$postgres" >/dev/null
[[ "$(docker exec "$backend" printenv SPRING_PROFILES_ACTIVE)" == "staging" ]]
[[ "$(docker exec "$backend" printenv HHY_CI_AUTOMATION_ENABLED)" == "true" ]]
phone=$(docker exec "$backend" printenv HHY_CI_AUTOMATION_USER_PHONE)
[[ "$phone" =~ ^[0-9]{6,32}$ ]]

docker exec -i "$postgres" sh -lc \
  'exec psql -U "$POSTGRES_USER" -d "$POSTGRES_DB" -v ON_ERROR_STOP=1 -v ci_phone="$1"' sh "$phone" <<'SQL'
BEGIN;
SELECT pg_advisory_xact_lock(709009);

CREATE TEMP TABLE hhy_r09_ci_fixture_context ON COMMIT DROP AS
SELECT id AS user_id
FROM hhy.users
WHERE phone=:'ci_phone' AND status='ACTIVE';

DO $$
DECLARE
  ci_user bigint;
  fixture_content bigint;
  fixture_config bigint;
  fixture_binding bigint;
  fixture_media_list bigint;
  fixture_media_editor bigint;
BEGIN
  IF NOT EXISTS (SELECT 1 FROM hhy.flyway_schema_history WHERE version='035' AND success) THEN
    RAISE EXCEPTION 'R09 fixture requires Flyway V035';
  END IF;

  SELECT user_id INTO ci_user FROM hhy_r09_ci_fixture_context;
  IF ci_user IS NULL THEN RAISE EXCEPTION 'Dedicated CI user is missing or inactive'; END IF;

  INSERT INTO hhy.user_profiles(user_id,nickname,bio)
  VALUES (ci_user,'R09候选发布者','仅用于隔离预发布App候选验证')
  ON CONFLICT (user_id) DO UPDATE
    SET nickname=EXCLUDED.nickname,bio=EXCLUDED.bio,version=hhy.user_profiles.version+1;

  INSERT INTO hhy.identity_profiles(user_id,name_cipher,id_no_cipher,id_hash,status,verified_at)
  VALUES (ci_user,'r09-ci-name-cipher','r09-ci-id-cipher',repeat('9',64),'VERIFIED',clock_timestamp())
  ON CONFLICT (user_id) DO UPDATE
    SET name_cipher=EXCLUDED.name_cipher,id_no_cipher=EXCLUDED.id_no_cipher,
        id_hash=EXCLUDED.id_hash,status='VERIFIED',verified_at=EXCLUDED.verified_at,
        version=hhy.identity_profiles.version+1;

  SELECT id INTO fixture_content FROM hhy.content_posts
   WHERE owner_id=ci_user AND title='R09候选联调App'
   ORDER BY id LIMIT 1 FOR UPDATE;
  IF fixture_content IS NULL THEN
    INSERT INTO hhy.content_posts(owner_id,type,title,summary,status,review_status)
    VALUES (ci_user,'APP','R09候选联调App','验证真实App列表、详情与编辑候选闭环','ONLINE','APPROVED')
    RETURNING id INTO fixture_content;
  ELSE
    UPDATE hhy.content_posts
       SET type='APP',summary='验证真实App列表、详情与编辑候选闭环',
           status='ONLINE',review_status='APPROVED',version=version+1
     WHERE id=fixture_content;
  END IF;

  INSERT INTO hhy.app_details(content_id,app_name,platform,version_text,download_url,website)
  VALUES (fixture_content,'R09候选联调App','ANDROID','1.2.2',
          'https://www.orbexa.cc','https://www.orbexa.cc')
  ON CONFLICT (content_id) DO UPDATE
    SET app_name=EXCLUDED.app_name,platform=EXCLUDED.platform,
        version_text=EXCLUDED.version_text,download_url=EXCLUDED.download_url,
        website=EXCLUDED.website,updated_at=clock_timestamp();

  INSERT INTO hhy.content_versions(content_id,version_no,snapshot_json,created_by)
  VALUES (fixture_content,'0',
    '{"appName":"R09候选联调App","description":"用于验证App推广列表、详情和所有者编辑闭环的真实候选资料。","categoryCode":"TOOLS","platform":"ANDROID","versionText":"1.2.2","downloadUrl":"https://www.orbexa.cc","website":"https://www.orbexa.cc"}'::jsonb,
    'r09-ci-fixture')
  ON CONFLICT (content_id,version_no) DO NOTHING;

  INSERT INTO hhy.content_stats(content_id,organic_views,redpacket_views,task_views,favorites,chats,contacts)
  VALUES (fixture_content,'0','0','0','0','0','0')
  ON CONFLICT (content_id) DO UPDATE
    SET organic_views='0',redpacket_views='0',task_views='0',favorites='0',
        chats='0',contacts='0',updated_at=clock_timestamp();

  SELECT id INTO fixture_config
  FROM hhy.provider_config_versions
  WHERE provider_code='storage' AND version_no='r09-ci-public-media-v1'
  ORDER BY id LIMIT 1;
  IF fixture_config IS NULL THEN
    INSERT INTO hhy.provider_config_versions(
      provider_code,version_no,status,created_by,activated_at,environment,
      values_json,secret_refs_json,remark,connection_successful,masked_test_result,tested_at)
    VALUES (
      'storage','r09-ci-public-media-v1','ACTIVE','r09-ci-fixture',clock_timestamp(),'STAGING',
      jsonb_build_object(
        'storage.default_provider','CLOUDFLARE_R2',
        'storage.scope.public_media.provider','CLOUDFLARE_R2',
        'storage.r2.endpoint','https://api.orbexa.cc',
        'storage.r2.public_domain','https://download.orbexa.cc/r09-candidate-media/attempt-2/',
        'storage.r2.bucket.public_media','hhy-r09-candidate-media',
        'storage.signed_url.ttl_seconds',120,
        'storage.private_preview.ttl_seconds',120),
      jsonb_build_object(
        'storage.r2.access_key_id','vault://hhy/r09-ci/storage-access-key',
        'storage.r2.secret_access_key','vault://hhy/r09-ci/storage-secret-key'),
      'R09隔离候选公共媒体，只用于真实截图显示验证',TRUE,
      'R09 staging public media fixture',clock_timestamp())
    RETURNING id INTO fixture_config;
  END IF;

  SELECT id INTO fixture_binding
  FROM hhy.storage_scope_bindings
  WHERE scope_code='public_media' AND status='ACTIVE'
  ORDER BY id LIMIT 1;
  IF fixture_binding IS NULL THEN
    INSERT INTO hhy.storage_scope_bindings(
      scope_code,provider_code,config_version_id,bucket,public_domain,status)
    VALUES (
      'public_media','CLOUDFLARE_R2',fixture_config,'hhy-r09-candidate-media',
      'https://download.orbexa.cc/r09-candidate-media/attempt-2/','ACTIVE')
    RETURNING id INTO fixture_binding;
  END IF;

  SELECT id INTO fixture_media_list
  FROM hhy.media_objects
  WHERE owner_id=ci_user AND storage_binding_id=fixture_binding
    AND object_key='01-app-list.png'
  ORDER BY id LIMIT 1;
  IF fixture_media_list IS NULL THEN
    INSERT INTO hhy.media_objects(
      owner_id,bucket,object_key,mime,size,sha256,visibility,purpose,
      storage_scope,storage_binding_id,status)
    VALUES (
      ci_user,'hhy-r09-candidate-media','01-app-list.png','image/png',122463,
      '4327c819bbaa435f681ac34f5dd01fff920bd850f26974b4708c792490b14878',
      'PUBLIC','CONTENT_APP_PROMOTION','public_media',fixture_binding,'READY')
    RETURNING id INTO fixture_media_list;
  END IF;

  SELECT id INTO fixture_media_editor
  FROM hhy.media_objects
  WHERE owner_id=ci_user AND storage_binding_id=fixture_binding
    AND object_key='03-app-editor.png'
  ORDER BY id LIMIT 1;
  IF fixture_media_editor IS NULL THEN
    INSERT INTO hhy.media_objects(
      owner_id,bucket,object_key,mime,size,sha256,visibility,purpose,
      storage_scope,storage_binding_id,status)
    VALUES (
      ci_user,'hhy-r09-candidate-media','03-app-editor.png','image/png',171071,
      '4d15b62c15e596e01a19fea2a6bd02f3fb3ba5b0d0f1645482785e90119cffde',
      'PUBLIC','CONTENT_APP_PROMOTION','public_media',fixture_binding,'READY')
    RETURNING id INTO fixture_media_editor;
  END IF;

  DELETE FROM hhy.content_media WHERE content_id=fixture_content;
  INSERT INTO hhy.content_media(content_id,media_id,media_type,sort_order)
  VALUES
    (fixture_content,fixture_media_list,'image/png',0),
    (fixture_content,fixture_media_editor,'image/png',1);
END;
$$;

COMMIT;

SELECT 'R09_CI_FIXTURE_OK|user='||u.id||'|content='||p.id||'|media='||
       string_agg(m.id::text,',' ORDER BY cm.sort_order)
FROM hhy.users u
JOIN hhy.content_posts p ON p.owner_id=u.id
JOIN hhy.content_media cm ON cm.content_id=p.id
JOIN hhy.media_objects m ON m.id=cm.media_id
WHERE u.phone=:'ci_phone' AND p.title='R09候选联调App'
GROUP BY u.id,p.id
ORDER BY p.id LIMIT 1;
SQL
