#!/usr/bin/env bash
#
# YuTbe build script for Arch Linux.
#
# Usage:
#   ./build-arch.sh [debug|release]        (default: release)
#
# Environment overrides:
#   ANDROID_HOME   - reuse an existing Android SDK installation
#   JAVA_HOME      - reuse an existing JDK 17 installation
#   KEYSTORE       - path to a keystore for signing the release APK
#   KEYSTORE_PASSWORD / KEY_ALIAS / KEY_PASSWORD - signing credentials
#
set -euo pipefail

BUILD_TYPE="${1:-release}"
PROJECT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
SDK_ROOT="${ANDROID_HOME:-$HOME/.local/share/yutbe-android-sdk}"
CMDLINE_TOOLS_VERSION="11076708"
COMPILE_SDK="36"
BUILD_TOOLS="36.0.0"

log() { printf '\033[1;34m==>\033[0m %s\n' "$*"; }
warn() { printf '\033[1;33m==>\033[0m %s\n' "$*" >&2; }

# --- 1. System packages -------------------------------------------------------
install_packages() {
    local missing=()
    for pkg in "$@"; do
        pacman -Qi "$pkg" &>/dev/null || missing+=("$pkg")
    done
    if ((${#missing[@]})); then
        log "Installing missing packages: ${missing[*]}"
        if [[ $EUID -eq 0 ]]; then
            pacman -S --needed --noconfirm "${missing[@]}"
        else
            sudo pacman -S --needed --noconfirm "${missing[@]}"
        fi
    fi
}

install_packages jdk17-openjdk unzip zip curl

# --- 2. Java ------------------------------------------------------------------
if [[ -z "${JAVA_HOME:-}" ]]; then
    JAVA_HOME="/usr/lib/jvm/java-17-openjdk"
    if [[ ! -x "$JAVA_HOME/bin/javac" ]]; then
        JAVA_HOME="$(dirname "$(dirname "$(readlink -f "$(command -v javac)")")")"
    fi
fi
export JAVA_HOME
log "Using JAVA_HOME=$JAVA_HOME"

# --- 3. Android SDK -----------------------------------------------------------
if [[ ! -d "$SDK_ROOT/platforms/android-$COMPILE_SDK" ]]; then
    log "Setting up Android SDK in $SDK_ROOT"
    mkdir -p "$SDK_ROOT/cmdline-tools"
    if [[ ! -x "$SDK_ROOT/cmdline-tools/latest/bin/sdkmanager" ]]; then
        tmp_zip="$(mktemp --suffix=.zip)"
        curl -sSL -o "$tmp_zip" \
            "https://dl.google.com/android/repository/commandlinetools-linux-${CMDLINE_TOOLS_VERSION}_latest.zip"
        unzip -q -o "$tmp_zip" -d "$SDK_ROOT/cmdline-tools"
        rm -f "$tmp_zip"
        rm -rf "$SDK_ROOT/cmdline-tools/latest"
        mv "$SDK_ROOT/cmdline-tools/cmdline-tools" "$SDK_ROOT/cmdline-tools/latest"
    fi
    yes | "$SDK_ROOT/cmdline-tools/latest/bin/sdkmanager" --licenses >/dev/null || true
    "$SDK_ROOT/cmdline-tools/latest/bin/sdkmanager" \
        "platform-tools" "platforms;android-$COMPILE_SDK" "build-tools;$BUILD_TOOLS"
fi
export ANDROID_HOME="$SDK_ROOT"
export ANDROID_SDK_ROOT="$SDK_ROOT"
log "Using ANDROID_HOME=$ANDROID_HOME"

# --- 4. Build -----------------------------------------------------------------
cd "$PROJECT_DIR"
printf 'sdk.dir=%s\n' "$ANDROID_HOME" > local.properties

# Keep memory usage sane on smaller machines.
export GRADLE_OPTS="${GRADLE_OPTS:--Xmx3g}"

case "$BUILD_TYPE" in
    debug)
        log "Building debug APK..."
        sh gradlew assembleDebug --max-workers=2
        APK="app/build/outputs/apk/debug/app-debug.apk"
        ;;
    release)
        log "Building release APK..."
        sh gradlew assembleRelease --max-workers=2
        APK="app/build/outputs/apk/release/app-release-unsigned.apk"
        ;;
    *)
        echo "Unknown build type '$BUILD_TYPE' (expected debug or release)" >&2
        exit 1
        ;;
esac

[[ -f "$APK" ]] || { echo "Build failed: $APK not found" >&2; exit 1; }

# --- 5. Optional signing (release only) ---------------------------------------
if [[ "$BUILD_TYPE" == "release" && -n "${KEYSTORE:-}" ]]; then
    log "Signing APK with $KEYSTORE"
    SIGNED="app/build/outputs/apk/release/yutbe-release.apk"
    "$ANDROID_HOME/build-tools/$BUILD_TOOLS/zipalign" -p -f 4 "$APK" "$SIGNED"
    "$ANDROID_HOME/build-tools/$BUILD_TOOLS/apksigner" sign \
        --ks "$KEYSTORE" \
        --ks-key-alias "${KEY_ALIAS:-vidlite}" \
        --ks-pass "env:KEYSTORE_PASSWORD" \
        --key-pass "env:KEY_PASSWORD" \
        "$SIGNED"
    APK="$SIGNED"
fi

log "Done: $PROJECT_DIR/$APK"
