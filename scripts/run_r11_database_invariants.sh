#!/usr/bin/env bash
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
: "${DATABASE_URL:?DATABASE_URL is required}"
: "${HHY_DB_SMOKE_CONFIRM:?Set HHY_DB_SMOKE_CONFIRM=YES for a disposable database}"
[[ "${HHY_DB_SMOKE_CONFIRM}" == "YES" ]] || exit 2
PSQL=(psql "${DATABASE_URL}" -X -v ON_ERROR_STOP=1)

"${PSQL[@]}" -f "${ROOT}/database/tests/r11_team_leader_invariants.sql" >/dev/null
echo "R11_TEAM_LEADER_INVARIANTS PASS"

"${PSQL[@]}" -c "
  INSERT INTO hhy.users(phone,status,invite_code) VALUES ('13900001103','ACTIVE','R11CONCURRENT');
  INSERT INTO hhy.media_objects(owner_id,bucket,object_key,mime,size,sha256,visibility)
    SELECT id,'r11-concurrent','leaders/logo.png','image/png',64,repeat('f',64),'PUBLIC'
    FROM hhy.users WHERE invite_code='R11CONCURRENT';
  INSERT INTO hhy.content_posts(owner_id,type,title,status)
    SELECT id,'TEAM_LEADER','R11 concurrent profile','DRAFT'
    FROM hhy.users WHERE invite_code='R11CONCURRENT';
  INSERT INTO hhy.team_leader_details(content_id,team_name,nickname,logo_media_id,region,personal_intro,
    team_intro,size_range,skills,cooperation_types,cooperation_requirement,past_cases,accept_private_chat)
    SELECT content.id,'并发团队','并发队长',media.id,'CN-31','个人介绍','团队介绍','10-20人','社群推广',
      '项目推广','合作要求','[\"案例\"]'::jsonb,true
    FROM hhy.content_posts content
    JOIN hhy.users actor ON actor.id=content.owner_id AND actor.invite_code='R11CONCURRENT'
    JOIN hhy.media_objects media ON media.owner_id=actor.id AND media.object_key='leaders/logo.png';
  INSERT INTO hhy.content_contacts(content_id,channel,value_cipher,display_mask,sort_order)
    SELECT id,'WECHAT','hhy-contact-v1:r11-concurrent','微信***',0
    FROM hhy.content_posts WHERE title='R11 concurrent profile';
  UPDATE hhy.content_posts SET status='ONLINE' WHERE title='R11 concurrent profile';" >/dev/null

profile_id="$("${PSQL[@]}" -qAt -c "SELECT id FROM hhy.content_posts WHERE title='R11 concurrent profile'")"
a_log="$(mktemp)"; b_log="$(mktemp)"
set +e
"${PSQL[@]}" -c "BEGIN; SET CONSTRAINTS ALL DEFERRED; DELETE FROM hhy.content_contacts WHERE content_id=${profile_id}; SELECT pg_sleep(2); COMMIT;" >"${a_log}" 2>&1 &
a_pid=$!; sleep 0.4
"${PSQL[@]}" -c "BEGIN; SET lock_timeout='10s'; SET CONSTRAINTS ALL DEFERRED; UPDATE hhy.content_posts SET status='OFFLINE_BY_OWNER' WHERE id=${profile_id}; COMMIT;" >"${b_log}" 2>&1 &
b_pid=$!; wait "${a_pid}"; a_rc=$?; wait "${b_pid}"; b_rc=$?
set -e
if [[ "${a_rc}" -eq 0 && "${b_rc}" -eq 0 ]]; then
  cat "${a_log}" "${b_log}" >&2; exit 1
fi
grep -q 'R11_ONLINE_TEAM_LEADER_CONTACT_REQUIRED' "${a_log}" "${b_log}" || {
  cat "${a_log}" "${b_log}" >&2; exit 1;
}
rm -f "${a_log}" "${b_log}"
echo "R11_PARENT_LOCK_WRITE_SKEW PASS A=${a_rc} B=${b_rc}"

"${PSQL[@]}" -c "UPDATE hhy.content_posts SET status='DELETED' WHERE id=${profile_id};
  UPDATE hhy.team_leader_details SET nickname=NULL,logo_media_id=NULL,region=NULL,personal_intro=NULL,team_intro=NULL,
    cooperation_requirement=NULL,past_cases=NULL,accept_private_chat=NULL WHERE content_id=${profile_id};" >/dev/null
