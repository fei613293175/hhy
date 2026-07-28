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
  echo "R16 requires PostgreSQL 17 or newer, got ${server_version}" >&2
  exit 1
}

database_url_prefix="${DATABASE_URL%/*}"
if [[ "${database_url_prefix}" == "${DATABASE_URL}" || "${DATABASE_URL}" == *\?* ]]; then
  echo "DATABASE_URL must be a query-free PostgreSQL URI for disposable clone tests" >&2
  exit 2
fi

run_suffix="$(date +%s%N)"
run_suffix="${run_suffix: -8}"
template_db="r16_v044_${run_suffix}"
empty_db="r16_empty_${run_suffix}"
upgrade_db="r16_upgrade_${run_suffix}"
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
apply_through "${template_url}" 44

drop_database "${empty_db}"
"${PSQL[@]}" -q -c "CREATE DATABASE ${empty_db} TEMPLATE ${template_db}" >/dev/null
empty_url="${database_url_prefix}/${empty_db}"
psql "${empty_url}" -X -v ON_ERROR_STOP=1 --single-transaction \
  -f "${ROOT}/database/migrations/V045__r16_commerce_order_invariants.sql" >/dev/null
empty_state="$(psql "${empty_url}" -X -qAt -c "
  SELECT
    (SELECT count(*) FROM information_schema.columns
      WHERE table_schema='hhy' AND (
        (table_name='products' AND column_name IN ('product_code','description','version')) OR
        (table_name='product_skus' AND column_name IN (
          'name','member_price_cent','duration_days','benefits_json',
          'sale_starts_at','sale_ends_at','version')) OR
        (table_name='orders' AND column_name IN (
          'currency','paid_amount_cent','paid_at','no_refund_confirmed',
          'no_refund_agreement_version','no_refund_confirmed_at',
          'idempotency_key','request_hash','legacy_without_idempotency')) OR
        (table_name='order_items' AND column_name IN ('item_name','subtotal_amount_cent')) OR
        (table_name='order_price_snapshots' AND column_name='rule_versions_json'))),
    (SELECT count(*) FROM pg_trigger WHERE tgname LIKE 'trg_r16_%' AND NOT tgisinternal);")"
[[ "${empty_state}" == "22|6" ]] || {
  echo "R16 empty migration state invalid: ${empty_state}" >&2
  exit 1
}
echo "R16_EMPTY_DATABASE_MIGRATION PASS state=${empty_state}"

drop_database "${upgrade_db}"
"${PSQL[@]}" -q -c "CREATE DATABASE ${upgrade_db} TEMPLATE ${template_db}" >/dev/null
upgrade_url="${database_url_prefix}/${upgrade_db}"
psql "${upgrade_url}" -X -v ON_ERROR_STOP=1 <<'SQL' >/dev/null
DO $$
DECLARE
  actor_id bigint;
  product_id bigint;
  sku_id bigint;
  order_id bigint;
BEGIN
  INSERT INTO hhy.users(phone,status,invite_code)
  VALUES ('13900004511','ACTIVE','R16UPGRADE') RETURNING id INTO actor_id;
  INSERT INTO hhy.products(type,name,status,display_order)
  VALUES ('MEMBERSHIP','历史会员','ACTIVE',1) RETURNING id INTO product_id;
  INSERT INTO hhy.product_skus(
    product_id,code,price_cent,duration,attributes_json,status)
  VALUES (
    product_id,'R16-LEGACY-SKU',990,30,
    '{"name":"历史月度会员","benefits":[{"benefitCode":"DAYS","name":"天数","value":30,"unit":"DAY"}]}',
    'ACTIVE'
  ) RETURNING id INTO sku_id;
  INSERT INTO hhy.sku_commission_policies(
    sku_id,level1_bps,level2_bps,enabled,version)
  VALUES (sku_id,500,200,true,0);
  INSERT INTO hhy.orders(
    order_no,user_id,biz_type,biz_id,amount_cent,status,version)
  VALUES (
    'R16-UPGRADE-ORDER',actor_id,'MEMBERSHIP',product_id,990,
    'PENDING_PAYMENT',0
  ) RETURNING id INTO order_id;
  INSERT INTO hhy.order_items(
    order_id,sku_id,quantity,unit_price,snapshot_json)
  VALUES (
    order_id,sku_id,1,990,'{"name":"历史月度会员","skuCode":"R16-LEGACY-SKU"}'
  );
  INSERT INTO hhy.order_price_snapshots(
    order_id,original,discount,service_fee,payable,rule_versions)
  VALUES (order_id,1090,100,0,990,'legacy-price-v1');
