#!/usr/bin/env bash
set -uo pipefail

repo_root="${GITHUB_WORKSPACE:-$(git rev-parse --show-toplevel)}"
cd "$repo_root"

runtime_dir="artifacts/android-ci/runtime"
screenshots_dir="artifacts/android-ci/screenshots"
mkdir -p "$runtime_dir" "$screenshots_dir"

adb shell settings put system accelerometer_rotation 0
adb shell settings put system user_rotation 0
adb shell settings put system font_scale 1.0
adb logcat -c

set +e
(
  cd apps/android
  ./gradlew --no-daemon --no-parallel --max-workers=1 --stacktrace \
    "-Dorg.gradle.jvmargs=-Xmx1536m -Dfile.encoding=UTF-8 -Duser.timezone=UTC" \
    connectedDebugAndroidTest
) 2>&1 | tee "$runtime_dir/instrumentation.log"
test_rc=${PIPESTATUS[0]}
printf '%s\n' "$test_rc" > "$runtime_dir/test-exit-code.txt"

adb pull /sdcard/Download/hhy-ci-screenshots/. "$screenshots_dir/" || true
adb logcat -d -v threadtime \
  | grep -E 'cc\.orbexa\.hhy|AndroidRuntime|FATAL EXCEPTION|ANR in' \
  > "$runtime_dir/logcat.txt" || true
adb shell dumpsys activity exit-info cc.orbexa.hhy.debug > "$runtime_dir/exit-info.txt" || true

python3 scripts/android_ci_gate.py analyze \
  --release "$ANDROID_RELEASE" \
  --commit "$GITHUB_SHA" \
  --attempt "${REMEDIATION_ATTEMPT:-1}" \
  --test-exit-code-file "$runtime_dir/test-exit-code.txt" \
  --junit-root apps/android/app/build/outputs/androidTest-results/connected \
  --logcat "$runtime_dir/logcat.txt" \
  --screenshots "$screenshots_dir" \
  --output "$runtime_dir/runtime-report.json" \
  2>&1 | tee "$runtime_dir/analyze.log"
analysis_rc=${PIPESTATUS[0]}
set -e

if (( analysis_rc != 0 )); then
  summary=$(grep -E 'FAIL|ERROR|failure|exception|exit_code|screenshots=' \
    "$runtime_dir/instrumentation.log" "$runtime_dir/analyze.log" 2>/dev/null \
    | tail -n 25 || true)
  if [[ -z "$summary" ]]; then
    summary="Android emulator gate failed; inspect android-runtime artifact"
  fi
  summary=${summary//'%'/'%25'}
  summary=${summary//$'\r'/'%0D'}
  summary=${summary//$'\n'/'%0A'}
  echo "::error title=Android emulator gate failed::$summary"
fi

exit "$analysis_rc"
