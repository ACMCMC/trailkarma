#!/usr/bin/env bash
set -euo pipefail

source "$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)/android-env.sh"

require_android_tool "$ADB_BIN" "adb"

ARTIFACT_DIR="${ARTIFACT_DIR:-$REPO_ROOT/.artifacts/android-smoke}"
mkdir -p "$ARTIFACT_DIR"

"$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)/android-emulator-start.sh"
"$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)/android-install-debug.sh"

"$ADB_BIN" logcat -c
"$ADB_BIN" shell am force-stop fyi.acmc.trailkarma || true
"$ADB_BIN" shell am start -W -n fyi.acmc.trailkarma/.MainActivity >/dev/null
sleep 8

LAUNCH_LOG="$ARTIFACT_DIR/launch-logcat.txt"
"$ADB_BIN" logcat -d -v time AndroidRuntime:E *:S > "$LAUNCH_LOG" || true
if grep -q "fyi.acmc.trailkarma" "$LAUNCH_LOG"; then
  echo "Crash detected during launch. See $LAUNCH_LOG" >&2
  exit 1
fi

(
  cd "$ANDROID_PROJECT_DIR"
  ./gradlew :app:connectedDebugAndroidTest \
    -Pandroid.testInstrumentationRunnerArguments.class=fyi.acmc.trailkarma.RewardsRepositoryIntegrationTest
)

"$ADB_BIN" shell am force-stop fyi.acmc.trailkarma || true
"$ADB_BIN" logcat -c

(
  cd "$ANDROID_PROJECT_DIR"
  ./gradlew :app:connectedDebugAndroidTest \
    -Pandroid.testInstrumentationRunnerArguments.class=fyi.acmc.trailkarma.SmokeNavigationTest
)

"$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)/android-ui-dump.sh" "$ARTIFACT_DIR/ui"
echo "Android smoke loop passed."
