#!/usr/bin/env bash
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
: "${DATABASE_URL:?DATABASE_URL is required}"
: "${HHY_DB_SMOKE_CONFIRM:?Set HHY_DB_SMOKE_CONFIRM=YES for a disposable database}"
[[ "${HHY_DB_SMOKE_CONFIRM}" == "YES" ]] || {
  echo "Refusing destructive R10 database tests without HHY_DB_SMOKE_CONFIRM=YES" >&2
  exit 2
}
command -v psql >/dev/null 2>&1 || { echo "psql is required" >&2; exit 2; }

PSQL=(psql "${DATABASE_URL}" -X -v ON_ERROR_STOP=1)

object_counts() {
  "${PSQL[@]}" -qAt -c "
    SELECT
      (SELECT count(*) FROM pg_constraint
        WHERE connamespace='hhy'::regnamespace AND conname LIKE '%r10%'),
      (SELECT count(*) FROM pg_indexes
        WHERE schemaname='hhy' AND indexname LIKE '%r10%'),
      (SELECT count(*) FROM pg_trigger
        WHERE tgname LIKE 'trg_r10_%' AND NOT tgisinternal),
      (SELECT count(*) FROM pg_proc function
        JOIN pg_namespace namespace ON namespace.oid=function.pronamespace
        WHERE namespace.nspname='hhy' AND function.proname LIKE '%r10%');"
}

assert_nonzero_object_counts() {
  local counts="$1"
  local constraints indexes triggers functions
  IFS='|' read -r constraints indexes triggers functions <<<"${counts}"
  [[ "${constraints}" -gt 0 && "${indexes}" -gt 0 && "${triggers}" -gt 0 && "${functions}" -gt 0 ]] || {
    echo "R10 object inventory is incomplete: ${counts}" >&2
    exit 1
  }
}

"${PSQL[@]}" -f "${ROOT}/database/tests/r10_group_promotion_invariants.sql" >/dev/null
echo "R10_GROUP_PROMOTION_INVARIANTS PASS"

"${PSQL[@]}" -c "
  INSERT INTO hhy.users(phone,status,invite_code)
  VALUES ('13900001005','ACTIVE','R10CONCURRENT');
  INSERT INTO hhy.content_posts(owner_id,type,title,status)
    SELECT id,'GROUP','R10 concurrent group','DRAFT'
    FROM hhy.users WHERE invite_code='R10CONCURRENT';
  INSERT INTO hhy.group_details(content_id,platform,group_link)
    SELECT id,'WECHAT','https://group.example.invalid/concurrent'
    FROM hhy.content_posts WHERE title='R10 concurrent group';
  INSERT INTO hhy.content_contacts(content_id,channel,value_cipher,display_mask,sort_order)
    SELECT id,'JOIN_PASSWORD','hhy-contact-v1:r10-concurrent-password','口令***',0
    FROM hhy.content_posts WHERE title='R10 concurrent group';
  INSERT INTO hhy.content_contacts(content_id,channel,value_cipher,display_mask,sort_order)
    SELECT id,'WECHAT','hhy-contact-v1:r10-concurrent-wechat','群主微信***',1
    FROM hhy.content_posts WHERE title='R10 concurrent group';
  INSERT INTO hhy.content_contacts(content_id,channel,value_cipher,display_mask,sort_order)
    SELECT id,'QQ','hhy-contact-v1:r10-concurrent-qq','群主QQ***',2
    FROM hhy.content_posts WHERE title='R10 concurrent group';
  UPDATE hhy.content_posts SET status='ONLINE'
    WHERE title='R10 concurrent group';" >/dev/null

concurrent_id="$("${PSQL[@]}" -qAt -c "
  SELECT id FROM hhy.content_posts WHERE title='R10 concurrent group';")"
