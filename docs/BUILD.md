# Building Binti from source

This document explains how to build the Binti APKs locally from source.
Two product flavors are available — see [README §Tiered Vehicle Data
Architecture](README.md#tiered-vehicle-data-architecture) for the full
background.

| Flavor       | Application ID                  | Tier 2? | Distribution channel         |
|--------------|---------------------------------|---------|------------------------------|
| `store`      | `com.binti.dilink`              | No      | Huawei AppGallery            |
| `enthusiast` | `com.binti.dilink.enthusiast`   | Yes     | GitHub Releases (sideload)   |

## Prerequisites

- **JDK 17** — Temurin 17 recommended. Set `JAVA_HOME` to the JDK install path.
- **Android SDK** with:
  - Platform `android-34` (API 34)
  - Build Tools `34.0.0`
  - Platform Tools (latest)
- **Gradle 8.10.2** — the project includes the Gradle wrapper (`./gradlew`),
  so you do NOT need to install Gradle separately. The wrapper downloads the
  correct version automatically.

### Installing the Android SDK (if you don't have Android Studio)

```bash
# Download the command-line tools from:
# https://developer.android.com/studio#command-line-tools-only
mkdir -p ~/android-sdk/cmdline-tools
unzip commandlinetools-linux-*.zip -d ~/android-sdk/cmdline-tools/
mv ~/android-sdk/cmdline-tools/cmdline-tools ~/android-sdk/cmdline-tools/latest

export ANDROID_HOME=~/android-sdk
export PATH=$PATH:$ANDROID_HOME/cmdline-tools/latest/bin:$ANDROID_HOME/platform-tools

# Accept licenses and install required components
yes | sdkmanager --licenses
sdkmanager "platforms;android-34" "build-tools;34.0.0" "platform-tools"
```

### Tell Gradle where the SDK is

Create a file called `local.properties` in the repository root (this file is
gitignored — never commit it):

```properties
sdk.dir=/path/to/your/android-sdk
```

## Build commands

### Debug APKs (fastest, for testing)

```bash
# Store flavor (Huawei AppGallery build — Tier 1 only)
./gradlew assembleStoreDebug
# Output: app/build/outputs/apk/store/debug/app-store-debug.apk

# Enthusiast flavor (Tier 1 + Tier 2 Advanced Telemetry)
./gradlew assembleEnthusiastDebug
# Output: app/build/outputs/apk/enthusiast/debug/app-enthusiast-debug.apk

# Both flavors at once
./gradlew assembleStoreDebug assembleEnthusiastDebug
```

### Release APKs (signed, for distribution)

Release builds require a keystore. The project's `build.gradle.kts` looks for
`app/release-keystore.jks` and reads passwords from environment variables.

#### Option A: Use the bundled debug keystore (quick, for personal testing)

```bash
# No keystore setup needed — the debug signing config is used as a fallback
# when release-keystore.jks doesn't exist.
./gradlew assembleStoreRelease
./gradlew assembleEnthusiastRelease
```

#### Option B: Create your own release keystore (for proper distribution)

```bash
# 1. Generate a keystore (one-time)
keytool -genkey -v \
  -keystore app/release-keystore.jks \
  -keyalg RSA -keysize 2048 -validity 10000 \
  -alias binti_release

# 2. Set the passwords as environment variables
export KEYSTORE_PASSWORD="your_keystore_password"
export KEY_ALIAS="binti_release"
export KEY_PASSWORD="your_key_password"

# 3. Build
./gradlew assembleStoreRelease assembleEnthusiastRelease
# Output:
#   app/build/outputs/apk/store/release/app-store-release.apk
#   app/build/outputs/apk/enthusiast/release/app-enthusiast-release.apk
```

## Run the unit tests

```bash
./gradlew testStoreDebugUnitTest
# Report: app/build/reports/tests/testStoreDebugUnitTest/index.html
```

37 tests covering:
- `VehicleSnapshot` merge semantics and `hasAdvancedTelemetry` flag
- `VehicleDataRepository` publish/merge/staleness contract
- `VehicleSnapshotParser` fallback ID walking, Arabic unit suffixes
- `BYDModels` resource ID constants and `forModel()` factory

## Verify Tier 2 isolation (store flavor only)

After building the store APK, verify it contains zero references to
`android.hardware.bydauto.*` — this is the architectural guarantee that
Tier 2 code never ships in the AppGallery build:

```bash
# Using dexdump (from Android build-tools)
$ANDROID_HOME/build-tools/34.0.0/dexdump -l plain \
  app/build/outputs/apk/store/debug/app-store-debug.apk \
  | grep -c "AdvancedTelemetryProbe"
# Expected: 0

$ANDROID_HOME/build-tools/34.0.0/dexdump -d \
  app/build/outputs/apk/store/debug/app-store-debug.apk \
  | grep -c "bydauto"
# Expected: 0

# Quick check with strings (less reliable but no tools needed)
unzip -p app/build/outputs/apk/store/debug/app-store-debug.apk classes.dex \
  | strings | grep -ci bydauto
# Expected: 0
```

The enthusiast APK should have **>0** references to both — that's how you
know the Tier 2 probe code is present.

## Install on a BYD DiLink head unit

```bash
# Via ADB (USB cable connected to the head unit)
adb install -r app/build/outputs/apk/store/debug/app-store-debug.apk

# Or via USB drive: copy the APK to a FAT32-formatted USB drive,
# plug into the DiLink USB port, and use the DiLink file manager to install.
```

After installation:
1. Open "يا بنتي" from the app launcher.
2. Grant Accessibility Service permission (Settings → Accessibility → "يا بنتي" → Enable).
3. Grant Display Over Apps permission.
4. Grant Microphone permission.
5. Download the offline AI model (~318 MB) when prompted.
6. Tap "تشغيل المساعد" to start the assistant.

## Troubleshooting

### `SDK location not found`
Create `local.properties` with `sdk.dir=/path/to/android-sdk`.

### `Toolchain installation does not provide JAVA_COMPILER`
You're using a JRE, not a JDK. Install JDK 17 (Temurin recommended) and
set `JAVA_HOME` to point to it.

### `Task 'assembleDebug' not found`
This task no longer exists after the product flavor split. Use
`assembleStoreDebug` or `assembleEnthusiastDebug` instead. (If you see
this in a workflow file, the workflow is out of date.)

### `Method v in android.util.Log not mocked`
This only happens in unit tests. The `testOptions.unitTests.isReturnDefaultValues = true`
setting in `build.gradle.kts` should prevent it — if you see it, check that
setting hasn't been removed.

### Build hangs downloading dependencies
The project uses Google's Maven (`dl.google.com`) and Maven Central. If
you're behind a proxy, configure Gradle's proxy settings in
`gradle.properties`.

## CI

The [CI workflow](.github/workflows/ci.yml) runs on every push to `main` and
on every tag `v*`. It:
1. Runs unit tests (`testStoreDebugUnitTest`)
2. Builds both debug APKs (`assembleStoreDebug` + `assembleEnthusiastDebug`)
3. Verifies the store APK has zero `bydauto` references
4. On tag pushes: builds release APKs and uploads them to the GitHub Release

To trigger a release build in CI, push a tag:
```bash
git tag -a v2.3.2-beta -m "Release v2.3.2-beta"
git push origin v2.3.2-beta
```

For signed release builds in CI, set these GitHub repository secrets:
- `KEYSTORE_BASE64` — base64-encoded `release-keystore.jks`
  (`base64 -w0 release-keystore.jks`)
- `KEYSTORE_PASSWORD`
- `KEY_ALIAS`
- `KEY_PASSWORD`
