# 🚗 Binti - Egyptian Arabic Voice Assistant for BYD DiLink

<div align="center">
  <img src="docs/binti-logo.png" alt="Binti Logo" width="200"/>
  
  **بنتي - مساعد صوتي باللهجة المصرية لسيارات BYD DiLink**
  
  [![License: MIT](https://img.shields.io/badge/License-MIT-yellow.svg)](https://opensource.org/licenses/MIT)
  [![Android](https://img.shields.io/badge/Platform-Android-green.svg)](https://www.android.com)
  [![Kotlin](https://img.shields.io/badge/Language-Kotlin-blue.svg)](https://kotlinlang.org)
</div>

---

## 📖 About

**Binti** (بنتي - "my daughter") is an Egyptian Arabic voice assistant designed specifically for BYD DiLink infotainment systems. It responds to the wake word **"يا بنتي"** (Ya Binti - "Oh my daughter") and processes commands in Egyptian dialect.

### ✨ Features

- 🎤 **Wake Word Detection** - Custom TFLite model for "يا بنتي"
- 🗣️ **Egyptian Arabic ASR** - Vosk offline speech recognition
- 🧠 **Intent Classification** - EgyBERT-tiny for NLU
- 🔊 **Egyptian TTS** - Female voice responses in Egyptian dialect
- 🚙 **DiLink Integration** - Control AC, navigation, media via AccessibilityService
- ☁️ **Huawei HMS Fallback** - Cloud ASR/TTS for HMS devices
- 📴 **Offline-First** - Works without internet (after model download)

---

## 🚀 Getting Started

### Prerequisites

- Android Studio Hedgehog (2023.1.1) or later
- JDK 17
- Android SDK 34
- Physical BYD vehicle with DiLink 5.0 (for testing)

### Build & Install

```bash
# Clone the repository
git clone https://github.com/waleedmandour/binti2.git
cd binti2

# Build debug APK
./gradlew assembleDebug

# Install on device
./gradlew installDebug
```

### First Run Setup

1. Open Binti app
2. Grant required permissions:
   - 🎤 Microphone
   - 🔲 Display over apps
   - ♿ Accessibility service
   - 🔔 Notifications (Android 13+)
3. Download AI models (~160MB) or choose cloud-only mode
4. Say **"يا بنتي"** to activate!

---

## 💬 Supported Commands

### Air Conditioning
| Arabic Command | English Translation | Action |
|----------------|---------------------|--------|
| يا بنتي، شغلي التكييف | Turn on AC | AC On |
| يا بنتي، طفي التكييف | Turn off AC | AC Off |
| يا بنتي، زيود الحرارة | Increase temperature | Temp +1°C |
| يا بنتي، التكييف على درجة 22 | Set AC to 22 degrees | Set temp |

### Navigation
| Arabic Command | English Translation | Action |
|----------------|---------------------|--------|
| يا بنتي، خديني للبيت | Take me home | Navigate home |
| يا بنتي، أقرب بنزين | Nearest gas station | POI search |
| يا بنتي، خديني لـ[مكان] | Take me to [place] | Navigate |

### Media
| Arabic Command | English Translation | Action |
|----------------|---------------------|--------|
| يا بنتي، شغلي موسيقى | Play music | Media play |
| يا بنتي، وقفة الأغنية | Pause the song | Media pause |
| يا بنتي، اللي بعدها | Next one | Next track |

---

## 🏗️ Architecture

```
┌─────────────────────────────────────────────────────────┐
│                     User Voice Input                     │
└────────────────────────┬────────────────────────────────┘
                         ▼
┌─────────────────────────────────────────────────────────┐
│  Wake Word Detector (TFLite CNN)                        │
│  "يا بنتي" → Trigger                                     │
└────────────────────────┬────────────────────────────────┘
                         ▼
┌─────────────────────────────────────────────────────────┐
│  Voice Processor (Vosk / Huawei ML Kit)                 │
│  Speech → Egyptian Arabic Text                          │
└────────────────────────┬────────────────────────────────┘
                         ▼
┌─────────────────────────────────────────────────────────┐
│  Intent Classifier (EgyBERT-tiny)                       │
│  Text → Intent + Entities                               │
└────────────────────────┬────────────────────────────────┘
                         ▼
┌─────────────────────────────────────────────────────────┐
│  DiLink Command Executor                                │
│  Intent → Accessibility Actions / Intents               │
└────────────────────────┬────────────────────────────────┘
                         ▼
┌─────────────────────────────────────────────────────────┐
│  Egyptian TTS (Coqui / Huawei ML Kit)                   │
│  Response → Egyptian Female Voice                       │
└─────────────────────────────────────────────────────────┘
```

---

## 📦 Model Stack

| Component | Primary (Offline) | License | Size | Cloud Fallback |
|-----------|-------------------|---------|------|----------------|
| Wake Word | Custom TFLite CNN | MIT | ~5 MB | N/A |
| ASR | Vosk Egyptian | Apache 2.0 | ~50 MB | Huawei ML Kit |
| NLU | EgyBERT-tiny | MIT | ~30 MB | Custom API |
| TTS | Coqui Egyptian Female | MPL 2.0 | ~80 MB | Huawei TTS |

**Total offline models: ~165MB compressed**

---

## 📱 Huawei AppGallery Distribution

Binti is designed for Huawei HMS devices commonly found in BYD vehicles.

### Setup

1. Register at [Huawei Developer](https://developer.huawei.com)
2. Create app in AppGallery Connect
3. Add `agconnect-services.json` to `app/` directory
4. Configure signing certificate

---

## 🛠️ Development

### Project Structure

```
binti2/
├── app/
│   ├── src/main/
│   │   ├── java/com/binti/dilink/
│   │   │   ├── BintiApplication.kt      # App initialization
│   │   │   ├── BintiService.kt          # Voice service
│   │   │   ├── MainActivity.kt          # Setup wizard
│   │   │   ├── voice/
│   │   │   │   ├── WakeWordDetector.kt  # "يا بنتي" detection
│   │   │   │   └── VoiceProcessor.kt    # ASR
│   │   │   ├── nlp/
│   │   │   │   └── IntentClassifier.kt  # NLU
│   │   │   ├── dilink/
│   │   │   │   ├── DiLinkCommandExecutor.kt
│   │   │   │   └── DiLinkAccessibilityService.kt
│   │   │   └── response/
│   │   │       └── EgyptianTTS.kt       # TTS
│   │   └── res/
│   │       ├── values/strings.xml       # English
│   │       └── values-ar/strings.xml    # Egyptian Arabic
│   └── build.gradle.kts
├── gradle/
├── scripts/
│   ├── quantize_models.py
│   └── test_dilink_commands.py
└── .github/workflows/
    └── ci.yml
```

### Running Tests

```bash
./gradlew test
./gradlew connectedAndroidTest
```

---

## 🤝 Contributing

Contributions are welcome! Please read [CONTRIBUTING.md](CONTRIBUTING.md) for guidelines.

### Areas for Contribution

- 🎙️ Additional wake word training data
- 📝 More Egyptian Arabic commands and responses
- 🌍 Support for other Arabic dialects
- 🔧 DiLink integration improvements
- 📚 Documentation and tutorials

---

## 📄 License

This project is licensed under the MIT License - see [LICENSE](LICENSE) for details.

### Third-Party Licenses

- Vosk - Apache 2.0
- TensorFlow Lite - Apache 2.0
- ONNX Runtime - MIT
- CAMeL Tools - MIT
- Coqui TTS - MPL 2.0
- Huawei HMS - See [Huawei SDK Agreement](https://developer.huawei.com/consumer/en/doc/development/HMS-Core-Guides/android-license-0000001058069715)

---

## 👨‍💻 Author

**Dr. Waleed Mandour**

- GitHub: [@waleedmandour](https://github.com/waleedmandour)

---

## 🙏 Acknowledgments

- [Vosk](https://alphacephei.com/vosk/) for offline speech recognition
- [CAMeL Lab](https://camel.abudhabi.nyu.edu/) for Egyptian Arabic NLP tools
- [Coqui](https://coqui.ai/) for TTS framework
- [Huawei Developer](https://developer.huawei.com) for HMS Core ML Kit

---

<div align="center">
  <b>بنتي - مساعدك الشخصي في سيارتك 🚗✨</b>
</div>
