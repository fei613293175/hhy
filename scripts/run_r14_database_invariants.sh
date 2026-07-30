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
  echo "R14 requires PostgreSQL 17 or newer, got ${server_version}" >&2
  exit 1
}

database_url_prefix="${DATABASE_URL%/*}"
if [[ "${database_url_prefix}" == "${DATABASE_URL}" || "${DATABASE_URL}" == *\?* ]]; then
  echo "DATABASE_URL must be a query-free PostgreSQL URI for disposable clone tests" >&2
  exit 2
fi
run_suffix="$(date +%s%N)"
run_suffix="${run_suffix: -8}"
template_db="r14_v042_${run_suffix}"
empty_db="r14_empty_${run_suffix}"
upgrade_db="r14_upgrade_${run_suffix}"
active_case_db=""

drop_database() {
  local name="$1"
  "${PSQL[@]}" -q -c "DROP DATABASE IF EXISTS ${name} WITH (FORCE)" >/dev/null || true
}
cleanup() {
  [[ -n "${active_case_db}" ]] && drop_database "${active_case_db}"
  drop_database "${upgrade_db}"
  drop_database "${empty_db}"
  drop_database "${template_db}"
}
trap cleanup EXIT

apply_through() {
  local url="$1"
  local target="$2"
  local migration migration_name migration_version migration_number
  for migration in "${ROOT}"/database/migrations/V*.sql; do
    migration_name="$(basename "${migration}")"
    migration_version="${migration_name%%__*}"
    migration_number=$((10#${migration_version#V}))
    (( migration_number > target )) && break
    psql "${url}" -X -v ON_ERROR_STOP=1 --single-transaction -f "${migration}" >/dev/null
  done
}

drop_database "${template_db}"
"${PSQL[@]}" -q -c "CREATE DATABASE ${template_db}" >/dev/null
template_url="${database_url_prefix}/${template_db}"
apply_through "${template_url}" 42
psql "${template_url}" -X -v ON_ERROR_STOP=1 <<'SQL' >/dev/null
CREATE TABLE IF NOT EXISTS public.flyway_schema_history(
  version varchar(50),success boolean NOT NULL DEFAULT true
);
INSERT INTO public.flyway_schema_history(version,success) VALUES ('42',true);
SQL

drop_database "${empty_db}"
"${PSQL[@]}" -q -c "CREATE DATABASE ${empty_db}" >/dev/null
empty_url="${database_url_prefix}/${empty_db}"
apply_through "${empty_url}" 43
empty_state="$(psql "${empty_url}" -X -qAt -c "
  SELECT
    (SELECT data_type||':'||is_nullable FROM information_schema.columns
      WHERE table_schema='hhy' AND table_name='chat_messages' AND column_name='client_msg_id'),
    (SELECT data_type||':'||column_default FROM information_schema.columns
      WHERE table_schema='hhy' AND table_name='conversations' AND column_name='version'),
    (SELECT count(*) FROM pg_trigger WHERE tgname LIKE '%r14%' AND NOT tgisinternal);")"
[[ "${empty_state}" == character\ varying:NO\|bigint:0\|* ]] || {
  echo "R14 empty migration state invalid: ${empty_state}" >&2
  exit 1
}
echo "R14_EMPTY_DATABASE_MIGRATION PASS state=${empty_state}"

drop_database "${upgrade_db}"
"${PSQL[@]}" -q -c "CREATE DATABASE ${upgrade_db} TEMPLATE ${template_db}" >/dev/null
upgrade_url="${database_url_prefix}/${upgrade_db}"
psql "${upgrade_url}" -X -v ON_ERROR_STOP=1 <<'SQL' >/dev/null
DO $$
DECLARE
  actor_a bigint;
  actor_b bigint;
  conversation_id bigint;
  message_id bigint;
  message_created_at timestamptz;
BEGIN
  INSERT INTO hhy.users(phone,status,invite_code)
  VALUES ('13900004341','ACTIVE','R14UPA') RETURNING id INTO actor_a;
  INSERT INTO hhy.users(phone,status,invite_code)
  VALUES ('13900004342','ACTIVE','R14UPB') RETURNING id INTO actor_b;
  INSERT INTO hhy.conversations(type) VALUES ('DIRECT')
  RETURNING id INTO conversation_id;
  INSERT INTO hhy.conversation_members(conversation_id,user_id,unread_count)
  VALUES (conversation_id,actor_a,0),(conversation_id,actor_b,0);
  INSERT INTO hhy.chat_messages(
    conversation_id,sender_id,type,body_json,status,client_msg_id)
  VALUES (conversation_id,actor_a,'TEXT','{"text":"upgrade"}','SENT',43001)
  RETURNING id,created_at INTO message_id,message_created_at;
  UPDATE hhy.conversations
  SET last_message_id=message_id,last_message_at=message_created_at
  WHERE id=conversation_id;
END;
$$;
SQL
psql "${upgrade_url}" -X -v ON_ERROR_STOP=1 --single-transaction \
  -f "${ROOT}/database/migrations/V043__r14_chat_invariants.sql" >/dev/null
upgrade_state="$(psql "${upgrade_url}" -X -qAt -c "
  SELECT message.client_msg_id||'|'||conversation.direct_user_low_id||'|'||
    conversation.direct_user_high_id||'|'||member.unread_count
  FROM hhy.chat_messages message
  JOIN hhy.conversations conversation ON conversation.id=message.conversation_id
  JOIN hhy.conversation_members member ON member.conversation_id=conversation.id
  ORDER BY member.user_id LIMIT 1;")"
[[ "${upgrade_state}" == 43001\|*\|*\|0 ]] || {
  echo "R14 V042 upgrade did not preserve/backfill facts: ${upgrade_state}" >&2
  exit 1
}
echo "R14_V042_UPGRADE PASS state=${upgrade_state}"

inject_dirty_case() {
  local case_name="$1"
  local url="$2"
  case "${case_name}" in
    missing_members)
      psql "${url}" -X -v ON_ERROR_STOP=1 -c "INSERT INTO hhy.conversations(type) VALUES ('DIRECT')" >/dev/null
      ;;
    excess_members)
      psql "${url}" -X -v ON_ERROR_STOP=1 <<'SQL' >/dev/null
WITH actors AS (
  INSERT INTO hhy.users(phone,status,invite_code) VALUES
    ('13900004351','ACTIVE','R14EXA'),('13900004352','ACTIVE','R14EXB'),
    ('13900004353','ACTIVE','R14EXC') RETURNING id
), conversation AS (
  INSERT INTO hhy.conversations(type) VALUES ('DIRECT') RETURNING id
)
INSERT INTO hhy.conversation_members(conversation_id,user_id)
SELECT conversation.id,actors.id FROM conversation CROSS JOIN actors;
SQL
      ;;
    self_chat)
      psql "${url}" -X -v ON_ERROR_STOP=1 <<'SQL' >/dev/null
