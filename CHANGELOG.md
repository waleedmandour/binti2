# Changelog

All notable changes to Binti will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.0.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

---

## [3.0.0-beta] - 2026-10-11

### 🔧 Phase 3 — DiLink Integration Tier

This release ports DiPlay's ADB substrate into the enthusiast flavor,
enabling Tier 2 vehicle data reads (speed, SoC, range, gear, tyres)
via `service call autoservice` over a self-implemented local ADB
protocol — WITHOUT root and WITHOUT a platform signing key.

**This is a beta release** — the ADB substrate is additive to the
existing enthusiast flavor. The new code compiles in the enthusiast
source set only; the store flavor is unaffected (no `bydauto` or ADB
references leak into the store APK, verified by CI).

#### Added — ADB Protocol Substrate (ported from DiPlay)
- **`adb/AdbPacket.kt`** — 24-byte little-endian ADB wire protocol message
  encoder/decoder. Handles CNXN, AUTH, OPEN, OKAY, CLSE, WRTE, STLS
  commands with payload-sum integrity check.
- **`adb/AdbKeys.kt`** — RSA-2048 keypair management. Generates and stores
  the keypair in `noBackupFilesDir/adb/`. Implements the AOSP
  `android_pubkey` wire format (modulus, n0inv, R^2 mod n, exponent) and
  signs auth tokens with NONEwithRSA + SHA-1 DigestInfo header.
- **`adb/LocalAdb.kt`** (273 lines) — pure-Java ADB protocol over TCP
  `127.0.0.1:5555`. Implements the full CNXN/AUTH handshake, one-shot
  `shell()` commands, and `InteractiveShell` for bounded request/response
  with a hard deadline (used by the `app_process` bridge). Approval-aware:
  `connect(mayAsk=true)` shows the car's "Allow debugging?" dialog;
  background reads always use `mayAsk=false`.
- **`adb/BydAdbShell.kt`** — lazy ADB shell wrapper for background reads.
  Never asks for approval (dialog never appears while driving). Retries
  every 30 seconds after a refused/unreachable connection. Includes
  `BydParcel` which parses `service call` Parcel hex output.
- **`adb/BydAdbShell.kt` BydParcel** — parses `Result: Parcel(00000000
  00000002 '........')` replies into `List<Long>` or a single `Int` value.

#### Added — Vehicle Field Constants
- **`vehicle/BydVehicleFields.kt`** — verified BYD vehicle field addresses
  (device, fid, transaction tuples) for:
  - Speed (device 1013, fid -1807745016, float)
  - Gear (device 1011, fid 555745336, int — codes: 1=P, 2=R, 3=N, 4-6=D)
  - Battery SoC (device 1014, CANFD fid 1246777400 / CAN fid 1033543720)
  - Battery range (device 1014, CANFD fid 1246765118 / CAN fid 1033203771)
  - Remaining kWh (device 1005, fid 882901008)
  - BMS charging state (device 1009, CANFD/CAN fids)
  - Tyre pressure (device 1016, 4 corners)
  - Tyre temperature (device 1007, 4 corners)
  - CAN vs CANFD detection via `ro.car.protocol` / `sys.car.protocol`

#### Added — Vehicle Data Reader
- **`vehicle/BydVehicleReader.kt`** — reads speed, SoC, range, gear,
  remaining kWh, tyre pressures, and tyre temperatures via ADB shell
  commands. Auto-detects CAN vs CANFD protocol. READ-ONLY — never writes
  to any vehicle system. Shares a single `BydAdbShell` instance to avoid
  wasting ADB connections.

#### Added — Recovery Journal
- **`vehicle/RecoveryJournal.kt`** — SharedPreferences-backed journal
  for vendor side-effects. Pattern: `markPending()` before issuing a
  command, `clearPending()` after confirmed revert. `recoverPending()`
  is called on app launch to clean up any side-effects left from a
  previous crash.

