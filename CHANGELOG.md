# Changelog

All notable changes to Binti will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.0.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

---

## [2.3.3-beta] - 2026-08-01

### 🐛 Critical Fix: Accessibility Service Crash on DiLink 3

Fixed the crash that occurred when enabling the accessibility service on BYD
DiLink 3 (Android 10 head unit). The crash happened during the accessibility
enable flow and prevented the service from connecting.

#### Root Cause
The previous `onServiceConnected()` created a **new** `AccessibilityServiceInfo()`
and assigned it to `serviceInfo`, which conflicted with the XML config
(`accessibility_service_config.xml`). On DiLink 3's Android 10 build, this
programmatic override triggered a re-binding loop / `IllegalStateException`
that crashed the service before it could fully connect.

#### Fixes
1. **`onServiceConnected()`** now *mutates* the existing `serviceInfo` (loaded
   from XML) instead of replacing it: `val info = serviceInfo;
   info.packageNames = ...; serviceInfo = info`. This is the safe pattern
   recommended by the Android accessibility docs for OEM head units. Wrapped
   in try/catch so if the programmatic config fails, the XML config remains
   active (better than crashing).
2. **Removed `notificationTimeout` override** — XML says `0`, programmatic code
   said `100`. The mismatch was another crash trigger.
3. **Removed `directBootAware="true"`** from the accessibility service in
   `AndroidManifest.xml`. DiLink 3's Direct Boot implementation is buggy —
   the service tried to start before user unlock, crashing because
   credential-encrypted `SharedPreferences` wasn't available.
4. **Removed `flagRequestFilterKeyEvents`** and `canRequestFilterKeyEvents`
   from `accessibility_service_config.xml`. This flag caused crashes on some
   Android 10 OEM builds. Media-key interception is handled via
   `AudioManager.dispatchMediaKeyEvent()` which doesn't need this flag.
5. **Removed unused `AccessibilityServiceInfo` import**.

#### New: Accessibility Diagnostics Screen
Added an "Accessibility Diagnostics" section to the Vehicle State debug screen
(Settings → Developer → Vehicle State). Shows:
- Whether the service is enabled in system settings
- Whether the service instance is connected (`onServiceConnected` fired)
- The service's `AccessibilityServiceInfo` flags, event types, packageNames
- All enabled accessibility services on the device
- The detected BYD model

If the service still crashes after these fixes, this screen will show
"Instance connected: false" even after the user toggles it on — confirming
the crash and pointing to the next debugging step.

### 📦 Version
- Bumped to `2.3.3-beta` (versionCode 9)
- Updated `model_config.json`, `dilink_intent_map.json`, and CI model-manifest
  URLs to v2.3.3-beta

---

## [2.3.2-beta] - 2026-08-01

### 🔒 Security & Architecture Fixes

#### WRITE_SETTINGS Isolation (Fix #1)
- **Brightness control moved to enthusiast flavor only** — the `WRITE_SETTINGS` permission (required for `Settings.System` brightness control) is no longer declared in the shared `AndroidManifest.xml`. It now lives in `app/src/enthusiast/AndroidManifest.xml` only.
- **New `BrightnessBridge` interface** (`app/src/main/.../settings/BrightnessBridge.kt`) — mirrors the `AdvancedTelemetryBridge` pattern. Main-source code calls `BrightnessLoader.get()` and never references the enthusiast implementation directly.
- **`BrightnessLoader` registry** (`BrightnessLoader.kt`) with `NoOpBrightnessBridge` default — the store flavor returns "not available" for all brightness commands; the enthusiast flavor registers `RealBrightnessBridge`.
- **Store flavor** `BrightnessInitializer` — no-op, asserts no real bridge is registered.
- **Enthusiast flavor** `RealBrightnessBridge` — implements `Settings.System.putInt` brightness control with `WRITE_SETTINGS` (declared in enthusiast manifest).
- **`DiLinkCommandExecutor`** refactored — `setBrightness()` and `adjustBrightness()` now delegate to `BrightnessLoader.get()` instead of calling `Settings.System` directly. Dead `requestWriteSettings()` method removed.
- **Verified**: store APK has zero `WRITE_SETTINGS` references (`aapt2 dump permissions`); enthusiast APK has the permission.