ALTER TABLE hhy.conversation_members DROP CONSTRAINT uq_conversation_members_conversation_id_user_id;
WITH actor AS (
  INSERT INTO hhy.users(phone,status,invite_code)
  VALUES ('13900004354','ACTIVE','R14SELF') RETURNING id
), conversation AS (
  INSERT INTO hhy.conversations(type) VALUES ('DIRECT') RETURNING id
)
INSERT INTO hhy.conversation_members(conversation_id,user_id)
SELECT conversation.id,actor.id FROM conversation CROSS JOIN actor CROSS JOIN generate_series(1,2);
SQL
      ;;
    duplicate_pair)
      psql "${url}" -X -v ON_ERROR_STOP=1 <<'SQL' >/dev/null
WITH actors AS (
  INSERT INTO hhy.users(phone,status,invite_code) VALUES
    ('13900004355','ACTIVE','R14DUPA'),('13900004356','ACTIVE','R14DUPB') RETURNING id
), conversations AS (
  INSERT INTO hhy.conversations(type) VALUES ('DIRECT'),('DIRECT') RETURNING id
)
INSERT INTO hhy.conversation_members(conversation_id,user_id)
SELECT conversations.id,actors.id FROM conversations CROSS JOIN actors;
SQL
      ;;
    invalid_client_message)
      psql "${url}" -X -v ON_ERROR_STOP=1 <<'SQL' >/dev/null
