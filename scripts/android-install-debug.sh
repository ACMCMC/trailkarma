#!/usr/bin/env bash
set -euo pipefail

source "$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)/android-env.sh"

require_android_tool "$ADB_BIN" "adb"

REWARDS_URL="${REWARDS_URL:-http://10.0.2.2:3000}"
GRADLE_CMD="${GRADLE_CMD:-./gradlew}"

(
  cd "$ANDROID_PROJECT_DIR"
  "$GRADLE_CMD" :app:assembleDebug "-Prewards.url=$REWARDS_URL"
)

APK_PATH="$ANDROID_PROJECT_DIR/app/build/outputs/apk/debug/app-debug.apk"
"$ADB_BIN" install -r "$APK_PATH"
"$ADB_BIN" shell pm grant fyi.acmc.trailkarma android.permission.ACCESS_FINE_LOCATION || true
"$ADB_BIN" shell pm grant fyi.acmc.trailkarma android.permission.ACCESS_COARSE_LOCATION || true
"$ADB_BIN" shell pm grant fyi.acmc.trailkarma android.permission.BLUETOOTH_SCAN || true
"$ADB_BIN" shell pm grant fyi.acmc.trailkarma android.permission.BLUETOOTH_ADVERTISE || true
"$ADB_BIN" shell pm grant fyi.acmc.trailkarma android.permission.BLUETOOTH_CONNECT || true

echo "Installed debug APK."
