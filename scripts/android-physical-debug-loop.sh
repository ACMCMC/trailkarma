#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
source "$SCRIPT_DIR/android-env.sh"

require_android_tool "$ADB_BIN" "adb"

SESSION_NAME="${SESSION_NAME:-manual}"
ARTIFACT_ROOT="${ARTIFACT_ROOT:-$REPO_ROOT/.artifacts/physical-device}"

resolve_usb_serial() {
  if [[ -n "${ANDROID_SERIAL:-}" ]]; then
    printf '%s\n' "$ANDROID_SERIAL"
    return
  fi

  local serial
  serial="$("$ADB_BIN" devices -l | awk 'NR > 1 && $2 == "device" && $0 ~ /usb:/ { print $1; exit }')"
  if [[ -z "$serial" ]]; then
    echo "No USB-connected Android device found. Connect the phone and ensure USB debugging is enabled." >&2
    exit 1
  fi
  printf '%s\n' "$serial"
}

SERIAL="$(resolve_usb_serial)"
export ANDROID_SERIAL="$SERIAL"

echo "Preparing physical-device debug loop for $SERIAL"
curl --fail --silent --show-error "http://127.0.0.1:3000/health" > /dev/null
SESSION_NAME="$SESSION_NAME" ANDROID_SERIAL="$SERIAL" "$SCRIPT_DIR/android-physical-install-debug.sh"
SESSION_NAME="$SESSION_NAME" ANDROID_SERIAL="$SERIAL" "$SCRIPT_DIR/android-physical-logcat.sh" start

echo
echo "Ready for manual testing on device $SERIAL"
echo "1. Reproduce the bug on the phone."
echo "2. Run: SESSION_NAME=$SESSION_NAME scripts/android-physical-capture.sh"
echo "3. When finished, run: SESSION_NAME=$SESSION_NAME scripts/android-physical-logcat.sh stop"
echo "Artifacts will be under $ARTIFACT_ROOT/$SESSION_NAME"