DO $$
DECLARE
  actor_a bigint;
  actor_b bigint;
  conversation_id bigint;
  message_id bigint;
  message_created_at timestamptz;
BEGIN
  INSERT INTO hhy.users(phone,status,invite_code)
  VALUES ('13900004357','ACTIVE','R14MSGA') RETURNING id INTO actor_a;
  INSERT INTO hhy.users(phone,status,invite_code)
  VALUES ('13900004358','ACTIVE','R14MSGB') RETURNING id INTO actor_b;
  INSERT INTO hhy.conversations(type) VALUES ('DIRECT')
  RETURNING id INTO conversation_id;
  INSERT INTO hhy.conversation_members(conversation_id,user_id)
  VALUES (conversation_id,actor_a),(conversation_id,actor_b);
  INSERT INTO hhy.chat_messages(
    conversation_id,sender_id,type,body_json,status,client_msg_id)
  VALUES (conversation_id,actor_a,'TEXT','{"text":"dirty"}','SENT',NULL)
  RETURNING id,created_at INTO message_id,message_created_at;
  UPDATE hhy.conversations
  SET last_message_id=message_id,last_message_at=message_created_at
  WHERE id=conversation_id;
END;
$$;
SQL
      ;;
    report_contract)
      psql "${url}" -X -v ON_ERROR_STOP=1 <<'SQL' >/dev/null
WITH actors AS (
  INSERT INTO hhy.users(phone,status,invite_code) VALUES
    ('13900004359','ACTIVE','R14REPA'),('13900004360','ACTIVE','R14REPB') RETURNING id
), conversation AS (
  INSERT INTO hhy.conversations(type) VALUES ('DIRECT') RETURNING id
), members AS (
  INSERT INTO hhy.conversation_members(conversation_id,user_id)
  SELECT conversation.id,actors.id FROM conversation CROSS JOIN actors
)
INSERT INTO hhy.chat_reports(reporter_id,target_user_id,conversation_id,message_ids,status)
SELECT min(actors.id),max(actors.id),conversation.id,'[]','PENDING'
FROM conversation CROSS JOIN actors GROUP BY conversation.id;
SQL
      ;;
    *) echo "unknown dirty case ${case_name}" >&2; exit 2 ;;
  esac
}