#### Hardcoded Keystore Password Removed (Fix #2)
- **`app/build.gradle.kts`** `signingConfigs.release` — removed the `"binti2026beta"` fallback for `storePassword` and `keyPassword`, and the `"binti_release"` fallback for `keyAlias`. All three now default to empty string.
- **Release build type** — now checks that the keystore exists AND all three env vars (`KEYSTORE_PASSWORD`, `KEY_ALIAS`, `KEY_PASSWORD`) are non-empty before using the release signing config. Falls back to debug signing with a clear `logger.warn` message if credentials are missing.
- No release APK can ever be silently signed with the public weak password again.

#### License Reference Fix (Fix #3)
- **`CONTRIBUTING.md`** — changed "MIT License" to "Apache License, Version 2.0" to match the actual `LICENSE` file.

#### Changelog Update (Fix #4)
- Added entries for v2.3.0-beta, v2.3.1-beta, and this v2.3.2-beta fix set (the changelog previously stopped at v2.2.0-beta).

#### Version Drift Fix (Fix #5)
- **`app/src/main/assets/models/model_config.json`** — bumped `"version"` and `"min_app_version"` to `"2.3.2-beta"` to match the current app version.
- **`app/src/main/assets/commands/dilink_intent_map.json`** — bumped `"version"` to `"2.3.2-beta"`.

#### CI Model Manifest Fix (Fix #6)
- **`.github/workflows/ci.yml`** `model-manifest` job — replaced the stale Google Drive hosting URL (`drive.google.com/uc?export=download&id=...`) and incorrect "1247 MB" model size with the actual GitHub Releases hosting (`github.com/waleedmandour/binti2/releases/download/...`) and correct ~318 MB size.

---

## [2.3.1-beta] - 2026-08-01

### 🏗️ Build & CI Fixes

#### Build-blocking compile errors fixed
- **Duplicate string resource** — `accessibility_service_description` was declared twice in `values/strings.xml` (lines 9 and 79). Android resource merger rejects duplicates. Removed the second declaration.
- **XML comments inside tag attribute lists** — `AndroidManifest.xml` and `accessibility_service_config.xml` had XML comments between attributes of a start tag, causing AAPT2 parse errors ("Element type must be followed by either attribute specifications, > or />"). Moved all comments into top-level comment blocks.
- **Missing string resources** — added `download_error` (referenced by `MainActivity.kt:403`) and `state_off` (referenced by `MainActivity.updateServiceState()`).
- **Kotlin compile errors** — `BootReceiver.kt` (val shadowing of `Intent.setAction()`), `ModelManager.kt` (`isActive` without `CoroutineScope` receiver), `AdvancedTelemetryProbe.kt` (`return` in expression body).
- **`CONTENT_SCRAPE_THROTTLE_MS`** — declared as `fun` but used as `val`; changed to `val`.

#### Unit test infrastructure
- **`testOptions.unitTests.isReturnDefaultValues = true`** — added to `build.gradle.kts` so `android.util.Log` calls don't throw "Method not mocked" during unit tests.
- **Initial snapshot `timestamp = 0L`** — `VehicleDataRepository` now starts with epoch timestamp so `isStale()` returns true on a never-published repository.
- **Test correctness fixes** — Arabic-Indic digit parsing (JVM handles them by default), `parseRange` replacement order (longer units first), `mergeAdvanced` no-op semantics.

#### CI workflow fixes
- **`permissions: contents: write`** — added to both workflow files. Without this, `softprops/action-gh-release@v2` failed with "Resource not accessible by integration".
- **`generate_release_notes: false`** — the auto-notes API call was the specific blocked endpoint.
- **Removed redundant `build.yml`** — `ci.yml` is now the single CI workflow. `build.yml` was racing with `ci.yml` and failing with 0 jobs.
- **`gradle-home-cache-cleanup: false`** — transient GitHub cache service outages were marking jobs as failed even when all build steps succeeded.

### 🔒 License Change
- **MIT → Apache 2.0** — see v2.3.0-beta's LICENSE-ANALYSIS.md for rationale. Updated LICENSE, NOTICE, README, CONTRIBUTING, and added SPDX-License-Identifier headers to all 32 Kotlin source files.

### 📖 Documentation
- **`docs/BUILD.md`** — comprehensive local build guide (prerequisites, debug/release commands, keystore setup, troubleshooting, CI trigger instructions).
- **`docs/LICENSE-ANALYSIS.md`** — analysis of MIT vs Apache 2.0 for the project's characteristics (patent grant, Android ecosystem norm, NOTICE file protection, trademark exclusion).
- **`docs/Binti-Guide-ar.pdf`** (9 pages, 581 KB) — professional Arabic user guide with full RTL layout, 8 chapters covering installation, permissions, voice commands, advanced telemetry, troubleshooting, and privacy.

