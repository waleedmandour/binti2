# 🎙️ Binti - Egyptian Arabic Voice Assistant for BYD DiLink

<p align="center">
  <img src="docs/images/binti-logo.png" alt="Binti Logo" width="200"/>
</p>

<p align="center">
  <a href="#features">Features</a> •
  <a href="#download">Download</a> •
  <a href="#installation">Installation</a> •
  <a href="#usage">Usage</a> •
  <a href="#development">Development</a> •
  <a href="#contributing">Contributing</a>
</p>

<p align="center">
  <img src="https://img.shields.io/github/v/release/waleedmandour/binti2?style=flat-square" alt="Release"/>
  <img src="https://img.shields.io/github/license/waleedmandour/binti2?style=flat-square" alt="License"/>
  <img src="https://img.shields.io/github/stars/waleedmandour/binti2?style=flat-square" alt="Stars"/>
</p>

---

## 🇪🇬 بالعربي

**بنتي** هي مساعد صوتي مصري لسيارات BYD DiLink. بتفهم المصري وبتشتغل أوفلاين!

### المميزات
- 🎤 تسمعك وتفهم المصري
- 🚗 بتتحكم في شاشة السيارة
- 🔊 بتتكلم مصري كمان!
- 📴 بتشتغل أوفلاين (بدون إنترنت)
- ⚡ سريعة وخفيفة

### الأوامر المدعومة
| الأمر بالعربي | الوظيفة |
|--------------|---------|
| يا بنتي | تفعيل المساعد |
| شغّل التكييف | تشغيل المكيف |
| خديني للبيت | الملاحة للمنزل |
| شغّل المزيكا | تشغيل الموسيقى |
| ارفع الصوت | رفع مستوى الصوت |

---

## 🌍 English

**Binti** is an Egyptian Arabic voice assistant designed for BYD DiLink infotainment systems. It understands Egyptian dialect and works completely offline!

### Features
- 🎤 **Egyptian Arabic ASR**: Understands Egyptian dialect natively
- 🚗 **DiLink Integration**: Controls climate, navigation, and media
- 🔊 **Egyptian TTS**: Responds in natural Egyptian Arabic
- 📴 **Offline-First**: Works without internet after initial setup
- ⚡ **Lightweight**: Optimized for embedded systems (~400MB total)
- 🔒 **Privacy-Focused**: All processing happens on-device

---

## Features

### Wake Word Detection
Simply say **"يا بنتي"** (Ya Binti) to activate the assistant.

### Voice Commands

| Category | Egyptian Arabic | Function |
|----------|----------------|----------|
| **Climate** | شغّل التكييف | Turn on AC |
| | أطفئ التكييف | Turn off AC |
| | درجة حرارة [20-30] | Set temperature |
| **Navigation** | خديني للبيت | Navigate home |
| | وديني [المكان] | Navigate to location |
| **Media** | شغّل المزيكا | Play music |
| | وقّف المزيكا | Pause music |
| | الأغنية الجاية | Next track |
| **Volume** | ارفع الصوت | Volume up |
| | اخفض الصوت | Volume down |

### Response Examples

```
User: "يا بنتي، شغّل التكييف"
Binti: "تمام يا باشا، شغلت التكييف! 🌬️"

User: "يا بنتي، خديني للبيت"
Binti: "من عيوني! بفتحلك الخريطة... يلا بينا! 🗺️"

User: "يا بنتي، الجو حر"
Binti: "فهمت يا حبيبي! هخفف التكييف شوية... تمام كده؟ 😊"
```

---

## Download

### Latest Release
[![Download APK](https://img.shields.io/github/downloads/waleedmandour/binti2/latest/total?style=for-the-badge&label=Download%20APK)](https://github.com/waleedmandour/binti2/releases/latest)

### Requirements
- BYD vehicle with DiLink 5.0+ infotainment system
- Android 8.0+ (API 26+)
- ~400MB storage for AI models
- Microphone permission

---

## Installation

### Step 1: Download APK
Download the latest APK from [Releases](https://github.com/waleedmandour/binti2/releases).

### Step 2: Enable Unknown Sources
On your DiLink system:
1. Go to **Settings** → **Security**
2. Enable **Allow installation from unknown sources**

### Step 3: Install
Open the downloaded APK and follow the installation wizard.

### Step 4: Grant Permissions
Binti requires the following permissions:
- **Microphone**: For voice recognition
- **Overlay**: To show voice feedback
- **Accessibility**: To control DiLink functions

### Step 5: Download AI Models
On first launch, Binti will download AI models (~400MB). WiFi is recommended.

---

## Usage

### Basic Usage
1. Say **"يا بنتي"** to activate
2. Wait for the greeting
3. Speak your command in Egyptian Arabic
4. Binti will respond and execute

### Tips
- Speak clearly and naturally
- Use Egyptian colloquialisms freely
- The assistant works best in a quiet environment

---

## Development

### Prerequisites
- Android Studio Hedgehog or later
- JDK 17+
- Android SDK 34
- NDK 25+

### Building from Source

```bash
# Clone the repository
git clone https://github.com/waleedmandour/binti2.git
cd binti2

# Build debug APK
./gradlew assembleDebug

# Build release APK
./gradlew assembleRelease
```

### Project Structure
```
binti2/
├── app/                    # Main Android app
│   ├── src/main/
│   │   ├── java/com/binti/dilink/
│   │   │   ├── voice/      # Voice processing (ASR, Wake Word)
│   │   │   ├── nlp/        # NLU (Intent classification)
│   │   │   ├── dilink/     # DiLink integration
│   │   │   ├── response/   # TTS and response generation
│   │   │   └── utils/      # Utilities
│   │   └── res/            # Resources (layouts, strings)
│   └── build.gradle.kts
├── models/                 # AI models (GitHub Releases)
├── scripts/                # Build and test scripts
└── docs/                   # Documentation
```

### Key Components

| Component | Technology | Size |
|-----------|------------|------|
| Wake Word | TensorFlow Lite CNN | ~5 MB |
| ASR | HuBERT-Egyptian (ONNX) | ~150 MB |
| NLU | EgyBERT-tiny (TFLite) | ~80 MB |
| TTS | Egyptian Female Voice | ~120 MB |

---

## Contributing

We welcome contributions from the Egyptian developer community!

### Ways to Contribute
- 🌍 Add more Egyptian Arabic phrases and responses
- 🐛 Report bugs and issues
- 💡 Suggest new features
- 🔧 Submit pull requests

### Development Setup
1. Fork the repository
2. Create a feature branch
3. Make your changes
4. Submit a pull request

### Code Style
- Follow [Kotlin coding conventions](https://kotlinlang.org/docs/coding-conventions.html)
- Use meaningful variable names
- Add comments in Arabic for Arabic-specific code

---

## License

This project is licensed under the MIT License - see the [LICENSE](LICENSE) file for details.

---

## Acknowledgments

- 🇪🇬 Egyptian Arabic NLP community
- 🚗 BYD DiLink developer community
- 🤗 HuggingFace for model hosting
- All contributors and testers

---

## Support

- 📧 Email: support@binti.app
- 💬 Discord: [Join our community](https://discord.gg/binti)
- 📱 Telegram: [@BintiAssistant](https://t.me/BintiAssistant)

---

<p align="center">
  Made with ❤️ for the Egyptian community 🇪🇬
</p>
