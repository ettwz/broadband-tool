#!/usr/bin/env bash
# Source this file from WSL before running Flutter, Gradle or Android tools.
BROADBAND_ROOT="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")/.." && pwd)"
export BROADBAND_ROOT
export JAVA_HOME="$BROADBAND_ROOT/.toolchains/jdk"
export ANDROID_HOME="$BROADBAND_ROOT/.toolchains/android"
export ANDROID_SDK_ROOT="$ANDROID_HOME"
export ANDROID_USER_HOME="$BROADBAND_ROOT/.cache/android"
export ANDROID_AVD_HOME="$ANDROID_USER_HOME/avd"
export GRADLE_USER_HOME="$BROADBAND_ROOT/.cache/gradle"
export PUB_CACHE="$BROADBAND_ROOT/.cache/pub"
export XDG_CONFIG_HOME="$BROADBAND_ROOT/.cache/config"
export XDG_CACHE_HOME="$BROADBAND_ROOT/.cache/xdg"
export ANALYZER_STATE_LOCATION_OVERRIDE="$BROADBAND_ROOT/.cache/dart-analysis"
export ANDROID_EMULATOR_HOME="$BROADBAND_ROOT/.cache/android"
export FLUTTER_SUPPRESS_ANALYTICS=true
export PATH="$BROADBAND_ROOT/.toolchains/flutter/bin:$JAVA_HOME/bin:$ANDROID_HOME/cmdline-tools/latest/bin:$ANDROID_HOME/platform-tools:$ANDROID_HOME/emulator:$PATH"
