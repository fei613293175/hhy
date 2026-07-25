#!/usr/bin/env bash
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
: "${DATABASE_URL:?DATABASE_URL is required}"
: "${HHY_DB_SMOKE_CONFIRM:?Set HHY_DB_SMOKE_CONFIRM=YES for a disposable database}"
[[ "${HHY_DB_SMOKE_CONFIRM}" == "YES" ]] || exit 2
command -v psql >/dev/null 2>&1 || { echo "psql is required" >&2; exit 2; }

PSQL=(psql "${DATABASE_URL}" -X -v ON_ERROR_STOP=1)
server_version="$("${PSQL[@]}" -qAt -c "SHOW server_version_num")"
(( server_version >= 170000 )) || {
  echo "R12 requires PostgreSQL 17 or newer, got ${server_version}" >&2
  exit 1
}

run_token="$(date +%s%N)"
phone_suffix="${run_token: -8}"
database_url_prefix="${DATABASE_URL%/*}"
if [[ "${database_url_prefix}" == "${DATABASE_URL}" || "${DATABASE_URL}" == *\?* ]]; then
  echo "DATABASE_URL must be a query-free PostgreSQL URI for disposable clone tests" >&2
  exit 2
fi

template_db="r12_v038_${phone_suffix}"
active_case_db=""
cleanup_dirty_upgrade_databases() {
  if [[ -n "${active_case_db}" ]]; then
    "${PSQL[@]}" -q -c "DROP DATABASE IF EXISTS ${active_case_db} WITH (FORCE)" >/dev/null || true
  fi
  "${PSQL[@]}" -q -c "DROP DATABASE IF EXISTS ${template_db} WITH (FORCE)" >/dev/null || true
}
trap cleanup_dirty_upgrade_databases EXIT

"${PSQL[@]}" -q -c "DROP DATABASE IF EXISTS ${template_db} WITH (FORCE)" >/dev/null
"${PSQL[@]}" -q -c "CREATE DATABASE ${template_db}" >/dev/null
template_url="${database_url_prefix}/${template_db}"
for migration in "${ROOT}"/database/migrations/V*.sql; do
  migration_name="$(basename "${migration}")"
  migration_version="${migration_name%%__*}"
  migration_number=$((10#${migration_version#V}))
  (( migration_number > 38 )) && break
  psql "${template_url}" -X -v ON_ERROR_STOP=1 --single-transaction -f "${migration}" >/dev/null
done

seed_v038_content() {
  local target_url="$1"
  psql "${target_url}" -X -v ON_ERROR_STOP=1 <<'SQL' >/dev/null
WITH actor AS (
  INSERT INTO hhy.users(phone,status,invite_code)
  VALUES ('13900001202','ACTIVE','R12DIRTY') RETURNING id
), content AS (
  INSERT INTO hhy.content_posts(owner_id,type,title,status,version)
  SELECT id,'PROJECT','R12 dirty upgrade sentinel','DRAFT',0 FROM actor RETURNING id
)
INSERT INTO hhy.project_details(content_id,cooperation)
SELECT id,'dirty upgrade matrix' FROM content;
SQL
}

inject_dirty_upgrade_case() {
  local case_name="$1"
  local target_url="$2"
  if [[ "${case_name}" == "detail_cardinality" ]]; then
    psql "${target_url}" -X -v ON_ERROR_STOP=1 <<'SQL' >/dev/null
WITH actor AS (
  INSERT INTO hhy.users(phone,status,invite_code)
  VALUES ('13900001202','ACTIVE','R12DIRTY') RETURNING id
)
INSERT INTO hhy.content_posts(owner_id,type,title,status,version)
SELECT id,'PROJECT','R12 dirty upgrade sentinel','DRAFT',0 FROM actor;
SQL
    return
  fi

  seed_v038_content "${target_url}"
  case "${case_name}" in
    illegal_status_edge)
      psql "${target_url}" -X -v ON_ERROR_STOP=1 <<'SQL' >/dev/null
INSERT INTO hhy.content_status_logs(content_id,from_status,to_status,operator)
SELECT id,'DRAFT','ONLINE','r12-dirty-test' FROM hhy.content_posts
WHERE title='R12 dirty upgrade sentinel';
SQL
      ;;
    invalid_contact)
      psql "${target_url}" -X -v ON_ERROR_STOP=1 <<'SQL' >/dev/null
ALTER TABLE hhy.content_contacts DROP CONSTRAINT ck_r06_content_contact;
ALTER TABLE hhy.content_contacts DROP CONSTRAINT ck_r07_contact_cipher_envelope;
INSERT INTO hhy.content_contacts(content_id,channel,value_cipher,display_mask,sort_order)
SELECT id,'','',NULL,-1 FROM hhy.content_posts
WHERE title='R12 dirty upgrade sentinel';
SQL
      ;;
    invalid_media)
      psql "${target_url}" -X -v ON_ERROR_STOP=1 <<'SQL' >/dev/null
