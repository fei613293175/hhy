#!/usr/bin/env bash
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
command -v docker >/dev/null 2>&1 || { echo "docker is required" >&2; exit 2; }

CONTAINER="${HHY_R10_DB_CONTAINER:-hhy-r10-disposable-postgres}"
case "${CONTAINER}" in
  hhy-r10-*) ;;
  *) echo "R10 disposable container name must start with hhy-r10-" >&2; exit 2 ;;
esac

cleanup() { docker rm -f "${CONTAINER}" >/dev/null 2>&1 || true; }
trap cleanup EXIT
cleanup

docker run -d --name "${CONTAINER}" \
  -e POSTGRES_HOST_AUTH_METHOD=trust -e POSTGRES_DB=hhy_r10 \
  -v "${ROOT}:${ROOT}:ro" postgres:17.10-alpine >/dev/null
for _ in $(seq 1 30); do
  docker exec "${CONTAINER}" pg_isready -h 127.0.0.1 -U postgres -d hhy_r10 >/dev/null 2>&1 && break
  sleep 1
done
docker exec "${CONTAINER}" pg_isready -h 127.0.0.1 -U postgres -d hhy_r10 >/dev/null

psql() { docker exec -i "${CONTAINER}" psql "$@"; }
export CONTAINER
export -f psql
DATABASE_URL=postgresql://postgres@localhost/hhy_r10
PSQL=(psql "${DATABASE_URL}" -X -v ON_ERROR_STOP=1)

reset_schema() {
  "${PSQL[@]}" -c "DROP SCHEMA IF EXISTS hhy CASCADE" >/dev/null
}

apply_before_v036() {
  local migration
  for migration in "${ROOT}"/database/migrations/V*.sql; do
    [[ "$(basename "${migration}")" == V036__* ]] && break
    "${PSQL[@]}" --single-transaction -f "${migration}" >/dev/null
  done
}

apply_through_v036() {
  local migration
  for migration in "${ROOT}"/database/migrations/V*.sql; do
    "${PSQL[@]}" --single-transaction -f "${migration}" >/dev/null
    [[ "$(basename "${migration}")" == V036__* ]] && return 0
  done
  echo "V036 migration was not found" >&2
  return 1
}

expect_v036_failure() {
  local marker="$1"
  local log_file
  local rc
  log_file="$(mktemp)"
  set +e
  "${PSQL[@]}" --single-transaction \
    -f "${ROOT}/database/migrations/V036__r10_group_promotion_invariants.sql" \
    >"${log_file}" 2>&1
  rc=$?
  set -e
  if [[ "${rc}" -eq 0 ]] || ! grep -Fq "${marker}" "${log_file}"; then
    cat "${log_file}" >&2
    rm -f "${log_file}"
    echo "V036 did not fail closed with ${marker}" >&2
    exit 1
  fi
  rm -f "${log_file}"
  echo "${marker} PASS"
}

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

reset_schema
apply_before_v036
"${PSQL[@]}" -c "
  ALTER TABLE hhy.group_details DROP CONSTRAINT uq_group_details_content_id;" >/dev/null
expect_v036_failure "R10_GROUP_UNIQUENESS_PREREQUISITE_MISSING"

reset_schema
apply_before_v036
"${PSQL[@]}" -c "
  ALTER TABLE hhy.idempotency_records
  DROP CONSTRAINT uq_idempotency_records_scope_idem_key;" >/dev/null
expect_v036_failure "R10_IDEMPOTENCY_PREREQUISITE_MISSING"

reset_schema
apply_before_v036
"${PSQL[@]}" -c "
  INSERT INTO hhy.users(phone,status,invite_code)
  VALUES ('13900001009','ACTIVE','R10NON_GROUP_PASSWORD');
  INSERT INTO hhy.content_posts(owner_id,type,title,status)
    SELECT id,'PROJECT','R10 legacy non-group password','DRAFT'
    FROM hhy.users WHERE invite_code='R10NON_GROUP_PASSWORD';
  INSERT INTO hhy.project_details(content_id,cooperation)
    SELECT id,'数据库升级验证'
    FROM hhy.content_posts WHERE title='R10 legacy non-group password';
  INSERT INTO hhy.content_contacts(content_id,channel,value_cipher,display_mask,sort_order)
    SELECT id,'JOIN_PASSWORD','hhy-contact-v1:r10-legacy-wrong-type','口令***',0
    FROM hhy.content_posts WHERE title='R10 legacy non-group password';" >/dev/null
