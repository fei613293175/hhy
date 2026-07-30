#!/usr/bin/env bash
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
: "${DATABASE_URL:?DATABASE_URL is required}"
: "${HHY_DB_SMOKE_CONFIRM:?Set HHY_DB_SMOKE_CONFIRM=YES for a disposable database}"
[[ "${HHY_DB_SMOKE_CONFIRM}" == "YES" ]] || {
  echo "Refusing destructive R07 database tests without HHY_DB_SMOKE_CONFIRM=YES" >&2
  exit 2
}
command -v psql >/dev/null 2>&1 || { echo "psql is required" >&2; exit 2; }

PSQL=(psql "${DATABASE_URL}" -X -v ON_ERROR_STOP=1)
"${PSQL[@]}" -f "${ROOT}/database/tests/r07_search_invariants.sql" >/dev/null
echo "R07_SEARCH_INVARIANTS PASS"
"${PSQL[@]}" -f "${ROOT}/database/tests/r07_contact_contract_alignment.sql" >/dev/null
echo "R07_CONTACT_CONTRACT_INVARIANTS PASS"

"${PSQL[@]}" -c "
  INSERT INTO hhy.users(phone,status,invite_code) VALUES ('13900000703','ACTIVE','R07ROLLBACK');
  INSERT INTO hhy.content_posts(owner_id,type,title,status)
    SELECT id,'PROJECT','R07 rollback contact','ONLINE' FROM hhy.users WHERE invite_code='R07ROLLBACK';
  INSERT INTO hhy.content_contacts(content_id,channel,value_cipher,display_mask,sort_order)
    SELECT id,'EMAIL','987654321','r***@example.com',1
    FROM hhy.content_posts WHERE title='R07 rollback contact';" >/dev/null

"${PSQL[@]}" --single-transaction -f "${ROOT}/database/rollback/U032__r07_contact_contract_alignment.sql" >/dev/null
numeric_state="$(${PSQL[@]} -qAt -c "
  SELECT
    (SELECT format_type(attribute.atttypid,attribute.atttypmod)
     FROM pg_attribute attribute
     JOIN pg_class relation ON relation.oid=attribute.attrelid
     JOIN pg_namespace namespace ON namespace.oid=relation.relnamespace
     WHERE namespace.nspname='hhy' AND relation.relname='content_contacts'
       AND attribute.attname='value_cipher' AND attribute.attnum>0 AND NOT attribute.attisdropped),
    (SELECT value_cipher::text FROM hhy.content_contacts contact
     JOIN hhy.content_posts content ON content.id=contact.content_id
     WHERE content.title='R07 rollback contact'),
    (SELECT count(*) FROM pg_constraint WHERE connamespace='hhy'::regnamespace
       AND conname='ck_r07_contact_cipher_envelope');")"
[[ "${numeric_state}" == "bigint|987654321|0" ]] || {
  echo "R07 U032 numeric rollback state was unexpected: ${numeric_state}" >&2
  exit 1
}
echo "R07_U032_NUMERIC_ROLLBACK PASS state=${numeric_state}"

"${PSQL[@]}" --single-transaction -f "${ROOT}/database/migrations/V032__r07_contact_contract_alignment.sql" >/dev/null
replayed_state="$(${PSQL[@]} -qAt -c "
  SELECT
    (SELECT format_type(attribute.atttypid,attribute.atttypmod)
     FROM pg_attribute attribute
     JOIN pg_class relation ON relation.oid=attribute.attrelid
     JOIN pg_namespace namespace ON namespace.oid=relation.relnamespace
     WHERE namespace.nspname='hhy' AND relation.relname='content_contacts'
       AND attribute.attname='value_cipher' AND attribute.attnum>0 AND NOT attribute.attisdropped),
    (SELECT value_cipher FROM hhy.content_contacts contact
     JOIN hhy.content_posts content ON content.id=contact.content_id
     WHERE content.title='R07 rollback contact'),
    (SELECT count(*) FROM pg_constraint WHERE connamespace='hhy'::regnamespace
       AND conname='ck_r07_contact_cipher_envelope');")"
[[ "${replayed_state}" == "character varying(2048)|987654321|1" ]] || {
  echo "R07 V032 replay state was unexpected: ${replayed_state}" >&2
  exit 1
}
echo "R07_V032_REPLAY PASS state=${replayed_state}"

"${PSQL[@]}" -c "
  UPDATE hhy.content_contacts SET value_cipher='hhy-contact-v1.test-nonce.test-ciphertext'
  WHERE content_id=(SELECT id FROM hhy.content_posts WHERE title='R07 rollback contact');" >/dev/null