concurrent_a_log="$(mktemp)"
concurrent_b_log="$(mktemp)"
set +e
"${PSQL[@]}" -c "
  BEGIN;
  SET CONSTRAINTS ALL DEFERRED;
  UPDATE hhy.group_details SET group_link=NULL WHERE content_id=${concurrent_id};
  DELETE FROM hhy.content_contacts
    WHERE content_id=${concurrent_id} AND channel='WECHAT';
  SELECT pg_sleep(2);
  COMMIT;" >"${concurrent_a_log}" 2>&1 &
concurrent_a_pid=$!
sleep 0.4
"${PSQL[@]}" -c "
  BEGIN;
  SET lock_timeout='10s';
  SET CONSTRAINTS ALL DEFERRED;
  DELETE FROM hhy.content_contacts
    WHERE content_id=${concurrent_id} AND channel IN ('JOIN_PASSWORD','QQ');
  COMMIT;" >"${concurrent_b_log}" 2>&1 &
concurrent_b_pid=$!
wait "${concurrent_a_pid}"
concurrent_a_rc=$?
wait "${concurrent_b_pid}"
concurrent_b_rc=$?
set -e

if [[ ("${concurrent_a_rc}" -eq 0 && "${concurrent_b_rc}" -eq 0) \
   || ("${concurrent_a_rc}" -ne 0 && "${concurrent_b_rc}" -ne 0) ]]; then
  cat "${concurrent_a_log}" "${concurrent_b_log}" >&2
  rm -f "${concurrent_a_log}" "${concurrent_b_log}"
  echo "R10 concurrent write-skew test expected exactly one transaction to commit: A=${concurrent_a_rc} B=${concurrent_b_rc}" >&2
  exit 1
fi
if ! grep -Eq 'R10_ONLINE_GROUP_(ENTRY_CHANNEL|OWNER_CONTACT)_REQUIRED' \
    "${concurrent_a_log}" "${concurrent_b_log}"; then
  cat "${concurrent_a_log}" "${concurrent_b_log}" >&2
  rm -f "${concurrent_a_log}" "${concurrent_b_log}"
  echo "R10 concurrent loser did not fail on the final-state invariant" >&2
  exit 1
fi
concurrent_final="$("${PSQL[@]}" -qAt -c "
  SELECT
    (detail.group_link IS NOT NULL OR EXISTS (
      SELECT 1 FROM hhy.content_contacts contact
      WHERE contact.content_id=content.id AND upper(btrim(contact.channel))='JOIN_PASSWORD'
    )),
    EXISTS (
      SELECT 1 FROM hhy.content_contacts contact
      WHERE contact.content_id=content.id AND upper(btrim(contact.channel))<>'JOIN_PASSWORD'
    )
  FROM hhy.content_posts content
  JOIN hhy.group_details detail ON detail.content_id=content.id
  WHERE content.id=${concurrent_id} AND content.status='ONLINE';")"
rm -f "${concurrent_a_log}" "${concurrent_b_log}"
[[ "${concurrent_final}" == "t|t" ]] || {
  echo "R10 concurrent write-skew left an invalid ONLINE group: ${concurrent_final}" >&2
  exit 1
}
echo "R10_PARENT_LOCK_WRITE_SKEW PASS A=${concurrent_a_rc} B=${concurrent_b_rc} final=${concurrent_final}"

before_objects="$(object_counts)"
assert_nonzero_object_counts "${before_objects}"