dirty_cases=(missing_members excess_members self_chat duplicate_pair invalid_client_message report_contract)
dirty_markers=(
  R14_DIRTY_UPGRADE_DIRECT_MEMBERS_MISSING
  R14_DIRTY_UPGRADE_DIRECT_MEMBERS_EXCESS
  R14_DIRTY_UPGRADE_DIRECT_SELF_CHAT
  R14_DIRTY_UPGRADE_DUPLICATE_DIRECT_PAIR
  R14_DIRTY_UPGRADE_INVALID_CLIENT_MESSAGE_ID
  R14_DIRTY_UPGRADE_REPORT_CONTRACT_MISSING
)
for index in "${!dirty_cases[@]}"; do
  active_case_db="r14_dirty_${index}_${run_suffix}"
  drop_database "${active_case_db}"
  "${PSQL[@]}" -q -c "CREATE DATABASE ${active_case_db} TEMPLATE ${template_db}" >/dev/null
  case_url="${database_url_prefix}/${active_case_db}"
  inject_dirty_case "${dirty_cases[$index]}" "${case_url}"
  case_log="$(mktemp)"
  set +e
  psql "${case_url}" -X -v ON_ERROR_STOP=1 --single-transaction \
    -f "${ROOT}/database/migrations/V043__r14_chat_invariants.sql" >"${case_log}" 2>&1
  case_rc=$?
  set -e
  if [[ "${case_rc}" -eq 0 ]] || ! grep -q "${dirty_markers[$index]}" "${case_log}"; then
    cat "${case_log}" >&2
    rm -f "${case_log}"
    echo "R14 dirty upgrade case failed: ${dirty_cases[$index]}" >&2
    exit 1
  fi
  rm -f "${case_log}"
  atomic_state="$(psql "${case_url}" -X -qAt -c "
    SELECT
      (SELECT count(*) FROM information_schema.columns WHERE table_schema='hhy' AND (
        (table_name='conversations' AND column_name IN ('direct_user_low_id','direct_user_high_id','version')) OR
        (table_name='conversation_members' AND column_name='hidden_at') OR
        (table_name='chat_reports' AND column_name IN ('reason_code','description','evidence_media_ids','version')))),
      (SELECT count(*) FROM pg_trigger WHERE tgname LIKE '%r14%' AND NOT tgisinternal),
      (SELECT count(*) FROM pg_indexes WHERE schemaname='hhy' AND indexname LIKE '%r14%'),
      (SELECT count(*) FROM pg_constraint WHERE connamespace='hhy'::regnamespace AND conname LIKE '%r14%'),
      (SELECT count(*) FROM pg_proc WHERE pronamespace='hhy'::regnamespace AND proname LIKE '%r14%'),
      (SELECT count(*) FROM public.flyway_schema_history WHERE version='43');")"
  [[ "${atomic_state}" == "0|0|0|0|0|0" ]] || {
    echo "R14 dirty upgrade left partial objects case=${dirty_cases[$index]} state=${atomic_state}" >&2
    exit 1
  }
  drop_database "${active_case_db}"
  active_case_db=""
  echo "R14_DIRTY_UPGRADE_BLOCKED PASS case=${dirty_cases[$index]} marker=${dirty_markers[$index]}"
done
echo "R14_DIRTY_UPGRADE_ATOMIC_MATRIX PASS cases=${#dirty_cases[@]}"

facts_before="$(psql "${upgrade_url}" -X -qAt -c "
  SELECT (SELECT count(*) FROM hhy.conversations)||'|'||
         (SELECT count(*) FROM hhy.chat_messages)||'|'||
         (SELECT count(*) FROM hhy.outbox_events)||'|'||
         (SELECT count(*) FROM information_schema.columns
           WHERE table_schema='hhy' AND table_name='conversations' AND column_name='version');")"
rollback_log="$(mktemp)"
set +e
psql "${upgrade_url}" -X -v ON_ERROR_STOP=1 --single-transaction \
  -f "${ROOT}/database/rollback/U043__r14_chat_invariants.sql" >"${rollback_log}" 2>&1
rollback_rc=$?
set -e
if [[ "${rollback_rc}" -eq 0 ]] || ! grep -q 'R14_U043_BUSINESS_FACTS_PRESENT' "${rollback_log}"; then
  cat "${rollback_log}" >&2
  rm -f "${rollback_log}"
  echo "U043 accepted a database containing chat facts" >&2
  exit 1
fi
rm -f "${rollback_log}"
facts_after="$(psql "${upgrade_url}" -X -qAt -c "
  SELECT (SELECT count(*) FROM hhy.conversations)||'|'||
         (SELECT count(*) FROM hhy.chat_messages)||'|'||
         (SELECT count(*) FROM hhy.outbox_events)||'|'||
         (SELECT count(*) FROM information_schema.columns
           WHERE table_schema='hhy' AND table_name='conversations' AND column_name='version');")"
[[ "${facts_before}" == "${facts_after}" ]] || {
  echo "U043 facts rejection was not atomic before=${facts_before} after=${facts_after}" >&2
  exit 1
}
echo "R14_U043_ROLLBACK_WITH_FACTS_REJECTED_ATOMICALLY PASS state=${facts_after}"