END;
$$;
SQL
psql "${upgrade_url}" -X -v ON_ERROR_STOP=1 --single-transaction \
  -f "${ROOT}/database/migrations/V045__r16_commerce_order_invariants.sql" >/dev/null
upgrade_state="$(psql "${upgrade_url}" -X -qAt -c "
  SELECT product.product_code||'|'||sku.name||'|'||
    jsonb_array_length(sku.benefits_json)||'|'||sku.duration_days||'|'||
    item.item_name||'|'||item.subtotal_amount_cent||'|'||
    (snapshot.rule_versions_json->>0)||'|'||orders.currency||'|'||
    orders.legacy_without_idempotency||'|'||orders.no_refund_confirmed||'|'||
    COALESCE(orders.no_refund_agreement_version,'NULL')
  FROM hhy.orders orders
  JOIN hhy.products product ON product.id=orders.biz_id
  JOIN hhy.order_items item ON item.order_id=orders.id
  JOIN hhy.product_skus sku ON sku.id=item.sku_id
  JOIN hhy.order_price_snapshots snapshot ON snapshot.order_id=orders.id
  WHERE orders.order_no='R16-UPGRADE-ORDER';")"
[[ "${upgrade_state}" == LEGACY-*\|历史月度会员\|1\|30\|历史月度会员\|990\|legacy-price-v1\|CNY\|true\|false\|NULL ]] || {
  echo "R16 V044 upgrade did not preserve exact legacy facts: ${upgrade_state}" >&2
  exit 1
}
echo "R16_V044_UPGRADE_NO_FABRICATION PASS state=${upgrade_state}"

inject_dirty_case() {
  local case_name="$1"
  local url="$2"
  case "${case_name}" in
    product_required)
      psql "${url}" -X -v ON_ERROR_STOP=1 -c \
        "INSERT INTO hhy.products(type,name,status) VALUES ('MEMBERSHIP',NULL,'ACTIVE')" >/dev/null
      ;;
    sku_explicit)
      psql "${url}" -X -v ON_ERROR_STOP=1 <<'SQL' >/dev/null
WITH product AS (
  INSERT INTO hhy.products(type,name,status)
  VALUES ('MEMBERSHIP','商品','ACTIVE') RETURNING id
)
INSERT INTO hhy.product_skus(product_id,code,price_cent,attributes_json,status)
SELECT id,'R16-DIRTY-SKU',100,'{"name":"缺权益"}','ACTIVE' FROM product;
SQL
      ;;
    order_item)
      psql "${url}" -X -v ON_ERROR_STOP=1 <<'SQL' >/dev/null
DO $$
DECLARE actor_id bigint; order_id bigint;
BEGIN
  INSERT INTO hhy.users(phone,status,invite_code)
  VALUES ('13900004521','ACTIVE','R16DIRTYI') RETURNING id INTO actor_id;
  INSERT INTO hhy.orders(order_no,user_id,biz_type,amount_cent,status)
  VALUES ('R16-DIRTY-ITEM',actor_id,'MEMBERSHIP',100,'PENDING_PAYMENT')
  RETURNING id INTO order_id;
  INSERT INTO hhy.order_items(order_id,quantity,unit_price,snapshot_json)
  VALUES (order_id,0,100,'{}');
  INSERT INTO hhy.order_price_snapshots(
    order_id,original,discount,service_fee,payable)
  VALUES (order_id,100,0,0,100);
