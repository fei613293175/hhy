#!/usr/bin/env bash
set -euo pipefail

script_root="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
repo_root="$(cd "$script_root/.." && pwd)"
screenshots_dir="$repo_root/artifacts/historical-ui/screenshots"

mkdir -p "$screenshots_dir"
adb shell rm -rf /sdcard/Pictures/hhy-ci-screenshots

set +e
(
  cd "$repo_root/apps/android"
  ./gradlew :app:connectedDebugAndroidTest \
    "-Pandroid.testInstrumentationRunnerArguments.class=cc.orbexa.hhy.HistoricalVisualAuditTest" \
    --no-daemon --no-parallel --max-workers=1 \
    --stacktrace "-Dorg.gradle.jvmargs=-Xmx1536m -Dfile.encoding=UTF-8 -Duser.timezone=UTC"
)
instrumentation_status=$?
set -e

adb pull /sdcard/Pictures/hhy-ci-screenshots/. "$screenshots_dir/" || true
if [[ "$instrumentation_status" -ne 0 ]]; then
  exit "$instrumentation_status"
fi

screenshot_count="$(find "$screenshots_dir" -maxdepth 1 -name '*.png' | wc -l)"
if [[ "$screenshot_count" -ne 26 ]]; then
  echo "Expected exactly 26 historical UI screenshots, found $screenshot_count" >&2
  exit 1
fi
