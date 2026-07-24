#!/usr/bin/env bash
set -euo pipefail

if [[ "${HHY_R11_CI_FIXTURE_CONFIRM:-}" != "YES" ]]; then
  echo "Set HHY_R11_CI_FIXTURE_CONFIRM=YES for the dedicated R11 staging CI fixture" >&2
  exit 2
fi

backend=${HHY_R11_CI_BACKEND_CONTAINER:?HHY_R11_CI_BACKEND_CONTAINER is required}
postgres=${HHY_R11_CI_POSTGRES_CONTAINER:?HHY_R11_CI_POSTGRES_CONTAINER is required}
case "$backend" in hhy-r11-ci-candidate-*) ;; *) echo "Unsafe R11 backend container name" >&2; exit 2 ;; esac
case "$postgres" in hhy-*-postgres-1) ;; *) echo "Unsafe staging PostgreSQL container name" >&2; exit 2 ;; esac

docker inspect "$backend" "$postgres" >/dev/null
[[ "$(docker exec "$backend" printenv SPRING_PROFILES_ACTIVE)" == "staging" ]]
[[ "$(docker exec "$backend" printenv HHY_CI_AUTOMATION_ENABLED)" == "true" ]]
phone=$(docker exec "$backend" printenv HHY_CI_AUTOMATION_USER_PHONE)
[[ "$phone" =~ ^[0-9]{6,32}$ ]]

docker exec -i "$postgres" sh -lc \
  'exec psql -U "$POSTGRES_USER" -d "$POSTGRES_DB" -v ON_ERROR_STOP=1 -v ci_phone="$1"' sh "$phone" <<'SQL'
BEGIN;
SELECT pg_advisory_xact_lock(709011);

CREATE TEMP TABLE hhy_r11_ci_fixture_context ON COMMIT DROP AS
SELECT id AS user_id
FROM hhy.users
WHERE phone=:'ci_phone' AND status='ACTIVE';

DO $$
DECLARE
  ci_user bigint;
  fixture_content bigint;
  fixture_config bigint;
  fixture_binding bigint;
  fixture_logo bigint;