psql "${empty_url}" -X -v ON_ERROR_STOP=1 --single-transaction \
  -f "${ROOT}/database/rollback/U043__r14_chat_invariants.sql" >/dev/null
psql "${empty_url}" -X -v ON_ERROR_STOP=1 --single-transaction \
  -f "${ROOT}/database/migrations/V043__r14_chat_invariants.sql" >/dev/null
replay_state="$(psql "${empty_url}" -X -qAt -c "
  SELECT count(*) FROM information_schema.columns
  WHERE table_schema='hhy' AND (
    (table_name='conversations' AND column_name IN ('direct_user_low_id','direct_user_high_id','version')) OR
    (table_name='conversation_members' AND column_name='hidden_at') OR
    (table_name='chat_reports' AND column_name IN ('reason_code','description','evidence_media_ids','version')));")"
[[ "${replay_state}" == "8" ]] || { echo "R14 U043/V043 replay state=${replay_state}" >&2; exit 1; }
echo "R14_U043_ROLLBACK_V043_REPLAY PASS columns=${replay_state}"

psql "${empty_url}" -X -v ON_ERROR_STOP=1 --single-transaction \
  -f "${ROOT}/database/migrations/V044__r14_websocket_reliability.sql" >/dev/null
v044_table_count="$(psql "${empty_url}" -X -qAt -c \
  "SELECT count(*) FROM information_schema.tables WHERE table_schema='hhy' AND table_type='BASE TABLE'")"
[[ "${v044_table_count}" == "203" ]] || { echo "R14 V044 table count=${v044_table_count}" >&2; exit 1; }
psql "${empty_url}" -X -v ON_ERROR_STOP=1 --single-transaction \
  -f "${ROOT}/database/rollback/U044__r14_websocket_reliability_DEV_ONLY.sql" >/dev/null
u044_table_count="$(psql "${empty_url}" -X -qAt -c \
  "SELECT count(*) FROM information_schema.tables WHERE table_schema='hhy' AND table_type='BASE TABLE'")"
[[ "${u044_table_count}" == "200" ]] || { echo "R14 U044 table count=${u044_table_count}" >&2; exit 1; }
psql "${empty_url}" -X -v ON_ERROR_STOP=1 --single-transaction \
  -f "${ROOT}/database/migrations/V044__r14_websocket_reliability.sql" >/dev/null
replayed_v044_count="$(psql "${empty_url}" -X -qAt -c \
  "SELECT count(*) FROM information_schema.tables WHERE table_schema='hhy' AND table_type='BASE TABLE'")"
[[ "${replayed_v044_count}" == "203" ]] || { echo "R14 replayed V044 table count=${replayed_v044_count}" >&2; exit 1; }
echo "R14_U044_ROLLBACK_V044_REPLAY PASS tables=${replayed_v044_count}"

# Standalone execution starts from an empty disposable database; the shared
# migration smoke reaches V039 first. Both paths converge on the exact V043
# schema before the executable property matrix.
if [[ "$("${PSQL[@]}" -qAt -c "SELECT count(*) FROM information_schema.schemata WHERE schema_name='hhy'")" == "0" ]]; then
  apply_through "${DATABASE_URL}" 44
