#!/usr/bin/env bash
set -euo pipefail
source "$(dirname -- "${BASH_SOURCE[0]}")/wsl-env.sh"
cd "$BROADBAND_ROOT"
BROADBAND_VERSION="$(awk '/^version:/ {split($2, parts, /[+]/); print parts[1]; exit}' apps/mobile/pubspec.yaml)"
if [[ ! "$BROADBAND_VERSION" =~ ^[0-9]+\.[0-9]+\.[0-9]+$ ]]; then
  echo 'Expected a three-part application version in pubspec.yaml.' >&2
  exit 1
fi
BROADBAND_APK="broadband-tool-${BROADBAND_VERSION}.apk"
if [[ -f apps/mobile/android/app/src/debug/res/raw/web_fixture.crt || -f apps/mobile/android/app/src/debug/res/xml/network_security_config.xml ]]; then
  echo 'Remove the temporary HTTPS test resources before delivery.' >&2
  exit 1
fi
python3 tools/build_iperf.py --abi android
cd apps/mobile
flutter pub get --enforce-lockfile
# Flutter regenerates the release plugin registry during this step. --no-pub
# would retain integration_test registration from a preceding device test.
flutter build apk --release --target-platform android-arm64,android-x64 -t lib/main.dart
cd "$BROADBAND_ROOT"
python3 tools/check_apk.py apps/mobile/build/app/outputs/flutter-apk/app-release.apk
mkdir -p dist
cp apps/mobile/build/app/outputs/flutter-apk/app-release.apk "dist/$BROADBAND_APK"
"$ANDROID_HOME/build-tools/36.0.0/apksigner" verify "dist/$BROADBAND_APK"
"$ANDROID_HOME/build-tools/36.0.0/zipalign" -c -P 16 4 "dist/$BROADBAND_APK"
cd dist
sha256sum "$BROADBAND_APK" > SHA256SUMS
cat SHA256SUMS