"${PSQL[@]}" -c "
  INSERT INTO hhy.users(phone,status,invite_code)
  VALUES ('13900001002','ACTIVE','R10ROLLBACK');
  INSERT INTO hhy.media_objects(owner_id,bucket,object_key,mime,size,sha256,visibility)
    SELECT id,'r10-rollback','groups/rollback-qr.png','image/png',64,repeat('c',64),'PRIVATE'
    FROM hhy.users WHERE invite_code='R10ROLLBACK';
  INSERT INTO hhy.content_posts(owner_id,type,title,status)
    SELECT id,'GROUP','R10 rollback group','DRAFT'
    FROM hhy.users WHERE invite_code='R10ROLLBACK';
  INSERT INTO hhy.group_details(content_id,platform,size_range,join_requirement,qr_media_id)
    SELECT content.id,'WECHAT','100-200','备注来源后申请',media.id
    FROM hhy.content_posts content
    JOIN hhy.users actor ON actor.id=content.owner_id AND actor.invite_code='R10ROLLBACK'
    JOIN hhy.media_objects media ON media.owner_id=actor.id AND media.object_key='groups/rollback-qr.png'
    WHERE content.title='R10 rollback group';
  INSERT INTO hhy.content_contacts(content_id,channel,value_cipher,display_mask,sort_order)
    SELECT id,'WECHAT','hhy-contact-v1:r10-rollback','群主***',0
    FROM hhy.content_posts WHERE title='R10 rollback group';
  INSERT INTO hhy.idempotency_records(scope,idem_key,request_hash,response_ref)
    SELECT 'R10_GROUP_CREATE:' || id,'r10-rollback',repeat('d',64),'r10-rollback-response'
    FROM hhy.users WHERE invite_code='R10ROLLBACK';
  UPDATE hhy.content_posts SET status='ONLINE'
    WHERE title='R10 rollback group';" >/dev/null

before_data="$("${PSQL[@]}" -qAt -c "
  SELECT content.id,content.status,detail.platform,detail.size_range,detail.join_requirement,
         detail.qr_media_id,media.object_key,contact.channel,contact.value_cipher,
         (SELECT count(*) FROM hhy.idempotency_records idem
          WHERE idem.scope='R10_GROUP_CREATE:' || actor.id AND idem.idem_key='r10-rollback')
  FROM hhy.content_posts content
  JOIN hhy.users actor ON actor.id=content.owner_id AND actor.invite_code='R10ROLLBACK'
  JOIN hhy.group_details detail ON detail.content_id=content.id
  JOIN hhy.media_objects media ON media.id=detail.qr_media_id
  JOIN hhy.content_contacts contact ON contact.content_id=content.id
  WHERE content.title='R10 rollback group';")"

"${PSQL[@]}" --single-transaction \
  -f "${ROOT}/database/rollback/U036__r10_group_promotion_invariants.sql" >/dev/null

remaining_objects="$(object_counts)"
after_data="$("${PSQL[@]}" -qAt -c "
  SELECT content.id,content.status,detail.platform,detail.size_range,detail.join_requirement,
         detail.qr_media_id,media.object_key,contact.channel,contact.value_cipher,
         (SELECT count(*) FROM hhy.idempotency_records idem
          WHERE idem.scope='R10_GROUP_CREATE:' || actor.id AND idem.idem_key='r10-rollback')
  FROM hhy.content_posts content
  JOIN hhy.users actor ON actor.id=content.owner_id AND actor.invite_code='R10ROLLBACK'
  JOIN hhy.group_details detail ON detail.content_id=content.id
  JOIN hhy.media_objects media ON media.id=detail.qr_media_id
  JOIN hhy.content_contacts contact ON contact.content_id=content.id
  WHERE content.title='R10 rollback group';")"

[[ "${remaining_objects}" == "0|0|0|0" && -n "${before_data}" && "${after_data}" == "${before_data}" ]] || {
  echo "R10 U036 rollback changed data or left objects: objects=${remaining_objects} before=${before_data} after=${after_data}" >&2
  exit 1
}
echo "R10_U036_ROLLBACK PASS objects=${remaining_objects} data=${after_data}"

"${PSQL[@]}" --single-transaction \
  -f "${ROOT}/database/migrations/V036__r10_group_promotion_invariants.sql" >/dev/null
reapplied_objects="$(object_counts)"
[[ "${reapplied_objects}" == "${before_objects}" ]] || {
  echo "R10 V036 replay object inventory changed: before=${before_objects} after=${reapplied_objects}" >&2
  exit 1
}
"${PSQL[@]}" -f "${ROOT}/database/tests/r10_group_promotion_invariants.sql" >/dev/null
echo "R10_V036_REPLAY PASS objects=${reapplied_objects}"
