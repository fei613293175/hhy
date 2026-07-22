#!/usr/bin/env bash
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
: "${DATABASE_URL:?DATABASE_URL is required}"
: "${HHY_DB_SMOKE_CONFIRM:?Set HHY_DB_SMOKE_CONFIRM=YES for a disposable database}"
[[ "${HHY_DB_SMOKE_CONFIRM}" == "YES" ]] || {
  echo "Refusing destructive R08 backend tests without HHY_DB_SMOKE_CONFIRM=YES" >&2
  exit 2
}
command -v psql >/dev/null 2>&1 || { echo "psql is required" >&2; exit 2; }

PSQL=(psql "${DATABASE_URL}" -X -v ON_ERROR_STOP=1)
"${PSQL[@]}" -f "${ROOT}/database/tests/r08_backend_configuration.sql" >/dev/null
echo "R08_BACKEND_CONFIGURATION PASS"

"${PSQL[@]}" -c "UPDATE hhy.system_configs SET value_json='7'::jsonb
  WHERE key='content.limit.normal.online' AND scope='GLOBAL';" >/dev/null
"${PSQL[@]}" --single-transaction -f "${ROOT}/database/migrations/V034__r08_backend_configuration.sql" >/dev/null
preserved="$(${PSQL[@]} -qAt -c "SELECT value_json #>> '{}' FROM hhy.system_configs
  WHERE key='content.limit.normal.online' AND scope='GLOBAL';")"
[[ "${preserved}" == "7" ]] || { echo "V034 overwrote an active value: ${preserved}" >&2; exit 1; }
if "${PSQL[@]}" --single-transaction -f "${ROOT}/database/rollback/U034__r08_backend_configuration.sql" >/dev/null 2>&1; then
  echo "U034 accepted a changed active configuration" >&2
  exit 1
fi
"${PSQL[@]}" -c "UPDATE hhy.system_configs SET value_json='3'::jsonb
  WHERE key='content.limit.normal.online' AND scope='GLOBAL';" >/dev/null
echo "R08_CONFIG_NON_OVERWRITE_AND_ROLLBACK_GUARD PASS"

"${PSQL[@]}" -c "
  INSERT INTO hhy.users(phone,status,invite_code) VALUES ('13900000804','ACTIVE','R08LONG');
  INSERT INTO hhy.content_posts(owner_id,type,title,status)
  SELECT id,'PROJECT',repeat('长',300),'DRAFT' FROM hhy.users WHERE invite_code='R08LONG';
  INSERT INTO hhy.project_details(content_id,cooperation)
  SELECT id,repeat('说明',150) FROM hhy.content_posts WHERE title=repeat('长',300);" >/dev/null
if "${PSQL[@]}" --single-transaction -f "${ROOT}/database/rollback/U034__r08_backend_configuration.sql" >/dev/null 2>&1; then
  echo "U034 truncated or accepted legal long R08 data" >&2
  exit 1
fi
"${PSQL[@]}" -c "DELETE FROM hhy.project_details WHERE content_id IN
  (SELECT id FROM hhy.content_posts WHERE title=repeat('长',300));
  DELETE FROM hhy.content_posts WHERE title=repeat('长',300);
  DELETE FROM hhy.users WHERE invite_code='R08LONG';" >/dev/null
echo "R08_SCHEMA_ROLLBACK_GUARD PASS"

"${PSQL[@]}" --single-transaction -f "${ROOT}/database/rollback/U034__r08_backend_configuration.sql" >/dev/null
removed="$(${PSQL[@]} -qAt -c "SELECT count(*) FROM hhy.system_configs WHERE scope='GLOBAL' AND key IN
 ('content.limit.normal.online','content.limit.month.online','content.limit.quarter.online',
  'content.limit.year.online','content.team_leader_per_account','content.limit.normal.pending',
  'content.limit.normal.drafts','content.limit.normal.daily_submissions',
  'chat.stranger.daily_conversation_limit','chat.message.per_minute_limit','chat.image.max_mb',
  'chat.history.retention_days','domain.h5.host');")"
capacity="$(${PSQL[@]} -qAt -c "SELECT character_maximum_length FROM information_schema.columns
  WHERE table_schema='hhy' AND table_name='content_posts' AND column_name='title';")"
[[ "${removed}" == "0" && "${capacity}" == "255" ]] || {
  echo "U034 rollback state unexpected: configs=${removed} title_capacity=${capacity}" >&2
  exit 1
}
echo "R08_U034_ROLLBACK PASS"

"${PSQL[@]}" --single-transaction -f "${ROOT}/database/migrations/V034__r08_backend_configuration.sql" >/dev/null
"${PSQL[@]}" -f "${ROOT}/database/tests/r08_backend_configuration.sql" >/dev/null
echo "R08_V034_REPLAY PASS"
