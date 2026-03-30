# 🚗 Binti (بنتي) - Egyptian Arabic Voice Assistant for BYD DiLink

<div align="center">
  
  **Egyptian Arabic Voice Assistant | BYD DiLink Integration | ADB Driven**
  
  [![License: MIT](https://img.shields.io/badge/License-MIT-yellow.svg)](https://opensource.org/licenses/MIT)
  [![Android](https://img.shields.io/badge/Platform-Android_8.0+-green.svg)](https://www.android.com)
  [![Kotlin](https://img.shields.io/badge/Language-Kotlin_1.9-blue.svg)](https://kotlinlang.org)
  [![Huawei HMS](https://img.shields.io/badge/HMS-Enabled-red.svg)](https://developer.huawei.com/consumer/en/hms)
</div>

---

## 📖 About

**Binti** (بنتي - "my daughter") is a professional-grade Egyptian Arabic voice assistant designed specifically for BYD DiLink infotainment systems. It enables drivers to control vehicle functions using natural Egyptian dialect, responding to the wake word **"يا بنتي"** (Ya Binti).

Binti is optimized for the hardware and software environment of BYD vehicles, featuring a landscape-native UI and **ADB-based system control** to ensure compatibility across all DiLink versions.

### ✨ Key Features

| Feature | Description | Technology |
|---------|-------------|------------|
| 🎤 **Wake Word Detection** | Optimized for "يا بنتي" with high noise immunity | Vosk Local Grammars / TFLite |
| 🗣️ **Egyptian Arabic ASR** | Robust offline speech-to-text for Egyptian dialect | Vosk MGB2 / Huawei ML Kit |
| 🧠 **Intent Classification** | Local NLU processing for Egyptian colloquialisms | EgyBERT-tiny + Rule Engine |
| 🔊 **Egyptian TTS** | Natural female voice responses with Egyptian tone | Huawei ML Kit / Android TTS |
| 👤 **User Profile** | Personalized interaction based on name and tone | SharedPreferences / Context-Aware |
| 🚙 **DiLink Control** | Control AC, Navigation, Media, and Phone calls | **ADB Shell & System Intents** |
| 📱 **Quick Actions Widget**| 4x1 Home screen widget for one-tap car control | Android RemoteViews |
| 📐 **Car Display UX** | Optimized for 10.1", 12.8", and 15.6" BYD screens | Landscape-First UI |

---

## 🧠 Conversational & Context Intelligence

Binti isn't just a command-executor; she is designed to be a companion that understands the social context of the Egyptian driver:

- **Personalized Profile**: Users can set their name and choose between **Formal (رسمي)** or **Informal/Friendly (ودي)** tones.
- **Proactive Contextual Greetings**: Binti greets the driver based on the time of day (e.g., "يا صباح الفل يا دكتور وليد" in the morning or "يا مساء الجمال" in the evening).
- **Dialect Normalization**: Custom NLP layers handle the nuances of Egyptian slang (e.g., "عايز/عايزة", "إزاي", "إعملي") to provide a natural "human-like" interaction.
- **State Awareness**: The assistant tracks whether she is listening, thinking, or executing, providing visual and auditory feedback consistent with her "personality".

---

## 🚙 Deep DiLink Integration

Binti integrates deeply with the BYD DiLink ecosystem using ADB, system-level intents, and Accessibility Services:

- **AC & Climate**: Control temperature, fan speed, and modes ("يا بنتي، خلي الحرارة ٢٢").
- **Navigation**: Start guidance to home, work, or local POIs ("يا بنتي، وديني أقرب محطة شحن").
- **Media System**: Manage music playback across the system ("يا بنتي، المقطع التالي").
- **Telephony**: Hands-free calling and call management ("يا بنتي، كلمي أحمد").
- **System Settings**: Control brightness and volume ("يا بنتي، وطي الإضاءة شوية").

---

## 🚀 Installation & Permissions

Binti requires specific permissions to function as a full car assistant. For Android 14+ systems, Binti uses compliant Foreground Service types.

1. **Microphone**: For wake word detection and command listening.
2. **Location**: For navigation and finding nearby charging stations.
3. **Phone & Contacts**: For managing calls through the car's hands-free system.
4. **ADB Debugging**: **(Crucial)** Required for Binti to execute system commands on DiLink.
5. **Accessibility Service**: **(Required)** Enables Binti to interact with DiLink system UI elements.
6. **Display Over Apps**: For the voice interaction overlay (listening animation).
7. **Write Settings**: To manage screen brightness and system volume.
8. **Storage**: To store and manage offline AI models.

---

## 🏗️ Technical Architecture

Binti follows a robust offline-first pipeline to ensure reliability in areas with poor connectivity:

1.  **Wake Word Engine**: Always-on low-power listener for "يا بنتي".
2.  **Voice Processing**: Uses Vosk MGB2 for high-accuracy Egyptian dialect transcription.
3.  **NLP Pipeline**: **EgyBERT-tiny** classifies intents and extracts entities (like temperature or location).
4.  **Command Executor**: Translates intents into ADB shell commands or DiLink system intents.
5.  **Response Engine**: **Egyptian TTS** uses Huawei ML Kit with a custom normalization layer for Egyptian tone.

---

## 💬 Sample Commands

- *يا بنتي، شغلي التكييف* (Turn on AC)
- *يا بنتي، خدينا للبيت* (Take us home)
- *يا بنتي، إزيك النهاردة؟* (How are you today? - Conversational)
- *يا بنتي، وطي الصوت شوية* (Lower the volume)
- *يا بنتي، البطارية فيها كام؟* (Check battery status)

---

## 📄 License & Credits

Developed by **Dr. Waleed Mandour**. Licensed under the MIT License.
Optimization for BYD DiLink systems using ADB bridge and Accessibility technology.
