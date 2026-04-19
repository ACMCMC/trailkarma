#!/usr/bin/env bash
set -euo pipefail

source "$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)/android-env.sh"

require_android_tool "$ADB_BIN" "adb"
require_android_tool "$EMULATOR_BIN" "emulator"

AVD_NAME="${1:-${ANDROID_AVD_NAME:-trailkarma-api36-1}}"

if ! "$EMULATOR_BIN" -list-avds | grep -qx "$AVD_NAME"; then
  "$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)/android-avd-create.sh" "$AVD_NAME"
fi

if "$ADB_BIN" devices | awk 'NR>1 && $2=="device"{print $1}' | grep -q .; then
  echo "An emulator or device is already connected."
else
  echo "Starting emulator @$AVD_NAME"
  nohup "$EMULATOR_BIN" @"$AVD_NAME" -no-snapshot-save -no-boot-anim -netdelay none -netspeed full >/tmp/"$AVD_NAME".log 2>&1 &
fi

"$ADB_BIN" wait-for-device
until [[ "$("$ADB_BIN" shell getprop sys.boot_completed 2>/dev/null | tr -d '\r')" == "1" ]]; do
  sleep 2
done

"$ADB_BIN" shell settings put global window_animation_scale 0
"$ADB_BIN" shell settings put global transition_animation_scale 0
"$ADB_BIN" shell settings put global animator_duration_scale 0

echo "Emulator is ready."
