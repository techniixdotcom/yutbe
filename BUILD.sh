#!/usr/bin/env bash
set -Eeuo pipefail
umask 077

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
YUTBE_HOME="${YUTBE_HOME:-$HOME/.yutbe}"
JDK_MAJOR=21
JDK_DIR="$YUTBE_HOME/jdk-$JDK_MAJOR"
SDK_DIR="$YUTBE_HOME/android-sdk"
SIGNING_DIR="$YUTBE_HOME/signing"
SIGNING_ENV="$SIGNING_DIR/signing.env"
KEYSTORE="$SIGNING_DIR/yutbe-release.p12"
DIST_DIR="$ROOT/dist"
LOG_FILE="$ROOT/build.log"
TMP_DIR=""

log() { printf '\033[1;32m[YuTbe]\033[0m %s\n' "$*"; }
warn() { printf '\033[1;33m[YuTbe] WARNING:\033[0m %s\n' "$*" >&2; }
die() { printf '\033[1;31m[YuTbe] ERROR:\033[0m %s\n' "$*" >&2; exit 1; }

cleanup() {
	if [[ -n "$TMP_DIR" && -d "$TMP_DIR" ]]; then
		rm -rf "$TMP_DIR"
	fi
}
on_error() {
	local code=$?
	printf '\033[1;31m[YuTbe] Build failed (exit %s) at line %s. Full log: %s\033[0m\n' "$code" "$1" "$LOG_FILE" >&2
	exit "$code"
}
trap cleanup EXIT
trap 'on_error $LINENO' ERR

usage() {
	cat <<'EOF'
Usage: ./BUILD.sh [--clean] [--debug] [--help]

  --clean   delete previous build outputs before building
  --debug   build a debug APK instead of the signed release APK
  --help    show this help

Environment:
  YUTBE_HOME   where the JDK, Android SDK and signing key are kept (default: ~/.yutbe)
EOF
}

CLEAN=0
VARIANT="release"
for arg in "$@"; do
	case "$arg" in
		--clean) CLEAN=1 ;;
		--debug) VARIANT="debug" ;;
		--help|-h) usage; exit 0 ;;
		*) usage; die "Unknown option: $arg" ;;
	esac
done

exec > >(tee -a "$LOG_FILE") 2>&1
log "Build started $(date '+%Y-%m-%d %H:%M:%S')"

detect_platform() {
	case "$(uname -s)" in
		Linux) OS="linux"; ADOPTIUM_OS="linux"; SDK_OS="linux" ;;
		Darwin) OS="mac"; ADOPTIUM_OS="mac"; SDK_OS="mac" ;;
		*) die "Unsupported operating system $(uname -s). Use Linux, macOS or WSL2 on Windows." ;;
	esac
	case "$(uname -m)" in
		x86_64|amd64) ADOPTIUM_ARCH="x64" ;;
		aarch64|arm64) ADOPTIUM_ARCH="aarch64" ;;
		*) die "Unsupported CPU architecture $(uname -m)." ;;
	esac
	log "Platform: $OS / $ADOPTIUM_ARCH"
}

run_privileged() {
	if [[ "$(id -u)" -eq 0 ]]; then
		"$@"
	elif command -v sudo >/dev/null 2>&1; then
		sudo "$@"
	else
		die "Root rights are needed to install system packages ($*). Install them manually and re-run."
	fi
}