END;
$$;
SQL
      ;;
    price_snapshot)
      psql "${url}" -X -v ON_ERROR_STOP=1 <<'SQL' >/dev/null
DO $$
DECLARE actor_id bigint; order_id bigint;
BEGIN
  INSERT INTO hhy.users(phone,status,invite_code)
  VALUES ('13900004522','ACTIVE','R16DIRTYP') RETURNING id INTO actor_id;
  INSERT INTO hhy.orders(order_no,user_id,biz_type,amount_cent,status)
  VALUES ('R16-DIRTY-PRICE',actor_id,'MEMBERSHIP',100,'PENDING_PAYMENT')
  RETURNING id INTO order_id;
  INSERT INTO hhy.order_price_snapshots(
    order_id,original,discount,service_fee,payable)
  VALUES (order_id,100,NULL,0,100);
END;
$$;
SQL
      ;;
    order_quote)
      psql "${url}" -X -v ON_ERROR_STOP=1 <<'SQL' >/dev/null
WITH actor AS (
  INSERT INTO hhy.users(phone,status,invite_code)
  VALUES ('13900004523','ACTIVE','R16DIRTYQ') RETURNING id
)
INSERT INTO hhy.orders(order_no,user_id,biz_type,amount_cent,status)
SELECT 'R16-DIRTY-QUOTE',id,'MEMBERSHIP',100,'PENDING_PAYMENT' FROM actor;
SQL
      ;;
    *) echo "unknown dirty case ${case_name}" >&2; exit 2 ;;
  esac
}

dirty_cases=(product_required sku_explicit order_item price_snapshot order_quote)
dirty_markers=(
  R16_DIRTY_UPGRADE_PRODUCT_REQUIRED_FACT_MISSING
  R16_DIRTY_UPGRADE_SKU_EXPLICIT_FACT_MISSING
  R16_DIRTY_UPGRADE_ORDER_ITEM_FACT_MISSING
  R16_DIRTY_UPGRADE_PRICE_SNAPSHOT_INVALID
  R16_DIRTY_UPGRADE_ORDER_QUOTE_MISSING
)
for index in "${!dirty_cases[@]}"; do
  active_case_db="r16_dirty_${index}_${run_suffix}"
  drop_database "${active_case_db}"
  "${PSQL[@]}" -q -c "CREATE DATABASE ${active_case_db} TEMPLATE ${template_db}" >/dev/null
  case_url="${database_url_prefix}/${active_case_db}"
  inject_dirty_case "${dirty_cases[$index]}" "${case_url}"
  case_log="$(mktemp)"
  set +e
  psql "${case_url}" -X -v ON_ERROR_STOP=1 --single-transaction \
    -f "${ROOT}/database/migrations/V045__r16_commerce_order_invariants.sql" \
    >"${case_log}" 2>&1
  case_rc=$?
  set -e
  if [[ "${case_rc}" -eq 0 ]] || ! grep -q "${dirty_markers[$index]}" "${case_log}"; then
    cat "${case_log}" >&2
    rm -f "${case_log}"
    echo "R16 dirty upgrade case failed: ${dirty_cases[$index]}" >&2
    exit 1
  fi
  rm -f "${case_log}"
  atomic_state="$(psql "${case_url}" -X -qAt -c "
    SELECT
      (SELECT count(*) FROM information_schema.columns
        WHERE table_schema='hhy' AND (
          (table_name='products' AND column_name='product_code') OR
          (table_name='product_skus' AND column_name='benefits_json') OR
          (table_name='orders' AND column_name='idempotency_key') OR
          (table_name='order_items' AND column_name='item_name') OR
          (table_name='order_price_snapshots' AND column_name='rule_versions_json'))),
      (SELECT count(*) FROM pg_trigger WHERE tgname LIKE 'trg_r16_%' AND NOT tgisinternal),
      (SELECT count(*) FROM pg_proc
        WHERE pronamespace='hhy'::regnamespace AND proname LIKE '%r16%');")"
  [[ "${atomic_state}" == "0|0|0" ]] || {
    echo "R16 dirty upgrade left partial objects case=${dirty_cases[$index]} state=${atomic_state}" >&2
    exit 1
  }
  drop_database "${active_case_db}"
  active_case_db=""
  echo "R16_DIRTY_UPGRADE_BLOCKED PASS case=${dirty_cases[$index]} marker=${dirty_markers[$index]}"
