#!/usr/bin/env bash
set -euo pipefail

if [[ "${HHY_R13_CI_FIXTURE_CONFIRM:-}" != "YES" ]]; then
  echo "Set HHY_R13_CI_FIXTURE_CONFIRM=YES for the dedicated R13 staging CI fixture" >&2
  exit 2
fi

backend=${HHY_R13_CI_BACKEND_CONTAINER:?HHY_R13_CI_BACKEND_CONTAINER is required}
postgres=${HHY_R13_CI_POSTGRES_CONTAINER:?HHY_R13_CI_POSTGRES_CONTAINER is required}
case "$backend" in hhy-r13-ci-candidate-*) ;; *) echo "Unsafe R13 backend container name" >&2; exit 2 ;; esac
case "$postgres" in
  hhy-r13-staging-postgres-1|hhy-r13-staging-*-postgres-1) ;;
  *) echo "Unsafe R13 staging PostgreSQL container name" >&2; exit 2 ;;
esac

docker inspect "$backend" "$postgres" >/dev/null
[[ "$(docker exec "$backend" printenv SPRING_PROFILES_ACTIVE)" == "staging" ]]
[[ "$(docker exec "$backend" printenv HHY_CI_AUTOMATION_ENABLED)" == "true" ]]
phone=$(docker exec "$backend" printenv HHY_CI_AUTOMATION_USER_PHONE)
[[ "$phone" =~ ^[0-9]{6,32}$ ]]

docker exec -i "$postgres" sh -lc \
  'exec psql -qAt -U "$POSTGRES_USER" -d "$POSTGRES_DB" -v ON_ERROR_STOP=1 -v ci_phone="$1"' sh "$phone" <<'SQL'
BEGIN;
DO $fixture_lock$ BEGIN PERFORM pg_advisory_xact_lock(709013); END $fixture_lock$;

INSERT INTO hhy.users(phone,status,invite_code)
VALUES (:'ci_phone','ACTIVE','R13CANDIDATE')
ON CONFLICT (phone) DO NOTHING;

CREATE TEMP TABLE hhy_r13_ci_fixture_context ON COMMIT DROP AS
SELECT id AS user_id
FROM hhy.users
WHERE phone=:'ci_phone' AND status='ACTIVE';

DO $$
DECLARE
  ci_user bigint;
  fixture_binding bigint;
  fixture_config bigint;
  fixture_media bigint;
  fixture_content bigint;
  fixture_admin bigint;
  fixture_snapshot bigint;
  fixture_build_profile bigint;
  fixture_build_job bigint;
  fixture_artifact bigint;
  fixture_channel bigint;
  fixture_release bigint;
  fixture_title text;
  fixture_index integer:=0;
