YuTbe

YuTbe is an advanced WebView wrapper for YouTube with a native player, ad-free playback, SponsorBlock, mini-player, picture-in-picture, background play, a local queue with YouTube-style autoplay and a built-in downloader. Source code: https://github.com/techniixdotcom/yutbe

Build

Requirements: Linux (x86_64 or arm64), macOS, or Windows through WSL2; about 10 GB of free disk space; 4 GB of RAM or more; an internet connection.

    chmod +x BUILD.sh
    ./BUILD.sh

The script downloads and checksum-verifies Eclipse Temurin JDK 21 and the Android SDK into ~/.yutbe, accepts the SDK licenses, creates a private release signing key the first time, builds the extractor from ./newpipe-extractor together with the app, verifies the APK signature and writes the APK to ./dist as yutbe<version>.apk, for example dist/yutbe1.0.2.apk. A full log is written to build.log.

Options: ./BUILD.sh --clean rebuilds from scratch, ./BUILD.sh --debug builds a debug APK.

Run BUILD.sh as your normal user, not with sudo; it asks for your password itself only if system packages are missing. If an earlier run left files you cannot move or delete (a lock on folders), run ./BUILD.sh --fix-permissions once.

Signing key

The key is kept in ~/.yutbe/signing (yutbe-release.p12 and signing.env), outside the project folder, so deleting or replacing the project folder does not touch it. Back it up: Android only installs updates that are signed with the same key. Every build prints the key's SHA-256 fingerprint, so you can check that two builds used the same key.

The simplest way to install or update is to connect the phone with USB debugging on and run ./BUILD.sh --install. It reads which key the installed YuTbe was signed with, picks that key if it is anywhere on this computer (including /root/.yutbe/signing from an old sudo build), builds and installs, and prints the exact reason if Android refuses.

If an update is refused with "App not installed" or "package conflicts with an existing package", the new build was signed with a different key. When the old key still exists, build again with it:

    ./BUILD.sh --import-key /root/.yutbe/signing

If the old key is gone, run ./BUILD.sh --reinstall, which uninstalls the old app first (its settings, history and login are lost once).

Install

Copy dist/yutbe1.0.2.apk to the phone and open it (allow installing from unknown sources), or with USB debugging enabled:

    ./BUILD.sh --install

Notes

Playback uses NewPipe Extractor's visionOS client, which YouTube does not throttle with proof-of-origin tokens. The extractor sources are the NewPipe Extractor dev branch at commit eb53b79, vendored in ./newpipe-extractor.

Releases

The in-app update check reads the latest release of https://github.com/techniixdotcom/yutbe. Publish each build as a GitHub release whose tag is the version name, for example v1.0.2, and raise appVersionName and appVersionCode at the top of app/build.gradle.kts before every new build. The APK file name follows appVersionName automatically.

License

GPL-3.0. YuTbe is derived from Litube by HydeYYHH (GPL-3.0); this notice is required by the license.
