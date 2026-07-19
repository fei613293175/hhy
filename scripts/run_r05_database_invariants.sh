#!/usr/bin/env bash
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
: "${DATABASE_URL:?DATABASE_URL is required}"
: "${HHY_DB_SMOKE_CONFIRM:?Set HHY_DB_SMOKE_CONFIRM=YES for a disposable database}"
[[ "${HHY_DB_SMOKE_CONFIRM}" == "YES" ]] || {
  echo "Refusing destructive R05 database tests without HHY_DB_SMOKE_CONFIRM=YES" >&2
  exit 2
}
command -v psql >/dev/null 2>&1 || { echo "psql is required" >&2; exit 2; }

PSQL=(psql "${DATABASE_URL}" -X -v ON_ERROR_STOP=1)
"${PSQL[@]}" -f "${ROOT}/database/tests/r05_identity_invariants.sql" >/dev/null
echo "R05_IDENTITY_INVARIANTS PASS"

"${PSQL[@]}" --single-transaction \
  -f "${ROOT}/database/rollback/U023__r05_identity_invariants.sql" >/dev/null
remaining="$(${PSQL[@]} -qAt -c "
  SELECT
    (SELECT count(*) FROM pg_constraint
      WHERE connamespace='hhy'::regnamespace AND conname LIKE '%r05_%'),
    (SELECT count(*) FROM pg_indexes
      WHERE schemaname='hhy' AND indexname LIKE '%r05%'),
    (SELECT count(*) FROM pg_trigger
      WHERE tgname='trg_r05_identity_review_immutable' AND NOT tgisinternal),
    (SELECT count(*) FROM information_schema.columns
      WHERE table_schema='hhy' AND (table_name,column_name) IN (
        ('identity_profiles','verified_at'),('identity_profiles','frozen_at'),
        ('identity_profiles','freeze_reason'),
        ('identity_verification_sessions','idempotency_key'),
        ('identity_verification_sessions','attempt_no'),
        ('identity_verification_sessions','retry_of_session_id'),
        ('identity_verification_sessions','last_event'),
        ('identity_verification_sessions','completed_at'),
        ('identity_provider_requests','idempotency_key'),
        ('identity_provider_requests','request_hash'),
        ('identity_provider_requests','status'),
        ('identity_provider_requests','attempt_no'),
        ('identity_provider_requests','from_status'),
        ('identity_provider_requests','to_status'),
        ('identity_provider_requests','event'),
        ('identity_provider_requests','error_code'),
        ('identity_provider_requests','completed_at'),
        ('identity_media','storage_scope'),('identity_media','purpose'),
        ('identity_media','version'),
        ('identity_review_records','from_status'),
        ('identity_review_records','to_status'),('identity_review_records','event'),
        ('identity_review_records','idempotency_key'),
        ('identity_review_records','expected_version'),
        ('sensitive_data_access_logs','operation'),
        ('sensitive_data_access_logs','request_id'),
        ('sensitive_data_access_logs','media_object_id')
      ));")"
[[ "${remaining}" == "0|0|0|0" ]] || {
  echo "R05 rollback left constraints, indexes, triggers, or columns: ${remaining}" >&2
  exit 1
}
echo "R05_U023_ROLLBACK PASS"

"${PSQL[@]}" --single-transaction \
  -f "${ROOT}/database/migrations/V023__r05_identity_invariants.sql" >/dev/null
reapplied="$(${PSQL[@]} -qAt -c "
  SELECT
    (SELECT count(*) FROM pg_constraint
      WHERE connamespace='hhy'::regnamespace AND conname LIKE '%r05_%'),
    (SELECT count(*) FROM pg_indexes
      WHERE schemaname='hhy' AND indexname LIKE '%r05%'),
    (SELECT count(*) FROM pg_trigger
      WHERE tgname='trg_r05_identity_review_immutable' AND NOT tgisinternal),
    (SELECT count(*) FROM information_schema.columns
      WHERE table_schema='hhy' AND (table_name,column_name) IN (
        ('identity_profiles','verified_at'),('identity_profiles','frozen_at'),
        ('identity_profiles','freeze_reason'),
        ('identity_verification_sessions','idempotency_key'),
        ('identity_verification_sessions','attempt_no'),
        ('identity_verification_sessions','retry_of_session_id'),
        ('identity_verification_sessions','last_event'),
        ('identity_verification_sessions','completed_at'),
        ('identity_provider_requests','idempotency_key'),
        ('identity_provider_requests','request_hash'),
        ('identity_provider_requests','status'),
        ('identity_provider_requests','attempt_no'),
        ('identity_provider_requests','from_status'),
        ('identity_provider_requests','to_status'),
        ('identity_provider_requests','event'),
        ('identity_provider_requests','error_code'),
        ('identity_provider_requests','completed_at'),
        ('identity_media','storage_scope'),('identity_media','purpose'),
        ('identity_media','version'),
        ('identity_review_records','from_status'),
        ('identity_review_records','to_status'),('identity_review_records','event'),
        ('identity_review_records','idempotency_key'),
        ('identity_review_records','expected_version'),
        ('sensitive_data_access_logs','operation'),
        ('sensitive_data_access_logs','request_id'),
        ('sensitive_data_access_logs','media_object_id')
      ));")"
[[ "${reapplied}" == "30|12|1|28" ]] || {
  echo "R05 reapply counts were unexpected: ${reapplied}" >&2
  exit 1
}
"${PSQL[@]}" -f "${ROOT}/database/tests/r05_identity_invariants.sql" >/dev/null
echo "R05_V023_REAPPLY PASS constraints=30 indexes=12 trigger=1 columns=28"
