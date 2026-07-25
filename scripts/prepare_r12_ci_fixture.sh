#!/usr/bin/env bash
set -euo pipefail

if [[ "${HHY_R12_CI_FIXTURE_CONFIRM:-}" != "YES" ]]; then
  echo "Set HHY_R12_CI_FIXTURE_CONFIRM=YES for the dedicated R12 staging CI fixture" >&2
  exit 2
fi

backend=${HHY_R12_CI_BACKEND_CONTAINER:?HHY_R12_CI_BACKEND_CONTAINER is required}
postgres=${HHY_R12_CI_POSTGRES_CONTAINER:?HHY_R12_CI_POSTGRES_CONTAINER is required}
case "$backend" in hhy-r12-ci-candidate-*) ;; *) echo "Unsafe R12 backend container name" >&2; exit 2 ;; esac
case "$postgres" in hhy-r12-staging-*-postgres-1) ;; *) echo "Unsafe R12 staging PostgreSQL container name" >&2; exit 2 ;; esac

docker inspect "$backend" "$postgres" >/dev/null
[[ "$(docker exec "$backend" printenv SPRING_PROFILES_ACTIVE)" == "staging" ]]
[[ "$(docker exec "$backend" printenv HHY_CI_AUTOMATION_ENABLED)" == "true" ]]
phone=$(docker exec "$backend" printenv HHY_CI_AUTOMATION_USER_PHONE)
[[ "$phone" =~ ^[0-9]{6,32}$ ]]

docker exec -i "$postgres" sh -lc \
  'exec psql -qAt -U "$POSTGRES_USER" -d "$POSTGRES_DB" -v ON_ERROR_STOP=1 -v ci_phone="$1"' sh "$phone" <<'SQL'
BEGIN;
DO $fixture_lock$ BEGIN PERFORM pg_advisory_xact_lock(709012); END $fixture_lock$;

INSERT INTO hhy.users(phone,status,invite_code)
VALUES (:'ci_phone','ACTIVE','R12CANDIDATE')
ON CONFLICT (phone) DO NOTHING;

CREATE TEMP TABLE hhy_r12_ci_fixture_context ON COMMIT DROP AS
SELECT id AS user_id
FROM hhy.users
WHERE phone=:'ci_phone' AND status='ACTIVE';

CREATE OR REPLACE FUNCTION pg_temp.r12_ci_create_project(
  p_user_id bigint,
  p_title text,
  p_summary text,
  p_description text,
  p_category text,
  p_region text,
  p_fixture_time timestamptz,
  p_media_id bigint,
  p_views bigint,
  p_favorites bigint,
  p_chats bigint,
  p_contacts bigint
) RETURNS bigint
LANGUAGE plpgsql AS $$
DECLARE
  content_id bigint;
BEGIN
  INSERT INTO hhy.content_posts(
    owner_id,type,title,summary,status,review_status,refresh_times,version,created_at,updated_at
  ) VALUES (
    p_user_id,'PROJECT',p_title,p_summary,'DRAFT',NULL,0,0,p_fixture_time,p_fixture_time
  ) RETURNING id INTO content_id;

  INSERT INTO hhy.project_details(
    content_id,cooperation,conditions,region,website,created_at,updated_at
  ) VALUES (
    content_id,p_description,'合作目标、周期和双方责任需在启动前确认。',p_region,
    'https://h5.orbexa.cc',p_fixture_time,p_fixture_time
  );

  INSERT INTO hhy.content_versions(content_id,version_no,snapshot_json,created_by,created_at)
  VALUES (
    content_id,'0',jsonb_build_object(
      'description',p_description,
      'categoryCode',p_category,
      'regionCode',p_region,
      'conditions','合作目标、周期和双方责任需在启动前确认。',
      'website','https://h5.orbexa.cc'
    ),'r12-ci-fixture',p_fixture_time
  );

  INSERT INTO hhy.content_stats(
    content_id,organic_views,redpacket_views,task_views,favorites,chats,contacts,created_at,updated_at
  ) VALUES (
    content_id,p_views::text,'0','0',p_favorites::text,p_chats::text,p_contacts::text,
    p_fixture_time,p_fixture_time
  );

  IF p_media_id IS NOT NULL THEN
    INSERT INTO hhy.content_media(content_id,media_id,media_type,sort_order,created_at,updated_at)
    VALUES (content_id,p_media_id,'image/png',0,p_fixture_time,p_fixture_time);
  END IF;
  RETURN content_id;
