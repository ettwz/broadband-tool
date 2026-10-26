#!/usr/bin/env bash
set -euo pipefail
source "$(dirname -- "${BASH_SOURCE[0]}")/wsl-env.sh"
if [[ -z "${BROADBAND_DEVICE:-}" ]]; then
  if [[ ! -f "$BROADBAND_ROOT/.cache/android-device.txt" ]]; then
    echo 'Set BROADBAND_DEVICE to your ADB serial or host:port. Device addresses are local configuration.' >&2
    exit 2
  fi
  IFS= read -r BROADBAND_DEVICE < "$BROADBAND_ROOT/.cache/android-device.txt"
fi
if [[ ! "$BROADBAND_DEVICE" =~ ^[a-zA-Z0-9.:_-]+$ ]]; then
  echo "Invalid ADB device address" >&2
  exit 2
fi
if [[ "$BROADBAND_DEVICE" == *:* ]]; then
  timeout 15s adb connect "$BROADBAND_DEVICE" >/dev/null
fi
exec adb -s "$BROADBAND_DEVICE" "$@"
