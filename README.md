YuTbe

A continuation of the Litube project: https://github.com/HydeYYHH/litube


YuTbe is an advanced WebView wrapper for YouTube with a native player, ad-free playback, SponsorBlock, mini-player, picture-in-picture, background play, a local queue with YouTube-style autoplay, and a built-in downloader. Source code: https://github.com/techniixdotcom/yutbe

Build

Requirements: Linux (x86_64 or arm64), macOS, or Windows through WSL2; about 10 GB of free disk space; 4 GB of RAM or more; an internet connection.

    chmod +x BUILD.sh
    ./BUILD.sh

The script downloads and checksum-verifies Eclipse Temurin JDK 21 and the Android SDK into ~/.yutbe, accepts the SDK licenses, creates a private release signing key the first time, builds the extractor from ./newpipe-extractor together with the app, verifies the APK signature and writes the APK to ./dist as yutbe<version>.apk, for example dist/yutbe1.0.1.apk. A full log is written to build.log.

Options: ./BUILD.sh --clean rebuilds from scratch, ./BUILD.sh --debug builds a debug APK.

Back up ~/.yutbe/signing. Android only installs updates that are signed with the same key.

Install

Copy dist/yutbe1.0.1.apk to the phone and open it (allow installing from unknown sources), or with USB debugging enabled:

    ~/.yutbe/android-sdk/platform-tools/adb install -r dist/yutbe1.0.1.apk

Notes

Playback uses NewPipe Extractor's visionOS client, which YouTube does not throttle with proof-of-origin tokens. The extractor sources are the NewPipe Extractor dev branch at commit eb53b79, vendored in ./newpipe-extractor.

Releases

The in-app update check reads the latest release of https://github.com/techniixdotcom/yutbe. Publish each build as a GitHub release whose tag is the version name, for example v1.0.1, and raise appVersionName and appVersionCode at the top of app/build.gradle.kts before every new build. The APK file name follows appVersionName automatically.

License

GPL-3.0. YuTbe is derived from Litube by HydeYYHH (GPL-3.0); this notice is required by the license. NewPipe Extractor is GPL-3.0-or-later.