---

## [2.3.0-beta] - 2026-07-31

### 🎉 Tier 1 + Tier 2 Vehicle Data Architecture

#### Tier 1 — Accessibility-layer structured vehicle data (ship-ready)
- **`VehicleSnapshot`** data class — Tier 1 + Tier 2 nullable fields, merge semantics, `hasAdvancedTelemetry` flag, `DataSource` enum.
- **`VehicleDataRepository`** — `StateFlow<VehicleSnapshot>` with 15-second staleness check. `DiLinkAccessibilityService` publishes into it whenever a vehicle-info / energy / climate screen comes to the foreground.
- **`VehicleSnapshotParser`** — pure extractor with pluggable `IDLookup` interface so the parsing logic is unit-testable without the Android Accessibility API. Top-level `parsePercent` / `parseRange` / `parseTemp` extension functions handle Arabic unit suffixes (٪, كم, °).
- **`DiLinkCommandExecutor`** INFO commands refactored — `getBatteryInfo`, `getRangeInfo`, `getChargingInfo`, `getTemperatureInfo`, `getWeatherInfo`, `getVehicleStatus` now read from the repository first, with a fresh Accessibility scrape fallback. "⚠️ (تجريبي)" badge appended when Tier 2 fields are present.
- **New voice intents** — "الرينج فيه كام؟" (range), "العربية بتشحن؟" (charging), "حالة العربية" (composite status) added to `dilink_intent_map.json`.
- **Developer debug screen** — Settings → Developer → Vehicle State. Collects from `vehicleDataRepository.snapshot` StateFlow, has Refresh / Probe / Telemetry Settings buttons.

#### Tier 2 — Optional Advanced Telemetry (experimental, opt-in, gated)
- **Product flavors** — `store` (Huawei AppGallery, Tier 2 compiled out entirely) and `enthusiast` (GitHub Releases sideload, Tier 2 available). Gradle source-set isolation guarantees the store flavor never contains `android.hardware.bydauto.*` code.
- **`AdvancedTelemetryBridge`** interface + `AdvancedTelemetryLoader` registry + `NoOpAdvancedTelemetryBridge` default — shared contract in main source so the developer debug screen and command executor can query Tier 2 without importing enthusiast classes.
- **`TelemetryConsentManager`** — three-state consent (NOT_SHOWN / GRANTED / REVOKED), version-gated, persisted in SharedPreferences.
- **`AdvancedTelemetryProbe`** (enthusiast only) — reflection-based reader modelled on the MIT-licensed `wheregoes/byd-apps` BYD Probe research. Allow-list of service prefixes (battery, tyre, motor, hvbus, auxbattery, energy, vehicle) + hard-blocked substrings (steer, brake, abs, esp, torque, airbag, srs, adas, cruise, control, actuator, drive) as the primary safety boundary.
- **`AdvancedTelemetryOnboardingActivity`** (enthusiast only) — verbose warning, acknowledgement checkbox, reversible toggle. Calls `consent.grant()` / `consent.revoke()` and resets the repository on revoke.

#### Documentation
- **`docs/ADVANCED_TELEMETRY.md`** — scope table, architecture diagram, consent model, MIT attribution, `bydauto` isolation verification command.
- **`docs/APP_GALLERY_SUBMISSION_CHECKLIST.md`** — 10-section pre-submission checklist for the store flavor.
- **`NOTICE`** — MIT attribution for `wheregoes/byd-apps` reference research; explicit non-inclusion of BUSL-1.1-licensed `angoikon/byd-trip-stats`; trademark disclaimer.
- **README.md** — "Independent, unofficial project" banner; "A note on BYD D++" section; "Tiered Vehicle Data Architecture" section.

#### Unit tests (37 tests, 0 failures)
- `VehicleSnapshotTest` — merge semantics, `hasAdvancedTelemetry`, source promotion, `toDebugString`.
- `VehicleDataRepositoryTest` — publish/merge/staleness/`freshOrNull` contract.
- `VehicleSnapshotParserTest` — happy path with Yuan Plus IDs, Arabic unit suffixes, fallback ID walking, Dolphin/Seal ID sets, `parsePercent`/`parseRange`/`parseTemp` helpers.
- `BYDModelsTest` — Yuan Plus ID constants, `forModel()` factory for all 5 models + unknown fallback, `FallbackIDs` coverage.

