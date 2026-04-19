#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
source "$SCRIPT_DIR/android-env.sh"

require_android_tool "$ADB_BIN" "adb"

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

HOST_API_PORT="${HOST_API_PORT:-3000}"
PHONE_BASE_URL="${PHONE_BASE_URL:-http://127.0.0.1:${HOST_API_PORT}}"

echo "Using physical device $SERIAL"
"$ADB_BIN" -s "$SERIAL" reverse "tcp:${HOST_API_PORT}" "tcp:${HOST_API_PORT}"
echo "Reversed device tcp:${HOST_API_PORT} -> host tcp:${HOST_API_PORT}"

API_BASE_URL="$PHONE_BASE_URL" REWARDS_URL="$PHONE_BASE_URL" "$SCRIPT_DIR/android-install-debug.sh"

echo "Physical device install complete."