ALTER TABLE hhy.content_media DROP CONSTRAINT ck_r06_content_media;
WITH media AS (
  INSERT INTO hhy.media_objects(owner_id,bucket,object_key,mime,size,sha256,visibility)
  SELECT owner_id,'r12-dirty','invalid-media.png','image/png',1,repeat('c',64),'PRIVATE'
  FROM hhy.content_posts WHERE title='R12 dirty upgrade sentinel' RETURNING id
)
INSERT INTO hhy.content_media(content_id,media_id,media_type,sort_order)
SELECT content.id,media.id,'',-1 FROM hhy.content_posts content CROSS JOIN media
WHERE content.title='R12 dirty upgrade sentinel';
SQL
      ;;
    duplicate_contact_order)
      psql "${target_url}" -X -v ON_ERROR_STOP=1 <<'SQL' >/dev/null
INSERT INTO hhy.content_contacts(content_id,channel,value_cipher,display_mask,sort_order)
SELECT id,'WECHAT','hhy-contact-v1:dirty-wechat','微信***',0 FROM hhy.content_posts
WHERE title='R12 dirty upgrade sentinel';
INSERT INTO hhy.content_contacts(content_id,channel,value_cipher,display_mask,sort_order)
SELECT id,'PHONE','hhy-contact-v1:dirty-phone','139****1202',0 FROM hhy.content_posts
WHERE title='R12 dirty upgrade sentinel';
SQL
      ;;
    duplicate_contact_channel)
      psql "${target_url}" -X -v ON_ERROR_STOP=1 <<'SQL' >/dev/null
INSERT INTO hhy.content_contacts(content_id,channel,value_cipher,display_mask,sort_order)
SELECT id,'WECHAT','hhy-contact-v1:dirty-one','微信***',0 FROM hhy.content_posts
WHERE title='R12 dirty upgrade sentinel';
INSERT INTO hhy.content_contacts(content_id,channel,value_cipher,display_mask,sort_order)
SELECT id,'WECHAT','hhy-contact-v1:dirty-two','微信另***',1 FROM hhy.content_posts
WHERE title='R12 dirty upgrade sentinel';
SQL
      ;;
    duplicate_media)
      psql "${target_url}" -X -v ON_ERROR_STOP=1 <<'SQL' >/dev/null
WITH media AS (
  INSERT INTO hhy.media_objects(owner_id,bucket,object_key,mime,size,sha256,visibility)
  SELECT owner_id,'r12-dirty','duplicate-media.png','image/png',1,repeat('d',64),'PRIVATE'
  FROM hhy.content_posts WHERE title='R12 dirty upgrade sentinel' RETURNING id
)
INSERT INTO hhy.content_media(content_id,media_id,media_type,sort_order)
SELECT content.id,media.id,'image/png',position
FROM hhy.content_posts content CROSS JOIN media CROSS JOIN generate_series(0,1) AS position
WHERE content.title='R12 dirty upgrade sentinel';
SQL
      ;;
    invalid_version_sequence)
      psql "${target_url}" -X -v ON_ERROR_STOP=1 <<'SQL' >/dev/null