BEGIN
  IF NOT EXISTS (SELECT 1 FROM hhy.flyway_schema_history WHERE version='042' AND success) THEN
    RAISE EXCEPTION 'R13 fixture requires Flyway V042';
  END IF;

  SELECT user_id INTO ci_user FROM hhy_r13_ci_fixture_context;
  IF ci_user IS NULL THEN RAISE EXCEPTION 'Dedicated CI user is missing or inactive'; END IF;

  SELECT id INTO fixture_build_profile FROM hhy.app_build_profiles
  WHERE environment='STAGING' AND name='r13-ci-startup-policy';
  IF fixture_build_profile IS NULL THEN
    INSERT INTO hhy.app_build_profiles(
      name,environment,display_name,gradle_params,allowed_refs,status,version
    ) VALUES (
      'r13-ci-startup-policy','STAGING','R13候选启动版本策略',
      'fixture-only','refs/fixtures/r13/startup-policy','ACTIVE',0
    ) RETURNING id INTO fixture_build_profile;
  ELSIF EXISTS (
    SELECT 1 FROM hhy.app_build_profiles WHERE id=fixture_build_profile AND (
      display_name IS DISTINCT FROM 'R13候选启动版本策略'
      OR gradle_params IS DISTINCT FROM 'fixture-only'
      OR allowed_refs IS DISTINCT FROM 'refs/fixtures/r13/startup-policy'
      OR status IS DISTINCT FROM 'ACTIVE'
    )
  ) THEN
    RAISE EXCEPTION 'R13 startup build profile drifted';
  END IF;

  IF (SELECT count(*) FROM hhy.app_build_jobs
      WHERE profile_id=fixture_build_profile AND triggered_by='r13-ci-startup-policy'
        AND version_code=10222)>1 THEN
    RAISE EXCEPTION 'R13 startup build job is duplicated';
  END IF;
  SELECT id INTO fixture_build_job FROM hhy.app_build_jobs
  WHERE profile_id=fixture_build_profile AND triggered_by='r13-ci-startup-policy'
    AND version_code=10222;
  IF fixture_build_job IS NULL THEN
    INSERT INTO hhy.app_build_jobs(
      profile_id,git_ref,commit_sha,version_name,version_code,status,triggered_by,
      started_at,finished_at
    ) VALUES (
      fixture_build_profile,'refs/fixtures/r13/startup-policy',repeat('0',40),
      '1.2.2',10222,'SUCCEEDED','r13-ci-startup-policy',
      timestamptz '2026-07-27 00:00:00+00',timestamptz '2026-07-27 00:00:00+00'
    ) RETURNING id INTO fixture_build_job;
  END IF;

  SELECT id INTO fixture_artifact FROM hhy.app_build_artifacts
  WHERE job_id=fixture_build_job AND artifact_type='APK';
  IF fixture_artifact IS NULL THEN
    INSERT INTO hhy.app_build_artifacts(
      job_id,artifact_type,object_key,file_size,sha256,signing_fingerprint,retention_until
    ) VALUES (
      fixture_build_job,'APK','fixtures/r13/hhy-pro-1.2.2-10222.apk',1,repeat('d',64),
      repeat('e',64),timestamptz '2099-12-31 23:59:59+00'
    ) RETURNING id INTO fixture_artifact;
  END IF;

  SELECT id INTO fixture_channel FROM hhy.app_release_channels
  WHERE code='official' AND environment='STAGING';
  IF fixture_channel IS NULL THEN
    INSERT INTO hhy.app_release_channels(
      code,environment,download_domain,update_policy,status
    ) VALUES (
      'official','STAGING','https://download.orbexa.cc','R13_CI_STARTUP_GATE','ACTIVE'
    ) RETURNING id INTO fixture_channel;
  ELSIF EXISTS (
    SELECT 1 FROM hhy.app_release_channels WHERE id=fixture_channel AND (
      download_domain IS DISTINCT FROM 'https://download.orbexa.cc' OR status IS DISTINCT FROM 'ACTIVE'
    )
  ) THEN
    RAISE EXCEPTION 'R13 official STAGING release channel drifted';
  END IF;

  IF (SELECT count(*) FROM hhy.app_release_records
      WHERE channel_id=fixture_channel AND version_code=10222)>1 THEN
    RAISE EXCEPTION 'R13 startup release record is duplicated';
  END IF;
  SELECT id INTO fixture_release FROM hhy.app_release_records
  WHERE channel_id=fixture_channel AND version_code=10222;
  IF fixture_release IS NULL THEN
    INSERT INTO hhy.app_release_records(
      artifact_id,channel_id,version_name,version_code,update_type,status,published_at,
      min_supported_version_code,release_notes
    ) VALUES (
      fixture_artifact,fixture_channel,'1.2.2',10222,'NONE','PUBLISHED',
      timestamptz '2026-07-27 00:00:00+00',10222,'R13隔离候选启动策略夹具'
    ) RETURNING id INTO fixture_release;
  ELSIF EXISTS (
    SELECT 1 FROM hhy.app_release_records WHERE id=fixture_release AND (
      artifact_id IS DISTINCT FROM fixture_artifact OR version_name IS DISTINCT FROM '1.2.2'
      OR update_type IS DISTINCT FROM 'NONE' OR status IS DISTINCT FROM 'PUBLISHED'
      OR min_supported_version_code IS DISTINCT FROM 10222
    )
  ) THEN
    RAISE EXCEPTION 'R13 startup release record drifted';
  END IF;

  INSERT INTO hhy.user_profiles(user_id,nickname,bio)
  VALUES (ci_user,'R13候选体验用户','关注真实合作机会、产品增长与长期协作')
  ON CONFLICT (user_id) DO UPDATE
    SET nickname=EXCLUDED.nickname,bio=EXCLUDED.bio,version=hhy.user_profiles.version+1
  WHERE hhy.user_profiles.nickname IS DISTINCT FROM EXCLUDED.nickname
     OR hhy.user_profiles.bio IS DISTINCT FROM EXCLUDED.bio;

  INSERT INTO hhy.admin_users(username,password_hash,status)
  VALUES ('r13-ci-reviewer',repeat('a',60),'ACTIVE')
  ON CONFLICT (username) DO UPDATE SET status='ACTIVE'
  RETURNING id INTO fixture_admin;

  SELECT id INTO fixture_binding FROM hhy.storage_scope_bindings
  WHERE scope_code='public_media' AND status='ACTIVE'
  ORDER BY id DESC LIMIT 1;
  IF fixture_binding IS NULL THEN
    SELECT id INTO fixture_config FROM hhy.provider_config_versions
    WHERE provider_code='storage' AND environment='STAGING' AND status='ACTIVE'
    ORDER BY activated_at DESC NULLS LAST,id DESC LIMIT 1;
    IF fixture_config IS NULL THEN
      INSERT INTO hhy.provider_config_versions(
        provider_code,version_no,status,created_by,activated_at,environment,
        values_json,secret_refs_json,remark,connection_successful,masked_test_result,tested_at
      ) VALUES (
        'storage','r13-ci-public-media-v1','ACTIVE','r13-ci-fixture',clock_timestamp(),'STAGING',
        jsonb_build_object(
          'storage.default_provider','CLOUDFLARE_R2',
          'storage.scope.public_media.provider','CLOUDFLARE_R2',
          'storage.r2.endpoint','https://api.orbexa.cc',
          'storage.r2.public_domain','https://download.orbexa.cc/r09-candidate-media/attempt-2/',
          'storage.r2.bucket.public_media','hhy-r13-candidate-media',
          'storage.signed_url.ttl_seconds',120,
          'storage.private_preview.ttl_seconds',120
        ),
        jsonb_build_object(
          'storage.r2.access_key_id','vault://hhy/r13-ci/storage-access-key',
          'storage.r2.secret_access_key','vault://hhy/r13-ci/storage-secret-key'
        ),
        'R13隔离候选公共媒体，复用已验证协作视觉资产',TRUE,
        'R13 staging public media fixture',clock_timestamp()
      ) RETURNING id INTO fixture_config;
    END IF;
    INSERT INTO hhy.storage_scope_bindings(
      scope_code,provider_code,config_version_id,bucket,public_domain,status
    ) VALUES (
      'public_media','CLOUDFLARE_R2',fixture_config,'hhy-r13-candidate-media',
      'https://download.orbexa.cc/r09-candidate-media/attempt-2/','ACTIVE'
    ) RETURNING id INTO fixture_binding;
  END IF;

  SELECT id INTO fixture_media FROM hhy.media_objects
  WHERE owner_id=ci_user AND storage_binding_id=fixture_binding
    AND object_key='r11-team-logo.png' AND status='READY' AND deleted_at IS NULL
  ORDER BY id LIMIT 1;
  IF fixture_media IS NULL THEN
    INSERT INTO hhy.media_objects(
      owner_id,bucket,object_key,mime,size,sha256,visibility,purpose,
      storage_scope,storage_binding_id,status
    ) VALUES (
      ci_user,'hhy-r13-candidate-media','r11-team-logo.png','image/png',51614,
      'eb2138d67b8a23255240b5ce4ecc1cd758f2bbb8fe3586b874f7d7b32aea6709',
      'PUBLIC','CONTENT_PROJECT','public_media',fixture_binding,'READY'
    ) RETURNING id INTO fixture_media;
  END IF;

  FOREACH fixture_title IN ARRAY ARRAY[
    'R13候选协作项目','R13品牌联合增长计划','R13产品共创伙伴招募'
  ] LOOP
    fixture_index:=fixture_index+1;
    SELECT id INTO fixture_content FROM hhy.content_posts
    WHERE owner_id=ci_user AND title=fixture_title ORDER BY id LIMIT 1 FOR UPDATE;
    IF fixture_content IS NULL THEN
      INSERT INTO hhy.content_posts(
        owner_id,type,title,summary,status,review_status,refresh_times,version,created_at,updated_at
      ) VALUES (
        ci_user,'PROJECT',fixture_title,'面向真实合作伙伴的资源协同与阶段性交付计划',
        'DRAFT',NULL,0,0,
        timestamptz '2026-07-26 08:00:00+00'-(fixture_index*interval '1 day'),
        timestamptz '2026-07-26 08:00:00+00'-(fixture_index*interval '1 day')
      ) RETURNING id INTO fixture_content;
      INSERT INTO hhy.project_details(
        content_id,cooperation,conditions,region,website,created_at,updated_at
      ) VALUES (
        fixture_content,'联合策划、渠道协作与效果复盘','合作目标、时间和双方责任需在启动前确认。',
        '北京','https://h5.orbexa.cc',
        timestamptz '2026-07-26 08:00:00+00'-(fixture_index*interval '1 day'),
        timestamptz '2026-07-26 08:00:00+00'-(fixture_index*interval '1 day')
      );
      INSERT INTO hhy.content_versions(content_id,version_no,snapshot_json,created_by,created_at)
      VALUES (
        fixture_content,'0',jsonb_build_object(
          'description','以可验证目标、明确分工和阶段复盘推进长期合作。',
          'categoryCode','联合增长','regionCode','北京',
          'conditions','合作目标、时间和双方责任需在启动前确认。',
          'website','https://h5.orbexa.cc'
        ),'r13-ci-fixture',
        timestamptz '2026-07-26 08:00:00+00'-(fixture_index*interval '1 day')
      );
      INSERT INTO hhy.content_stats(
        content_id,organic_views,redpacket_views,task_views,favorites,chats,contacts,created_at,updated_at
      ) VALUES (
        fixture_content,(128+fixture_index*37)::text,'0','0',(18+fixture_index)::text,
        (6+fixture_index)::text,(9+fixture_index)::text,
        timestamptz '2026-07-26 08:00:00+00'-(fixture_index*interval '1 day'),
        timestamptz '2026-07-26 08:00:00+00'-(fixture_index*interval '1 day')
      );
      INSERT INTO hhy.content_media(
        content_id,media_id,media_type,sort_order,created_at,updated_at
      ) VALUES (
        fixture_content,fixture_media,'image/png',0,
        timestamptz '2026-07-26 08:00:00+00'-(fixture_index*interval '1 day'),
        timestamptz '2026-07-26 08:00:00+00'-(fixture_index*interval '1 day')
      );

      UPDATE hhy.content_posts
         SET status='PENDING_REVIEW',review_status='PENDING',version=1
       WHERE id=fixture_content AND status='DRAFT' AND version=0;
      IF NOT FOUND THEN RAISE EXCEPTION 'R13 fixture submit transition failed'; END IF;
      INSERT INTO hhy.content_versions(content_id,version_no,snapshot_json,created_by,created_at)
      SELECT fixture_content,'1',snapshot_json,'r13-ci-submit',
             timestamptz '2026-07-26 09:00:00+00'-(fixture_index*interval '1 day')
      FROM hhy.content_versions WHERE content_id=fixture_content AND version_no='0'
      RETURNING id INTO fixture_snapshot;
      INSERT INTO hhy.content_status_logs(
        content_id,from_status,to_status,reason,operator,created_at,transition_version
      ) VALUES (
        fixture_content,'DRAFT','PENDING_REVIEW',NULL,'r13-ci-owner',
        timestamptz '2026-07-26 09:00:00+00'-(fixture_index*interval '1 day'),1
      );

      UPDATE hhy.content_posts SET status='REVIEWING',version=2
      WHERE id=fixture_content AND status='PENDING_REVIEW' AND version=1;
      IF NOT FOUND THEN RAISE EXCEPTION 'R13 fixture review claim transition failed'; END IF;
      INSERT INTO hhy.content_review_records(
        content_id,version_no,decision,reason,admin_id,created_at,snapshot_version_id,command_id
      ) VALUES (
        fixture_content,'1','CLAIM',NULL,fixture_admin,
        timestamptz '2026-07-26 10:00:00+00'-(fixture_index*interval '1 day'),fixture_snapshot,
        'r13-ci-claim-'||fixture_content::text
      );
      INSERT INTO hhy.content_status_logs(
        content_id,from_status,to_status,reason,operator,created_at,transition_version
      ) VALUES (
        fixture_content,'PENDING_REVIEW','REVIEWING',NULL,'r13-ci-reviewer',
        timestamptz '2026-07-26 10:00:00+00'-(fixture_index*interval '1 day'),2
      );

      UPDATE hhy.content_posts
         SET status='APPROVED',review_status='APPROVED',version=3
       WHERE id=fixture_content AND status='REVIEWING' AND version=2;
      IF NOT FOUND THEN RAISE EXCEPTION 'R13 fixture approval transition failed'; END IF;
      INSERT INTO hhy.content_review_records(
        content_id,version_no,decision,reason,admin_id,created_at,snapshot_version_id,command_id
      ) VALUES (
        fixture_content,'1','APPROVE',NULL,fixture_admin,
        timestamptz '2026-07-26 11:00:00+00'-(fixture_index*interval '1 day'),fixture_snapshot,
        'r13-ci-approve-'||fixture_content::text
      );
      INSERT INTO hhy.content_status_logs(
        content_id,from_status,to_status,reason,operator,created_at,transition_version
      ) VALUES (
        fixture_content,'REVIEWING','APPROVED',NULL,'r13-ci-reviewer',
        timestamptz '2026-07-26 11:00:00+00'-(fixture_index*interval '1 day'),3
      );

      UPDATE hhy.content_posts SET status='ONLINE',version=4
      WHERE id=fixture_content AND status='APPROVED' AND version=3;
      IF NOT FOUND THEN RAISE EXCEPTION 'R13 fixture online transition failed'; END IF;
      INSERT INTO hhy.content_status_logs(
        content_id,from_status,to_status,reason,operator,created_at,transition_version
      ) VALUES (
        fixture_content,'APPROVED','ONLINE',NULL,'r13-ci-owner',
        timestamptz '2026-07-26 12:00:00+00'-(fixture_index*interval '1 day'),4
      );
    ELSIF EXISTS (
      SELECT 1 FROM hhy.content_posts WHERE id=fixture_content AND (
        type IS DISTINCT FROM 'PROJECT' OR status IS DISTINCT FROM 'ONLINE'
        OR review_status IS DISTINCT FROM 'APPROVED'
      )
    ) THEN
      RAISE EXCEPTION 'R13 activity content drifted: %',fixture_title;
    END IF;

    INSERT INTO hhy.content_favorites(user_id,content_id)
    SELECT ci_user,fixture_content
    WHERE NOT EXISTS (
      SELECT 1 FROM hhy.content_favorites WHERE user_id=ci_user AND content_id=fixture_content
    );
    INSERT INTO hhy.content_view_logs(user_id,content_id,traffic_type,duration,source,created_at)
    SELECT ci_user,fixture_content,'ORGANIC_TRAFFIC',0,'R13_CANDIDATE',
           timestamptz '2026-07-26 12:00:00+00'-(fixture_index*interval '1 hour')
    WHERE NOT EXISTS (
      SELECT 1 FROM hhy.content_view_logs
      WHERE user_id=ci_user AND content_id=fixture_content AND source='R13_CANDIDATE'
        AND traffic_type='ORGANIC_TRAFFIC'
    );

    IF fixture_title='R13候选协作项目' AND NOT EXISTS (
      SELECT 1 FROM hhy.content_contacts
      WHERE content_id=fixture_content AND channel='LINK' AND removed_at IS NULL
    ) THEN
      INSERT INTO hhy.content_contacts(
        content_id,channel,value_cipher,display_mask,sort_order,created_at,updated_at
      ) VALUES (
        fixture_content,'LINK','hhy-contact-v1:r13-ci-link','平台认证链接',1,
        timestamptz '2026-07-26 08:00:00+00',timestamptz '2026-07-26 08:00:00+00'
      );
    END IF;
  END LOOP;