expect_v036_failure "R10_LEGACY_JOIN_PASSWORD_NON_GROUP_REQUIRES_REVIEW"

reset_schema
apply_before_v036
"${PSQL[@]}" -c "
  INSERT INTO hhy.users(phone,status,invite_code)
  VALUES ('13900001010','ACTIVE','R10DUP_PASSWORD');
  INSERT INTO hhy.content_posts(owner_id,type,title,status)
    SELECT id,'GROUP','R10 legacy duplicate password','DRAFT'
    FROM hhy.users WHERE invite_code='R10DUP_PASSWORD';
  INSERT INTO hhy.group_details(content_id,platform)
    SELECT id,'WECHAT'
    FROM hhy.content_posts WHERE title='R10 legacy duplicate password';
  INSERT INTO hhy.content_contacts(content_id,channel,value_cipher,display_mask,sort_order)
    SELECT id,'JOIN_PASSWORD','hhy-contact-v1:r10-legacy-password-1','口令1***',0
    FROM hhy.content_posts WHERE title='R10 legacy duplicate password';
  INSERT INTO hhy.content_contacts(content_id,channel,value_cipher,display_mask,sort_order)
    SELECT id,'JOIN_PASSWORD','hhy-contact-v1:r10-legacy-password-2','口令2***',1
    FROM hhy.content_posts WHERE title='R10 legacy duplicate password';" >/dev/null
expect_v036_failure "R10_LEGACY_JOIN_PASSWORD_DUPLICATE_REQUIRES_REVIEW"

reset_schema
apply_before_v036
"${PSQL[@]}" -c "
  INSERT INTO hhy.users(phone,status,invite_code)
  VALUES ('13900001006','ACTIVE','R10MISSINGDETAIL');
  INSERT INTO hhy.content_posts(owner_id,type,title,status)
    SELECT id,'GROUP','R10 legacy missing detail','DRAFT'
    FROM hhy.users WHERE invite_code='R10MISSINGDETAIL';" >/dev/null
expect_v036_failure "R10_LEGACY_GROUP_DETAIL_MISSING_REQUIRES_REVIEW"

reset_schema
apply_before_v036
"${PSQL[@]}" -c "
  INSERT INTO hhy.users(phone,status,invite_code)
  VALUES ('13900001007','ACTIVE','R10MISSINGENTRY');
  INSERT INTO hhy.content_posts(owner_id,type,title,status)
    SELECT id,'GROUP','R10 legacy online missing entry','ONLINE'
    FROM hhy.users WHERE invite_code='R10MISSINGENTRY';
  INSERT INTO hhy.group_details(content_id,platform,join_requirement)
    SELECT id,'WECHAT','回答验证问题'
    FROM hhy.content_posts WHERE title='R10 legacy online missing entry';
  INSERT INTO hhy.content_contacts(content_id,channel,value_cipher,display_mask,sort_order)
    SELECT id,'WECHAT','hhy-contact-v1:r10-legacy-owner','群主***',0
    FROM hhy.content_posts WHERE title='R10 legacy online missing entry';" >/dev/null
expect_v036_failure "R10_LEGACY_ONLINE_GROUP_ENTRY_CHANNEL_MISSING_REQUIRES_REVIEW"

reset_schema
apply_before_v036
"${PSQL[@]}" -c "
  INSERT INTO hhy.users(phone,status,invite_code)
  VALUES ('13900001008','ACTIVE','R10MISSINGOWNER');
  INSERT INTO hhy.content_posts(owner_id,type,title,status)
    SELECT id,'GROUP','R10 legacy online missing owner','ONLINE'
    FROM hhy.users WHERE invite_code='R10MISSINGOWNER';
  INSERT INTO hhy.group_details(content_id,platform,group_link)
    SELECT id,'WECHAT','https://group.example.invalid/legacy-online'
    FROM hhy.content_posts WHERE title='R10 legacy online missing owner';
  INSERT INTO hhy.content_contacts(content_id,channel,value_cipher,display_mask,sort_order)
    SELECT id,'JOIN_PASSWORD','hhy-contact-v1:r10-legacy-password','口令***',0
    FROM hhy.content_posts WHERE title='R10 legacy online missing owner';" >/dev/null
expect_v036_failure "R10_LEGACY_ONLINE_GROUP_OWNER_CONTACT_MISSING_REQUIRES_REVIEW"

