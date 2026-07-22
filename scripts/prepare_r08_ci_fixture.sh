#!/usr/bin/env bash
set -euo pipefail

if [[ "${HHY_R08_CI_FIXTURE_CONFIRM:-}" != "YES" ]]; then
  echo "Set HHY_R08_CI_FIXTURE_CONFIRM=YES for the dedicated R08 staging CI fixture" >&2
  exit 2
fi

backend=${HHY_R08_CI_BACKEND_CONTAINER:?HHY_R08_CI_BACKEND_CONTAINER is required}
postgres=${HHY_R08_CI_POSTGRES_CONTAINER:?HHY_R08_CI_POSTGRES_CONTAINER is required}
case "$backend" in hhy-r08-ci-candidate-*) ;; *) echo "Unsafe R08 backend container name" >&2; exit 2 ;; esac
case "$postgres" in hhy-*-postgres-1) ;; *) echo "Unsafe staging PostgreSQL container name" >&2; exit 2 ;; esac

docker inspect "$backend" "$postgres" >/dev/null
[[ "$(docker exec "$backend" printenv SPRING_PROFILES_ACTIVE)" == "staging" ]]
[[ "$(docker exec "$backend" printenv HHY_CI_AUTOMATION_ENABLED)" == "true" ]]
phone=$(docker exec "$backend" printenv HHY_CI_AUTOMATION_USER_PHONE)
[[ "$phone" =~ ^[0-9]{6,32}$ ]]

docker exec -i "$postgres" sh -lc \
  'exec psql -U "$POSTGRES_USER" -d "$POSTGRES_DB" -v ON_ERROR_STOP=1 -v ci_phone="$1"' sh "$phone" <<'SQL'
BEGIN;
SELECT pg_advisory_xact_lock(708008);

CREATE TEMP TABLE hhy_r08_ci_fixture_context ON COMMIT DROP AS
SELECT id AS user_id
FROM hhy.users
WHERE phone=:'ci_phone' AND status='ACTIVE';

DO $$
DECLARE
  ci_user bigint;
  fixture_content bigint;
BEGIN
  IF NOT EXISTS (SELECT 1 FROM hhy.flyway_schema_history WHERE version='034' AND success) THEN
    RAISE EXCEPTION 'R08 fixture requires Flyway V034';
  END IF;

  SELECT user_id INTO ci_user FROM hhy_r08_ci_fixture_context;
  IF ci_user IS NULL THEN RAISE EXCEPTION 'Dedicated CI user is missing or inactive'; END IF;

  INSERT INTO hhy.user_profiles(user_id,nickname,bio)
  VALUES (ci_user,'R08候选发布者','仅用于隔离预发布项目候选验证')
  ON CONFLICT (user_id) DO UPDATE
    SET nickname=EXCLUDED.nickname,bio=EXCLUDED.bio,version=hhy.user_profiles.version+1;

  INSERT INTO hhy.identity_profiles(user_id,name_cipher,id_no_cipher,id_hash,status,verified_at)
  VALUES (ci_user,'r08-ci-name-cipher','r08-ci-id-cipher',repeat('8',64),'VERIFIED',clock_timestamp())
  ON CONFLICT (user_id) DO UPDATE
    SET name_cipher=EXCLUDED.name_cipher,id_no_cipher=EXCLUDED.id_no_cipher,
        id_hash=EXCLUDED.id_hash,status='VERIFIED',verified_at=EXCLUDED.verified_at,
        version=hhy.identity_profiles.version+1;

  SELECT id INTO fixture_content FROM hhy.content_posts
   WHERE owner_id=ci_user AND title='R08候选联调项目'
   ORDER BY id LIMIT 1 FOR UPDATE;
  IF fixture_content IS NULL THEN
    INSERT INTO hhy.content_posts(owner_id,type,title,summary,status,review_status)
    VALUES (ci_user,'PROJECT','R08候选联调项目','真实项目列表、详情与编辑候选夹具','ONLINE','APPROVED')
    RETURNING id INTO fixture_content;
  ELSE
    UPDATE hhy.content_posts
       SET type='PROJECT',summary='真实项目列表、详情与编辑候选夹具',
           status='ONLINE',review_status='APPROVED',version=version+1
     WHERE id=fixture_content;
  END IF;

  INSERT INTO hhy.project_details(content_id,cooperation,conditions,region,website)
  VALUES (fixture_content,'联合推广与资源协作','仅使用真实可验证资料','CN-11',NULL)
  ON CONFLICT (content_id) DO UPDATE
    SET cooperation=EXCLUDED.cooperation,conditions=EXCLUDED.conditions,
        region=EXCLUDED.region,website=NULL,updated_at=clock_timestamp();

  INSERT INTO hhy.content_versions(content_id,version_no,snapshot_json,created_by)
  VALUES (fixture_content,'0',
    '{"description":"用于验证项目列表、详情和所有者编辑闭环的真实候选数据。","categoryCode":"COOPERATION","regionCode":"CN-11"}'::jsonb,
    'r08-ci-fixture')
  ON CONFLICT (content_id,version_no) DO NOTHING;

  INSERT INTO hhy.content_stats(content_id,organic_views,favorites,chats,contacts)
  VALUES (fixture_content,'128','12','3','8')
  ON CONFLICT (content_id) DO UPDATE
    SET organic_views='128',favorites='12',chats='3',contacts='8',updated_at=clock_timestamp();

  IF EXISTS (SELECT 1 FROM hhy.content_contacts WHERE content_id=fixture_content AND upper(channel)='EMAIL') THEN
    UPDATE hhy.content_contacts
       SET value_cipher='hhy-contact-v1.fixture-not-readable',display_mask='候***箱',sort_order=1
     WHERE id=(SELECT id FROM hhy.content_contacts
                WHERE content_id=fixture_content AND upper(channel)='EMAIL'
                ORDER BY id LIMIT 1);
  ELSE
    INSERT INTO hhy.content_contacts(content_id,channel,value_cipher,display_mask,sort_order)
    VALUES (fixture_content,'EMAIL','hhy-contact-v1.fixture-not-readable','候***箱',1);
  END IF;
END;
$$;

COMMIT;

SELECT 'R08_CI_FIXTURE_OK|user='||u.id||'|content='||p.id
FROM hhy.users u JOIN hhy.content_posts p ON p.owner_id=u.id
WHERE u.phone=:'ci_phone' AND p.title='R08候选联调项目'
ORDER BY p.id LIMIT 1;
SQL
