# Changelog

All notable changes to Binti will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.0.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [Unreleased]

### Added
- Initial project structure
- Wake word detection with TFLite
- Vosk offline ASR integration
- Intent classification with EgyBERT
- DiLink AccessibilityService integration
- Egyptian TTS responses
- Huawei HMS ML Kit fallback

## [1.0.0-beta01] - 2024-03-24

### Added
- **Core Features**
  - Wake word detection for "يا بنتي" (Ya Binti)
  - Egyptian Arabic speech recognition (Vosk)
  - Intent classification for 30+ commands
  - Egyptian female voice responses
  - DiLink vehicle integration via AccessibilityService
  
- **Supported Commands**
  - AC control: power, temperature, mode
  - Navigation: home, work, POI search
  - Media: play, pause, next, previous, volume
  - Phone: make calls, answer, end (basic)
  
- **Model Management**
  - Download from GitHub Releases
  - SHA256 integrity verification
  - Offline-first with cloud fallback
  
- **Huawei Integration**
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

### [1.1.0] - Planned
- More DiLink app integrations
- Custom wake word training
- Multi-user voice profiles
- Conversation history

### [1.2.0] - Planned
- Hybrid online/offline mode
- Voice biometrics
- Custom command creation
- Widget support

---

## Version Naming Convention

- **Major (X.0.0)**: Breaking changes, major features
- **Minor (1.X.0)**: New features, backwards compatible
- **Patch (1.0.X)**: Bug fixes, minor improvements
- **Pre-release (-beta, -alpha)**: Testing versions
