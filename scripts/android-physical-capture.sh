#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
source "$SCRIPT_DIR/android-env.sh"

require_android_tool "$ADB_BIN" "adb"

ARTIFACT_ROOT="${ARTIFACT_ROOT:-$REPO_ROOT/.artifacts/physical-device}"
SESSION_NAME="${SESSION_NAME:-manual}"
STAMP="$(date +"%Y%m%d-%H%M%S")"
REMOTE_DUMP_PATH="${REMOTE_DUMP_PATH:-/sdcard/window_dump.xml}"

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
OUT_DIR="$ARTIFACT_ROOT/$SESSION_NAME/capture-$STAMP"
mkdir -p "$OUT_DIR"

"$ADB_BIN" -s "$SERIAL" exec-out screencap -p > "$OUT_DIR/screen.png"
"$ADB_BIN" -s "$SERIAL" shell rm -f "$REMOTE_DUMP_PATH"
"$ADB_BIN" -s "$SERIAL" shell uiautomator dump "$REMOTE_DUMP_PATH" >/dev/null 2>&1
"$ADB_BIN" -s "$SERIAL" shell test -s "$REMOTE_DUMP_PATH"
"$ADB_BIN" -s "$SERIAL" pull "$REMOTE_DUMP_PATH" "$OUT_DIR/window_dump.xml" >/dev/null
"$ADB_BIN" -s "$SERIAL" shell dumpsys activity activities > "$OUT_DIR/activities.txt"
"$ADB_BIN" -s "$SERIAL" shell dumpsys window windows > "$OUT_DIR/windows.txt"

echo "Wrote physical-device capture to $OUT_DIR"