done
echo "R16_DIRTY_UPGRADE_ATOMIC_MATRIX PASS cases=${#dirty_cases[@]}"

facts_before="$(psql "${upgrade_url}" -X -qAt -c "
  SELECT
    (SELECT count(*) FROM hhy.orders)||'|'||
    (SELECT count(*) FROM hhy.product_skus)||'|'||
    (SELECT count(*) FROM information_schema.columns
      WHERE table_schema='hhy' AND table_name='orders' AND column_name='idempotency_key');")"
rollback_log="$(mktemp)"
set +e
psql "${upgrade_url}" -X -v ON_ERROR_STOP=1 --single-transaction \
  -f "${ROOT}/database/rollback/U045__r16_commerce_order_invariants_DEV_ONLY.sql" \
  >"${rollback_log}" 2>&1
rollback_rc=$?
set -e
if [[ "${rollback_rc}" -eq 0 ]] || ! grep -q 'R16_U045_BUSINESS_FACTS_PRESENT' "${rollback_log}"; then
  cat "${rollback_log}" >&2
  rm -f "${rollback_log}"
  echo "U045 accepted a database containing commerce facts" >&2
  exit 1
fi
rm -f "${rollback_log}"
facts_after="$(psql "${upgrade_url}" -X -qAt -c "
  SELECT
    (SELECT count(*) FROM hhy.orders)||'|'||
    (SELECT count(*) FROM hhy.product_skus)||'|'||
    (SELECT count(*) FROM information_schema.columns
      WHERE table_schema='hhy' AND table_name='orders' AND column_name='idempotency_key');")"
[[ "${facts_before}" == "${facts_after}" ]] || {
  echo "U045 facts rejection was not atomic before=${facts_before} after=${facts_after}" >&2
  exit 1
}
echo "R16_U045_ROLLBACK_WITH_FACTS_REJECTED_ATOMICALLY PASS state=${facts_after}"

psql "${empty_url}" -X -v ON_ERROR_STOP=1 --single-transaction \
  -f "${ROOT}/database/rollback/U045__r16_commerce_order_invariants_DEV_ONLY.sql" >/dev/null
rolled_back_columns="$(psql "${empty_url}" -X -qAt -c "
  SELECT count(*) FROM information_schema.columns
  WHERE table_schema='hhy' AND (
    (table_name='products' AND column_name='product_code') OR
    (table_name='product_skus' AND column_name='benefits_json') OR
    (table_name='orders' AND column_name='idempotency_key') OR
    (table_name='order_items' AND column_name='item_name') OR
    (table_name='order_price_snapshots' AND column_name='rule_versions_json'));")"
[[ "${rolled_back_columns}" == "0" ]] || {
  echo "R16 U045 rollback left columns=${rolled_back_columns}" >&2
  exit 1
}
psql "${empty_url}" -X -v ON_ERROR_STOP=1 --single-transaction \
  -f "${ROOT}/database/migrations/V045__r16_commerce_order_invariants.sql" >/dev/null
replayed_columns="$(psql "${empty_url}" -X -qAt -c "
  SELECT count(*) FROM information_schema.columns
  WHERE table_schema='hhy' AND (
    (table_name='products' AND column_name IN ('product_code','description','version')) OR
    (table_name='product_skus' AND column_name IN (
      'name','member_price_cent','duration_days','benefits_json',
      'sale_starts_at','sale_ends_at','version')) OR
    (table_name='orders' AND column_name IN (
      'currency','paid_amount_cent','paid_at','no_refund_confirmed',
      'no_refund_agreement_version','no_refund_confirmed_at',
      'idempotency_key','request_hash','legacy_without_idempotency')) OR
    (table_name='order_items' AND column_name IN ('item_name','subtotal_amount_cent')) OR
    (table_name='order_price_snapshots' AND column_name='rule_versions_json'));")"