reset_schema
apply_before_v036
"${PSQL[@]}" -c "
  INSERT INTO hhy.users(phone,status,invite_code)
  VALUES ('13900001003','ACTIVE','R10ORPHAN');
  INSERT INTO hhy.content_posts(owner_id,type,title,status)
    SELECT id,'GROUP','R10 orphan QR group','DRAFT'
    FROM hhy.users WHERE invite_code='R10ORPHAN';
  INSERT INTO hhy.group_details(content_id,platform,qr_media_id)
    SELECT id,'WECHAT',9223372036854770000
    FROM hhy.content_posts WHERE title='R10 orphan QR group';" >/dev/null
expect_v036_failure "R10_GROUP_QR_MEDIA_ORPHAN_REQUIRES_REVIEW"

reset_schema
apply_through_v036
table_count="$("${PSQL[@]}" -qAt -c "
  SELECT count(*) FROM information_schema.tables
  WHERE table_schema='hhy' AND table_type='BASE TABLE';")"
[[ "${table_count}" == "200" ]] || {
  echo "R10 empty-database migration expected 200 tables, got ${table_count}" >&2
  exit 1
}
empty_objects="$(object_counts)"
assert_nonzero_object_counts "${empty_objects}"
qr_fk_state="$("${PSQL[@]}" -qAt -c "
  SELECT count(*)
  FROM pg_constraint constraint_row
  JOIN unnest(constraint_row.conkey) AS key(attnum) ON true
  JOIN pg_attribute attribute
    ON attribute.attrelid=constraint_row.conrelid AND attribute.attnum=key.attnum
  WHERE constraint_row.contype='f'
    AND constraint_row.conrelid='hhy.group_details'::regclass
    AND constraint_row.confrelid='hhy.media_objects'::regclass
    AND constraint_row.confdeltype='r'
    AND attribute.attname='qr_media_id';")"
[[ "${qr_fk_state}" == "1" ]] || {
  echo "R10 qr_media_id must have exactly one ON DELETE RESTRICT media_objects foreign key" >&2
  exit 1
}
"${PSQL[@]}" -f "${ROOT}/database/tests/r10_group_promotion_invariants.sql" >/dev/null
echo "R10_EMPTY_DATABASE_TO_V036 PASS tables=${table_count} objects=${empty_objects} qr_fk=${qr_fk_state}"

reset_schema
apply_before_v036
"${PSQL[@]}" -c "
  INSERT INTO hhy.users(phone,status,invite_code)
  VALUES ('13900001004','ACTIVE','R10UPGRADE');
  INSERT INTO hhy.content_posts(owner_id,type,title,status)
    SELECT id,'GROUP','R10 legacy upgrade group','DRAFT'
    FROM hhy.users WHERE invite_code='R10UPGRADE';
  INSERT INTO hhy.group_details(content_id,platform,size_range,join_requirement)
    SELECT id,'WECHAT','   ','回答验证问题'
    FROM hhy.content_posts WHERE title='R10 legacy upgrade group';" >/dev/null
"${PSQL[@]}" --single-transaction \
  -f "${ROOT}/database/migrations/V036__r10_group_promotion_invariants.sql" >/dev/null
upgrade_state="$("${PSQL[@]}" -qAt -c "
  SELECT content.status,detail.platform,(detail.size_range='   '),detail.join_requirement,
    (SELECT count(*) FROM pg_constraint
      WHERE connamespace='hhy'::regnamespace AND conname LIKE '%r10%')
  FROM hhy.content_posts content
  JOIN hhy.group_details detail ON detail.content_id=content.id
  WHERE content.title='R10 legacy upgrade group';")"
case "${upgrade_state}" in
  "DRAFT|WECHAT|t|回答验证问题|"*) ;;
  *) echo "R10 upgrade database state was unexpected: ${upgrade_state}" >&2; exit 1 ;;
esac
upgrade_objects="$(object_counts)"
assert_nonzero_object_counts "${upgrade_objects}"
echo "R10_UPGRADE_DATABASE_TO_V036 PASS state=${upgrade_state} objects=${upgrade_objects}"

DATABASE_URL="${DATABASE_URL}" HHY_DB_SMOKE_CONFIRM=YES \
  bash "${ROOT}/scripts/run_r10_database_invariants.sh"
echo "R10_DISPOSABLE_POSTGRES_CONTAINER PASS"
