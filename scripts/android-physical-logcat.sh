#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
source "$SCRIPT_DIR/android-env.sh"

require_android_tool "$ADB_BIN" "adb"

PACKAGE_NAME="${PACKAGE_NAME:-fyi.acmc.trailkarma}"
ARTIFACT_ROOT="${ARTIFACT_ROOT:-$REPO_ROOT/.artifacts/physical-device}"
SESSION_NAME="${SESSION_NAME:-manual}"
ACTION="${1:-start}"

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
RUN_DIR="$ARTIFACT_ROOT/$SESSION_NAME"
PID_FILE="$RUN_DIR/logcat.pid"
LOG_FILE="$RUN_DIR/logcat.txt"
META_FILE="$RUN_DIR/session.env"

mkdir -p "$RUN_DIR"

start_logcat() {
  if [[ -f "$PID_FILE" ]] && kill -0 "$(cat "$PID_FILE")" 2>/dev/null; then
    echo "Logcat capture already running for session '$SESSION_NAME' (pid $(cat "$PID_FILE"))."
    exit 0
  fi

  "$ADB_BIN" -s "$SERIAL" logcat -c
  cat > "$META_FILE" <<EOF
ANDROID_SERIAL=$SERIAL
PACKAGE_NAME=$PACKAGE_NAME
SESSION_NAME=$SESSION_NAME
STARTED_AT=$(date -u +"%Y-%m-%dT%H:%M:%SZ")
LOG_FILE=$LOG_FILE
EOF
  "$ADB_BIN" -s "$SERIAL" logcat -v threadtime > "$LOG_FILE" 2>&1 &
  echo $! > "$PID_FILE"
  echo "Started logcat capture for $SERIAL"
  echo "Artifacts: $RUN_DIR"
}

stop_logcat() {
  if [[ -f "$PID_FILE" ]] && kill -0 "$(cat "$PID_FILE")" 2>/dev/null; then
    kill "$(cat "$PID_FILE")"
    rm -f "$PID_FILE"
    echo "Stopped logcat capture for session '$SESSION_NAME'."
  else
    echo "No running logcat capture found for session '$SESSION_NAME'."
  fi
}

status_logcat() {
  if [[ -f "$PID_FILE" ]] && kill -0 "$(cat "$PID_FILE")" 2>/dev/null; then
    echo "running"
    echo "serial=$SERIAL"
    echo "session=$SESSION_NAME"
    echo "log_file=$LOG_FILE"
    echo "pid=$(cat "$PID_FILE")"
  else
    echo "stopped"
    echo "serial=$SERIAL"
    echo "session=$SESSION_NAME"
    echo "log_file=$LOG_FILE"
  fi
}

case "$ACTION" in
  start) start_logcat ;;
  stop) stop_logcat ;;
  status) status_logcat ;;
  *)
    echo "Usage: $0 {start|stop|status}" >&2
    exit 1
    ;;
esac