failure_log="$(mktemp)"
trap 'rm -f "${failure_log}"' EXIT
if "${PSQL[@]}" --single-transaction \
    -f "${ROOT}/database/rollback/U032__r07_contact_contract_alignment.sql" >"${failure_log}" 2>&1; then
  echo "R07 U032 unexpectedly accepted a nonnumeric contact envelope" >&2
  exit 1
fi
grep -q "R07_CONTACT_CIPHER_ROLLBACK_REQUIRES_DATA_EXPORT" "${failure_log}" || {
  echo "R07 U032 failed without the required safety reason" >&2
  cat "${failure_log}" >&2
  exit 1
}
blocked_state="$(${PSQL[@]} -qAt -c "
  SELECT
    (SELECT format_type(attribute.atttypid,attribute.atttypmod)
     FROM pg_attribute attribute
     JOIN pg_class relation ON relation.oid=attribute.attrelid
     JOIN pg_namespace namespace ON namespace.oid=relation.relnamespace
     WHERE namespace.nspname='hhy' AND relation.relname='content_contacts'
       AND attribute.attname='value_cipher' AND attribute.attnum>0 AND NOT attribute.attisdropped),
    (SELECT value_cipher FROM hhy.content_contacts contact
     JOIN hhy.content_posts content ON content.id=contact.content_id
     WHERE content.title='R07 rollback contact'),
    (SELECT count(*) FROM pg_constraint WHERE connamespace='hhy'::regnamespace
       AND conname='ck_r07_contact_cipher_envelope');")"
[[ "${blocked_state}" == "character varying(2048)|hhy-contact-v1.test-nonce.test-ciphertext|1" ]] || {
  echo "R07 U032 refusal was not atomic: ${blocked_state}" >&2
  exit 1
}
echo "R07_U032_ENVELOPE_REFUSAL PASS state=${blocked_state}"
"${PSQL[@]}" -c "
  DELETE FROM hhy.content_contacts WHERE content_id=(SELECT id FROM hhy.content_posts WHERE title='R07 rollback contact');
  DELETE FROM hhy.content_posts WHERE title='R07 rollback contact';
  DELETE FROM hhy.users WHERE invite_code='R07ROLLBACK';" >/dev/null

"${PSQL[@]}" --single-transaction -f "${ROOT}/database/rollback/U031__r07_search_invariants.sql" >/dev/null
remaining="$(${PSQL[@]} -qAt -c "
  SELECT
    (SELECT count(*) FROM pg_constraint WHERE connamespace='hhy'::regnamespace AND conname LIKE '%r07_%'),
    (SELECT count(*) FROM pg_indexes WHERE schemaname='hhy' AND indexname LIKE '%r07%'),
    (SELECT count(*) FROM pg_trigger WHERE tgname LIKE 'trg_r07_%' AND NOT tgisinternal);")"
weight_default="$(${PSQL[@]} -qAt -c "
  SELECT COALESCE(column_default, '') FROM information_schema.columns
  WHERE table_schema='hhy' AND table_name='hot_search_terms' AND column_name='weight';")"
[[ "${remaining}|${weight_default}" == "1|0|0|" ]] || {
  echo "R07 rollback left database objects or weight default: ${remaining}|${weight_default}" >&2
  exit 1
}
echo "R07_U031_ROLLBACK PASS"

"${PSQL[@]}" --single-transaction -f "${ROOT}/database/migrations/V031__r07_search_invariants.sql" >/dev/null
reapplied="$(${PSQL[@]} -qAt -c "
  SELECT
    (SELECT count(*) FROM pg_constraint WHERE connamespace='hhy'::regnamespace AND conname LIKE '%r07_%'),
    (SELECT count(*) FROM pg_indexes WHERE schemaname='hhy' AND indexname LIKE '%r07%'),
    (SELECT count(*) FROM pg_trigger WHERE tgname LIKE 'trg_r07_%' AND NOT tgisinternal);")"
weight_default="$(${PSQL[@]} -qAt -c "
  SELECT COALESCE(column_default, '') FROM information_schema.columns
  WHERE table_schema='hhy' AND table_name='hot_search_terms' AND column_name='weight';")"
[[ "${reapplied}|${weight_default}" == "5|4|1|0" ]] || {
  echo "R07 reapply counts were unexpected: ${reapplied}|${weight_default}" >&2
  exit 1
}
"${PSQL[@]}" -f "${ROOT}/database/tests/r07_search_invariants.sql" >/dev/null
"${PSQL[@]}" -f "${ROOT}/database/tests/r07_contact_contract_alignment.sql" >/dev/null
echo "R07_V031_REAPPLY PASS constraints=5 indexes=4 triggers=1"