END;
$$;

CREATE OR REPLACE FUNCTION pg_temp.r12_ci_advance_review(
  p_content_id bigint,
  p_admin_id bigint,
  p_final_status text,
  p_reason text,
  p_fixture_time timestamptz,
  p_suffix text
) RETURNS void
LANGUAGE plpgsql AS $$
DECLARE
  snapshot_id bigint;
BEGIN
  IF p_final_status NOT IN ('REVIEWING','REJECTED','ONLINE') THEN
    RAISE EXCEPTION 'R12 fixture final status is unsupported: %',p_final_status;
  END IF;

  UPDATE hhy.content_posts
     SET status='PENDING_REVIEW',review_status='PENDING',version=version+1,updated_at=p_fixture_time
   WHERE id=p_content_id AND status='DRAFT' AND version=0;
  IF NOT FOUND THEN RAISE EXCEPTION 'R12 fixture submission source is not a fresh draft: %',p_content_id; END IF;

  INSERT INTO hhy.content_versions(content_id,version_no,snapshot_json,created_by,created_at)
  SELECT content.id,content.version::text,latest.snapshot_json,'r12-ci-submit',p_fixture_time
  FROM hhy.content_posts content
  JOIN LATERAL (
    SELECT snapshot_json FROM hhy.content_versions
    WHERE content_id=content.id ORDER BY version_no::bigint DESC,id DESC LIMIT 1
  ) latest ON true
  WHERE content.id=p_content_id
  RETURNING id INTO snapshot_id;
  INSERT INTO hhy.content_status_logs(
    content_id,from_status,to_status,reason,operator,created_at,transition_version
  ) VALUES (
    p_content_id,'DRAFT','PENDING_REVIEW',NULL,'r12-ci-owner',p_fixture_time,1
  );

  UPDATE hhy.content_posts
     SET status='REVIEWING',version=version+1,updated_at=p_fixture_time
   WHERE id=p_content_id AND status='PENDING_REVIEW' AND version=1;
  IF NOT FOUND THEN RAISE EXCEPTION 'R12 fixture review claim failed: %',p_content_id; END IF;
  INSERT INTO hhy.content_review_records(
    content_id,version_no,decision,reason,admin_id,created_at,snapshot_version_id,command_id
  ) VALUES (
    p_content_id,'1','CLAIM',NULL,p_admin_id,p_fixture_time,snapshot_id,
    'r12-ci-claim-'||p_content_id::text||'-'||p_suffix
  );
  INSERT INTO hhy.content_status_logs(
    content_id,from_status,to_status,reason,operator,created_at,transition_version
  ) VALUES (
    p_content_id,'PENDING_REVIEW','REVIEWING',NULL,'r12-ci-reviewer',p_fixture_time,2
  );

  IF p_final_status='REJECTED' THEN
    UPDATE hhy.content_posts
       SET status='REJECTED',review_status='REJECTED',version=version+1,updated_at=p_fixture_time
     WHERE id=p_content_id AND status='REVIEWING' AND version=2;
    INSERT INTO hhy.content_review_records(
      content_id,version_no,decision,reason,admin_id,created_at,snapshot_version_id,command_id
    ) VALUES (
      p_content_id,'1','REJECT',p_reason,p_admin_id,p_fixture_time,snapshot_id,
      'r12-ci-reject-'||p_content_id::text||'-'||p_suffix
    );
    INSERT INTO hhy.content_status_logs(
      content_id,from_status,to_status,reason,operator,created_at,transition_version
    ) VALUES (
      p_content_id,'REVIEWING','REJECTED',p_reason,'r12-ci-reviewer',p_fixture_time,3
    );
  ELSIF p_final_status='ONLINE' THEN
    UPDATE hhy.content_posts
       SET status='APPROVED',review_status='APPROVED',version=version+1,updated_at=p_fixture_time
     WHERE id=p_content_id AND status='REVIEWING' AND version=2;
    INSERT INTO hhy.content_review_records(
      content_id,version_no,decision,reason,admin_id,created_at,snapshot_version_id,command_id
    ) VALUES (
      p_content_id,'1','APPROVE',NULL,p_admin_id,p_fixture_time,snapshot_id,
      'r12-ci-approve-'||p_content_id::text||'-'||p_suffix
    );
    INSERT INTO hhy.content_status_logs(
      content_id,from_status,to_status,reason,operator,created_at,transition_version
    ) VALUES (
      p_content_id,'REVIEWING','APPROVED',NULL,'r12-ci-reviewer',p_fixture_time,3
    );

    UPDATE hhy.content_posts
       SET status='ONLINE',version=version+1,updated_at=p_fixture_time
     WHERE id=p_content_id AND status='APPROVED' AND version=3;
    INSERT INTO hhy.content_status_logs(
      content_id,from_status,to_status,reason,operator,created_at,transition_version
    ) VALUES (
      p_content_id,'APPROVED','ONLINE',NULL,'r12-ci-owner',p_fixture_time,4
    );
  END IF;