[[ "${replayed_columns}" == "22" ]] || {
  echo "R16 U045/V045 replay columns=${replayed_columns}" >&2
  exit 1
}
echo "R16_U045_ROLLBACK_V045_REPLAY PASS columns=${replayed_columns}"

if [[ "$("${PSQL[@]}" -qAt -c \
  "SELECT count(*) FROM information_schema.schemata WHERE schema_name='hhy'")" == "0" ]]; then
  apply_through "${DATABASE_URL}" 45
elif [[ "$("${PSQL[@]}" -qAt -c "
  SELECT count(*) FROM information_schema.columns
  WHERE table_schema='hhy' AND table_name='orders'
    AND column_name='legacy_without_idempotency'")" == "0" ]]; then
  "${PSQL[@]}" --single-transaction \
    -f "${ROOT}/database/migrations/V045__r16_commerce_order_invariants.sql" >/dev/null
fi

"${PSQL[@]}" -f "${ROOT}/database/tests/r16_commerce_order_invariants.sql" >/dev/null
echo "R16_COMMERCE_ORDER_INVARIANT_PROPERTY_MATRIX PASS"

concurrent_actor="$("${PSQL[@]}" -qAt -c "
  INSERT INTO hhy.users(phone,status,invite_code)
  VALUES ('139${run_suffix:0:8}','ACTIVE','R16CON${run_suffix:0:5}')
  RETURNING id")"
concurrency_dir="$(mktemp -d)"
concurrency_pids=()
for index in $(seq 1 8); do
  (
    if psql "${DATABASE_URL}" -X -v ON_ERROR_STOP=1 <<SQL >/dev/null 2>&1
WITH created_order AS (
  INSERT INTO hhy.orders(
    order_no,user_id,biz_type,amount_cent,status,currency,
    idempotency_key,request_hash)
  VALUES (
    'R16-CON-${run_suffix}-${index}',${concurrent_actor},'MEMBERSHIP',100,
    'PENDING_PAYMENT','CNY','r16-concurrent-${run_suffix}',repeat('c',64))
  RETURNING id
)
INSERT INTO hhy.order_price_snapshots(
  order_id,original,discount,service_fee,payable,rule_versions_json)
SELECT id,100,0,0,100,'["concurrency-v1"]'::jsonb FROM created_order;
SQL
    then
      echo PASS >"${concurrency_dir}/${index}.result"
    else
      echo REJECTED >"${concurrency_dir}/${index}.result"
    fi
  ) &
  concurrency_pids+=("$!")
done
for pid in "${concurrency_pids[@]}"; do
  wait "${pid}"
done
concurrency_success="$(grep -l '^PASS$' "${concurrency_dir}"/*.result | wc -l | tr -d ' ')"
rm -rf "${concurrency_dir}"
concurrent_state="$("${PSQL[@]}" -qAt -c "
  SELECT count(*)||'|'||count(DISTINCT request_hash)
  FROM hhy.orders
  WHERE user_id=${concurrent_actor}
    AND biz_type='MEMBERSHIP'
    AND idempotency_key='r16-concurrent-${run_suffix}';")"
[[ "${concurrency_success}" == "1" && "${concurrent_state}" == "1|1" ]] || {
  echo "R16 concurrent idempotency invalid success=${concurrency_success} state=${concurrent_state}" >&2
  exit 1
}
echo "R16_ORDER_CONCURRENT_IDEMPOTENCY PASS success=${concurrency_success} state=${concurrent_state}"
echo "R16_DATABASE_INVARIANTS PASS"
