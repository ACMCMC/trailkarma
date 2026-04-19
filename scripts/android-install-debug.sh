#!/usr/bin/env bash
set -euo pipefail

source "$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)/android-env.sh"

require_android_tool "$ADB_BIN" "adb"

PACKAGE_NAME="${PACKAGE_NAME:-fyi.acmc.trailkarma}"
API_BASE_URL="${API_BASE_URL:-${REWARDS_URL:-http://10.0.2.2:3000}}"
REWARDS_URL="${REWARDS_URL:-$API_BASE_URL}"
GRADLE_CMD="${GRADLE_CMD:-./gradlew}"

(
  cd "$ANDROID_PROJECT_DIR"
  "$GRADLE_CMD" :app:assembleDebug "-Papi.baseUrl=$API_BASE_URL" "-Prewards.url=$REWARDS_URL"
)

APK_PATH="$ANDROID_PROJECT_DIR/app/build/outputs/apk/debug/app-debug.apk"
INSTALL_OUTPUT="$("$ADB_BIN" install -r "$APK_PATH" 2>&1)" || {
  if [[ "${FORCE_UNINSTALL_ON_SIGNATURE_MISMATCH:-0}" == "1" ]] && grep -q "INSTALL_FAILED_UPDATE_INCOMPATIBLE" <<<"$INSTALL_OUTPUT"; then
    echo "Existing app signature does not match. Uninstalling $PACKAGE_NAME and retrying install..."
    "$ADB_BIN" uninstall "$PACKAGE_NAME" >/dev/null
    "$ADB_BIN" install -r "$APK_PATH"
  else
    echo "$INSTALL_OUTPUT" >&2
    echo "Install failed. If this device has an older build signed with a different key, rerun with FORCE_UNINSTALL_ON_SIGNATURE_MISMATCH=1." >&2
    exit 1
  fi
}
"$ADB_BIN" shell pm grant "$PACKAGE_NAME" android.permission.ACCESS_FINE_LOCATION || true
"$ADB_BIN" shell pm grant "$PACKAGE_NAME" android.permission.ACCESS_COARSE_LOCATION || true
"$ADB_BIN" shell pm grant "$PACKAGE_NAME" android.permission.RECORD_AUDIO || true
"$ADB_BIN" shell pm grant "$PACKAGE_NAME" android.permission.CAMERA || true
"$ADB_BIN" shell pm grant "$PACKAGE_NAME" android.permission.BLUETOOTH_SCAN || true
"$ADB_BIN" shell pm grant "$PACKAGE_NAME" android.permission.BLUETOOTH_ADVERTISE || true
"$ADB_BIN" shell pm grant "$PACKAGE_NAME" android.permission.BLUETOOTH_CONNECT || true
"$ADB_BIN" shell pm grant "$PACKAGE_NAME" android.permission.POST_NOTIFICATIONS || true

echo "Installed debug APK."
