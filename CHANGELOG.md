# Changelog

All notable changes to Binti will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.0.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

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
- **Progress Tracking** - Enhanced progress reporting for large model downloads (1.2GB ASR model)

#### Voice Processing
- **Vosk Grammar-based Wake Word Detection** - Uses Vosk grammar constraint for "يا بنتي" detection instead of separate TFLite model
- **Shared ASR Model** - Wake word detection reuses the same Vosk Arabic model, reducing storage requirements
- **Silence Detection** - Automatic stop after 1.5 seconds of silence
- **Consecutive Detection Filter** - Reduces false positives with consecutive detection requirement

#### Intent Classification
- **Egyptian Arabic Normalization** - Better handling of dialect variations (أ→ا, ة→ه, ى→ي)
- **Entity Extraction** - Temperature, location, and contact name extraction
- **Fuzzy Matching** - Fallback for typos and speech recognition errors

#### DiLink Integration
- **Extended AC Control** - Temperature adjustment (16-32°C), mode selection, fan speed
- **Navigation POI Search** - Gas stations, charging stations, restaurants, hospitals, parking
- **Phone Commands** - Call by number, call by contact, answer, reject, end, redial
- **Media Key Events** - System-level media control for any music app

#### User Interface
- **Quick Actions Widget** - Home screen widget for quick voice activation
- **Enhanced Settings** - Configure home/work addresses, model sources
- **Arabic Localization** - Full Egyptian Arabic UI strings

### Changed

#### Architecture
- **Model Manager Refactored** - Now supports multiple download sources
- **TTS Priority Changed** - Huawei ML Kit TTS prioritized for better quality
- **Intent Classifier Enhanced** - Now uses rule-based + ML hybrid approach

#### Configuration
- **Model Config Format** - Updated to support multiple hosting options
- **Download URLs** - Changed from B2-only to multi-source support

### Fixed

- **Large File Downloads** - Fixed timeout issues with 1.2GB Vosk model
- **Memory Leaks** - Fixed AudioRecord and TFLite resource leaks
- **Wake Word False Positives** - Reduced with consecutive detection filter
- **TTS Cutting Off** - Fixed speech queue management
- **Permission Handling** - Proper Android 13+ notification permission handling
- **Boot Receiver** - Fixed auto-start on device boot

### Breaking Changes

#### Model Hosting
- **Model URLs Changed** - Previous B2 URLs replaced with Google Drive links
- **Config File Format** - `model_config.json` structure updated
- **Download API** - New methods for Google Drive and local model loading

#### Upgrade Notes

1. **Existing Users:**
   - Previously downloaded models will continue to work
   - New models will download from Google Drive by default
   - No action required for existing installations

2. **New Users:**
   - Models download automatically on first run
   - Option to use local models from USB/SD card
   - WiFi recommended for initial model download (~1.4GB)

3. **Developers:**
   - Update `ModelManager.kt` with new Google Drive file IDs
   - See `docs/B2_MODEL_SETUP.md` for model hosting guide
   - SHA256 hashes should be computed after upload

### Technical Details

| Component | Version |
|-----------|---------|
| Min SDK | 26 (Android 8.0) |
| Target SDK | 34 (Android 14) |
| Kotlin | 1.9.22 |
| TensorFlow Lite | 2.14.0 |
| ONNX Runtime | 1.16.3 |
| Vosk Android | 0.3.47 |
| Huawei HMS | 7.0+ |

### Model Stack

| Component | Size | License |
|-----------|------|---------|
| ASR + Wake Word (Vosk MGB2) | 1,247 MB | Apache 2.0 |
| NLU (EgyBERT) | 25 MB | MIT |
| TTS Voice | 80 MB | MPL 2.0 |
| **Total** | **~1.35 GB** | |

---

## [1.0.0-beta01] - 2024-03-24

### Added

#### Core Features
- Wake word detection for "يا بنتي" (Ya Binti)
- Egyptian Arabic speech recognition (Vosk)
- Intent classification for 30+ commands
- Egyptian female voice responses
- DiLink vehicle integration via AccessibilityService

#### Supported Commands
- **AC Control:** Power on/off, temperature adjustment, mode selection
- **Navigation:** Home, work, POI search
- **Media:** Play, pause, next, previous, volume
- **Phone:** Make calls, answer, end

#### Model Management
- Download from Backblaze B2
- SHA256 integrity verification
- Offline-first with cloud fallback

#### Huawei Integration
- HMS ML Kit ASR fallback
- HMS ML Kit TTS fallback
- AppGallery-ready configuration

### Technical Details
- Min SDK: 26 (Android 8.0)
- Target SDK: 34 (Android 14)
- Kotlin 1.9.22
- TensorFlow Lite 2.14.0
- ONNX Runtime 1.16.3
- Vosk Android 0.3.47

---

## Future Roadmap

### [2.1.0] - Planned
- Voice biometrics for multi-user support
- Custom wake word training
- Conversation history
- Enhanced error recovery

### [2.2.0] - Planned
- Hybrid online/offline mode optimization
- Additional BYD model support (Han, Tang, Song)
- Custom command creation
- Widget customization

### [3.0.0] - Planned
- Multi-language support (Levantine, Gulf Arabic)
- Voice cloning for personalized TTS
- Cloud sync for settings and profiles
- Integration with BYD cloud services

---

## Version Naming Convention

- **Major (X.0.0):** Breaking changes, major features, architecture changes
- **Minor (1.X.0):** New features, backwards compatible
- **Patch (1.0.X):** Bug fixes, minor improvements
- **Pre-release (-beta, -alpha):** Testing versions

---

## Migration Guide

### From 1.x to 2.0

1. **Update Model URLs:**
   ```kotlin
   // Old (v1.x)
   private const val B2_BASE_URL = "https://f001.backblazeb2.com/file/binti2-models"
   
   // New (v2.0)
   private const val GOOGLE_DRIVE_BASE_URL = "https://drive.google.com/uc?export=download"
   private const val WAKE_WORD_FILE_ID = "YOUR_FILE_ID"
   ```

2. **Update Model Config:**
   ```json
   {
     "version": "2.0.0",
     "hosting": {
       "provider": "Google Drive",
       "fallback": "Backblaze B2"
     }
   }
   ```

3. **Handle Local Models:**
   ```kotlin
   modelManager.setLocalModelPath("/sdcard/binti_models")
   modelManager.setUseLocalModels(true)
   ```

---

*Last Updated: January 2025*
# Build Thu Mar 26 18:48:56 UTC 2026
