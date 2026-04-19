#!/usr/bin/env bash
set -euo pipefail

source "$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)/android-env.sh"

require_android_tool "$ADB_BIN" "adb"

OUT_DIR="${1:-$REPO_ROOT/.artifacts/android-ui}"
mkdir -p "$OUT_DIR"

"$ADB_BIN" exec-out screencap -p > "$OUT_DIR/screen.png"
"$ADB_BIN" shell uiautomator dump /sdcard/window_dump.xml >/dev/null
"$ADB_BIN" pull /sdcard/window_dump.xml "$OUT_DIR/window_dump.xml" >/dev/null

echo "Wrote screenshot and UI dump to $OUT_DIR"
