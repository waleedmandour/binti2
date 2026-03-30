# 🚗 Binti (بنتي) - Egyptian Arabic Voice Assistant for BYD DiLink

<div align="center">

  **Egyptian Arabic Voice Assistant | BYD DiLink Integration | Accessibility Driven**

  [![License: MIT](https://img.shields.io/badge/License-MIT-yellow.svg)](https://opensource.org/licenses/MIT)
  [![Android](https://img.shields.io/badge/Platform-Android_8.0+-green.svg)](https://www.android.com)
  [![Kotlin](https://img.shields.io/badge/Language-Kotlin_2.0-blue.svg)](https://kotlinlang.org)
  [![Huawei HMS](https://img.shields.io/badge/HMS-Enabled-red.svg)](https://developer.huawei.com/consumer/en/hms)

</div>

---

## 📖 About

**Binti** (بنتي - "my daughter") is a professional-grade Egyptian Arabic voice assistant designed specifically for BYD DiLink infotainment systems. It enables drivers to control vehicle functions using natural Egyptian dialect, responding to the wake word **"يا بنتي"** (Ya Binti).

Binti is optimized for the hardware and software environment of BYD vehicles, featuring a landscape-native UI and **Android Accessibility Service-based UI automation** to interact with DiLink system apps directly — no root or ADB required.

### ✨ Key Features

| Feature | Description | Technology |
|---------|-------------|------------|
| 🎤 **Wake Word Detection** | Optimized for "يا بنتي" with high noise immunity | Vosk Local Grammars |
| 🗣️ **Egyptian Arabic ASR** | Robust offline speech-to-text for Egyptian dialect | Vosk MGB2 / Huawei ML Kit |
| 🧠 **Intent Classification** | Local NLU processing for Egyptian colloquialisms | Rule Engine + EgyBERT-tiny (optional) |
| 🔊 **Egyptian TTS** | Natural female voice with Sukoon-vocalized Egyptian tone | Huawei ML Kit / Android TTS |
| 👤 **User Profile** | Personalized interaction with Formal/Informal tone modes | SharedPreferences / Context-Aware |
| 🚙 **DiLink Control** | Control AC, Navigation, Media, and Phone via Accessibility | **Accessibility Service UI Automation** |
| 📱 **Quick Actions Widget**| 4×1 Home screen widget for one-tap car control | Android RemoteViews |
| 📐 **Car Display UX** | Optimized for 10.1", 12.8", and 15.6" BYD screens | Landscape-First UI |
| 🌐 **Offline-First** | Full functionality without internet connectivity | Vosk + Local NLP + Local TTS |

---

## 🧠 Conversational & Context Intelligence

Binti isn't just a command-executor; she is designed to be a companion that understands the social context of the Egyptian driver. Every spoken response is fully **vocalized with Tashkeel** (including Sukoon at word endings) to produce naturally-sounding Egyptian Arabic when processed by the TTS engine.

- **Personalized Profile**: Users can set their name and choose between **Formal (رسمي - يا فندم)** or **Informal/Friendly (ودي - يا ريّس)** interaction tones. Binti adapts her vocabulary and sentence structure accordingly.
- **Proactive Contextual Greetings**: Binti greets the driver based on the time of day — "يا صباح الفل يا دكتور وليد" in the morning, "يا مساء القشطة" in the afternoon, or "يا مساء الجمال" in the evening.
- **Demo Voice Message**: On first app launch, Binti introduces herself in Egyptian Arabic, explaining how to use the assistant and what she can do.
- **Dialect Normalization**: A dedicated NLP layer in `EgyptianTTS.kt` automatically converts any MSA remnants into Egyptian colloquial equivalents (e.g., "لماذا" → "ليه", "كيف" → "إزاي", "أين" → "فين") before speech output.
- **State Awareness**: The assistant visually and audibly communicates whether she is listening (🎤), thinking (🤔), or executing a command (✅), providing feedback consistent with her personality.

---

## 🚙 Deep DiLink Integration via Accessibility Service

Binti controls the BYD DiLink system entirely through the **Android Accessibility Service** — the same mechanism used by screen readers and automation tools. This approach provides a secure, permission-gated, and root-free way to interact with DiLink system apps.

### How It Works

The `DiLinkAccessibilityService` monitors all BYD DiLink system packages and interacts with their UI elements by discovering view resource IDs, performing clicks, reading text content, and dispatching gestures — all through the standard Android Accessibility API.

### Supported DiLink Packages

| Package | Function |
|---------|----------|
| `com.byd.auto.ac` | AC & Climate control |
| `com.byd.auto.navigation` | GPS navigation |
| `com.byd.auto.media` | Music and media playback |
| `com.byd.auto.phone` | Phone calls and contacts |
| `com.byd.auto.settings` | System settings |
| `com.byd.auto.launcher` | Home screen |
| `com.byd.auto.climate` | Advanced climate (some models) |
| `com.byd.auto.vehicleinfo` | Vehicle status and battery |
| `com.byd.auto.energy` | Energy management |
| `com.byd.auto.camera` | 360° camera view |

### Voice Commands

- **AC & Climate**: `"يا بنتي، شغلي التكييف"` — Opens the AC app via Accessibility and clicks the power button by its resource ID (`com.byd.auto.ac:id/btn_power`). Temperature adjustments use `btn_temp_up` / `btn_temp_down`.
- **Navigation**: `"يا بنتي، روّحيني البيت"` — Launches navigation via `geo:` intent targeting `com.byd.auto.navigation`. Supports arbitrary destinations extracted from speech.
- **Media System**: `"يا بنتي، الأغنية التالية"` — Uses Accessibility to click media control buttons (`btn_play`, `btn_pause`, `btn_next`, `btn_previous`) in the BYD media player.
- **Telephony**: `"يا بنتي، كلمي أحمد"` — Opens the phone dialer via Accessibility or triggers `ACTION_DIAL` intent for direct calls.
- **System Settings**: `"يا بنتي، زوّد الصوت شوية"` — Adjusts system volume directly via `AudioManager` (no Accessibility needed for audio routing).
- **Vehicle Info**: `"يا بنتي، البطارية فيها كام؟"` — Reads battery percentage from the DiLink vehicle info screen using Accessibility node inspection.
- **Greeting & Social**: `"يا بنتي، إزيك؟"` — Responds with contextually appropriate Egyptian Arabic greetings based on time of day and user profile.

### Supported BYD Models

Binti auto-detects the vehicle model from system properties (`ro.product.model`, `ro.byd.model`) and applies model-specific UI resource IDs. Primary support with fallback IDs for:

- **BYD Yuan Plus / Atto 3** (2023+) — Primary target with full resource ID mapping
- **BYD Dolphin**
- **BYD Seal**
- **BYD Han EV**
- **BYD Tang EV**

The `BYDModels.kt` configuration object provides primary resource IDs for the Yuan Plus along with fallback ID lists for other models, ensuring broad compatibility across the BYD lineup.

---

## 🔐 Security & Privacy

Binti's use of Accessibility Service is designed with security as a priority:

- **Explicit User Permission**: The Accessibility Service requires manual activation by the user in Android Settings → Accessibility. It cannot enable itself.
- **Scoped Package Monitoring**: The service only monitors BYD DiLink packages (`com.byd.auto.*`) and ignores all other apps.
- **No Root Required**: All vehicle interactions use standard Android Accessibility APIs — no root access, ADB debugging, or system shell commands are needed.
- **No External Data Transmission**: All speech recognition (Vosk), intent classification (Rule Engine), and text-to-speech run entirely offline on the device. No voice data is sent to external servers.
- **Local Model Storage**: AI models are stored in the app's private storage directory and are not shared with other applications.
- **Foreground Service**: Binti runs as a visible foreground service with a persistent notification, ensuring the user is always aware when the assistant is active.

---

## 🚀 Installation & Permissions

Binti requires specific permissions to function as a full car assistant. For Android 14+ systems, Binti uses compliant Foreground Service types.

1. **Microphone** *(Required)*: For wake word detection and voice command listening.
2. **Storage** *(Required)*: To download and store offline AI models (~318 MB for the Vosk Arabic ASR model).
3. **Accessibility Service** *(Required)*: Enables Binti to read and interact with DiLink system UI elements (AC, navigation, media, phone). This is the primary mechanism for all vehicle control.
4. **Display Over Apps** *(Required)*: For the voice interaction overlay that shows listening state and transcription feedback.
5. **Location** *(Optional)*: For navigation and finding nearby charging stations or restaurants.
6. **Phone & Contacts** *(Optional)*: For hands-free calling through the car's system.
7. **Notifications** *(Optional)*: For displaying the persistent service notification.

### Setup Steps

1. Install the APK on your BYD DiLink system.
2. On first launch, Binti plays a demo voice message in Egyptian Arabic introducing herself.
3. Download the offline AI model (~318 MB) when prompted (WiFi recommended).
4. Grant **Accessibility Service** permission: Settings → Accessibility → find "يا بنتي" → Enable. This is the critical step that enables all vehicle control.
5. Grant **Display Over Apps** permission for the voice overlay.
6. Grant **Microphone** and **Storage** permissions.
7. Tap **تشغيل المساعد** (Start Assistant) — Binti begins listening for the wake word "يا بنتي".

---

## 🏗️ Technical Architecture

Binti follows a robust offline-first pipeline to ensure reliability in areas with poor connectivity:

```
Wake Word ("يا بنتي")  →  ASR (Vosk MGB2)  →  NLU (Intent Classifier)  →  Command Executor  →  TTS Response (Egyptian Arabic)
                                    ↓                                        ↓
                            Egyptian Arabic                           Accessibility Service
                            Transcription                           (UI Automation)
```

1. **Wake Word Engine**: Always-on listener using Vosk grammar-based detection for "يا بنتي" — no dedicated ML model required.
2. **Voice Processing (ASR)**: Vosk MGB2 model provides high-accuracy Arabic speech recognition entirely offline. Model hosted on Google Drive (~318 MB).
3. **NLP Pipeline**: A rule-based intent matcher processes the transcription against the `dilink_intent_map.json` pattern database. An optional EgyBERT-tiny ML classifier can supplement the rule engine for complex or ambiguous commands.
4. **Command Executor**: `DiLinkCommandExecutor` translates classified intents into Accessibility Service actions — finding UI nodes by resource ID, performing clicks, reading state, and launching apps. No shell commands or root access involved.
5. **Response Engine**: `EgyptianTTS` produces spoken responses in natural Egyptian Arabic. All responses in the intent map are fully vocalized with Tashkeel (diacritics), and Sukoon (ْ) is preserved at word endings to ensure natural cadence. The engine also applies dialect normalization, converting any MSA artifacts to Egyptian colloquial equivalents.

### Project Structure

```
app/src/main/java/com/binti/dilink/
├── BintiApplication.kt          # Application class & notification setup
├── BintiService.kt              # Foreground service: wake word → ASR → NLU → TTS loop
├── MainActivity.kt              # Dashboard UI, permissions, model download, demo voice
├── dilink/
│   ├── DiLinkAccessibilityService.kt  # Core: Accessibility-based DiLink UI automation
│   ├── DiLinkCommandExecutor.kt       # Translates intents → Accessibility actions
│   └── BYDModels.kt                   # View resource IDs per BYD model
├── nlp/
│   └── IntentClassifier.kt     # Rule-based + ML intent classification
├── response/
│   └── EgyptianTTS.kt          # Egyptian Arabic TTS with Sukoon vocalization
├── voice/
│   ├── VoiceProcessor.kt       # Audio capture and Vosk ASR integration
│   └── WakeWordDetectorVosk.kt # Wake word detection via Vosk grammar
├── receivers/
│   └── BootReceiver.kt         # Auto-start on device boot
├── ui/
│   └── QuickActionsWidget.kt   # Home screen widget
└── utils/
    ├── ModelManager.kt         # Google Drive model download & extraction
    ├── VoiceProfileManager.kt  # User voice profile persistence
    ├── HMSUtils.kt             # Huawei ML Kit integration helpers
    └── OfflineFallbackManager.kt
```

---

## 💬 Sample Commands

| Command (Egyptian Arabic) | English | Action |
|--------------------------|---------|--------|
| *يا بنتي، شغلي التكييف* | Turn on the AC | Opens AC app, clicks power button |
| *يا بنتي، طفي التكييف* | Turn off the AC | Clicks AC power toggle |
| *يا بنتي، علي الحرارة* | Raise temperature | Clicks temperature up button |
| *يا بنتي، روّحيني البيت* | Take me home | Launches navigation to home |
| *يا بنتي، وديني الشغل* | Take me to work | Launches navigation to work |
| *يا بنتي، شغلي الموسيقى* | Play music | Opens media, clicks play |
| *يا بنتي، الأغنية التالية* | Next song | Clicks next track button |
| *يا بنتي، كلمي أحمد* | Call Ahmed | Opens phone dialer |
| *يا بنتي، زوّد الصوت* | Volume up | Adjusts system volume |
| *يا بنتي، الساعة كام دلوقتي؟* | What time is it? | Speaks current time |
| *يا بنتي، البطارية فيها كام؟* | Battery level? | Reads battery from vehicle info |
| *يا بنتي، إزيك النهاردة؟* | How are you? | Conversational greeting response |

---

## 📄 License & Credits

Developed by **Dr. Waleed Mandour**.
Email: w.abumandour@squ.edu.om

Licensed under the MIT License.
Built for BYD DiLink infotainment systems using Android Accessibility Service technology.
