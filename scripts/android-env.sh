#!/usr/bin/env bash
set -euo pipefail

REPO_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
ANDROID_PROJECT_DIR="${ANDROID_PROJECT_DIR:-$REPO_ROOT/android_app}"

discover_sdk_root() {
  if [[ -n "${ANDROID_SDK_ROOT:-}" && -d "${ANDROID_SDK_ROOT:-}" ]]; then
    printf '%s\n' "$ANDROID_SDK_ROOT"
    return
  fi
  if [[ -n "${ANDROID_HOME:-}" && -d "${ANDROID_HOME:-}" ]]; then
    printf '%s\n' "$ANDROID_HOME"
    return
  fi
  if [[ -f "$ANDROID_PROJECT_DIR/local.properties" ]]; then
    local sdk_dir
    sdk_dir="$(awk -F= '/^sdk.dir=/{print $2}' "$ANDROID_PROJECT_DIR/local.properties" | tail -1)"
    if [[ -n "$sdk_dir" && -d "$sdk_dir" ]]; then
      printf '%s\n' "$sdk_dir"
      return
    fi
  fi
  if [[ -d "$HOME/Library/Android/sdk" ]]; then
    printf '%s\n' "$HOME/Library/Android/sdk"
    return
  fi
  return 1
}

export ANDROID_SDK_ROOT="$(discover_sdk_root)"
export ANDROID_HOME="$ANDROID_SDK_ROOT"
export PATH="$ANDROID_SDK_ROOT/platform-tools:$ANDROID_SDK_ROOT/emulator:$ANDROID_SDK_ROOT/cmdline-tools/latest/bin:$PATH"

ADB_BIN="$ANDROID_SDK_ROOT/platform-tools/adb"
EMULATOR_BIN="$ANDROID_SDK_ROOT/emulator/emulator"
SDKMANAGER_BIN="$ANDROID_SDK_ROOT/cmdline-tools/latest/bin/sdkmanager"
AVDMANAGER_BIN="$ANDROID_SDK_ROOT/cmdline-tools/latest/bin/avdmanager"

require_android_tool() {
  local tool_path="$1"
  local label="$2"
  if [[ ! -x "$tool_path" ]]; then
    echo "Missing $label at $tool_path" >&2
    exit 1
  fi
}

