# Changelog

All notable changes to Binti will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.0.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

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
