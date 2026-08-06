#!/usr/bin/env bash
set -euo pipefail

ROOT="$(cd "$(dirname "\${BASH_SOURCE[0]}")/.." && pwd)"
: "\${DATABASE_URL:?DATABASE_URL is required}"
: "\${HHY_DB_SMOKE_CONFIRM:?Set HHY_DB_SMOKE_CONFIRM=YES for a disposable database}"
[[ "\${HHY_DB_SMOKE_CONFIRM}" == "YES" ]] || exit 2
command -v psql >/dev/null 2>&1 || { echo "psql is required" >&2; exit 2; }

PSQL=(psql "\${DATABASE_URL}" -X -v ON_ERROR_STOP=1)
server_version="$("\${PSQL[@]}" -qAt -c "SHOW server_version_num")"
(( server_version >= 160000 )) || { echo "R24 requires PostgreSQL 16 or newer, got \${server_version}" >&2; exit 1; }

apply_through() {
  local url="$1" target="$2" migration name version number
  for migration in "\${ROOT}"/database/migrations/V*.sql; do
    name="$(basename "\${migration}")"
    version="\${name%%__*}"
    number=$((10#\${version#V}))
    (( number > target )) && break
    psql "\${url}" -X -v ON_ERROR_STOP=1 --single-transaction -f "\${migration}" >/dev/null
  done
}

if [[ "$("\${PSQL[@]}" -qAt -c "SELECT count(*) FROM information_schema.tables WHERE table_schema='hhy'")" == "0" ]]; then
  apply_through "\${DATABASE_URL}" 57
else
  "\${PSQL[@]}" --single-transaction -f "\${ROOT}/database/migrations/V057__r24_reward_withdrawal_invariants.sql" >/dev/null
fi

"\${PSQL[@]}" -f "\${ROOT}/database/tests/r24_reward_withdrawal_invariants.sql" >/dev/null
echo "R24_REWARD_WITHDRAWAL_INVARIANTS PASS"
