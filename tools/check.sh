#!/usr/bin/env bash
set -euo pipefail
source "$(dirname -- "${BASH_SOURCE[0]}")/wsl-env.sh"
cd "$BROADBAND_ROOT"
python3 -B tools/check_public_source.py
python3 -B tests/test_public_source.py
python3 tools/generate_dns_aidl.py --check
python3 -B tests/native/test_probes.py
cd apps/mobile
flutter analyze
flutter test test --reporter expanded
cd android
./gradlew :app:testDebugUnitTest --console=plain
