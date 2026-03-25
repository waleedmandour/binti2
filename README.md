# 🚗 Binti - Egyptian Arabic Voice Assistant for BYD DiLink

<div align="center">
  
  **بنتي - مساعد صوتي باللهجة المصرية لسيارات BYD DiLink**
  
  [![License: MIT](https://img.shields.io/badge/License-MIT-yellow.svg)](https://opensource.org/licenses/MIT)
  [![Android](https://img.shields.io/badge/Platform-Android_8.0+-green.svg)](https://www.android.com)
  [![Kotlin](https://img.shields.io/badge/Language-Kotlin_1.9-blue.svg)](https://kotlinlang.org)
  [![API](https://img.shields.io/badge/Android_API-26%2B-brightgreen.svg)](https://developer.android.com/about/versions)
</div>

---

## 📖 About

**Binti** (بنتي - "my daughter") is an Egyptian Arabic voice assistant designed specifically for BYD DiLink infotainment systems. It responds to the wake word **"يا بنتي"** (Ya Binti - "Oh my daughter") and processes commands in Egyptian dialect.

### ✨ Key Features

| Feature | Description | Technology |
|---------|-------------|------------|
| 🎤 **Wake Word Detection** | Vosk grammar-based detection for "يا بنتي" | Vosk Arabic Model (reuses ASR model) |
| 🗣️ **Egyptian Arabic ASR** | Offline speech recognition | Vosk Arabic MGB2 (~1.2 GB) |
| 🧠 **Intent Classification** | Egyptian Arabic NLU | EgyBERT-tiny + Rule-based (~25 MB) |
| 🔊 **Egyptian TTS** | Female voice responses | Android TTS / Huawei ML Kit |
| 🚙 **DiLink Integration** | Control vehicle functions | AccessibilityService |
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
│  Wake Word Detector (Vosk Grammar)                          │
│  Continuous audio monitoring with grammar constraint         │
│  Grammar: ["يا بنتي", "[unk]"] → Detects only wake word      │
└────────────────────────┬────────────────────────────────────┘
                         ▼
┌─────────────────────────────────────────────────────────────┐
│  Voice Processor (Vosk Arabic MGB2)                         │
│  Speech → Arabic Text                                       │
│  Offline recognition using the same Vosk model              │
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
│  Egyptian TTS (Android TTS / Huawei ML Kit)                 │
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

---

## 🚙 BYD DiLink Installation

### Supported Vehicles

| Model | Year | DiLink Version | Status |
|-------|------|----------------|--------|
| Yuan Plus (Atto 3) | 2022-2024 | 4.0 | ✅ Fully Supported |
| Yuan Plus (Atto 3) | 2023 | 4.0 | ✅ Fully Supported |
| Dolphin | 2022-2024 | 4.0 | ✅ Supported |
| Seal | 2023-2024 | 5.0 | 🔄 Testing |
| Han EV | 2023-2024 | 5.0 | 🔄 Testing |
| Tang EV | 2023-2024 | 5.0 | 🔄 Testing |

### Installation Steps for BYD DiLink

1. **Enable Unknown Sources**
   - Go to Settings → Security → Unknown sources
   - Enable "Allow installation from unknown sources"

2. **Transfer APK to Vehicle**
   - Option A: Download from GitHub Releases via browser
   - Option B: Transfer via USB drive
   - Option C: Use ADB over USB debugging

3. **Install the APK**
   - Open the APK file from file manager
   - Grant required permissions when prompted

4. **Configure Accessibility Service**
   - Go to Settings → Accessibility → Binti
   - Enable the accessibility service
   - This is required for DiLink UI automation

5. **Download AI Models**
   - First launch will prompt to download models
   - Connect to WiFi (models are ~1.3GB total)
   - Alternatively, copy models from USB (see [Model Setup Guide](docs/MODEL_SETUP_GUIDE.md))

6. **Grant Permissions**
   - 🎤 **Microphone** - For voice recognition
   - 🔲 **Display over apps** - For voice feedback overlay
   - ♿ **Accessibility service** - For DiLink integration
   - 🔔 **Notifications** - For foreground service (Android 13+)
   - 📱 **Run in background** - For continuous listening

### First Run Setup

1. **Open Binti app** on your BYD vehicle
2. **Grant required permissions** as prompted
3. **Download AI models** (~1.3GB) - use WiFi or copy from USB
4. **Set your home and work addresses** in settings
5. **Say "يا بنتي"** to activate!

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

| Component | Primary (Offline) | License | Size | Notes |
|-----------|-------------------|---------|------|-------|
| Wake Word | Vosk Grammar Detection | Apache 2.0 | Uses ASR model | No separate model needed |
| ASR | Vosk Arabic MGB2 | Apache 2.0 | ~1.2 GB | Modern Standard Arabic |
| NLU | EgyBERT-tiny + Rules | MIT | ~25 MB | Fine-tuned for commands |
| TTS | Android TTS | - | Built-in | Arabic locale |

**Total offline models: ~1.3GB**

### Model Requirements

| Model | File | Size | Required | Description |
|-------|------|------|----------|-------------|
| Vosk Arabic ASR | `vosk-model-ar-mgb2` | ~1.2 GB | ✅ Yes | Speech recognition + wake word |
| Intent Classifier | `egybert_tiny_int8.onnx` | ~25 MB | ✅ Yes | Command understanding |
| Intent Patterns | `dilink_intent_map.json` | ~10 KB | ✅ Yes | Command patterns |

---

## 📥 Model Hosting (Google Drive)

Binti uses **Google Drive** for hosting AI models. This approach provides:
- ✅ Free hosting (Google Drive free tier)
- ✅ Reliable downloads
- ✅ Easy updates
- ✅ User self-hosting option

### Quick Setup

1. **Download Models**
   - Download the required models (see [Model Setup Guide](docs/MODEL_SETUP_GUIDE.md))
   - Vosk Arabic: [alphacephei.com/vosk/models](https://alphacephei.com/vosk/models)

2. **Upload to Your Google Drive**
   - Create a folder for models
   - Upload model files
   - Get shareable links

3. **Configure App**
   - Open Binti Settings → Model Sources
   - Add your Google Drive folder ID
   - Or copy models directly to device via USB

### Google Drive Direct Links

The app automatically handles Google Drive's virus scan warnings for large files. Just provide the file ID:

```
https://drive.google.com/file/d/YOUR_FILE_ID/view
```

The app converts this to a direct download link automatically.

### Local Model Setup (USB/SD Card)

For offline installation:

```bash
# Create models directory on USB drive
mkdir -p /usb/binti_models/{asr,nlu}

# Copy Vosk model (extract from ZIP first)
cp -r vosk-model-ar-mgb2 /usb/binti_models/asr/

# Copy other models
cp egybert_tiny_int8.onnx /usb/binti_models/nlu/
```

Then in the app:
1. Settings → Model Sources → Use Local Path
2. Enter the path (e.g., `/storage/USB/binti_models`)

For detailed instructions, see [MODEL_SETUP_GUIDE.md](docs/MODEL_SETUP_GUIDE.md).

---

## 🎤 Wake Word Detection Approach

### Vosk Grammar-Based Detection

Binti uses a clever approach that reuses the existing Vosk Arabic model for wake word detection:

**How it works:**
1. Vosk recognizer is configured with a limited grammar
2. Grammar only includes "يا بنتي" and an unknown token
3. This makes detection very efficient and accurate
4. No separate wake word model needed!

**Advantages:**
- ✅ No additional model to download
- ✅ Reuses existing ASR infrastructure
- ✅ Works offline
- ✅ Easy to modify wake word
- ✅ Supports any Arabic phrase

**Implementation:**
```kotlin
// Grammar-based wake word detection
val grammar = """["يا بنتي", "[unk]"]"""
val recognizer = Recognizer(model, 16000f, grammar)

// When recognizer outputs "يا بنتي", wake word detected!
```

For more details, see [WAKE_WORD_RESEARCH.md](docs/WAKE_WORD_RESEARCH.md).

---

## 🔧 Troubleshooting

### BYD Yuan Plus 2023 Specific Issues

#### Wake Word Not Detected

**Problem:** "يا بنتي" not being recognized

**Solutions:**
1. **Check microphone permissions**
   - Settings → Apps → Binti → Permissions → Microphone

2. **Adjust wake word sensitivity**
   - Settings → Voice → Wake Word Sensitivity
   - Try "High" for noisy environments

3. **Speak clearly**
   - The Vosk model expects Modern Standard Arabic pronunciation
   - Try pronouncing "يا بنتي" more formally

4. **Check model status**
   - Settings → Models → Check if ASR model is downloaded

#### Commands Not Executing

**Problem:** Wake word detected but commands don't work

**Solutions:**
1. **Check Accessibility Service**
   - Settings → Accessibility → Binti → Enable
   - This is required for UI automation

2. **Grant Display Overlay Permission**
   - Settings → Apps → Special access → Display over other apps
   - Enable for Binti

3. **Restart the service**
   - Notification panel → Stop Binti → Start again

#### App Crashes or Freezes

**Problem:** App becomes unresponsive

**Solutions:**
1. **Clear app cache**
   - Settings → Apps → Binti → Storage → Clear Cache

2. **Check available storage**
   - Models require ~1.5GB free space
   - Delete unused apps/media

3. **Reinstall models**
   - Settings → Models → Delete Models → Re-download

#### Poor Voice Recognition

**Problem:** ASR not understanding commands

**Solutions:**
1. **Use WiFi for model download**
   - Corrupted downloads can cause issues

2. **Speak in Modern Standard Arabic**
   - The Vosk model is trained on MSA
   - Egyptian dialect works but may have lower accuracy

3. **Reduce background noise**
   - Close windows at high speeds
   - Lower music volume when speaking

#### Connectivity Issues

**Problem:** Can't download models

**Solutions:**
1. **Use USB transfer**
   - Download models on computer
   - Copy to USB drive
   - Use local path in settings

2. **Check WiFi connection**
   - BYD DiLink WiFi can be unstable
   - Try mobile hotspot

3. **Use mirror links**
   - Settings → Model Sources → Add custom mirror

### General Issues

| Issue | Solution |
|-------|----------|
| Service not starting | Check battery optimization settings |
| No voice output | Check TTS settings, install Arabic voice |
| GPS not working | Grant location permission |
| Contacts not found | Grant contacts permission |

---

## 📁 Project Structure

```
binti2/
├── app/
│   ├── src/main/
│   │   ├── java/com/binti/dilink/
│   │   │   ├── BintiApplication.kt          # App initialization
│   │   │   ├── BintiService.kt              # Foreground voice service
│   │   │   ├── MainActivity.kt              # Permission setup wizard
│   │   │   ├── voice/
│   │   │   │   ├── WakeWordDetector.kt      # TFLite wake word (legacy)
│   │   │   │   ├── WakeWordDetectorVosk.kt  # Vosk grammar detection
│   │   │   │   └── VoiceProcessor.kt        # Vosk ASR
│   │   │   ├── nlp/
│   │   │   │   └── IntentClassifier.kt      # NLU + entity extraction
│   │   │   ├── dilink/
│   │   │   │   ├── DiLinkCommandExecutor.kt # Command execution
│   │   │   │   └── DiLinkAccessibilityService.kt # UI automation
│   │   │   ├── response/
│   │   │   │   └── EgyptianTTS.kt           # TTS engine
│   │   │   └── utils/
│   │   │       ├── ModelManager.kt          # Model download/verification
│   │   │       └── HMSUtils.kt              # Huawei services helper
│   │   ├── res/
│   │   │   ├── values/strings.xml           # English strings
│   │   │   ├── values-ar/strings.xml        # Egyptian Arabic strings
│   │   │   └── layout/                      # UI layouts
│   │   ├── assets/
│   │   │   ├── commands/
│   │   │   │   └── dilink_intent_map.json   # Intent patterns
│   │   │   └── models/
│   │   │       ├── model_config.json        # Model configuration
│   │   │       └── README.md                # Model download info
│   │   └── AndroidManifest.xml              # Permissions, services
│   └── build.gradle.kts                     # App dependencies
├── docs/
│   ├── MODEL_SETUP_GUIDE.md                 # Google Drive setup guide
│   ├── WAKE_WORD_RESEARCH.md                # Wake word options research
│   └── B2_MODEL_SETUP.md                    # Legacy B2 hosting guide
├── build.gradle.kts                         # Project-level config
├── settings.gradle.kts                      # Repository config
├── README.md                                # This file
├── CHANGELOG.md                             # Version history
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

### Required GitHub Secrets

For release builds, configure these secrets:

| Secret | Description |
|--------|-------------|
| `KEYSTORE_BASE64` | Base64-encoded signing keystore |
| `KEYSTORE_PASSWORD` | Keystore password |
| `KEY_ALIAS` | Key alias name |
| `KEY_PASSWORD` | Key password |

---

## 🔐 Security Features

- ✅ **HTTPS-only** network connections (no cleartext traffic)
- ✅ **SHA256 verification** for downloaded models
- ✅ **ProGuard/R8** code obfuscation for release builds
- ✅ **Foreground service** with proper notification for Android 14+
- ✅ **Privacy-first** - all voice processing happens on-device after model download
- ✅ **No cloud services required** after initial model download

---

## 🤝 Contributing

Contributions are welcome! Please read [CONTRIBUTING.md](CONTRIBUTING.md) for guidelines.

### Priority Areas

- 🎙️ Testing on more BYD models (Han, Tang, Seal)
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
| OkHttp | Apache 2.0 |
| Kotlin Coroutines | Apache 2.0 |

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
- [BYD Auto](https://www.byd.com) - DiLink platform
- All contributors and testers

---

<div align="center">
  <b>بنتي - مساعدك الشخصي في سيارتك 🚗✨</b>
  <br/>
  <i>Binti - Your personal assistant in your car</i>
</div>