END;
$$;

COMMIT;

SELECT 'R13_CI_FIXTURE_OK|contents='||count(DISTINCT content.id)
       ||'|favorites='||count(DISTINCT favorite.id)
       ||'|history='||count(DISTINCT history.id)
       ||'|contacts='||count(DISTINCT contact.id)
       ||'|release='||count(DISTINCT release.id)
       ||'|version='||min(release.version_code)::text
FROM hhy.users candidate
JOIN hhy.content_posts content ON content.owner_id=candidate.id
  AND content.title IN ('R13候选协作项目','R13品牌联合增长计划','R13产品共创伙伴招募')
  AND content.status='ONLINE'
JOIN hhy.content_favorites favorite ON favorite.user_id=candidate.id AND favorite.content_id=content.id
JOIN hhy.content_view_logs history ON history.user_id=candidate.id AND history.content_id=content.id
  AND history.traffic_type='ORGANIC_TRAFFIC' AND history.source='R13_CANDIDATE'
LEFT JOIN hhy.content_contacts contact ON contact.content_id=content.id
  AND contact.channel='LINK' AND contact.removed_at IS NULL
JOIN hhy.app_release_channels channel ON channel.code='official' AND channel.environment='STAGING'
JOIN hhy.app_release_records release ON release.channel_id=channel.id
  AND release.version_code=10222 AND release.update_type='NONE' AND release.status='PUBLISHED'
WHERE candidate.phone=:'ci_phone'
HAVING count(DISTINCT content.id)=3
   AND count(DISTINCT favorite.id)=3
   AND count(DISTINCT history.id)=3
   AND count(DISTINCT contact.id)=1
   AND count(DISTINCT release.id)=1;
SQL