INSERT INTO hhy.content_versions(content_id,version_no,snapshot_json,created_by)
SELECT id,'1','{"dirty":true}'::jsonb,'r12-dirty-test' FROM hhy.content_posts
WHERE title='R12 dirty upgrade sentinel';
SQL
      ;;
    unbindable_review_version)
      psql "${target_url}" -X -v ON_ERROR_STOP=1 <<'SQL' >/dev/null
INSERT INTO hhy.content_review_records(content_id,version_no,decision)
SELECT id,'1','APPROVE' FROM hhy.content_posts
WHERE title='R12 dirty upgrade sentinel';
SQL
      ;;
    *)
      echo "Unknown R12 dirty upgrade case: ${case_name}" >&2
      exit 2
      ;;
  esac
}

dirty_cases=(
  detail_cardinality
  illegal_status_edge
  invalid_contact
  invalid_media
  duplicate_contact_order
  duplicate_contact_channel
  duplicate_media
  invalid_version_sequence
  unbindable_review_version
)
dirty_markers=(
  R12_DIRTY_UPGRADE_CONTENT_DETAIL_CARDINALITY
  R12_DIRTY_UPGRADE_ILLEGAL_STATUS_HISTORY_EDGE
  R12_DIRTY_UPGRADE_INVALID_CONTENT_CONTACT
  R12_DIRTY_UPGRADE_INVALID_CONTENT_MEDIA
  R12_DIRTY_UPGRADE_DUPLICATE_CONTACT_ORDER
  R12_DIRTY_UPGRADE_DUPLICATE_CONTACT_CHANNEL
  R12_DIRTY_UPGRADE_DUPLICATE_CONTENT_MEDIA
  R12_DIRTY_UPGRADE_INVALID_CONTENT_VERSION_SEQUENCE
  R12_DIRTY_UPGRADE_REVIEW_VERSION_UNBINDABLE
)

for index in "${!dirty_cases[@]}"; do
  case_name="${dirty_cases[$index]}"
  expected_marker="${dirty_markers[$index]}"
  active_case_db="r12_dirty_${index}_${phone_suffix}"
  "${PSQL[@]}" -q -c "DROP DATABASE IF EXISTS ${active_case_db} WITH (FORCE)" >/dev/null
  "${PSQL[@]}" -q -c "CREATE DATABASE ${active_case_db} TEMPLATE ${template_db}" >/dev/null
  case_url="${database_url_prefix}/${active_case_db}"
  inject_dirty_upgrade_case "${case_name}" "${case_url}"

  case_log="$(mktemp)"
  set +e
  psql "${case_url}" -X -v ON_ERROR_STOP=1 --single-transaction \
    -f "${ROOT}/database/migrations/V039__r12_publish_management_invariants.sql" \
    >"${case_log}" 2>&1
  case_rc=$?
  set -e
  if [[ "${case_rc}" -eq 0 ]] || ! grep -q "${expected_marker}" "${case_log}"; then
    cat "${case_log}" >&2
    rm -f "${case_log}"
    echo "R12 dirty upgrade case failed: ${case_name} expected=${expected_marker}" >&2
    exit 1
  fi
  rm -f "${case_log}"

  atomic_state="$(psql "${case_url}" -X -qAt -c "
    SELECT
      (SELECT count(*) FROM information_schema.columns WHERE table_schema='hhy' AND (
        (table_name='content_status_logs' AND column_name='transition_version') OR
        (table_name='content_review_records' AND column_name IN ('snapshot_version_id','command_id')) OR
        (table_name IN ('content_media','content_contacts') AND column_name='removed_at'))),
      (SELECT count(*) FROM pg_trigger WHERE tgname LIKE '%r12%' AND NOT tgisinternal),
      (SELECT count(*) FROM pg_indexes WHERE schemaname='hhy' AND indexname LIKE '%r12%'),
      (SELECT count(*) FROM pg_constraint WHERE connamespace='hhy'::regnamespace AND conname LIKE '%r12%'),
      (SELECT count(*) FROM pg_proc WHERE pronamespace='hhy'::regnamespace AND proname LIKE '%r12%'),
      (SELECT count(*) FROM hhy.content_posts WHERE title='R12 dirty upgrade sentinel');")"
  [[ "${atomic_state}" == "0|0|0|0|0|1" ]] || {
    echo "R12 dirty upgrade was not atomic case=${case_name} state=${atomic_state}" >&2
    exit 1
  }
  "${PSQL[@]}" -q -c "DROP DATABASE ${active_case_db} WITH (FORCE)" >/dev/null
  active_case_db=""
  echo "R12_DIRTY_UPGRADE_BLOCKED PASS case=${case_name} marker=${expected_marker}"