END;
$$;

DO $$
DECLARE
  ci_user bigint;
  fixture_admin bigint;
  fixture_plan bigint;
  fixture_binding bigint;
  fixture_config bigint;
  fixture_media bigint;
  fixture_content bigint;
  active_target_count integer;
  stale record;
BEGIN
  IF NOT EXISTS (SELECT 1 FROM hhy.flyway_schema_history WHERE version='041' AND success) THEN
    RAISE EXCEPTION 'R12 fixture requires Flyway V041';
  END IF;

  SELECT user_id INTO ci_user FROM hhy_r12_ci_fixture_context;
  IF ci_user IS NULL THEN RAISE EXCEPTION 'Dedicated CI user is missing or inactive'; END IF;

  INSERT INTO hhy.user_profiles(user_id,nickname,bio)
  VALUES (ci_user,'R12候选发布者','专注联合增长、内容运营与商业协作落地')
  ON CONFLICT (user_id) DO UPDATE
    SET nickname=EXCLUDED.nickname,bio=EXCLUDED.bio,version=hhy.user_profiles.version+1
  WHERE hhy.user_profiles.nickname IS DISTINCT FROM EXCLUDED.nickname
     OR hhy.user_profiles.bio IS DISTINCT FROM EXCLUDED.bio;

  INSERT INTO hhy.identity_profiles(user_id,name_cipher,id_no_cipher,id_hash,status,verified_at)
  VALUES (ci_user,'r12-ci-name-cipher','r12-ci-id-cipher',repeat('c',64),'VERIFIED',clock_timestamp())
  ON CONFLICT (user_id) DO UPDATE
    SET status='VERIFIED',verified_at=COALESCE(hhy.identity_profiles.verified_at,EXCLUDED.verified_at),
        version=hhy.identity_profiles.version+1
  WHERE hhy.identity_profiles.status IS DISTINCT FROM 'VERIFIED'
     OR hhy.identity_profiles.verified_at IS NULL;

  SELECT id INTO fixture_plan FROM hhy.membership_plans
  WHERE code='R12_CI_PRO' ORDER BY id LIMIT 1;
  IF fixture_plan IS NULL THEN
    INSERT INTO hhy.membership_plans(code,name,public_badge)
    VALUES ('R12_CI_PRO','专业协作会员','专业会员')
    RETURNING id INTO fixture_plan;
  END IF;
  IF (SELECT count(*) FROM hhy.user_memberships WHERE user_id=ci_user)>1 THEN
    RAISE EXCEPTION 'Dedicated CI user has multiple current memberships';
  END IF;
  IF NOT EXISTS (SELECT 1 FROM hhy.user_memberships WHERE user_id=ci_user) THEN
    INSERT INTO hhy.user_memberships(user_id,plan_id,status,starts_at,ends_at,version)
    VALUES (ci_user,fixture_plan,'ACTIVE',clock_timestamp()-interval '30 days',clock_timestamp()+interval '365 days',0);
  ELSE
    UPDATE hhy.user_memberships
       SET plan_id=fixture_plan,status='ACTIVE',ends_at=clock_timestamp()+interval '365 days',version=version+1
     WHERE user_id=ci_user AND (plan_id<>fixture_plan OR status<>'ACTIVE' OR ends_at<=clock_timestamp());
  END IF;

  INSERT INTO hhy.reward_accounts(user_id,pending,available,frozen,withdrawing,withdrawn,version)
  VALUES (ci_user,3200,128600,1200,0,38600,0)
  ON CONFLICT (user_id) DO UPDATE
    SET pending=EXCLUDED.pending,available=EXCLUDED.available,frozen=EXCLUDED.frozen,
        withdrawing=EXCLUDED.withdrawing,withdrawn=EXCLUDED.withdrawn,
        version=hhy.reward_accounts.version+1
  WHERE (hhy.reward_accounts.pending,hhy.reward_accounts.available,hhy.reward_accounts.frozen,
         hhy.reward_accounts.withdrawing,hhy.reward_accounts.withdrawn)
        IS DISTINCT FROM
        (EXCLUDED.pending,EXCLUDED.available,EXCLUDED.frozen,EXCLUDED.withdrawing,EXCLUDED.withdrawn);

  INSERT INTO hhy.admin_users(username,password_hash,status)
  VALUES ('r12-ci-reviewer',repeat('a',60),'ACTIVE')
  ON CONFLICT (username) DO UPDATE SET status='ACTIVE'
  RETURNING id INTO fixture_admin;

  SELECT id,config_version_id INTO fixture_binding,fixture_config
  FROM hhy.storage_scope_bindings
  WHERE scope_code='public_media' AND status='ACTIVE'
  ORDER BY id LIMIT 1;
  IF fixture_binding IS NULL THEN
    SELECT id INTO fixture_config FROM hhy.provider_config_versions
    WHERE provider_code='storage' AND status='ACTIVE'
    ORDER BY activated_at DESC NULLS LAST,id DESC LIMIT 1;
    IF fixture_config IS NULL THEN
      INSERT INTO hhy.provider_config_versions(
        provider_code,version_no,status,created_by,activated_at,environment,
        values_json,secret_refs_json,remark,connection_successful,masked_test_result,tested_at
      ) VALUES (
        'storage','r12-ci-public-media-v1','ACTIVE','r12-ci-fixture',clock_timestamp(),'STAGING',
        jsonb_build_object(
          'storage.default_provider','CLOUDFLARE_R2',
          'storage.scope.public_media.provider','CLOUDFLARE_R2',
          'storage.r2.endpoint','https://api.orbexa.cc',
          'storage.r2.public_domain','https://download.orbexa.cc/r09-candidate-media/attempt-2/',
          'storage.r2.bucket.public_media','hhy-r12-candidate-media',
          'storage.signed_url.ttl_seconds',120,
          'storage.private_preview.ttl_seconds',120
        ),
        jsonb_build_object(
          'storage.r2.access_key_id','vault://hhy/r12-ci/storage-access-key',
          'storage.r2.secret_access_key','vault://hhy/r12-ci/storage-secret-key'
        ),
        'R12隔离候选公共媒体，复用已验证协作视觉资产',TRUE,
        'R12 staging public media fixture',clock_timestamp()
      ) RETURNING id INTO fixture_config;
    END IF;
    INSERT INTO hhy.storage_scope_bindings(
      scope_code,provider_code,config_version_id,bucket,public_domain,status
    ) VALUES (
      'public_media','CLOUDFLARE_R2',fixture_config,'hhy-r12-candidate-media',
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
      ci_user,'hhy-r12-candidate-media','r11-team-logo.png','image/png',51614,
      'eb2138d67b8a23255240b5ce4ecc1cd758f2bbb8fe3586b874f7d7b32aea6709',
      'PUBLIC','CONTENT_PROJECT','public_media',fixture_binding,'READY'
    ) RETURNING id INTO fixture_media;
  END IF;

  FOR stale IN
    SELECT id,status,version FROM hhy.content_posts
    WHERE owner_id=ci_user AND title='R12候选发布预览项目'
      AND status NOT IN ('DRAFT','DELETED') ORDER BY id FOR UPDATE
  LOOP
    IF stale.status='BANNED' THEN
      RAISE EXCEPTION 'Banned R12 candidate target cannot be recycled: %',stale.id;
    END IF;
    UPDATE hhy.content_posts
       SET status='DELETED',version=version+1,updated_at=clock_timestamp()
     WHERE id=stale.id AND version=stale.version;
    INSERT INTO hhy.content_status_logs(
      content_id,from_status,to_status,reason,operator,transition_version
    ) VALUES (
      stale.id,stale.status,'DELETED','候选轮次结束后回收旧提交目标','r12-ci-fixture',stale.version+1
    );
  END LOOP;

  SELECT count(*) INTO active_target_count FROM hhy.content_posts
  WHERE owner_id=ci_user AND title='R12候选发布预览项目' AND status='DRAFT';
  IF active_target_count>1 THEN RAISE EXCEPTION 'R12 candidate has duplicate active submit targets'; END IF;
  IF active_target_count=0 THEN
    PERFORM pg_temp.r12_ci_create_project(
      ci_user,'R12候选发布预览项目','以真实需求、资源和里程碑推动联合增长项目落地',
      '面向品牌与产品团队提供内容共创、渠道协同和结果复盘的一体化合作方案。',
      '联合增长','上海',clock_timestamp()-interval '1 minute',fixture_media,128,19,7,12
    );
  END IF;

  IF NOT EXISTS (
    SELECT 1 FROM hhy.content_posts
    WHERE owner_id=ci_user AND title='R12候选内容策略草稿' AND status='DRAFT'
  ) THEN
    PERFORM pg_temp.r12_ci_create_project(
      ci_user,'R12候选内容策略草稿','待继续完善的品牌内容共创方案',
      '围绕目标用户、内容节奏和渠道分工继续补充可执行计划。',
      '内容共创','杭州',clock_timestamp()-interval '2 minutes',NULL,36,5,2,3
    );
  END IF;

  SELECT id INTO fixture_content FROM hhy.content_posts
  WHERE owner_id=ci_user AND title='R12候选审核中的品牌合作' AND status<>'DELETED'
  ORDER BY id LIMIT 1;
  IF fixture_content IS NULL THEN
    fixture_content:=pg_temp.r12_ci_create_project(
      ci_user,'R12候选审核中的品牌合作','品牌联合发布与渠道资源协同计划',
      '项目已提交平台审核，合作范围与交付节点均已明确。',
      '品牌合作','北京',clock_timestamp()-interval '1 day',NULL,268,31,14,20
    );
    PERFORM pg_temp.r12_ci_advance_review(
      fixture_content,fixture_admin,'REVIEWING',NULL,clock_timestamp()-interval '1 day','reviewing'
    );
  ELSIF (SELECT status FROM hhy.content_posts WHERE id=fixture_content)<>'REVIEWING' THEN
    RAISE EXCEPTION 'R12 reviewing fixture drifted: %',fixture_content;
  END IF;

  SELECT id INTO fixture_content FROM hhy.content_posts
  WHERE owner_id=ci_user AND title='R12候选未通过的渠道方案' AND status<>'DELETED'
  ORDER BY id LIMIT 1;
  IF fixture_content IS NULL THEN
    fixture_content:=pg_temp.r12_ci_create_project(
      ci_user,'R12候选未通过的渠道方案','渠道合作方案等待补充预算与交付边界',
      '审核意见已记录，完善预算依据和双方责任后可重新提交。',
      '渠道协作','深圳',clock_timestamp()-interval '2 days',NULL,91,8,4,6
    );
    PERFORM pg_temp.r12_ci_advance_review(
      fixture_content,fixture_admin,'REJECTED','请补充预算依据与阶段性交付标准。',
      clock_timestamp()-interval '2 days','rejected'
    );
  ELSIF (SELECT status FROM hhy.content_posts WHERE id=fixture_content)<>'REJECTED' THEN
    RAISE EXCEPTION 'R12 rejected fixture drifted: %',fixture_content;
  END IF;

  SELECT id INTO fixture_content FROM hhy.content_posts
  WHERE owner_id=ci_user AND title='R12候选已上线的联合增长项目' AND status<>'DELETED'
  ORDER BY id LIMIT 1;
  IF fixture_content IS NULL THEN
    fixture_content:=pg_temp.r12_ci_create_project(
      ci_user,'R12候选已上线的联合增长项目','已通过审核并面向平台展示的商业合作项目',
      '项目覆盖策略共创、渠道联动、数据复盘和持续优化。',
      '商业增长','成都',clock_timestamp()-interval '3 days',NULL,1680,126,53,88
    );
    PERFORM pg_temp.r12_ci_advance_review(
      fixture_content,fixture_admin,'ONLINE',NULL,clock_timestamp()-interval '3 days','online'
    );
  ELSIF (SELECT status FROM hhy.content_posts WHERE id=fixture_content)<>'ONLINE' THEN
    RAISE EXCEPTION 'R12 online fixture drifted: %',fixture_content;
  END IF;
