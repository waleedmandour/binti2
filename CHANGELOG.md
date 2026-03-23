# Changelog

All notable changes to Binti will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.0.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [Unreleased]

### Added
- Initial project structure
- Core voice processing pipeline
- DiLink integration layer
- Egyptian Arabic TTS
- Overlay UI components
- Model download manager
- CI/CD pipeline

## [1.0.0] - 2024-01-XX

### Added
- **Voice Recognition**
  - Wake word detection ("يا بنتي")
  - Egyptian Arabic ASR with HuBERT-Egyptian
  - Voice activity detection
  - Noise suppression

- **Natural Language Understanding**
  - Intent classification with EgyBERT-tiny
  - Egyptian Arabic text normalization
  - Entity extraction (locations, numbers)

- **DiLink Integration**
  - Climate control (on/off/temperature)
  - Navigation commands
  - Media control (play/pause/next/prev)
  - Volume control

- **Text-to-Speech**
  - Egyptian female voice
  - Natural colloquial responses
  - Multiple response styles

- **User Interface**
  - Setup wizard with Arabic support
  - Floating overlay for voice feedback
  - Full-screen voice interaction
  - Lottie animations

- **Model Management**
  - Automatic model download
  - SHA256 integrity verification
  - Delta update support
  - Offline-first architecture

### Technical Details
- Minimum SDK: 26 (Android 8.0)
- Target SDK: 34 (Android 14)
- Total model size: ~400MB compressed
- Wake word latency: <10ms
- ASR latency: <500ms (on-device)

---

## Version History

| Version | Date | Highlights |
|---------|------|------------|
| 1.0.0 | 2024-01-XX | Initial release |

---

## Roadmap

### v1.1.0 (Planned)
- [ ] Additional Egyptian dialect variations (Alexandria, Upper Egypt)
- [ ] Voice speed and pitch customization
- [ ] Dark theme
- [ ] Widget support

### v1.2.0 (Planned)
- [ ] Multi-user support
- [ ] Custom wake word training
- [ ] Phone call integration
- [ ] SMS reading

### v2.0.0 (Future)
- [ ] Multi-language support (MSA, English)
- [ ] AI-powered conversation mode
- [ ] Cloud backup for settings
- [ ] Integration with other car systems

---

[Unreleased]: https://github.com/waleedmandour/binti2/compare/v1.0.0...HEAD
[1.0.0]: https://github.com/waleedmandour/binti2/releases/tag/v1.0.0
