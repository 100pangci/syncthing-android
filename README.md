# Syncthing-Fork (Compose Edition)

English | [简体中文](README_zh-CN.md)

[![License: MPLv2](https://img.shields.io/badge/License-MPLv2-blue.svg)](https://opensource.org/licenses/MPL-2.0)
[![Build App](https://github.com/100pangci/syncthing-android/actions/workflows/build-app.yaml/badge.svg)](https://github.com/100pangci/syncthing-android/actions/workflows/build-app.yaml)
[![Release](https://img.shields.io/github/v/release/100pangci/syncthing-android)](https://github.com/100pangci/syncthing-android/releases/latest)

An Android wrapper for [Syncthing](https://github.com/syncthing/syncthing). The Syncthing core, written in Go, is cross-compiled into a native library (`libsyncthingnative.so`) hosted by a foreground service, with a native Android UI on top — private, decentralized file syncing across devices, no root required.

<img src="app/src/main/play/listings/en-US/graphics/phone-screenshots/1.jpg" alt="Screenshot 1" width="150" /><img src="app/src/main/play/listings/en-US/graphics/phone-screenshots/2.jpg" alt="Screenshot 2" width="150" /><img src="app/src/main/play/listings/en-US/graphics/phone-screenshots/3.jpg" alt="Screenshot 3" width="150" />

## Changes in this Fork

An intensive rewrite on top of [researchxxl/Syncthing-Fork](https://github.com/researchxxl/syncthing-android):

### Full UI Rewrite
- The legacy Java/View UI has been completely rewritten with **Jetpack Compose + Material 3**, using a single-Activity **Navigation 3** architecture
- The folders / devices / status pages moved to a bottom navigation bar
- All legacy View code and resources removed

### New Features
- **AMOLED black theme**: follow system / light / dark / AMOLED, switching instantly, with the embedded Web GUI synced to its black theme
- **Full i18n coverage**: all 38 language packs completed to 100% (496 keys + 8 plurals each, including az / be / ckb / gl built from scratch), with every file's key order aligned to the master `values/strings.xml` for easy maintenance
- Application ID changed to `com.github.ywpc05.syncthingfork`, so it installs alongside the upstream app

### Stability & Fixes
- Core stability refactor: migrated to NetworkCallback, split service responsibilities, fixed real defects in config / event handling

### Engineering
- Added Robolectric unit tests for core sync paths (event processing, run conditions, config parsing)
- CI fully takes over: automated debug / release builds with signing

### Goal

- **Fully rewrite the service layer**: the service layer (`SyncthingService` / `RestApi` / `EventProcessor` / `RunConditionMonitor` / `ConfigXml`, etc.) is still Java; the plan is to migrate it step by step to a modern Kotlin implementation with coroutines / Flow, unifying the stack with the Compose UI and eventually removing all Java code and Dagger dependency injection.

## Download

Grab an APK from [Releases](https://github.com/100pangci/syncthing-android/releases/latest), or subscribe via [Obtainium](https://apps.obtainium.imranr.dev/redirect?r=obtainium%3A%2F%2Fapp%2F%7B%22id%22%3A%22com.github.ywpc05.syncthingfork%22%2C%22url%22%3A%22https%3A%2F%2Fgithub.com%2F100pangci%2Fsyncthing-android%22%2C%22author%22%3A%22100pangci%22%2C%22name%22%3A%22Syncthing-Fork%22%2C%22preferredApkIndex%22%3A0%2C%22additionalSettings%22%3A%22%7B%5C%22verifyLatestTag%5C%22%3Atrue%7D%22%2C%22overrideSource%22%3Anull%7D).

> The application ID is `com.github.ywpc05.syncthingfork` (debug builds get a `.debug` suffix). It does not conflict with the upstream `com.github.catfriend1.syncthingfork` and both can be installed side by side. For migrating data from an older version, see the [migration guide](wiki/migration/Switching-from-the-deprecated-official-version.md).

## Building

```bash
# 1. Install prerequisites (SDK / NDK / Go)
python3 scripts/install_minimum_android_sdk_prerequisites.py

# 2. Cross-compile the Syncthing native library
./gradlew buildNative

# 3. Build the app
./gradlew assembleDebug     # or assembleRelease
```

See [Building and Development](wiki/developers/Building-and-Development.md) for details. CI automatically builds and signs debug / release APKs.

## Docs / Wiki

The knowledge base (FAQ, battery optimization, vendor-specific background restrictions, troubleshooting, etc.) lives in the [wiki](wiki#readme).

## Tech Stack

| Layer | Technologies |
|---|---|
| UI | Kotlin, Jetpack Compose, Material 3, Navigation 3 |
| Service layer | Java (foreground service / REST API / event processing / run condition monitoring) |
| Sync core | Syncthing (Go, git submodule) → NDK cross-compile, run as child process |
| DI / data | Dagger 2 (KSP), Gson, Volley, SharedPreferences |
| Build | Gradle (Kotlin DSL) + Version Catalog, JDK 21, AGP 9.x |

- minSdk 23 (Android 6.0) / targetSdk 36 / compileSdk 37

## Acknowledgments

- Upstream fork source: [researchxxl/syncthing-android](https://github.com/researchxxl/syncthing-android)
- Former maintainers: [Catfriend1](https://github.com/Catfriend1), [imsodin](https://github.com/imsodin), [nutomic](https://github.com/nutomic)
- The [Syncthing](https://github.com/syncthing/syncthing) core team

## Privacy Policy

See [privacy-policy.md](privacy-policy.md).

## License

[MPLv2](LICENSE)