END;
$$;

COMMIT;

SELECT 'R12_CI_FIXTURE_OK|user='||u.id||
       '|membership='||count(DISTINCT membership.id)||
       '|reward='||count(DISTINCT reward.id)||
       '|drafts='||count(DISTINCT draft.id)||
       '|reviewing='||count(DISTINCT reviewing.id)||
       '|rejected='||count(DISTINCT rejected.id)||
       '|online='||count(DISTINCT online.id)||
       '|target='||target.id||
       '|target_media='||count(DISTINCT media.id)||
       '|target_snapshot='||count(DISTINCT snapshot.id)
FROM hhy.users u
JOIN hhy.identity_profiles identity ON identity.user_id=u.id AND identity.status='VERIFIED'
JOIN hhy.user_memberships membership ON membership.user_id=u.id AND membership.status='ACTIVE'
JOIN hhy.reward_accounts reward ON reward.user_id=u.id
JOIN hhy.content_posts target ON target.owner_id=u.id
  AND target.title='R12候选发布预览项目' AND target.status='DRAFT'
JOIN hhy.project_details detail ON detail.content_id=target.id
JOIN hhy.content_stats stats ON stats.content_id=target.id
JOIN hhy.content_versions snapshot ON snapshot.content_id=target.id AND snapshot.version_no='0'
JOIN hhy.content_media link ON link.content_id=target.id AND link.removed_at IS NULL
JOIN hhy.media_objects media ON media.id=link.media_id AND media.status='READY'
JOIN hhy.content_posts draft ON draft.owner_id=u.id AND draft.status='DRAFT'
JOIN hhy.content_posts reviewing ON reviewing.owner_id=u.id
  AND reviewing.title='R12候选审核中的品牌合作' AND reviewing.status='REVIEWING'
JOIN hhy.content_posts rejected ON rejected.owner_id=u.id
  AND rejected.title='R12候选未通过的渠道方案' AND rejected.status='REJECTED'
JOIN hhy.content_posts online ON online.owner_id=u.id
  AND online.title='R12候选已上线的联合增长项目' AND online.status='ONLINE'
WHERE u.phone=:'ci_phone' AND u.status='ACTIVE'
  AND reward.pending=3200 AND reward.available=128600 AND reward.frozen=1200
  AND detail.region='上海' AND stats.organic_views='128'
GROUP BY u.id,target.id
HAVING count(DISTINCT membership.id)=1
   AND count(DISTINCT reward.id)=1
   AND count(DISTINCT draft.id)>=2
   AND count(DISTINCT reviewing.id)=1
   AND count(DISTINCT rejected.id)=1
   AND count(DISTINCT online.id)=1
   AND count(DISTINCT media.id)=1
   AND count(DISTINCT snapshot.id)=1;
SQL