---

## [2.2.0-beta] - 2026-03-31

### 🎉 New Features

#### EV Charging Station Discovery
- **Station Database** — 30 EV charging stations across 22 Egyptian cities bundled in `stations.json`
- **StationManager** — New utility class with three-tier data loading: Remote (GitHub Pages) → Cache → Asset
- **Nearest Station Search** — Haversine-based distance calculation finds the closest active station within configurable radius (default 50 km)
- **Auto-Navigation** — After finding a station, Binti speaks the details in Egyptian Arabic and auto-launches BYD navigation via geo intent (with generic geo fallback)
- **Station Response** — Full Egyptian Arabic spoken response including station name, city, distance, power (kW), cost (EGP/kWh), connectors, and hours
- **STATION Intent** — New intent type in `dilink_intent_map.json` with 4 Egyptian Arabic patterns: "أقرب محطة شحن", "وين محطة الشحن", "شحن العربية", "أقرب كهربا"
- **Remote Station Updates** — Station data hosted on GitHub Pages (`waleedmandour.github.io/binti2/data/stations.json`), fetched automatically with 24-hour cache TTL
- **4 Operators** — Revolt EV (9 stations), REVO (8), Infinity EV (7), Morocco Cars (6)
- **Covered Cities** — Cairo, Alexandria, Giza, 6th October, Ain Sokhna, Hurghada, Suez, Mansoura, Assiut, Tanta, Fayoum, Ismailia, Banha, Zagazig, Obour City, Rehab City, Nasr City, Dokki, Heliopolis, Sidi Bishr, Abu Qir, Dreamland
- **Search API** — Free-text search across station name (Arabic/English), city, operator, and address

#### GitHub Releases Model Hosting
- **Replaced Google Drive** — Model downloads now use GitHub Releases instead of Google Drive, eliminating virus scan confirmation page issues for large files
- **DownloadManager Integration** — Models are downloaded via Android's built-in `DownloadManager` API for system-level retry, background download persistence, and visible progress notifications
- **GitHub Releases URLs** — ASR model URL: `github.com/waleedmandour/binti2/releases/download/v2.2.0-beta/vosk-model-ar-mgb2-0.4.zip`
- **ZIP Extraction** — Automatic post-download extraction with zip-slip path traversal protection
- **Download State Machine** — Sealed-class based state tracking: Pending → Running (with progress %) → Completed/Failed
- **Cancel Support** — `cancelAllDownloads()` method to abort active downloads

### Added
- **STATION intent** in `DiLinkCommandExecutor.kt` — Routes station queries to `StationManager`, builds Egyptian Arabic response, and auto-navigates
- **GREETINGS intent** — Contextual Egyptian Arabic greetings based on time of day
- **SOCIAL intent** — Response to thanks and social expressions
- **HELP intent** — Lists all capabilities including EV charging station discovery
- **`CommandResult.shouldSpeak`** — New boolean field for controlling whether TTS should speak a response (backward compatible, defaults to `true`)
- **GitHub Pages domain** in `network_security_config.xml` for station data fetching
- **`github.io`** domain added to network security config

### Changed
- **Model hosting** from Google Drive to GitHub Releases
- **`ModelManager.kt`** completely rewritten to use `DownloadManager` instead of OkHttp
- **`model_config.json`** updated with GitHub Releases URLs and hosting metadata
- **Version** bumped from `2.1.0-beta` (versionCode 4) to `2.2.0-beta` (versionCode 5)
- **`dilink_intent_map.json`** version updated to `2.2.0-beta`
- **NLU classifier labels** updated to include `STATION` intent

### Removed
- **ShellExecutor.kt** — Dead code deleted; the app has always used Accessibility Service exclusively, and this file had zero imports in the active codebase

### Technical Details

| Component | Version |
|-----------|---------|
| Min SDK | 26 (Android 8.0) |
| Target SDK | 34 (Android 14) |
| Kotlin | 2.0.21 |
| AGP | 8.7.3 |
| Gradle | 8.10.2 |
| TensorFlow Lite | 2.14.0 |
| ONNX Runtime | 1.16.3 |
| Vosk Android | 0.3.47 |
| Huawei HMS | 7.0+ |

### Model Stack

| Component | Size | Source |
|-----------|------|--------|
| ASR + Wake Word (Vosk MGB2) | 318 MB | GitHub Releases |
| NLU (EgyBERT-tiny, optional) | 25 MB | Not bundled |
| TTS Voice (optional) | 80 MB | Not bundled |
| **Required Download** | **318 MB** | |