done
"${PSQL[@]}" -q -c "DROP DATABASE ${template_db} WITH (FORCE)" >/dev/null
trap - EXIT
echo "R12_DIRTY_UPGRADE_ATOMIC_MATRIX PASS cases=${#dirty_cases[@]}"

"${PSQL[@]}" -f "${ROOT}/database/tests/r12_publish_management_invariants.sql" >/dev/null
echo "R12_PUBLISH_MANAGEMENT_INVARIANTS PASS"

invite_code="R12ROLLBACK${phone_suffix}"
"${PSQL[@]}" -v invite_code="${invite_code}" -v phone="139${phone_suffix}" <<'SQL' >/dev/null
BEGIN;
SET CONSTRAINTS ALL DEFERRED;
WITH actor AS (
  INSERT INTO hhy.users(phone,status,invite_code)
  VALUES (:'phone','ACTIVE',:'invite_code') RETURNING id
), content AS (
  INSERT INTO hhy.content_posts(owner_id,type,title,status,version)
  SELECT id,'PROJECT','R12 rollback replay '||:'invite_code','DRAFT',0 FROM actor RETURNING id
), detail AS (
  INSERT INTO hhy.project_details(content_id,cooperation)
  SELECT id,'rollback replay' FROM content
)
INSERT INTO hhy.content_versions(content_id,version_no,snapshot_json,created_by)
SELECT id,'0',jsonb_build_object('token',:'invite_code'),'r12-runner' FROM content;
COMMIT;
SQL

before_state="$("${PSQL[@]}" -qAt -v invite_code="${invite_code}" <<'SQL'
  SELECT content.id||'|'||content.status||'|'||content.version||'|'||
    (SELECT count(*) FROM hhy.content_status_logs log WHERE log.content_id=content.id)||'|'||
    (SELECT count(*) FROM hhy.outbox_events event WHERE event.aggregate_type='CONTENT' AND event.aggregate_id=content.id::text)
  FROM hhy.content_posts content
  JOIN hhy.users actor ON actor.id=content.owner_id
  WHERE actor.invite_code=:'invite_code';
SQL
)"
"${PSQL[@]}" --single-transaction -f "${ROOT}/database/rollback/U039__r12_publish_management_invariants.sql" >/dev/null
retained_columns="$(${PSQL[@]} -qAt -c "
  SELECT count(*) FROM information_schema.columns
  WHERE table_schema='hhy' AND (
    (table_name='content_status_logs' AND column_name='transition_version') OR
    (table_name='content_review_records' AND column_name IN ('snapshot_version_id','command_id')) OR
    (table_name IN ('content_media','content_contacts') AND column_name='removed_at')
  );")"
[[ "${retained_columns}" == "5" ]] || {
  echo "U039 removed R12 compatibility columns: ${retained_columns}" >&2
  exit 1
}
"${PSQL[@]}" --single-transaction -f "${ROOT}/database/migrations/V039__r12_publish_management_invariants.sql" >/dev/null
after_state="$("${PSQL[@]}" -qAt -v invite_code="${invite_code}" <<'SQL'
  SELECT content.id||'|'||content.status||'|'||content.version||'|'||
    (SELECT count(*) FROM hhy.content_status_logs log WHERE log.content_id=content.id)||'|'||
    (SELECT count(*) FROM hhy.outbox_events event WHERE event.aggregate_type='CONTENT' AND event.aggregate_id=content.id::text)
  FROM hhy.content_posts content
  JOIN hhy.users actor ON actor.id=content.owner_id
  WHERE actor.invite_code=:'invite_code';
