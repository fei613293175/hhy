#!/usr/bin/env bash
set -euo pipefail

if [[ "${HHY_R07_CI_FIXTURE_CONFIRM:-}" != "YES" ]]; then
  echo "Set HHY_R07_CI_FIXTURE_CONFIRM=YES for the dedicated R07 staging CI fixture" >&2
  exit 2
fi

backend=${HHY_R07_CI_BACKEND_CONTAINER:?HHY_R07_CI_BACKEND_CONTAINER is required}
postgres=${HHY_R07_CI_POSTGRES_CONTAINER:?HHY_R07_CI_POSTGRES_CONTAINER is required}
case "$backend" in hhy-r07-ci-candidate-*) ;; *) echo "Unsafe R07 backend container name" >&2; exit 2 ;; esac
case "$postgres" in hhy-*-postgres-1) ;; *) echo "Unsafe staging PostgreSQL container name" >&2; exit 2 ;; esac

docker inspect "$backend" "$postgres" >/dev/null
[[ "$(docker exec "$backend" printenv SPRING_PROFILES_ACTIVE)" == "staging" ]]
[[ "$(docker exec "$backend" printenv HHY_CI_AUTOMATION_ENABLED)" == "true" ]]
phone=$(docker exec "$backend" printenv HHY_CI_AUTOMATION_USER_PHONE)
[[ "$phone" =~ ^[0-9]{6,32}$ ]]

docker exec -i "$postgres" sh -lc \
  'exec psql -U "$POSTGRES_USER" -d "$POSTGRES_DB" -v ON_ERROR_STOP=1 -v ci_phone="$1"' sh "$phone" <<'SQL'
BEGIN;
SELECT pg_advisory_xact_lock(707007);

DO $$
DECLARE
  ci_user bigint;
  fixture_content bigint;
BEGIN
  IF NOT EXISTS (SELECT 1 FROM flyway_schema_history WHERE version='032' AND success) THEN
    RAISE EXCEPTION 'R07 fixture requires Flyway V032';
  END IF;

  SELECT id INTO ci_user FROM hhy.users WHERE phone=:'ci_phone' AND status='ACTIVE';
  IF ci_user IS NULL THEN RAISE EXCEPTION 'Dedicated CI user is missing or inactive'; END IF;

  INSERT INTO hhy.user_profiles(user_id,nickname,bio)
  VALUES (ci_user,'R07候选发布者','仅用于隔离预发布自动化验证')
  ON CONFLICT (user_id) DO UPDATE
    SET nickname=EXCLUDED.nickname,bio=EXCLUDED.bio,version=hhy.user_profiles.version+1;

  SELECT id INTO fixture_content FROM hhy.content_posts
   WHERE owner_id=ci_user AND title='R07候选联调项目'
   ORDER BY id LIMIT 1 FOR UPDATE;
  IF fixture_content IS NULL THEN
    INSERT INTO hhy.content_posts(owner_id,type,title,summary,status,review_status)
    VALUES (ci_user,'PROJECT','R07候选联调项目','搜索、发布者主页与联系方式保护自动化夹具','ONLINE','APPROVED')
    RETURNING id INTO fixture_content;
  ELSE
    UPDATE hhy.content_posts
       SET type='PROJECT',summary='搜索、发布者主页与联系方式保护自动化夹具',
           status='ONLINE',review_status='APPROVED',version=version+1
     WHERE id=fixture_content;
  END IF;

  IF EXISTS (SELECT 1 FROM hhy.content_contacts WHERE content_id=fixture_content AND upper(channel)='WECHAT') THEN
    UPDATE hhy.content_contacts
       SET value_cipher='hhy-contact-v1.fixture-not-readable',display_mask='微***测',sort_order=1
     WHERE id=(SELECT id FROM hhy.content_contacts
                WHERE content_id=fixture_content AND upper(channel)='WECHAT'
                ORDER BY id LIMIT 1);
  ELSE
    INSERT INTO hhy.content_contacts(content_id,channel,value_cipher,display_mask,sort_order)
    VALUES (fixture_content,'WECHAT','hhy-contact-v1.fixture-not-readable','微***测',1);
  END IF;

  INSERT INTO hhy.hot_search_terms(keyword,weight,enabled,starts_at,ends_at)
  VALUES ('R07热门合作',7007,true,clock_timestamp()-interval '1 day',clock_timestamp()+interval '30 days')
  ON CONFLICT ((lower(btrim(keyword)))) DO UPDATE
    SET weight=EXCLUDED.weight,enabled=true,starts_at=EXCLUDED.starts_at,ends_at=EXCLUDED.ends_at;

  DELETE FROM hhy.search_histories WHERE user_id=ci_user;
END;
$$;

COMMIT;

SELECT 'R07_CI_FIXTURE_OK|user='||u.id||'|content='||p.id
FROM hhy.users u JOIN hhy.content_posts p ON p.owner_id=u.id
WHERE u.phone=:'ci_phone' AND p.title='R07候选联调项目'
ORDER BY p.id LIMIT 1;
SQL