else
  if [[ "$("${PSQL[@]}" -qAt -c "SELECT count(*) FROM pg_indexes WHERE schemaname='hhy' AND indexname='uq_r12_content_review_escalation_snapshot'")" == "0" ]]; then
    "${PSQL[@]}" --single-transaction -f "${ROOT}/database/migrations/V040__r12_review_escalation.sql" >/dev/null
  fi
  if [[ "$("${PSQL[@]}" -qAt -c "SELECT count(*) FROM hhy.admin_permissions WHERE code='review.read'")" == "0" ]]; then
    "${PSQL[@]}" --single-transaction -f "${ROOT}/database/migrations/V041__r12_review_permission_alignment.sql" >/dev/null
  fi
  if [[ "$("${PSQL[@]}" -qAt -c "SELECT count(*) FROM pg_constraint WHERE connamespace='hhy'::regnamespace AND conname='ck_r13_content_view_event'")" == "0" ]]; then
    "${PSQL[@]}" --single-transaction -f "${ROOT}/database/migrations/V042__r13_activity_invariants.sql" >/dev/null
  fi
  if [[ "$("${PSQL[@]}" -qAt -c "SELECT count(*) FROM information_schema.columns WHERE table_schema='hhy' AND table_name='conversations' AND column_name='version'")" == "0" ]]; then
    "${PSQL[@]}" --single-transaction -f "${ROOT}/database/migrations/V043__r14_chat_invariants.sql" >/dev/null
  fi
  if [[ "$("${PSQL[@]}" -qAt -c "SELECT count(*) FROM information_schema.tables WHERE table_schema='hhy' AND table_name='websocket_deliveries'")" == "0" ]]; then
    "${PSQL[@]}" --single-transaction -f "${ROOT}/database/migrations/V044__r14_websocket_reliability.sql" >/dev/null
  fi
fi
"${PSQL[@]}" -f "${ROOT}/database/tests/r14_chat_invariants.sql" >/dev/null
echo "R14_CHAT_INVARIANT_PROPERTY_MATRIX PASS"
"${PSQL[@]}" -f "${ROOT}/database/tests/r14_websocket_reliability.sql" >/dev/null
echo "R14_WEBSOCKET_RELIABILITY_INVARIANTS PASS"

concurrent_actor="$("${PSQL[@]}" -qAt -c \
  "INSERT INTO hhy.users(phone,status,invite_code) VALUES ('13900004402','ACTIVE','R14WSC') RETURNING id")"
concurrency_dir="$(mktemp -d)"
concurrency_pids=()
for index in $(seq 1 8); do
  psql "${DATABASE_URL}" -X -v ON_ERROR_STOP=1 -c "
    WITH allocated AS (
      INSERT INTO hhy.websocket_user_sequences(user_id,high_watermark)
      VALUES (${concurrent_actor},1)
      ON CONFLICT (user_id) DO UPDATE
      SET high_watermark=hhy.websocket_user_sequences.high_watermark+1
      RETURNING high_watermark
    ), identified AS (
      SELECT high_watermark, gen_random_uuid() AS event_id
      FROM allocated
    )
    INSERT INTO hhy.websocket_deliveries(
      event_id,user_id,server_sequence,event_type,affected_scope,envelope,ack_required,expires_at)
    SELECT event_id,${concurrent_actor},high_watermark,'chat.message.new','CHAT',
      jsonb_build_object('eventId',event_id,'eventType','chat.message.new',
        'occurredAt',clock_timestamp(),'serverSequence',high_watermark,'payload',jsonb_build_object()),
      true,clock_timestamp()+interval '72 hours'
    FROM identified;" >"${concurrency_dir}/${index}.log" 2>&1 &
  concurrency_pids+=("$!")
done
for pid in "${concurrency_pids[@]}"; do
  if ! wait "${pid}"; then
    cat "${concurrency_dir}"/*.log >&2
    rm -rf "${concurrency_dir}"
    echo "R14 concurrent sequence allocation failed" >&2
    exit 1
  fi
done
rm -rf "${concurrency_dir}"
concurrent_state="$("${PSQL[@]}" -qAt -c "
  SELECT count(*)||'|'||count(DISTINCT server_sequence)||'|'||min(server_sequence)||'|'||max(server_sequence)
  FROM hhy.websocket_deliveries WHERE user_id=${concurrent_actor}")"
[[ "${concurrent_state}" == "8|8|1|8" ]] || {
  echo "R14 concurrent sequence allocation state=${concurrent_state}" >&2
  exit 1
}
echo "R14_WS_CONCURRENT_SEQUENCE_ALLOCATION PASS state=${concurrent_state}"
echo "R14_DATABASE_INVARIANTS PASS"