SQL
)"
[[ "${before_state}" == "${after_state}" ]] || {
  echo "U039/V039 replay changed business data before=${before_state} after=${after_state}" >&2
  exit 1
}
echo "R12_U039_ROLLBACK_V039_REPLAY PASS state=${after_state}"

objects="$(${PSQL[@]} -qAt -c "
  SELECT
    (SELECT count(*) FROM pg_trigger WHERE tgname LIKE '%r12%' AND NOT tgisinternal),
    (SELECT count(*) FROM pg_indexes WHERE schemaname='hhy' AND indexname LIKE '%r12%'),
    (SELECT count(*) FROM pg_constraint WHERE connamespace='hhy'::regnamespace AND conname LIKE '%r12%');")"
[[ "${objects}" == "20|6|5" ]] || {
  echo "R12 database object projection unexpected: ${objects}" >&2
  exit 1
}
echo "R12_DATABASE_OBJECTS PASS objects=${objects} postgres=${server_version}"

concurrency_invite="R12CONCURRENT${phone_suffix}"
concurrency_id="$("${PSQL[@]}" -qAt -v invite_code="${concurrency_invite}" -v phone="138${phone_suffix}" <<'SQL'
WITH actor AS (
  INSERT INTO hhy.users(phone,status,invite_code)
  VALUES (:'phone','ACTIVE',:'invite_code') RETURNING id
), content AS (
  INSERT INTO hhy.content_posts(owner_id,type,title,status,version)
  SELECT id,'PROJECT','R12 optimistic concurrency','DRAFT',0 FROM actor RETURNING id
), detail AS (
  INSERT INTO hhy.project_details(content_id,cooperation)
  SELECT id,'optimistic concurrency' FROM content
), snapshot AS (
  INSERT INTO hhy.content_versions(content_id,version_no,snapshot_json,created_by)
  SELECT id,'0','{"concurrency":true}'::jsonb,'r12-runner' FROM content
)
SELECT id FROM content;
SQL
)"

concurrency_one="$(mktemp)"
concurrency_two="$(mktemp)"
set +e
"${PSQL[@]}" -qAt -c "
  WITH updated AS (
    UPDATE hhy.content_posts
    SET summary='concurrent writer one',version=version+1
    WHERE id=${concurrency_id} AND version=0
    RETURNING 1
  ) SELECT count(*) FROM updated;" >"${concurrency_one}" &
pid_one=$!
"${PSQL[@]}" -qAt -c "
  WITH updated AS (
    UPDATE hhy.content_posts
    SET summary='concurrent writer two',version=version+1
    WHERE id=${concurrency_id} AND version=0
    RETURNING 1
  ) SELECT count(*) FROM updated;" >"${concurrency_two}" &
pid_two=$!
wait "${pid_one}"
rc_one=$?
wait "${pid_two}"
rc_two=$?
set -e
result_one="$(tr -d '[:space:]' <"${concurrency_one}")"
result_two="$(tr -d '[:space:]' <"${concurrency_two}")"
rm -f "${concurrency_one}" "${concurrency_two}"
[[ "${rc_one}" -eq 0 && "${rc_two}" -eq 0 ]] || {
  echo "R12 concurrent writers returned errors rc_one=${rc_one} rc_two=${rc_two}" >&2
  exit 1
}
[[ "${result_one}:${result_two}" == "1:0" || "${result_one}:${result_two}" == "0:1" ]] || {
  echo "R12 optimistic concurrency accepted unexpected writers one=${result_one} two=${result_two}" >&2
  exit 1
}
concurrency_state="$("${PSQL[@]}" -qAt -c "
  SELECT content.version,
    (SELECT count(*) FROM hhy.outbox_events event
      WHERE event.aggregate_type='CONTENT' AND event.aggregate_id=content.id::text
        AND event.payload->>'contentVersion'='1')
  FROM hhy.content_posts content WHERE content.id=${concurrency_id};")"
[[ "${concurrency_state}" == "1|1" ]] || {
  echo "R12 optimistic concurrency final state unexpected: ${concurrency_state}" >&2
  exit 1
}
echo "R12_OPTIMISTIC_CONCURRENCY PASS writers=${result_one}:${result_two} state=${concurrency_state}"