BEGIN
  IF NOT EXISTS (SELECT 1 FROM hhy.flyway_schema_history WHERE version='038' AND success) THEN
    RAISE EXCEPTION 'R11 fixture requires Flyway V038';
  END IF;

  SELECT user_id INTO ci_user FROM hhy_r11_ci_fixture_context;
  IF ci_user IS NULL THEN RAISE EXCEPTION 'Dedicated CI user is missing or inactive'; END IF;

  INSERT INTO hhy.user_profiles(user_id,nickname,bio)
  VALUES (ci_user,'R11候选团队长','增长策略、内容运营与渠道协作团队')
  ON CONFLICT (user_id) DO UPDATE
    SET nickname=EXCLUDED.nickname,bio=EXCLUDED.bio,version=hhy.user_profiles.version+1;

  INSERT INTO hhy.identity_profiles(user_id,name_cipher,id_no_cipher,id_hash,status,verified_at)
  VALUES (ci_user,'r11-ci-name-cipher','r11-ci-id-cipher',repeat('b',64),'VERIFIED',clock_timestamp())
  ON CONFLICT (user_id) DO UPDATE
    SET name_cipher=EXCLUDED.name_cipher,id_no_cipher=EXCLUDED.id_no_cipher,
        id_hash=EXCLUDED.id_hash,status='VERIFIED',verified_at=EXCLUDED.verified_at,
        version=hhy.identity_profiles.version+1;

  SELECT id,config_version_id INTO fixture_binding,fixture_config
  FROM hhy.storage_scope_bindings
  WHERE scope_code='public_media' AND status='ACTIVE'
  ORDER BY id LIMIT 1;
  IF fixture_binding IS NULL THEN
    SELECT id INTO fixture_config
    FROM hhy.provider_config_versions
    WHERE provider_code='storage' AND status='ACTIVE'
    ORDER BY activated_at DESC NULLS LAST,id DESC LIMIT 1;
    IF fixture_config IS NULL THEN
      INSERT INTO hhy.provider_config_versions(
        provider_code,version_no,status,created_by,activated_at,environment,
        values_json,secret_refs_json,remark,connection_successful,masked_test_result,tested_at)
      VALUES (
        'storage','r11-ci-public-media-v1','ACTIVE','r11-ci-fixture',clock_timestamp(),'STAGING',
        jsonb_build_object(
          'storage.default_provider','CLOUDFLARE_R2',
          'storage.scope.public_media.provider','CLOUDFLARE_R2',
          'storage.r2.endpoint','https://api.orbexa.cc',
          'storage.r2.public_domain','https://download.orbexa.cc/r09-candidate-media/attempt-2/',
          'storage.r2.bucket.public_media','hhy-r11-candidate-media',
          'storage.signed_url.ttl_seconds',120,
          'storage.private_preview.ttl_seconds',120),
        jsonb_build_object(
          'storage.r2.access_key_id','vault://hhy/r11-ci/storage-access-key',
          'storage.r2.secret_access_key','vault://hhy/r11-ci/storage-secret-key'),
        'R11隔离候选公共媒体，复用已验证公开测试图片',TRUE,
        'R11 staging public media fixture',clock_timestamp())
      RETURNING id INTO fixture_config;
    END IF;
    INSERT INTO hhy.storage_scope_bindings(
      scope_code,provider_code,config_version_id,bucket,public_domain,status)
    VALUES (
      'public_media','CLOUDFLARE_R2',fixture_config,'hhy-r11-candidate-media',
      'https://download.orbexa.cc/r09-candidate-media/attempt-2/','ACTIVE')
    RETURNING id INTO fixture_binding;
  END IF;

  SELECT id INTO fixture_logo
  FROM hhy.media_objects
  WHERE owner_id=ci_user AND storage_binding_id=fixture_binding
    AND object_key='r11-team-logo.png' AND status='READY'
  ORDER BY id LIMIT 1;
  IF fixture_logo IS NULL THEN
    INSERT INTO hhy.media_objects(
      owner_id,bucket,object_key,mime,size,sha256,visibility,purpose,
      storage_scope,storage_binding_id,status)
    VALUES (
      ci_user,'hhy-r11-candidate-media','r11-team-logo.png','image/png',51614,
      'eb2138d67b8a23255240b5ce4ecc1cd758f2bbb8fe3586b874f7d7b32aea6709',
      'PUBLIC','CONTENT_TEAM_LEADER','public_media',fixture_binding,'READY')
    RETURNING id INTO fixture_logo;
  END IF;

  SELECT id INTO fixture_content
  FROM hhy.content_posts
  WHERE owner_id=ci_user AND type='TEAM_LEADER' AND status<>'DELETED'
  ORDER BY CASE WHEN title='R11候选增长协作团队' THEN 0 ELSE 1 END,id
  LIMIT 1 FOR UPDATE;
  IF fixture_content IS NULL THEN
    INSERT INTO hhy.content_posts(
      owner_id,type,title,summary,status,review_status,refresh_times,version)
    VALUES (
      ci_user,'TEAM_LEADER','R11候选增长协作团队',
      '专注品牌增长、内容运营与渠道协作的真实候选团队资料',
      'DRAFT','APPROVED',0,0)
    RETURNING id INTO fixture_content;
  ELSE
    UPDATE hhy.content_posts
       SET title='R11候选增长协作团队',
           summary='专注品牌增长、内容运营与渠道协作的真实候选团队资料',
           status='DRAFT',review_status='APPROVED',refresh_times=0,version=version+1,
           updated_at=clock_timestamp()
     WHERE id=fixture_content;
  END IF;

  INSERT INTO hhy.team_leader_details(
    content_id,team_name,nickname,logo_media_id,region,personal_intro,team_intro,
    size_range,skills,cooperation_types,cooperation_requirement,past_cases,
    accept_private_chat)
  VALUES (
    fixture_content,'R11候选增长协作团队','R11候选团队长',fixture_logo,'上海',
    '长期负责增长策略与跨团队协作落地。',
    '面向品牌与产品团队提供可验证的增长规划、内容运营和渠道协作。',
    '10-50人','品牌增长','项目顾问与联合运营',
    '合作目标、周期和双方责任需在启动前确认。',
    jsonb_build_array('完成新产品冷启动增长方案','搭建内容运营与渠道协作流程'),TRUE)
  ON CONFLICT (content_id) DO UPDATE
    SET team_name=EXCLUDED.team_name,nickname=EXCLUDED.nickname,
        logo_media_id=EXCLUDED.logo_media_id,region=EXCLUDED.region,
        personal_intro=EXCLUDED.personal_intro,team_intro=EXCLUDED.team_intro,
        size_range=EXCLUDED.size_range,skills=EXCLUDED.skills,
        cooperation_types=EXCLUDED.cooperation_types,
        cooperation_requirement=EXCLUDED.cooperation_requirement,
        past_cases=EXCLUDED.past_cases,accept_private_chat=EXCLUDED.accept_private_chat,
        updated_at=clock_timestamp();

  INSERT INTO hhy.content_versions(content_id,version_no,snapshot_json,created_by)
  VALUES (
    fixture_content,'90011',
    jsonb_build_object(
      'description','候选环境用于验证团队长列表、详情和所有者编辑闭环。',
      'categoryCode','品牌增长','regionCode','上海',
      'teamName','R11候选增长协作团队','nickname','R11候选团队长',
      'logoMediaId',fixture_logo,'personalIntro','长期负责增长策略与跨团队协作落地。',
      'teamIntro','面向品牌与产品团队提供可验证的增长规划、内容运营和渠道协作。',
      'sizeRange','10-50人','skills','品牌增长',
      'cooperationTypes','项目顾问与联合运营',
      'cooperationRequirement','合作目标、周期和双方责任需在启动前确认。',
      'pastCases',jsonb_build_array('完成新产品冷启动增长方案','搭建内容运营与渠道协作流程'),
      'acceptPrivateChat',TRUE),
    'r11-ci-fixture')
  ON CONFLICT (content_id,version_no) DO NOTHING;

  INSERT INTO hhy.content_stats(content_id,organic_views,redpacket_views,task_views,favorites,chats,contacts)
  VALUES (fixture_content,'0','0','0','0','0','0')
  ON CONFLICT (content_id) DO UPDATE
    SET organic_views='0',redpacket_views='0',task_views='0',favorites='0',
        chats='0',contacts='0',updated_at=clock_timestamp();

  DELETE FROM hhy.content_media WHERE content_id=fixture_content;
  INSERT INTO hhy.content_media(content_id,media_id,media_type,sort_order)
  VALUES (fixture_content,fixture_logo,'image/png',0);

  DELETE FROM hhy.content_contacts WHERE content_id=fixture_content;
  INSERT INTO hhy.content_contacts(content_id,channel,value_cipher,display_mask,sort_order)
  VALUES (fixture_content,'WECHAT','hhy-contact-v1:r11-ci-team-contact','微信 R11***',0);

  UPDATE hhy.content_posts
     SET status='ONLINE',review_status='APPROVED',updated_at=clock_timestamp()
   WHERE id=fixture_content;
