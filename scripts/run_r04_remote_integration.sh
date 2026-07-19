#!/usr/bin/env bash
set -euo pipefail

ROOT="${HHY_R04_SOURCE_ROOT:-$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)}"
OUTPUT="${HHY_R04_OUTPUT_DIR:-${ROOT}/artifacts/validation/r04-test-evidence/raw}"
MAVEN_CACHE="${HHY_MAVEN_CACHE:-/root/.m2}"
DB_CONTAINER="${HHY_R04_DB_CONTAINER:-hhy-r04-integration-db}"

[[ -f "${ROOT}/releases/R04/RELEASE_MANIFEST.yaml" ]] || {
  echo "HHY_R04_SOURCE_ROOT is not an R04 source tree: ${ROOT}" >&2
  exit 2
}
[[ "${MAVEN_CACHE}" = /* ]] || { echo "HHY_MAVEN_CACHE must be absolute" >&2; exit 2; }
case "${DB_CONTAINER}" in
  hhy-r04-*) ;;
  *) echo "R04 disposable database container must start with hhy-r04-" >&2; exit 2 ;;
esac
command -v docker >/dev/null 2>&1 || { echo "docker is required" >&2; exit 2; }
mkdir -p "${OUTPUT}"

run_backend() {
  docker run --rm \
    -v "${ROOT}:/workspace" \
    -v "${MAVEN_CACHE}:/root/.m2" \
    -w /workspace/services/backend \
    maven:3.9.11-eclipse-temurin-21 \
    mvn -B verify
}

run_postgres() {
  HHY_R04_DB_CONTAINER="${DB_CONTAINER}" \
    bash "${ROOT}/scripts/run_r04_disposable_postgres_container.sh"
}

run_android() {
  docker run --rm \
    -e HHY_API_BASE_URL=https://api.orbexa.cc \
    -v "${ROOT}:/workspace" \
    -v hhy-r01-android-gradle-cache:/root/.gradle \
    -w /workspace/apps/android \
    hhy-android-toolchain:r01-46fb273 \
    sh -lc 'sh ./gradlew --no-daemon lintDebug testDebugUnitTest assembleDebug'
}

run_backend >"${OUTPUT}/backend-integration.log" 2>&1 &
backend_pid=$!
run_postgres >"${OUTPUT}/postgres17-integration.log" 2>&1 &
postgres_pid=$!
run_android >"${OUTPUT}/android-integration.log" 2>&1 &
android_pid=$!

set +e
wait "${backend_pid}"; backend_rc=$?
wait "${postgres_pid}"; postgres_rc=$?
wait "${android_pid}"; android_rc=$?
set -e

printf 'backend=%s\npostgres=%s\nandroid=%s\n' "${backend_rc}" "${postgres_rc}" "${android_rc}"
sha256sum \
  "${OUTPUT}/backend-integration.log" \
  "${OUTPUT}/postgres17-integration.log" \
  "${OUTPUT}/android-integration.log"

[[ "${backend_rc}" -eq 0 && "${postgres_rc}" -eq 0 && "${android_rc}" -eq 0 ]]