#### Added — Firmware-Fingerprint Gate
- **`vehicle/FirmwareGate.kt`** — capability gate for privileged operations.
  Verifies: SDK ≥ 28, package allowlist, firmware fingerprint prefix match
  (`BYD-AUTO/IVI/IVI:13/` for DiLink 5.1), and ClusterDebug receiver
  signing-cert SHA-256. Prevents accidental writes to a forged receiver
  on the wrong firmware. Verified values ported from DiPlay/DiAuto.

#### Added — BintiDiLinkBridge
- **`vehicle/BintiDiLinkBridge.kt`** — enthusiast-flavor entry point
  that ties together `BydVehicleReader`, `RecoveryJournal`, and
  `FirmwareGate`. Initialized by `DirectControlInitializer` at app
  startup. Reads all available vehicle data via ADB and publishes it
  into the shared `VehicleDataRepository` as a Tier 2 snapshot.

#### Changed — DirectControlInitializer
- The enthusiast-flavor `DirectControlInitializer.init()` now also
  initializes `BintiDiLinkBridge` before registering the existing
  `DirectControlBridge` (reflection-based fallback). Both paths coexist.

#### What's Next (Phase 4 — Polish & Ship)
- Multi-locale RTL discipline (English localization for non-Arabic users)
- Dark mode (`Theme.Material3.DayNight` + `values-night/colors.xml`)
- Full CI enforcement (`detekt` + `lint` fail the build)
- README and CHANGELOG rewrite to match v3.0.0 reality
- AppGallery submission readiness

---

## [3.0.0-alpha] - 2026-10-11

### 🏗️ Phase 2 — Modernize the Foundation

This release adds the foundational infrastructure for the modernized
architecture: Hilt DI, Jetpack Compose, Room database, a multi-module
split (the `:contract` module), and a public automation API
(`BintiAutomationActivity` + `binti://` deep links).

**This is an alpha release** — the new infrastructure is additive and
coexists with the existing code. No existing behavior changes. The
actual migration (moving files to modules, converting singletons to
@Inject, migrating screens to Compose, migrating StationManager to Room)
will be done incrementally in subsequent phases.

#### Added — Multi-Module Split (`:contract` module)
- Created the `:contract` module — a tiny Android library containing
  the public intent API for external automation.
- `BintiIntent.kt` defines:
  - Deep-link constants (`binti://start-voice`, `binti://stop`,
    `binti://toggle-ac`, `binti://navigate-home`, `binti://media-play`,
    `binti://next-track`, `binti://find-station`)
  - Broadcast action constants (for PendingIntent-based triggers)
  - Intent builders (`StartVoice.create(context)`, `ToggleAc.create(context)`,
    etc.)
  - The `PERMISSION` constant (`com.binti.dilink.permission.WIDGET_ACTION`)
    so external apps know what permission to request
- `:app` now depends on `:contract` — future external automation apps
  (Tasker plugins, MacroDroid tasks) can depend on `:contract` alone
  without pulling in the full app.

#### Added — Hilt Dependency Injection
- Annotated `BintiApplication` with `@HiltAndroidApp`.
- Created `di/AppModule.kt` — provides:
  - `SharedPreferences` ("binti_prefs")
  - `BintiDatabase` (Room)
  - `StationDao`
  - `StationRepository`
  - `VehicleDataRepository` (existing singleton, now also Hilt-provided)
  - `StationManager` (existing class, now also Hilt-provided)
- Created `di/DispatcherModule.kt` — provides qualified coroutine
  dispatchers (`@IoDispatcher`, `@DefaultDispatcher`, `@MainDispatcher`)
  and an `@ApplicationScope` `CoroutineScope`.
- **Existing singletons continue to work** via their `getInstance()` pattern.
  No existing code is modified. New code can use `@Inject` from the start.