END;
$$;

COMMIT;

SELECT 'R11_CI_FIXTURE_OK|user='||u.id||'|content='||p.id||'|logo='||d.logo_media_id||
       '|contacts='||count(DISTINCT c.id)||'|media='||count(DISTINCT cm.id)
FROM hhy.users u
JOIN hhy.identity_profiles identity ON identity.user_id=u.id AND identity.status='VERIFIED'
JOIN hhy.content_posts p ON p.owner_id=u.id
JOIN hhy.team_leader_details d ON d.content_id=p.id
JOIN hhy.content_contacts c ON c.content_id=p.id AND c.channel='WECHAT'
JOIN hhy.content_media cm ON cm.content_id=p.id AND cm.media_id=d.logo_media_id
JOIN hhy.media_objects m ON m.id=cm.media_id AND m.status='READY' AND m.visibility='PUBLIC'
JOIN hhy.storage_scope_bindings b ON b.id=m.storage_binding_id AND b.status='ACTIVE'
WHERE u.phone=:'ci_phone' AND p.title='R11候选增长协作团队'
  AND p.type='TEAM_LEADER' AND p.status='ONLINE' AND p.review_status='APPROVED'
  AND d.nickname='R11候选团队长' AND d.region='上海' AND d.size_range='10-50人'
  AND d.skills='品牌增长' AND d.cooperation_types='项目顾问与联合运营'
  AND d.personal_intro IS NOT NULL AND d.team_intro IS NOT NULL
  AND d.cooperation_requirement IS NOT NULL AND jsonb_array_length(d.past_cases)=2
  AND d.accept_private_chat IS TRUE
  AND (SELECT count(*) FROM hhy.content_posts owned
       WHERE owned.owner_id=u.id AND owned.type='TEAM_LEADER' AND owned.status<>'DELETED')=1
GROUP BY u.id,p.id,d.logo_media_id
HAVING count(DISTINCT c.id)=1 AND count(DISTINCT cm.id)=1;
SQL
