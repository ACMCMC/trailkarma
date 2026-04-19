#!/usr/bin/env bash
set -euo pipefail

REPO_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
ANDROID_PROJECT_DIR="${ANDROID_PROJECT_DIR:-$REPO_ROOT/android_app}"
SDK_ROOT="${ANDROID_SDK_ROOT:-}"
if [[ -z "$SDK_ROOT" && -f "$ANDROID_PROJECT_DIR/local.properties" ]]; then
  SDK_ROOT="$(awk -F= '/^sdk.dir=/{print $2}' "$ANDROID_PROJECT_DIR/local.properties" | tail -1)"
fi
SDK_ROOT="${SDK_ROOT:-$HOME/Library/Android/sdk}"

TOOLS_VERSION="${ANDROID_CMDLINE_TOOLS_VERSION:-14742923}"
TOOLS_ZIP_URL="https://dl.google.com/android/repository/commandlinetools-mac-${TOOLS_VERSION}_latest.zip"
TMP_DIR="$SDK_ROOT/.temp/cmdline-tools-download"
ZIP_PATH="$TMP_DIR/commandlinetools-mac-latest.zip"

mkdir -p "$SDK_ROOT" "$TMP_DIR" "$SDK_ROOT/cmdline-tools"

if [[ ! -x "$SDK_ROOT/cmdline-tools/latest/bin/sdkmanager" ]]; then
  echo "Installing Android command-line tools into $SDK_ROOT"
  curl -L "$TOOLS_ZIP_URL" -o "$ZIP_PATH"
  rm -rf "$TMP_DIR/extract" "$SDK_ROOT/cmdline-tools/latest"
  mkdir -p "$TMP_DIR/extract"
  unzip -q "$ZIP_PATH" -d "$TMP_DIR/extract"
  mv "$TMP_DIR/extract/cmdline-tools" "$SDK_ROOT/cmdline-tools/latest"
fi

export ANDROID_SDK_ROOT="$SDK_ROOT"
export ANDROID_HOME="$SDK_ROOT"
export PATH="$SDK_ROOT/platform-tools:$SDK_ROOT/emulator:$SDK_ROOT/cmdline-tools/latest/bin:$PATH"

yes | "$SDK_ROOT/cmdline-tools/latest/bin/sdkmanager" --licenses >/dev/null
"$SDK_ROOT/cmdline-tools/latest/bin/sdkmanager" --install \
  "platform-tools" \
  "emulator" \
  "platforms;android-36.1" \
  "system-images;android-36.1;google_apis;arm64-v8a"

echo "Android SDK bootstrap complete."