"${PSQL[@]}" --single-transaction -f "${ROOT}/database/rollback/U038__r11_team_leader_invariants.sql" >/dev/null
[[ "$("${PSQL[@]}" -qAt -c "SELECT count(*) FROM information_schema.columns WHERE table_schema='hhy' AND table_name='team_leader_details' AND column_name='nickname'")" == "0" ]]
"${PSQL[@]}" --single-transaction -f "${ROOT}/database/migrations/V038__r11_team_leader_invariants.sql" >/dev/null
"${PSQL[@]}" -f "${ROOT}/database/tests/r11_team_leader_invariants.sql" >/dev/null
echo "R11_U038_ROLLBACK_V038_REPLAY PASS"

"${PSQL[@]}" -c "UPDATE hhy.team_leader_details SET region='CN-31' WHERE content_id=${profile_id};" >/dev/null
rollback_block_log="$(mktemp)"
set +e
"${PSQL[@]}" --single-transaction -f "${ROOT}/database/rollback/U038__r11_team_leader_invariants.sql" \
  >"${rollback_block_log}" 2>&1
rollback_block_rc=$?
set -e
if [[ "${rollback_block_rc}" -eq 0 ]] \
  || ! grep -q 'R11_TEAM_LEADER_ROLLBACK_BLOCKED_NEW_VALUES_EXIST' "${rollback_block_log}"; then
  cat "${rollback_block_log}" >&2
  rm -f "${rollback_block_log}"
  exit 1
fi
rm -f "${rollback_block_log}"
[[ "$("${PSQL[@]}" -qAt -c "SELECT region FROM hhy.team_leader_details WHERE content_id=${profile_id}")" == "CN-31" ]]
"${PSQL[@]}" -c "UPDATE hhy.team_leader_details SET region=NULL WHERE content_id=${profile_id};" >/dev/null
echo "R11_U038_POPULATED_ROLLBACK_BLOCKED PASS"

"${PSQL[@]}" --single-transaction -f "${ROOT}/database/rollback/U038__r11_team_leader_invariants.sql" >/dev/null
legacy_profile_id="$("${PSQL[@]}" -qAt -c "
  WITH actor AS (
    INSERT INTO hhy.users(phone,status,invite_code)
    VALUES ('13900001104','ACTIVE','R11LEGACYDIRTY') RETURNING id
  ), content AS (
    INSERT INTO hhy.content_posts(owner_id,type,title,status)
    SELECT id,'TEAM_LEADER','R11 legacy incomplete review','PENDING_REVIEW' FROM actor RETURNING id
  )
  INSERT INTO hhy.team_leader_details(content_id,team_name)
  SELECT id,'历史待审团队' FROM content RETURNING content_id;")"
upgrade_block_log="$(mktemp)"
set +e
"${PSQL[@]}" --single-transaction -f "${ROOT}/database/migrations/V038__r11_team_leader_invariants.sql" \
  >"${upgrade_block_log}" 2>&1
upgrade_block_rc=$?
set -e
if [[ "${upgrade_block_rc}" -eq 0 ]] \
  || ! grep -q 'R11_LEGACY_REVIEWABLE_TEAM_LEADER_INCOMPLETE_REQUIRES_REVIEW' "${upgrade_block_log}"; then
  cat "${upgrade_block_log}" >&2
  rm -f "${upgrade_block_log}"
  exit 1
fi
rm -f "${upgrade_block_log}"
[[ "$("${PSQL[@]}" -qAt -c "SELECT count(*) FROM information_schema.columns
  WHERE table_schema='hhy' AND table_name='team_leader_details' AND column_name='region'")" == "0" ]]
"${PSQL[@]}" -c "UPDATE hhy.content_posts SET status='DELETED' WHERE id=${legacy_profile_id};" >/dev/null
"${PSQL[@]}" --single-transaction -f "${ROOT}/database/migrations/V038__r11_team_leader_invariants.sql" >/dev/null
"${PSQL[@]}" -f "${ROOT}/database/tests/r11_team_leader_invariants.sql" >/dev/null
echo "R11_DIRTY_UPGRADE_BLOCKED_ATOMICALLY PASS"
