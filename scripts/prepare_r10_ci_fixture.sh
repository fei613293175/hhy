#!/usr/bin/env bash
set -euo pipefail

if [[ "${HHY_R10_CI_FIXTURE_CONFIRM:-}" != "YES" ]]; then
  echo "Set HHY_R10_CI_FIXTURE_CONFIRM=YES for the dedicated R10 staging CI fixture" >&2
  exit 2
fi

backend=${HHY_R10_CI_BACKEND_CONTAINER:?HHY_R10_CI_BACKEND_CONTAINER is required}
postgres=${HHY_R10_CI_POSTGRES_CONTAINER:?HHY_R10_CI_POSTGRES_CONTAINER is required}
case "$backend" in hhy-r10-ci-candidate-*) ;; *) echo "Unsafe R10 backend container name" >&2; exit 2 ;; esac
case "$postgres" in hhy-*-postgres-1) ;; *) echo "Unsafe staging PostgreSQL container name" >&2; exit 2 ;; esac

docker inspect "$backend" "$postgres" >/dev/null
[[ "$(docker exec "$backend" printenv SPRING_PROFILES_ACTIVE)" == "staging" ]]
[[ "$(docker exec "$backend" printenv HHY_CI_AUTOMATION_ENABLED)" == "true" ]]
phone=$(docker exec "$backend" printenv HHY_CI_AUTOMATION_USER_PHONE)
[[ "$phone" =~ ^[0-9]{6,32}$ ]]

docker exec -i "$postgres" sh -lc \
  'exec psql -U "$POSTGRES_USER" -d "$POSTGRES_DB" -v ON_ERROR_STOP=1 -v ci_phone="$1"' sh "$phone" <<'SQL'
BEGIN;
SELECT pg_advisory_xact_lock(709010);

CREATE TEMP TABLE hhy_r10_ci_fixture_context ON COMMIT DROP AS
SELECT id AS user_id
FROM hhy.users
WHERE phone=:'ci_phone' AND status='ACTIVE';

DO $$
DECLARE
  ci_user bigint;
  fixture_content bigint;
BEGIN
  IF NOT EXISTS (SELECT 1 FROM hhy.flyway_schema_history WHERE version='036' AND success) THEN
    RAISE EXCEPTION 'R10 fixture requires Flyway V036';
  END IF;

  SELECT user_id INTO ci_user FROM hhy_r10_ci_fixture_context;
  IF ci_user IS NULL THEN RAISE EXCEPTION 'Dedicated CI user is missing or inactive'; END IF;

  INSERT INTO hhy.user_profiles(user_id,nickname,bio)
  VALUES (ci_user,'R10候选群主','仅用于隔离预发布群聊候选验证')
  ON CONFLICT (user_id) DO UPDATE
    SET nickname=EXCLUDED.nickname,bio=EXCLUDED.bio,version=hhy.user_profiles.version+1;

  INSERT INTO hhy.identity_profiles(user_id,name_cipher,id_no_cipher,id_hash,status,verified_at)
  VALUES (ci_user,'r10-ci-name-cipher','r10-ci-id-cipher',repeat('a',64),'VERIFIED',clock_timestamp())
  ON CONFLICT (user_id) DO UPDATE
    SET name_cipher=EXCLUDED.name_cipher,id_no_cipher=EXCLUDED.id_no_cipher,
        id_hash=EXCLUDED.id_hash,status='VERIFIED',verified_at=EXCLUDED.verified_at,
        version=hhy.identity_profiles.version+1;

  SELECT id INTO fixture_content FROM hhy.content_posts
   WHERE owner_id=ci_user AND title='R10候选产品交流圈'
   ORDER BY id LIMIT 1 FOR UPDATE;
  IF fixture_content IS NULL THEN
    INSERT INTO hhy.content_posts(owner_id,type,title,summary,status,review_status,refresh_times,version)
    VALUES (ci_user,'GROUP','R10候选产品交流圈','面向产品从业者的真实交流与经验分享','DRAFT','APPROVED',0,0)
    RETURNING id INTO fixture_content;
  ELSE
    UPDATE hhy.content_posts
       SET type='GROUP',summary='面向产品从业者的真实交流与经验分享',
           status='DRAFT',review_status='APPROVED',refresh_times=0
     WHERE id=fixture_content;
  END IF;

  INSERT INTO hhy.group_details(content_id,platform,size_range,join_requirement,group_link,group_no)
  VALUES (fixture_content,'WECHAT','100-200人','实名用户可申请加入','https://h5.orbexa.cc/groups/r10-candidate','R10-2026')
  ON CONFLICT (content_id) DO UPDATE
    SET platform=EXCLUDED.platform,size_range=EXCLUDED.size_range,
        join_requirement=EXCLUDED.join_requirement,qr_media_id=NULL,
        group_link=EXCLUDED.group_link,group_no=EXCLUDED.group_no,
        updated_at=clock_timestamp();

  INSERT INTO hhy.content_versions(content_id,version_no,snapshot_json,created_by)
  VALUES (fixture_content,'0',
    '{"description":"用于验证群聊列表、详情和所有者编辑闭环的真实候选资料。","categoryCode":"行业交流","regionCode":"全国","platform":"WECHAT","sizeRange":"100-200人","joinRequirement":"实名用户可申请加入","groupLink":"https://h5.orbexa.cc/groups/r10-candidate","groupNo":"R10-2026"}'::jsonb,
    'r10-ci-fixture')
  ON CONFLICT (content_id,version_no) DO NOTHING;

  INSERT INTO hhy.content_stats(content_id,organic_views,redpacket_views,task_views,favorites,chats,contacts)
  VALUES (fixture_content,'0','0','0','0','0','0')
  ON CONFLICT (content_id) DO UPDATE
    SET organic_views='0',redpacket_views='0',task_views='0',favorites='0',
        chats='0',contacts='0',updated_at=clock_timestamp();

  DELETE FROM hhy.content_media WHERE content_id=fixture_content;
  DELETE FROM hhy.content_contacts WHERE content_id=fixture_content;
  INSERT INTO hhy.content_contacts(content_id,channel,value_cipher,display_mask,sort_order)
  VALUES
    (fixture_content,'WECHAT','hhy-contact-v1:r10-ci-owner-contact','微信号 R10***',0),
    (fixture_content,'JOIN_PASSWORD','hhy-contact-v1:r10-ci-join-password','口令***',1);

  UPDATE hhy.content_posts
     SET status='ONLINE',review_status='APPROVED',updated_at=clock_timestamp()
   WHERE id=fixture_content;
END;
$$;

COMMIT;

SELECT 'R10_CI_FIXTURE_OK|user='||u.id||'|content='||p.id||'|contacts='||count(c.id)
FROM hhy.users u
JOIN hhy.content_posts p ON p.owner_id=u.id
JOIN hhy.group_details g ON g.content_id=p.id
JOIN hhy.content_contacts c ON c.content_id=p.id
WHERE u.phone=:'ci_phone' AND p.title='R10候选产品交流圈'
  AND p.type='GROUP' AND p.status='ONLINE' AND p.review_status='APPROVED'
  AND g.platform='WECHAT'
GROUP BY u.id,p.id
HAVING count(c.id)=2 AND bool_or(c.channel='WECHAT') AND bool_or(c.channel='JOIN_PASSWORD');
SQL