---

## [2.1.0-beta] - 2026-03-26

### Fixed
- **AGP Version** — Updated from non-existent 9.1.0 to stable 8.7.3
- **Gradle Version** — Updated from 9.3.1 to 8.10.2
- **Kotlin Version** — Aligned to 2.0.21 with AGP compatibility
- **Intent Map JSON** — Fixed `responses` → `response` field name mismatch
- **BootReceiver** — Added `android:exported="true"` for Android 12+
- **Widget PendingIntents** — Added `FLAG_IMMUTABLE` for Android 12+
- **VoiceProfileManager** — Fixed byte array truncation bug
- **Broadcast Restrictions** — Updated for Android 14+ compliance
- **ProGuard Rules** — Added rules for TFLite GPU/AutoValue missing classes
- **Lint Errors** — Fixed 13 issues (backup_rules, GridLayout namespace, translations)
- **Launcher Icons** — Generated proper icons for all mipmap densities
- **Tashkeel Preservation** — Fixed EgyptianTTS regex to preserve Sukoon (ْ) at word endings
- **Model Size** — Corrected from 1247MB to actual 318MB across all config files
- **Footer Credentials** — Updated to `w.abumandour@squ.edu.om`

### Added
- **Demo Voice Message** — Auto-play Egyptian Arabic welcome on first app launch
- **agconnect-services.json** — Huawei HMS plugin configuration
- **Release keystore** — Signing configuration for beta releases

---

## [2.0.0] - 2025-01-01

### 🎉 Major Release

This is a major version upgrade with significant improvements to model hosting, download options, and overall system reliability.

### Added

#### Model Hosting & Downloads
- **Google Drive Model Hosting** - Models can now be hosted on Google Drive with direct download support
- **Multiple Download Sources** - Support for primary and mirror URLs for model downloads
- **Local Model Support** - Load models from USB/SD card for completely offline setup
- **Virus Scan Handling** - Automatic handling of Google Drive virus scan warnings for large files
- **Custom Google Drive Folders** - Users can configure their own Google Drive folders for model hosting
- **Progress Tracking** - Enhanced progress reporting for large model downloads

#### Voice Processing
- **Vosk Grammar-based Wake Word Detection** - Uses Vosk grammar constraint for "يا بنتي" detection
- **Shared ASR Model** - Wake word detection reuses the same Vosk Arabic model, reducing storage
- **Silence Detection** - Automatic stop after 1.5 seconds of silence
- **Consecutive Detection Filter** - Reduces false positives with consecutive detection requirement

#### Intent Classification
- **Egyptian Arabic Normalization** - Better handling of dialect variations
- **Entity Extraction** - Temperature, location, and contact name extraction
- **Fuzzy Matching** - Fallback for typos and speech recognition errors

#### DiLink Integration
- **Extended AC Control** - Temperature adjustment, mode selection, fan speed
- **Navigation POI Search** - Gas stations, charging stations, restaurants, hospitals, parking
- **Phone Commands** - Call by number, call by contact, answer, reject, end, redial
- **Media Key Events** - System-level media control for any music app

#### User Interface
- **Quick Actions Widget** - Home screen widget for quick voice activation
- **Enhanced Settings** - Configure home/work addresses, model sources
- **Arabic Localization** - Full Egyptian Arabic UI strings

### Breaking Changes
- **Model URLs Changed** - Previous B2 URLs replaced with Google Drive links
- **Config File Format** - `model_config.json` structure updated
- **Download API** - New methods for Google Drive and local model loading

---

## [1.0.0-beta01] - 2024-03-24

### Added
- Wake word detection for "يا بنتي" (Ya Binti)
- Egyptian Arabic speech recognition (Vosk)
- Intent classification for 30+ commands
- Egyptian female voice responses
- DiLink vehicle integration via AccessibilityService
- AC Control, Navigation, Media, Phone commands
- Download from Backblaze B2 with SHA256 verification
- HMS ML Kit ASR/TTS fallback
- Huawei AppGallery-ready configuration

---

## Version Naming Convention

- **Major (X.0.0):** Breaking changes, major features, architecture changes
- **Minor (1.X.0):** New features, backwards compatible
- **Patch (1.0.X):** Bug fixes, minor improvements
- **Pre-release (-beta, -alpha):** Testing versions
