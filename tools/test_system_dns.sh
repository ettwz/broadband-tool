#!/usr/bin/env bash
# Device fixture only: two temporary networks, loopback DNS, no live DNS changes.
set -euo pipefail
source "$(dirname -- "${BASH_SOURCE[0]}")/wsl-env.sh"
cd "$BROADBAND_ROOT/apps/mobile"
flutter build apk --debug --target-platform android-arm64,android-x64 -t lib/main.dart
cd "$BROADBAND_ROOT"
bash tools/device.sh push apps/mobile/build/app/outputs/flutter-apk/app-debug.apk /data/local/tmp/broadband-dns-fixture.apk
bash tools/device.sh shell chmod 444 /data/local/tmp/broadband-dns-fixture.apk
trap 'bash tools/device.sh shell rm -f /data/local/tmp/broadband-dns-fixture.apk >/dev/null 2>&1 || true' EXIT
timeout 45s bash tools/device.sh shell "su -c 'CLASSPATH=/data/local/tmp/broadband-dns-fixture.apk app_process /system/bin --nice-name=broadband-dns-fixture dev.broadband.toolbox.privileged.SystemDnsCacheFixture'"
