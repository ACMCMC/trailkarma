#!/usr/bin/env bash
set -euo pipefail

source "$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)/android-env.sh"

require_android_tool "$SDKMANAGER_BIN" "sdkmanager"
require_android_tool "$AVDMANAGER_BIN" "avdmanager"

AVD_NAME="${1:-${ANDROID_AVD_NAME:-trailkarma-api36-1}}"
SYSTEM_IMAGE="${ANDROID_SYSTEM_IMAGE:-system-images;android-36.1;google_apis;arm64-v8a}"

"$SDKMANAGER_BIN" --install "$SYSTEM_IMAGE" >/dev/null

if "$EMULATOR_BIN" -list-avds | grep -qx "$AVD_NAME"; then
  echo "AVD already exists: $AVD_NAME"
  exit 0
fi

echo "Creating AVD $AVD_NAME using $SYSTEM_IMAGE"
echo "no" | "$AVDMANAGER_BIN" create avd --force --name "$AVD_NAME" --package "$SYSTEM_IMAGE" >/dev/null
echo "Created AVD $AVD_NAME"