ensure_system_tools() {
	local missing=()
	local tool
	for tool in curl unzip tar awk sed grep; do
		command -v "$tool" >/dev/null 2>&1 || missing+=("$tool")
	done
	if ! command -v sha256sum >/dev/null 2>&1 && ! command -v shasum >/dev/null 2>&1; then
		missing+=("coreutils")
	fi
	if ! command -v sha1sum >/dev/null 2>&1 && ! command -v shasum >/dev/null 2>&1; then
		missing+=("coreutils")
	fi
	if [[ ${#missing[@]} -eq 0 ]]; then
		return
	fi
	log "Installing missing system tools: ${missing[*]}"
	if command -v apt-get >/dev/null 2>&1; then
		run_privileged apt-get update
		run_privileged env DEBIAN_FRONTEND=noninteractive apt-get install -y curl unzip tar gawk sed grep coreutils ca-certificates
	elif command -v dnf >/dev/null 2>&1; then
		run_privileged dnf install -y curl unzip tar gawk sed grep coreutils ca-certificates
	elif command -v pacman >/dev/null 2>&1; then
		run_privileged pacman -Sy --noconfirm --needed curl unzip tar gawk sed grep coreutils ca-certificates
	elif command -v zypper >/dev/null 2>&1; then
		run_privileged zypper --non-interactive install curl unzip tar gawk sed grep coreutils ca-certificates
	elif command -v brew >/dev/null 2>&1; then
		brew install curl unzip coreutils
	else
		die "Please install: ${missing[*]}"
	fi
}

sha256_of() {
	if command -v sha256sum >/dev/null 2>&1; then
		sha256sum "$1" | awk '{print $1}'
	else
		shasum -a 256 "$1" | awk '{print $1}'
	fi
}

sha1_of() {
	if command -v sha1sum >/dev/null 2>&1; then
		sha1sum "$1" | awk '{print $1}'
	else
		shasum -a 1 "$1" | awk '{print $1}'
	fi
}

download() {
	curl --proto '=https' --tlsv1.2 -fL --retry 5 --retry-delay 3 --retry-connrefused \
		--connect-timeout 30 -o "$2" "$1"
}

install_jdk() {
	if [[ -x "$JDK_DIR/bin/java" ]] && "$JDK_DIR/bin/java" -version 2>&1 | grep "version \"$JDK_MAJOR" >/dev/null; then
		log "JDK $JDK_MAJOR already installed"
		return
	fi
	log "Downloading Eclipse Temurin JDK $JDK_MAJOR"
	local api="https://api.adoptium.net/v3/binary/latest/$JDK_MAJOR/ga/$ADOPTIUM_OS/$ADOPTIUM_ARCH/jdk/hotspot/normal/eclipse"
	local url
	url="$(curl --proto '=https' --tlsv1.2 -fsS -o /dev/null -w '%{redirect_url}' "$api")"
	[[ "$url" == https://github.com/adoptium/* ]] || die "Unexpected JDK download location: $url"
	local archive="$TMP_DIR/jdk.tar.gz"
	download "$url" "$archive"
	local expected
	expected="$(curl --proto '=https' --tlsv1.2 -fsSL --retry 5 "$url.sha256.txt" | awk '{print $1}')"
	[[ "$expected" =~ ^[0-9a-f]{64}$ ]] || die "Could not obtain the JDK checksum"
	[[ "$(sha256_of "$archive")" == "$expected" ]] || die "JDK checksum mismatch, download corrupted or tampered with"
	log "JDK checksum verified"
	local unpack="$TMP_DIR/jdk"
	mkdir -p "$unpack"
	tar -xzf "$archive" -C "$unpack"
	local home
	home="$(find "$unpack" -mindepth 1 -maxdepth 1 -type d | head -n 1)"
	[[ -n "$home" ]] || die "JDK archive is empty"
	if [[ -d "$home/Contents/Home" ]]; then
		home="$home/Contents/Home"
	fi
	rm -rf "$JDK_DIR"
	mkdir -p "$(dirname "$JDK_DIR")"
	mv "$home" "$JDK_DIR"
	"$JDK_DIR/bin/java" -version
}

install_android_sdk() {
	local sdkmanager="$SDK_DIR/cmdline-tools/latest/bin/sdkmanager"
	if [[ ! -x "$sdkmanager" ]]; then
		log "Downloading Android command-line tools"
		local repo="$TMP_DIR/repository.xml"
		download "https://dl.google.com/android/repository/repository2-3.xml" "$repo"
		local best
		best="$(tr -d '\r\n' < "$repo" | sed -E 's/>[[:space:]]+</></g' \
			| awk '{ gsub(/<\/archive>/, "</archive>\n"); print }' \
			| grep -o "<checksum[^>]*>[0-9a-f]\{40\}</checksum><url>commandlinetools-$SDK_OS-[0-9]*_latest\.zip</url>" \
			| sed -E 's#<checksum[^>]*>([0-9a-f]{40})</checksum><url>(commandlinetools-[a-z]+-([0-9]+)_latest\.zip)</url>#\3 \1 \2#' \
			| sort -n | tail -n 1 || true)"
		[[ -n "$best" ]] || die "Could not find the Android command-line tools in Google's repository index"
		local sha1 file
		sha1="$(awk '{print $2}' <<< "$best")"
		file="$(awk '{print $3}' <<< "$best")"
		local zip="$TMP_DIR/cmdline-tools.zip"
		download "https://dl.google.com/android/repository/$file" "$zip"
		[[ "$(sha1_of "$zip")" == "$sha1" ]] || die "Android command-line tools checksum mismatch"
		log "Android command-line tools checksum verified ($file)"
		rm -rf "$TMP_DIR/cmdline"
		mkdir -p "$TMP_DIR/cmdline" "$SDK_DIR/cmdline-tools"
		unzip -q "$zip" -d "$TMP_DIR/cmdline"
		rm -rf "$SDK_DIR/cmdline-tools/latest"
		mv "$TMP_DIR/cmdline/cmdline-tools" "$SDK_DIR/cmdline-tools/latest"
	else
		log "Android command-line tools already installed"
	fi
	log "Accepting Android SDK licenses"
	{ yes || true; } | "$sdkmanager" --sdk_root="$SDK_DIR" --licenses >/dev/null
	log "Installing Android platform tools"
	"$sdkmanager" --sdk_root="$SDK_DIR" --install "platform-tools" >/dev/null
	if "$sdkmanager" --sdk_root="$SDK_DIR" --install "platforms;android-37" >/dev/null 2>&1; then
		log "Android platform 37 installed"
	else
		warn "platforms;android-37 is not listed under that name; Gradle will download the platform it needs"
	fi
}

write_local_properties() {
	local escaped="${SDK_DIR//\\/\\\\}"
	escaped="${escaped//:/\\:}"
	printf 'sdk.dir=%s\n' "$escaped" > "$ROOT/local.properties"
}

random_secret() {
	od -An -tx1 -N32 /dev/urandom | tr -d ' \n'
}

ensure_signing_key() {
	mkdir -p "$SIGNING_DIR"
	chmod 700 "$SIGNING_DIR"
	if [[ -f "$KEYSTORE" && -f "$SIGNING_ENV" ]]; then
		log "Using existing signing key $KEYSTORE"
	else
		if [[ -f "$KEYSTORE" || -f "$SIGNING_ENV" ]]; then
			die "Signing key files in $SIGNING_DIR are incomplete. Restore them from your backup, or delete that folder to create a new key (a new key cannot update an installed YuTbe)."
		fi
		log "Creating a new release signing key in $SIGNING_DIR"
		YUTBE_KEYSTORE_PASSWORD="$(random_secret)"
		export YUTBE_KEYSTORE_PASSWORD
		"$JDK_DIR/bin/keytool" -genkeypair -noprompt \
			-keystore "$KEYSTORE" -storetype PKCS12 \
			-storepass:env YUTBE_KEYSTORE_PASSWORD -keypass:env YUTBE_KEYSTORE_PASSWORD \
			-alias yutbe -keyalg RSA -keysize 4096 -validity 36500 \
			-dname "CN=YuTbe, O=YuTbe"
		{
			printf 'YUTBE_KEYSTORE_FILE=%q\n' "$KEYSTORE"
			printf 'YUTBE_KEYSTORE_PASSWORD=%q\n' "$YUTBE_KEYSTORE_PASSWORD"
			printf 'YUTBE_KEY_ALIAS=yutbe\n'
			printf 'YUTBE_KEY_PASSWORD=%q\n' "$YUTBE_KEYSTORE_PASSWORD"
		} > "$SIGNING_ENV"
		chmod 600 "$KEYSTORE" "$SIGNING_ENV"
		warn "Back up $SIGNING_DIR. Without it future builds cannot update the installed app."
	fi
	set -a
	# shellcheck disable=SC1090
	source "$SIGNING_ENV"
	set +a
}

gradle_heap_mb() {
	local total_mb=4096
	if [[ -r /proc/meminfo ]]; then
		total_mb=$(( $(awk '/MemTotal/ {print $2}' /proc/meminfo) / 1024 ))
	elif command -v sysctl >/dev/null 2>&1; then
		total_mb=$(( $(sysctl -n hw.memsize) / 1024 / 1024 ))
	fi
	local heap=$(( total_mb / 2 ))
	if (( heap > 4096 )); then
		heap=4096
	fi
	if (( heap < 1536 )); then
		heap=1536
	fi
	if (( total_mb < 4096 )); then
		warn "Only ${total_mb} MB RAM detected. The build may be slow or run out of memory."
	fi
	echo "$heap"
}

run_gradle() {
	local heap
	heap="$(gradle_heap_mb)"
	local task=":app:assembleRelease"
	[[ "$VARIANT" == "debug" ]] && task=":app:assembleDebug"
	local args=(--no-daemon --stacktrace --console=plain
		"-Dorg.gradle.jvmargs=-Xmx${heap}m -XX:MaxMetaspaceSize=1g -Dfile.encoding=UTF-8")
	chmod +x "$ROOT/gradlew"
	if [[ "$CLEAN" -eq 1 ]]; then
		log "Cleaning previous outputs"
		rm -rf "$DIST_DIR"
		(cd "$ROOT" && ./gradlew "${args[@]}" clean)
	fi
	local attempt
	for attempt in 1 2 3; do
		log "Running Gradle $task (attempt $attempt of 3)"
		if (cd "$ROOT" && ./gradlew "${args[@]}" "$task"); then
			return 0
		fi
		if [[ "$attempt" -lt 3 ]]; then
			warn "Gradle failed, retrying in 15 seconds (network hiccups are the usual cause)"
			sleep 15
		fi
	done
	die "Gradle build failed three times. See $LOG_FILE"
}

collect_apk() {
	local version
	version="$(grep -E '^val appVersionName[[:space:]]*=' "$ROOT/app/build.gradle.kts" | head -n 1 | sed -E 's/.*"(.*)".*/\1/')"
	version="${version#v}"
	[[ -n "$version" ]] || die "Could not read appVersionName from app/build.gradle.kts"
	local out="$DIST_DIR/yutbe$version.apk"
	if [[ "$VARIANT" == "debug" ]]; then
		out="$DIST_DIR/yutbe$version-debug.apk"
	fi
	[[ -f "$out" ]] || die "APK not found at $out"
	local apksigner
	apksigner="$(find "$SDK_DIR/build-tools" -maxdepth 2 -name apksigner -type f 2>/dev/null | sort -V | tail -n 1 || true)"
	if [[ -n "$apksigner" ]]; then
		JAVA_HOME="$JDK_DIR" "$apksigner" verify "$out"
		log "Signature verified"
	else
		warn "apksigner not found, signature not verified"
	fi
	log "SHA-256: $(sha256_of "$out")"
	log "Done. Your APK: $out"
}

main() {
	detect_platform
	ensure_system_tools
	TMP_DIR="$(mktemp -d "${TMPDIR:-/tmp}/yutbe-build.XXXXXX")"
	mkdir -p "$YUTBE_HOME"
	install_jdk
	export JAVA_HOME="$JDK_DIR"
	export PATH="$JAVA_HOME/bin:$PATH"
	export ANDROID_HOME="$SDK_DIR"
	export ANDROID_SDK_ROOT="$SDK_DIR"
	install_android_sdk
	write_local_properties
	if [[ "$VARIANT" == "release" ]]; then
		ensure_signing_key
	fi
	run_gradle
	collect_apk
}

main
