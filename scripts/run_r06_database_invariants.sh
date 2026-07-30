#!/usr/bin/env bash
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
: "${DATABASE_URL:?DATABASE_URL is required}"
: "${HHY_DB_SMOKE_CONFIRM:?Set HHY_DB_SMOKE_CONFIRM=YES for a disposable database}"
[[ "${HHY_DB_SMOKE_CONFIRM}" == "YES" ]] || {
  echo "Refusing destructive R06 database tests without HHY_DB_SMOKE_CONFIRM=YES" >&2
  exit 2
}
command -v psql >/dev/null 2>&1 || { echo "psql is required" >&2; exit 2; }

PSQL=(psql "${DATABASE_URL}" -X -v ON_ERROR_STOP=1)
"${PSQL[@]}" -f "${ROOT}/database/tests/r06_content_home_invariants.sql" >/dev/null
echo "R06_CONTENT_HOME_INVARIANTS PASS"

permission_state="$(${PSQL[@]} -qAt -c "
  SELECT
    (SELECT count(*) FROM hhy.admin_permissions WHERE code IN (
      'content.ban','content.recommend','content.official','content.dict.read','content.dict.write')),
    (SELECT count(*) FROM (
        SELECT DISTINCT base.role_id FROM hhy.admin_role_permissions base
        JOIN hhy.admin_permissions managed ON managed.id=base.permission_id
        WHERE managed.code='content.manage'
      ) roles
      CROSS JOIN hhy.admin_permissions added
      WHERE added.code IN ('content.ban','content.recommend','content.official','content.dict.write')
        AND NOT EXISTS (
          SELECT 1 FROM hhy.admin_role_permissions inherited
          WHERE inherited.role_id=roles.role_id AND inherited.permission_id=added.id)),
    (SELECT count(*) FROM (
        SELECT DISTINCT base.role_id FROM hhy.admin_role_permissions base
        JOIN hhy.admin_permissions readable ON readable.id=base.permission_id
        WHERE readable.code='content.read'
      ) roles
      CROSS JOIN hhy.admin_permissions added
      WHERE added.code='content.dict.read'
        AND NOT EXISTS (
          SELECT 1 FROM hhy.admin_role_permissions inherited
          WHERE inherited.role_id=roles.role_id AND inherited.permission_id=added.id)); ")"
[[ "${permission_state}" == "5|0|0" ]] || {
  echo "R06 content permission inheritance was unexpected: ${permission_state}" >&2
  exit 1
}
super_admin_content="$(${PSQL[@]} -qAt -c "
  SELECT count(*) FROM hhy.admin_role_permissions role_permission
  JOIN hhy.admin_roles role ON role.id=role_permission.role_id AND role.code='SUPER_ADMIN'
  JOIN hhy.admin_permissions permission ON permission.id=role_permission.permission_id
  WHERE permission.code IN (
    'content.read','content.manage','content.ban','content.recommend',
    'content.official','content.dict.read','content.dict.write');")"
[[ "${super_admin_content}" == "7" ]] || {
  echo "R06 SUPER_ADMIN content grant was unexpected: ${super_admin_content}" >&2
  exit 1
}
echo "R06_CONTENT_PERMISSIONS PASS"

"${PSQL[@]}" --single-transaction -f "${ROOT}/database/rollback/U030__r06_content_admin_permissions.sql" >/dev/null
permission_count="$(${PSQL[@]} -qAt -c "
  SELECT count(*) FROM hhy.admin_permissions WHERE code IN (
    'content.ban','content.recommend','content.official','content.dict.read','content.dict.write');")"
base_grants="$(${PSQL[@]} -qAt -c "
  SELECT count(*) FROM hhy.admin_role_permissions role_permission
  JOIN hhy.admin_permissions permission ON permission.id=role_permission.permission_id
  WHERE permission.code IN ('content.read','content.manage');")"
[[ "${permission_count}|${base_grants}" == "0|0" ]] || {
  echo "R06 U030 rollback left content permissions or grants: ${permission_count}|${base_grants}" >&2
  exit 1
}
"${PSQL[@]}" --single-transaction -f "${ROOT}/database/migrations/V030__r06_content_admin_permissions.sql" >/dev/null
permission_count="$(${PSQL[@]} -qAt -c "
  SELECT count(*) FROM hhy.admin_permissions WHERE code IN (
    'content.ban','content.recommend','content.official','content.dict.read','content.dict.write');")"
[[ "${permission_count}" == "5" ]] || {
  echo "R06 V030 reapply did not restore content permissions: ${permission_count}" >&2
  exit 1
}
echo "R06_U030_ROLLBACK_REAPPLY PASS permissions=5"

"${PSQL[@]}" --single-transaction -f "${ROOT}/database/rollback/U029__r06_content_home_invariants.sql" >/dev/null
remaining="$(${PSQL[@]} -qAt -c "
  SELECT
    (SELECT count(*) FROM pg_constraint WHERE connamespace='hhy'::regnamespace AND conname LIKE '%r06_%'),
    (SELECT count(*) FROM pg_indexes WHERE schemaname='hhy' AND indexname LIKE '%r06%'),
    (SELECT count(*) FROM pg_trigger WHERE tgname LIKE 'trg_r06_%' AND NOT tgisinternal);")"
[[ "${remaining}" == "0|0|0" ]] || {
  echo "R06 rollback left constraints, indexes, or triggers: ${remaining}" >&2
  exit 1
}
echo "R06_U029_ROLLBACK PASS"

"${PSQL[@]}" --single-transaction -f "${ROOT}/database/migrations/V029__r06_content_home_invariants.sql" >/dev/null
reapplied="$(${PSQL[@]} -qAt -c "
  SELECT
    (SELECT count(*) FROM pg_constraint WHERE connamespace='hhy'::regnamespace AND conname LIKE '%r06_%'),
    (SELECT count(*) FROM pg_indexes WHERE schemaname='hhy' AND indexname LIKE '%r06%'),
    (SELECT count(*) FROM pg_trigger WHERE tgname LIKE 'trg_r06_%' AND NOT tgisinternal);")"
[[ "${reapplied}" == "12|7|8" ]] || {
  echo "R06 reapply counts were unexpected: ${reapplied}" >&2
  exit 1
}
"${PSQL[@]}" -f "${ROOT}/database/tests/r06_content_home_invariants.sql" >/dev/null
echo "R06_V029_REAPPLY PASS constraints=12 indexes=7 triggers=8"
