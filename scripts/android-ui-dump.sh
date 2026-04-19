#!/usr/bin/env bash
set -euo pipefail

source "$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)/android-env.sh"

require_android_tool "$ADB_BIN" "adb"

OUT_DIR="${1:-$REPO_ROOT/.artifacts/android-ui}"
REMOTE_DUMP_PATH="${REMOTE_DUMP_PATH:-/sdcard/window_dump.xml}"
mkdir -p "$OUT_DIR"

"$ADB_BIN" exec-out screencap -p > "$OUT_DIR/screen.png"
"$ADB_BIN" shell rm -f "$REMOTE_DUMP_PATH"
"$ADB_BIN" shell uiautomator dump "$REMOTE_DUMP_PATH" >/dev/null 2>&1
"$ADB_BIN" shell test -s "$REMOTE_DUMP_PATH"
"$ADB_BIN" pull "$REMOTE_DUMP_PATH" "$OUT_DIR/window_dump.xml" >/dev/null

echo "Wrote screenshot and UI dump to $OUT_DIR"
