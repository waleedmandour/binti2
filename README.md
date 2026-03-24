# 🚗 Binti - Egyptian Arabic Voice Assistant for BYD DiLink

<div align="center">
  
  **بنتي - مساعد صوتي باللهجة المصرية لسيارات BYD DiLink**
  
  [![License: MIT](https://img.shields.io/badge/License-MIT-yellow.svg)](https://opensource.org/licenses/MIT)
  [![Android](https://img.shields.io/badge/Platform-Android_14+-green.svg)](https://www.android.com)
  [![Kotlin](https://img.shields.io/badge/Language-Kotlin_1.9-blue.svg)](https://kotlinlang.org)
  [![API](https://img.shields.io/badge/Android_API-26%2B-brightgreen.svg)](https://developer.android.com/about/versions)
</div>

---

## 📖 About

**Binti** (بنتي - "my daughter") is an Egyptian Arabic voice assistant designed specifically for BYD DiLink infotainment systems. It responds to the wake word **"يا بنتي"** (Ya Binti - "Oh my daughter") and processes commands in Egyptian dialect.

### ✨ Key Features

| Feature | Description | Technology |
|---------|-------------|------------|
| 🎤 **Wake Word Detection** | Custom TFLite CNN for "يا بنتي" | TensorFlow Lite (~5 MB) |
| 🗣️ **Egyptian Arabic ASR** | Offline speech recognition | Vosk Egyptian Model (~50 MB) |
| 🧠 **Intent Classification** | Egyptian Arabic NLU | EgyBERT-tiny (~30 MB) |
| 🔊 **Egyptian TTS** | Female voice responses | Coqui TTS (~80 MB) |
| 🚙 **DiLink Integration** | Control vehicle functions | AccessibilityService |
| ☁️ **Huawei HMS Fallback** | Cloud ASR/TTS when offline unavailable | Huawei ML Kit |
| 📴 **Offline-First** | Works without internet after model download | All models local |

---

## 🏗️ Architecture

```
┌─────────────────────────────────────────────────────────────┐
│                     User Voice Input                         │
│                   "يا بنتي، شغلي التكييف"                     │
└────────────────────────┬────────────────────────────────────┘
                         ▼
┌─────────────────────────────────────────────────────────────┐
│  Wake Word Detector (TFLite CNN)                            │
│  Continuous audio monitoring → "يا بنتي" detection           │
└────────────────────────┬────────────────────────────────────┘
                         ▼
┌─────────────────────────────────────────────────────────────┐
│  Voice Processor (Vosk / Huawei ML Kit)                     │
│  Speech → Egyptian Arabic Text                              │
│  Primary: Vosk offline → Fallback: Huawei Cloud ASR         │
└────────────────────────┬────────────────────────────────────┘
                         ▼
┌─────────────────────────────────────────────────────────────┐
│  Intent Classifier (EgyBERT-tiny + Rule-based)              │
│  Text → Intent + Entities                                   │
│  Example: "شغلي التكييف" → AC_CONTROL {action: "on"}        │
└────────────────────────┬────────────────────────────────────┘
                         ▼
┌─────────────────────────────────────────────────────────────┐
│  DiLink Command Executor                                    │
│  Intent → Accessibility Actions / System Intents            │
│  Controls: AC, Navigation, Media, Phone, System             │
└────────────────────────┬────────────────────────────────────┘
                         ▼
┌─────────────────────────────────────────────────────────────┐
│  Egyptian TTS (Coqui / Huawei ML Kit / Android TTS)         │
│  Response → Egyptian Female Voice                           │
│  Example: "تمام، شغلت التكييف"                              │
└─────────────────────────────────────────────────────────────┘
```

---

## 🚀 Getting Started

### Prerequisites

| Requirement | Version |
|-------------|---------|
| Android Studio | Hedgehog (2023.1.1) or later |
| JDK | 17 |
| Android SDK | 34 |
| Gradle | 8.5 |
| Target Device | Android 8.0+ (API 26) |

### Build & Install

```bash
# Clone the repository
git clone https://github.com/waleedmandour/binti2.git
cd binti2

# Build debug APK
./gradlew assembleDebug

# Install on connected device
./gradlew installDebug

# Run lint checks
./gradlew lint

# Run unit tests
./gradlew test
```

### First Run Setup

1. **Open Binti app** on your BYD vehicle or Android device
2. **Grant required permissions**:
   - 🎤 **Microphone** - For voice recognition
   - 🔲 **Display over apps** - For voice feedback overlay
   - ♿ **Accessibility service** - For DiLink integration
   - 🔔 **Notifications** - For foreground service (Android 13+)
3. **Download AI models** (~165MB) when prompted, or choose cloud-only mode
4. **Say "يا بنتي"** to activate!

---

## 💬 Supported Commands

### Air Conditioning

| Arabic Command | English Translation | Action |
|----------------|---------------------|--------|
| يا بنتي، شغلي التكييف | Turn on AC | AC Power On |
| يا بنتي، طفي التكييف | Turn off AC | AC Power Off |
| يا بنتي، زيود الحرارة | Increase temperature | Temp +1°C |
| يا بنتي، قلل الحرارة | Decrease temperature | Temp -1°C |
| يا بنتي، التكييف على درجة 22 | Set AC to 22 degrees | Set Temperature |
| يا بنتي، تكييف بارد | Cool mode | AC Mode: Cool |
| يا بنتي، تكييف ساخن | Heat mode | AC Mode: Heat |

### Navigation

| Arabic Command | English Translation | Action |
|----------------|---------------------|--------|
| يا بنتي، خديني للبيت | Take me home | Navigate to saved home |
| يا بنتي، خديني للشغل | Take me to work | Navigate to saved work |
| يا بنتي، أقرب بنزين | Nearest gas station | POI: Gas Station |
| يا بنتي، أقرب شحن | Nearest charging station | POI: EV Charging |
| يا بنتي، أقرب مطعم | Nearest restaurant | POI: Restaurant |
| يا بنتي، أقرب مستشفى | Nearest hospital | POI: Hospital |
| يا بنتي، خديني لـ[مكان] | Take me to [place] | Custom destination |

### Media Control

| Arabic Command | English Translation | Action |
|----------------|---------------------|--------|
| يا بنتي، شغلي موسيقى | Play music | Media Play |
| يا بنتي، وقفة الأغنية | Pause the song | Media Pause |
| يا بنتي، اللي بعدها | Next one | Media Next |
| يا بنتي، اللي قبلها | Previous one | Media Previous |
| يا بنتي، صوت عالي | Volume up | Volume Increase |
| يا بنتي، صوت واطي | Volume down | Volume Decrease |

### Phone

| Arabic Command | English Translation | Action |
|----------------|---------------------|--------|
| يا بنتي، كلم أحمد | Call Ahmed | Contact Call |
| يا بنتي، رد عالمكالمة | Answer the call | Answer Call |
| يا بنتي، أقفل المكالمة | End the call | End Call |

### Information

| Arabic Command | English Translation | Action |
|----------------|---------------------|--------|
| يا بنتي، الساعة كام | What time is it? | Tell Time |
| يا بنتي، حرارة بره إيه | What's the outside temperature? | Weather Info |
| يا بنتي، البطارية كام | What's the battery level? | Battery Status |

---

## 📦 Model Stack

| Component | Primary (Offline) | License | Size | Cloud Fallback |
|-----------|-------------------|---------|------|----------------|
| Wake Word | Custom TFLite CNN | MIT | ~5 MB | N/A |
| ASR | Vosk Arabic MGB2 | Apache 2.0 | ~1.2 GB | Huawei ML Kit ASR |
| NLU | EgyBERT-tiny + Rules | MIT | ~25 MB | Rule-based |
| TTS | Coqui Egyptian Female | MPL 2.0 | ~80 MB | Huawei ML Kit TTS |

**Total offline models: ~1.4GB compressed**

---

## 📥 Model Download (Backblaze B2)

Binti uses **Backblaze B2** for hosting AI models. Models are downloaded on first run for offline privacy.

### Quick Setup

1. **Create Backblaze B2 Account** (Free: 10GB storage)
   - Sign up at [backblaze.com/b2](https://www.backblaze.com/b2/sign-up.html)

2. **Create a Public Bucket**
   ```bash
   # Install B2 CLI
   pip install b2
   
   # Authenticate with your keys
   b2 authorize-account <keyID> <applicationKey>
   
   # Create bucket
   b2 create-bucket binti2-models allPublic
   ```

3. **Upload Models**
   ```bash
   # Run upload script
   export B2_BUCKET=binti2-models
   ./scripts/upload_models_to_b2.sh
   ```

### Model Files Required

| File | B2 Path | Size | Source |
|------|---------|------|--------|
| `ya_binti_detector.tflite` | `wake/` | 5MB | Train custom |
| `vosk-model-ar-mgb2.zip` | `asr/` | 1.2GB | [Download](https://alphacephei.com/vosk/models) |
| `egybert_tiny_int8.onnx` | `nlu/` | 25MB | Train custom |
| `ar-eg-female.zip` | `tts/` | 80MB | Train custom |
| `dilink_intent_map.json` | `nlp/` | 10KB | Included in project |

### Download Pre-trained Vosk Arabic

```bash
# Download Arabic ASR model
wget https://alphacephei.com/vosk/models/vosk-model-ar-mgb2-0.4.zip
mv vosk-model-ar-mgb2-0.4.zip models_to_upload/vosk-model-ar-mgb2.zip

# Upload to B2
b2 upload-file binti2-models models_to_upload/vosk-model-ar-mgb2.zip asr/vosk-model-ar-mgb2.zip
```

### Update App Configuration

Edit `ModelManager.kt`:

```kotlin
private const val B2_BASE_URL = 
    "https://f001.backblazeb2.com/file/binti2-models"
```

### Detailed Guide

See [docs/B2_MODEL_SETUP.md](docs/B2_MODEL_SETUP.md) for training instructions.

---

## 📁 Project Structure

```
binti2/
├── app/
│   ├── src/main/
│   │   ├── java/com/binti/dilink/
│   │   │   ├── BintiApplication.kt          # App initialization, HMS setup
│   │   │   ├── BintiService.kt              # Foreground voice service
│   │   │   ├── MainActivity.kt              # Permission setup wizard
│   │   │   ├── voice/
│   │   │   │   ├── WakeWordDetector.kt      # TFLite wake word detection
│   │   │   │   └── VoiceProcessor.kt        # Vosk/Huawei ASR
│   │   │   ├── nlp/
│   │   │   │   └── IntentClassifier.kt      # NLU + entity extraction
│   │   │   ├── dilink/
│   │   │   │   ├── DiLinkCommandExecutor.kt # Command execution
│   │   │   │   └── DiLinkAccessibilityService.kt # UI automation
│   │   │   ├── response/
│   │   │   │   └── EgyptianTTS.kt           # TTS engine
│   │   │   ├── utils/
│   │   │   │   ├── ModelManager.kt          # Model download/verification
│   │   │   │   └── HMSUtils.kt              # Huawei services helper
│   │   │   └── receivers/
│   │   │       └── BootReceiver.kt          # Auto-start on boot
│   │   ├── res/
│   │   │   ├── values/strings.xml           # English strings
│   │   │   ├── values-ar/strings.xml        # Egyptian Arabic strings
│   │   │   ├── layout/                      # UI layouts
│   │   │   ├── drawable/                    # Icons and graphics
│   │   │   └── xml/                         # Config files
│   │   ├── assets/
│   │   │   ├── commands/
│   │   │   │   └── dilink_intent_map.json   # Intent patterns
│   │   │   └── models/README.md             # Model download info
│   │   └── AndroidManifest.xml              # Permissions, services
│   ├── build.gradle.kts                     # App dependencies
│   └── proguard-rules.pro                   # ProGuard configuration
├── gradle/
│   └── wrapper/
│       ├── gradle-wrapper.jar               # Gradle wrapper binary
│       └── gradle-wrapper.properties        # Gradle version config
├── .github/workflows/
│   └── ci.yml                               # CI/CD pipeline
├── build.gradle.kts                         # Project-level config
├── settings.gradle.kts                      # Repository config
├── gradle.properties                        # JVM settings
├── README.md                                # This file
├── CHANGELOG.md                             # Version history
├── CONTRIBUTING.md                          # Contribution guidelines
└── LICENSE                                  # MIT License
```

---

## 🔧 CI/CD Pipeline

The project uses GitHub Actions for continuous integration:

### Workflow Jobs

| Job | Trigger | Description |
|-----|---------|-------------|
| `validate` | Push/PR | Validate Gradle wrapper, check model sizes, verify Arabic strings |
| `build` | After validate | Build debug APK, run unit tests, run lint |
| `release` | Release created | Build signed release APK, upload to GitHub Releases |
| `model-pack` | Release created | Create and upload model manifest |

### Required GitHub Secrets

For release builds, configure these secrets:

| Secret | Description |
|--------|-------------|
| `KEYSTORE_BASE64` | Base64-encoded signing keystore |
| `KEYSTORE_PASSWORD` | Keystore password |
| `KEY_ALIAS` | Key alias name |
| `KEY_PASSWORD` | Key password |

---

## 📱 Huawei AppGallery Distribution

Binti is designed for Huawei HMS devices commonly found in BYD vehicles.

### Setup Steps

1. Register at [Huawei Developer](https://developer.huawei.com)
2. Create an app in [AppGallery Connect](https://developer.huawei.com/consumer/en/service/hms/catalog/agc.html)
3. Download `agconnect-services.json` and place in `app/` directory
4. Configure signing certificate in AppGallery Connect
5. Build release APK and upload to AppGallery

### Huawei HMS Services Used

| Service | Purpose |
|---------|---------|
| ML Kit ASR | Cloud speech recognition fallback |
| ML Kit TTS | Cloud text-to-speech fallback |
| HMS Core | Device identification |

---

## 🔐 Security Features

- ✅ **HTTPS-only** network connections (no cleartext traffic)
- ✅ **SHA256 verification** for downloaded models
- ✅ **ProGuard/R8** code obfuscation for release builds
- ✅ **Foreground service** with proper notification for Android 14+
- ✅ **Privacy-first** - all voice processing happens on-device after model download

---

## 🤝 Contributing

Contributions are welcome! Please read [CONTRIBUTING.md](CONTRIBUTING.md) for guidelines.

### Priority Areas

- 🎙️ Additional wake word training data
- 📝 More Egyptian Arabic commands and responses
- 🌍 Support for other Arabic dialects (Levantine, Gulf, Maghrebi)
- 🔧 DiLink integration improvements for specific BYD models
- 📚 Documentation translations
- 🐛 Bug fixes and performance improvements

---

## 📄 License

This project is licensed under the MIT License - see [LICENSE](LICENSE) for details.

### Third-Party Licenses

| Library | License |
|---------|---------|
| Vosk | Apache 2.0 |
| TensorFlow Lite | Apache 2.0 |
| ONNX Runtime | MIT |
| CAMeL Tools | MIT |
| Coqui TTS | MPL 2.0 |
| Huawei HMS | [Huawei SDK Agreement](https://developer.huawei.com/consumer/en/doc/development/HMS-Core-Guides/android-license-0000001058069715) |

---

## 👨‍💻 Author

**Dr. Waleed Mandour**

- 📧 Email: waleedmandour@gmail.com; w.abumandour@squ.edu.om
- 💻 GitHub: [@waleedmandour](https://github.com/waleedmandour)
- 🏛️ Institution: Sultan Qaboos University

---

## 🙏 Acknowledgments

- [Vosk](https://alphacephei.com/vosk/) - Offline speech recognition
- [CAMeL Lab](https://camel.abudhabi.nyu.edu/) - Egyptian Arabic NLP tools
- [Coqui](https://coqui.ai/) - TTS framework
- [Huawei Developer](https://developer.huawei.com) - HMS Core ML Kit
- [BYD Auto](https://www.byd.com) - DiLink platform

---

<div align="center">
  <b>بنتي - مساعدك الشخصي في سيارتك 🚗✨</b>
  <br/>
  <i>Binti - Your personal assistant in your car</i>
</div>