#### Added — Jetpack Compose
- Enabled Compose in `app/build.gradle.kts` (`buildFeatures.compose = true`).
- Added the Kotlin Compose compiler plugin (`org.jetbrains.kotlin.plugin.compose`).
- Created `ui/theme/Color.kt` — brand colors matching `colors.xml`:
  - Binti Dark Blue (#001C35), Binti Light Blue (#0082C8), Binti Gold (#D4AF37)
  - Light and dark color variants for DayNight support
- Created `ui/theme/Type.kt` — Material 3 typography.
- Created `ui/theme/Theme.kt` — `BintiTheme` composable with:
  - Material 3 color scheme (light + dark)
  - Dynamic color support (Android 12+, BYD DiLink 5)
  - System dark mode detection
  - Status bar appearance sync
- **Existing XML views continue to work** — Compose is additive. New screens
  will use Compose; existing screens migrate incrementally.

#### Added — Room Database
- Created `data/local/entity/StationEntity.kt` — Room entity for charging
  stations (mirrors `StationManager.ChargingStation` with Room annotations).
  Indexed on (latitude, longitude), city, and status for efficient queries.
- Created `data/local/dao/StationDao.kt` — DAO with:
  - `insertAll()` — bulk insert with REPLACE conflict strategy
  - `getActiveStations()` — reactive `Flow<List<StationEntity>>`
  - `searchStations()` — full-text search across name/city/address/operator
  - `getActiveStationCount()` — quick count for population check
  - `getCities()` — distinct cities for filter dropdown
  - `deleteAll()` — for refresh/re-import
- Created `data/local/BintiDatabase.kt` — Room database with schema export
  (to `app/schemas/` for migration testing).
- Created `data/repository/StationRepository.kt` — wraps Room with asset
  fallback. On first access, populates the DB from the bundled
  `stations.json` asset via the legacy `StationManager`. Subsequent reads
  come from Room. The existing `StationManager` continues to serve the
  voice pipeline — `StationRepository` serves new UI screens.
- Schema version 1 with `fallbackToDestructiveMigration` (Phase 3 will
  add proper Migration objects when the schema evolves).

#### Added — BintiAutomationActivity
- Created `BintiAutomationActivity.kt` — a transparent, no-history
  activity that receives `binti://` deep links and translates them to
  `BintiService` foreground service starts.
- Manifest declaration:
  - `exported="true"` — external apps can target it
  - `excludeFromRecents="true"` — doesn't appear in recent apps
  - `noHistory="true"` — finishes before appearing in back stack
  - `taskAffinity=""` — runs in its own task
  - `Theme.Translucent.NoTitleBar` — invisible to the user
- **ADB usage:** `adb shell am start -a android.intent.action.VIEW -d "binti://start-voice"`
- **Tasker usage:** Create an "Intent" action with `binti://toggle-ac`
  as the data URI.

#### Changed — Build Configuration
- Updated `gradle/libs.versions.toml` with all new dependencies:
  Hilt 2.52, Room 2.6.1, Compose BOM 2024.12.01, KSP 2.0.21-1.0.28,
  Activity Compose 1.9.3, Navigation Compose 2.8.5, Hilt Navigation
  Compose 1.2.0.
- Updated root `build.gradle.kts` with new plugin aliases:
  `kotlin-compose`, `ksp`, `hilt`, `android-library`.
- Updated `app/build.gradle.kts`:
  - Added `kotlin-compose`, `ksp`, `hilt` plugins
  - Enabled `buildFeatures.compose = true`
  - Added KSP args for Room schema export
  - Added all Compose, Hilt, Room dependencies
- Updated `settings.gradle.kts` to include `:contract` module.
- Updated `app/proguard-rules.pro`:
  - Removed TFLite, ONNX, OkHttp keep rules (dependencies removed in Phase 0)
  - Added Hilt keep rules (Hilt-generated classes, @HiltViewModel)
  - Added Room keep rules (@Entity, @Dao, RoomDatabase subclasses)
  - Added Compose keep rules
  - Added `:contract` module keep rules

#### Version Bump
- `versionCode` 15 → 16
- `versionName` "2.7.0-beta" → "3.0.0-alpha"

#### What's Next (Phase 3 — DiLink Integration Tier)
- Port DiPlay's ADB substrate (`LocalAdb.kt`, `AdbKeys.kt`, `BydAdbShell.kt`)
  into a new `:dilink:src/enthusiast/` source set.
- Port vehicle-data readers (`BydBattery.kt`, `BydWheelSpeed.kt`,
  `BydTyres.kt`) for Tier 2 vehicle data without Accessibility scraping.
- Port the `app_process` bridge pattern for privileged BYD SDK access.
- Add recovery journals for every vendor side-effect.
- Add firmware-fingerprint + signing-cert-hash gating.

---

## [2.7.0-beta] - 2026-10-11

### 🔌 Phase 1 — Wire Up Half-Built Features

This release delivers the features the README already promised but the
code did not implement. The STATION, GREETINGS, SOCIAL, and HELP intents
are now routed in `DiLinkCommandExecutor` and produce correct Egyptian
Arabic responses. The `StationManager` class — fully implemented but
never instantiated — is now wired into the executor and launches the BYD
navigation app with a `geo:` intent to the nearest EV charging station.

#### Added — STATION Intent (EV Charging Station Discovery)
- **`DiLinkCommandExecutor.executeStationCommand()`** — new handler that:
  1. Loads stations from `StationManager` (cache/asset/remote, 24h cache).
  2. Reads the current device location via `LocationManager`.
  3. Finds the nearest station within 50 km using Haversine distance.
  4. Launches the BYD navigation app (`com.byd.auto.navigation`) with a
     `geo:` intent containing the station's coordinates and name.
  5. Falls back to a generic `geo:` handler if the BYD navigation app
     is unavailable.
  6. Returns an Egyptian-Arabic response with the station name + distance.
- The `StationManager` class (281 lines) is now instantiated via `by lazy`
  and shared across all STATION invocations.
- The 486-line `stations.json` asset (30 stations across 22 Egyptian
  cities) is now actually read at runtime — previously it was bundled
  but never loaded.
- User-facing response strings are in `strings.xml`:
  - `cmd_station_no_data` — when no station data is available
  - `cmd_station_no_location` — when location permission is denied
  - `cmd_station_none_nearby` — when no station is within 50 km
  - `cmd_station_found_navigating` — success with auto-navigation
  - `cmd_station_found_no_nav` — station found but navigation app missing

#### Added — GREETINGS, SOCIAL, HELP Intents
- **`executeGreetingCommand()`** — responds to "صباح الخير" / "إزيك"
  with a friendly Egyptian-Arabic greeting. Uses the matched pattern's
  response from `dilink_intent_map.json`, falling back to
  `cmd_greeting_default` if no pattern matched.
- **`executeSocialCommand()`** — responds to "شكرا" with a polite
  acknowledgment. Falls back to `cmd_social_default`.
- **`executeHelpCommand()`** — responds to "بتعملي إيه" with a summary
  of Binti's capabilities. Falls back to `cmd_help_default`.
- All three are TTS-only — no vehicle interaction.

#### Changed — Command Response Localization
- Extracted hardcoded Arabic strings from `DiLinkCommandExecutor.kt`:
  - `"أمر غير معروف: ${intent.action}"` → `R.string.cmd_unknown_action`
  - `"خطأ: ${e.message}"` → `R.string.cmd_error`
- Added plain Arabic equivalents in `values-ar/strings.xml` for all
  new command response strings.
- Note: keyword-matching literals (e.g., `"شغل" in pattern`) remain in
  code — they are matching logic, not user-facing strings. Full
  extraction of all 169 Arabic literals is deferred to Phase 2/3
  (god-class refactor).

#### Added — Unit Tests
- **`IntentClassifierTest`** (5 tests) — verifies the classifier
  gracefully handles unrecognized input, empty input, and gibberish
  without crashing. Confirms `IntentResult` data class holds all
  expected fields with correct defaults.
- **`StationManagerTest`** (10 tests) — verifies:
  - Haversine distance calculation (Cairo→Alexandria ~180 km,
    Cairo→Riyadh ~1600 km, antipodal points ~20015 km)
  - Station JSON parsing (valid, minimal, malformed, empty, missing
    required fields)
  - `findStationsNearby` invalid-coordinate guards
  - Reflection-based access to the private `parseStationJson` method
- Total test count: 37 (existing) + 15 (new) = 52 tests.

#### Version Bump
- `versionCode` 14 → 15
- `versionName` "2.6.0-beta" → "2.7.0-beta"

---

## [2.6.0-beta] - 2026-10-11

### 🧹 Phase 0 — Stabilize & Cleanup

This release is the first step of the comprehensive refactor plan. It deletes
~2,440 lines of dead code, removes unused dependencies (~10 MB APK size
reduction), hardens the widget security, fixes the hardcoded ADB IP, wires
`BYDModels.forModel()` into the runtime snapshot parser, and adds engineering
fundamentals (version catalog, detekt, lint, editorconfig).

#### Deleted — Dead Code (~2,440 lines)
- **`voice/WakeWordDetector.kt`** (328 lines) — TFLite-based wake-word
  detector; never instantiated. `BintiService` uses `WakeWordDetectorVosk`.
- **`utils/OfflineFallbackManager.kt`** (516 lines) — never instantiated.
- **`utils/VoiceProfileManager.kt`** + data classes (636 lines) — never
  instantiated; orphan `dialog_user_profile.xml` also deleted.
- **`res/layout/card_permission_notifications.xml`** (54 lines) — orphan
  layout, never inflated.
- **`scripts/train_wake_word.py`** (350 lines) — targeted the deleted
  TFLite detector.
- **`README_UPDATE.md`** (417 lines) — stale, referred to v2.0.0 URLs.

#### Removed — Unused Dependencies (~10 MB APK savings)
- `com.squareup.okhttp3:okhttp:4.12.0` — declared but never used in any
  source file (verified by grep). `StationManager` uses `HttpURLConnection`.
- `org.tensorflow:tensorflow-lite:2.14.0` + `tensorflow-lite-support:0.4.4`
  + `tensorflow-lite-gpu:2.14.0` — only used by the deleted
  `WakeWordDetector.kt` and the stubbed `IntentClassifier.classifyWithML()`.
- `com.microsoft.onnxruntime:onnxruntime-android:1.16.3` — only referenced
  in dead code (`OfflineFallbackManager`) and `model_config.json` config
  text. Sherpa-ONNX TTS loads via reflection from a manually-placed JAR,
  not this Maven dependency.

#### Changed — IntentClassifier Cleanup
- Removed `classifyWithML()` and `matchByFuzzy()` stub methods (always
  returned `null` with `// TODO: implement`).
- Removed the `Interpreter` field, `loadModelFile()` function, and
  `org.tensorflow.lite.Interpreter` import.
- The classifier is now **purely rule-based**, which is sufficient for the
  Egyptian-Arabic intent vocabulary. The README's "EgyBERT-tiny (optional)"
  claim is removed — the ML classifier was aspirational and never trained.

#### Security — QuickActionsWidget Hardening
- Declared custom signature-level permission
  `com.binti.dilink.permission.WIDGET_ACTION` in `AndroidManifest.xml`.
- Guarded the `QuickActionsWidget` receiver with the permission — only
  apps signed with the same key as Binti can send broadcasts to it.
- Removed `com.binti.dilink.action.*` actions from the manifest
  intent-filter. PendingIntents use explicit component targeting
  (`Intent(context, QuickActionsWidget::class.java)`), so they reach
  `onReceive` without needing a manifest filter entry. This prevents
  malicious apps from triggering widget actions via implicit broadcast.

#### Fixed — Hardcoded ADB IP
- Replaced the hardcoded `192.168.10.10:5555` in the DiLink 3 ADB
  instructions dialog with auto-detection of the WiFi gateway IP
  (`WifiManager.DhcpInfo`).
- The dialog now shows an **editable EditText** pre-filled with the
  detected IP, so the user can override it if the detection is wrong.
- Added a "نسخ الأوامر" (Copy Commands) button that copies the three
  ADB commands with the user's IP to the clipboard.
- Falls back to `127.0.0.1:5555` (loopback for USB ADB) when WiFi is
  unavailable.

#### Fixed — BYDModels.forModel() Wiring
- `DiLinkAccessibilityService.publishSnapshotFromCurrentScreen()` now calls
  `BYDModels.forModel(detectedModel)` instead of always passing
  `BYDModels.YuanPlus2023`. This **unlocks Dolphin / Seal / Han / Tang
  support** — their resource IDs differ from Yuan Plus, and were previously
  never used at runtime despite being defined in `BYDModels.kt`.
- Logs a warning when using an unconfirmed ID set (`isConfirmed = false`)
  so developers know which models need hardware verification.

#### Added — Engineering Fundamentals
- **`gradle/libs.versions.toml`** — Gradle version catalog. All dependency
  coordinates now live in one place instead of being hardcoded in
  `app/build.gradle.kts`. Migrated `app/build.gradle.kts` and root
  `build.gradle.kts` to use `libs.*` accessors.
- **`.editorconfig`** — consistent coding style (4-space indent, 140-char
  max line, UTF-8, LF line endings).
- **`detekt.yml`** + detekt Gradle plugin — static analysis. Runs with
  `ignoreFailures = true` in Phase 0 (surfaces issues without blocking
  the build). Phase 4 will tighten this to `false`.
- **`app/lint.xml`** — Android lint configuration with project-specific
  severity overrides (MissingTranslation as warning, OldTargetApi
  informational since `targetSdk=33` is intentional for DiLink 3).
- **`ForbiddenImport` detekt rule** — rejects `org.tensorflow.*`,
  `com.microsoft.onnxruntime.*`, and `okhttp3.*` imports to prevent
  the removed dependencies from being re-introduced.

#### Changed — Dependency Version Bumps
- `androidx.core:core-ktx` 1.12.0 → 1.15.0
- `androidx.appcompat:appcompat` 1.6.1 → 1.7.0
- `androidx.lifecycle:lifecycle-*` 2.7.0 → 2.8.7
- `androidx.constraintlayout` 2.1.4 → 2.2.0
- `com.google.android.material:material` 1.11.0 → 1.12.0
- `org.jetbrains.kotlinx:kotlinx-coroutines-*` 1.7.3 → 1.9.0
- `org.jetbrains.kotlinx:kotlinx-serialization-json` 1.6.2 → 1.7.3
- `com.google.code.gson:gson` 2.10.1 → 2.11.0
- `targetSdk` remains **33** (intentional, for DiLink 3 compatibility).
- `compileSdk` remains **34** (will bump to 35 in Phase 2).
- Kotlin remains **2.0.21**, AGP remains **8.7.3** (will bump in Phase 2
  after multi-module split is stable).

#### Updated — Documentation
- **`docs/MODEL_SETUP_GUIDE.md`** — rewritten to describe alphacephei.com
  hosting and the correct ~318 MB total (was: Google Drive hosting and
  ~1.3 GB). Removed references to the deleted `egybert_tiny_int8.onnx`
  and `ya_binti_detector.tflite` models.
- **`docs/LICENSE-ANALYSIS.md`** — updated to reflect that the Apache 2.0
  migration is complete (was: "Binti is currently licensed under MIT").

#### Version Bump
- `versionCode` 13 → 14
- `versionName` "2.5.0-beta" → "2.6.0-beta"

---

## [2.5.0-beta] - 2026-08-08

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
