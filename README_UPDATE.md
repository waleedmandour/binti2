# Binti 2.0 - README Update

## 🚗 Installation Instructions for BYD Vehicles

### Prerequisites

Before installing Binti on your BYD vehicle, ensure you have:

1. **BYD Vehicle with DiLink System**
   - BYD Yuan Plus (Atto 3) - Fully Supported
   - BYD Han EV - Partial Support
   - BYD Tang EV - Partial Support
   - Other BYD models with DiLink 3.0+

2. **Required Permissions on Vehicle**
   - Unknown Sources enabled (Settings → Security)
   - Accessibility permission for Binti
   - Microphone permission
   - Display over apps permission

3. **Storage Requirements**
   - 2GB free space for models
   - WiFi connection recommended for initial download

---

### Installation Methods

#### Method 1: APK Installation via USB

1. **Download the APK**
   ```bash
   # Download from GitHub Releases
   wget https://github.com/waleedmandour/binti2/releases/download/v2.0.0/binti-2.0.0-release.apk
   ```

2. **Copy to USB Drive**
   - Copy `binti-2.0.0-release.apk` to a FAT32 formatted USB drive

3. **Install on BYD Vehicle**
   - Insert USB drive into vehicle USB port
   - Open File Manager on DiLink
   - Navigate to USB drive
   - Tap on APK file to install
   - Accept installation from unknown sources if prompted

#### Method 2: ADB Installation

```bash
# Enable USB debugging on DiLink (Settings → Developer Options)
adb devices
adb install binti-2.0.0-release.apk
```

#### Method 3: Huawei AppGallery (Coming Soon)

Binti will be available on Huawei AppGallery for easy installation on BYD vehicles.

---

### First Run Setup

1. **Open Binti App**
   - Launch from DiLink app drawer

2. **Grant Permissions**
   - Tap "Grant Permission" for each required permission
   - Microphone: Required for voice input
   - Display over apps: Required for voice feedback overlay
   - Accessibility: Required for DiLink control
   - Notifications: Required for foreground service (Android 13+)

3. **Enable Accessibility Service**
   - Go to Settings → Accessibility → Binti
   - Toggle on "Binti Accessibility Service"
   - Confirm permission

4. **Download Models**
   - Choose download method:
     - **Google Drive (Recommended)**: Fast download with automatic updates
     - **Local Models**: Load from USB/SD card
   - Wait for download to complete (~1.4GB)
   - WiFi recommended

5. **Configure Locations** (Optional)
   - Set home address: "يا بنتي، البيت هو [address]"
   - Set work address: "يا بنتي، الشغل هو [address]"

6. **Ready to Use!**
   - Say "يا بنتي" to activate
   - Speak your command

---

## 📥 Model Download Options

### Option 1: Google Drive (Recommended)

Binti 2.0 uses Google Drive for model hosting with direct download links.

**Advantages:**
- Fast download speeds
- Automatic virus scan handling
- Resume supported
- Free hosting for developers

**Configuration:**
```kotlin
// In ModelManager.kt, set your Google Drive file IDs
private const val WAKE_WORD_FILE_ID = "YOUR_FILE_ID"
private const val ASR_MODEL_FILE_ID = "YOUR_FILE_ID"
private const val NLU_MODEL_FILE_ID = "YOUR_FILE_ID"
private const val TTS_VOICE_FILE_ID = "YOUR_FILE_ID"
```

**How to Get File IDs:**
1. Upload model files to Google Drive
2. Right-click → Share → Anyone with the link
3. Copy the file ID from URL: `https://drive.google.com/file/d/[FILE_ID]/view`

### Option 2: Backblaze B2

Alternative hosting with Backblaze B2 cloud storage.

**Advantages:**
- Low cost ($0.005/GB/month)
- 10GB free tier
- Fast CDN

**Setup:**
```bash
# Install B2 CLI
pip install b2

# Authenticate
b2 authorize-account <keyID> <applicationKey>

# Create bucket
b2 create-bucket binti2-models allPublic

# Upload models
./scripts/upload_models_to_b2.sh
```

### Option 3: Local Models (Offline)

Load models from USB drive or SD card for completely offline operation.

**Setup:**
1. Create directory structure on USB:
   ```
   /binti_models/
   ├── models/
   │   ├── wake/ya_binti_detector.tflite
   │   ├── nlu/egybert_tiny_int8.onnx
   │   └── vosk-model-ar-mgb2/
   └── voices/
       └── ar-eg-female/
   ```

2. Configure in Binti:
   - Settings → Model Source → Local
   - Select USB path: `/storage/USB-A1/binti_models`

### Option 4: Mirror Servers

Custom mirror URLs for enterprise deployments.

**Configuration:**
```kotlin
// Add custom mirror URLs
val mirrorUrls = listOf(
    "https://your-mirror.com/models/wake/ya_binti_detector.tflite",
    "https://backup-mirror.com/models/wake/ya_binti_detector.tflite"
)
```

---

## 🔧 Troubleshooting

### Common Issues

#### Wake Word Not Detecting

**Symptoms:**
- No response to "يا بنتي"
- Delayed response

**Solutions:**
1. Check microphone permission
   - Settings → Apps → Binti → Permissions → Microphone
2. Adjust sensitivity
   - Settings → Wake Word → Sensitivity → Increase
3. Check for background noise
   - Reduce AC fan speed during testing
4. Retrain wake word model
   - Collect more training samples for your voice

#### ASR Not Working

**Symptoms:**
- "مسمعتش كويس" (I didn't hear well) response
- Incorrect transcription

**Solutions:**
1. Verify model downloaded
   - Settings → Models → Check status
2. Check Vosk model integrity
   - Redownload if corrupted
3. Try cloud fallback
   - Settings → Use Cloud ASR → Enable
4. Speak closer to microphone
   - DiLink microphone is usually near the mirror

#### Commands Not Executing

**Symptoms:**
- Voice recognized but no action taken
- "مقدرتش أعمل كده" (I couldn't do that) response

**Solutions:**
1. Check accessibility permission
   - Settings → Accessibility → Binti → Enable
2. Verify DiLink app packages
   - Some BYD models use different package names
3. Check DiLink version
   - Older versions may have different UI
4. Enable debug mode
   - Settings → Developer → Debug Mode → Enable
   - Check logs for error messages

#### Models Not Downloading

**Symptoms:**
- Download stuck at 0%
- Download fails repeatedly

**Solutions:**
1. Check internet connection
   - Try opening a website in browser
2. Check storage space
   - Need at least 2GB free
3. Try alternative source
   - Settings → Model Source → Change
4. Use local models
   - Download models on computer
   - Copy to USB drive
   - Load from USB

#### TTS Not Speaking

**Symptoms:**
- Commands execute but no voice response
- Robotic voice quality

**Solutions:**
1. Check volume
   - Media volume should be > 50%
2. Try different TTS engine
   - Settings → TTS → Select engine
3. Install Arabic TTS data
   - Android Settings → Language → Text-to-speech → Install data
4. Use Huawei TTS
   - Better quality on BYD/Huawei devices

### Debug Mode

Enable debug mode for detailed logging:

```kotlin
// In BintiApplication.kt
BuildConfig.DEBUG = true
```

Or via settings:
- Settings → Developer → Debug Mode → Enable

**View Logs:**
```bash
adb logcat -s BintiService VoiceProcessor IntentClassifier DiLinkExecutor
```

---

## 💬 Supported Commands List

### Air Conditioning (التكييف)

| Arabic Command | English | Action |
|----------------|---------|--------|
| يا بنتي، شغلي التكييف | Turn on AC | AC Power On |
| يا بنتي، طفي التكييف | Turn off AC | AC Power Off |
| يا بنتي، زيود الحرارة | Increase temperature | Temp +1°C |
| يا بنتي، قلل الحرارة | Decrease temperature | Temp -1°C |
| يا بنتي، التكييف على درجة 22 | Set AC to 22°C | Set Temperature |
| يا بنتي، تكييف بارد | Cool mode | AC Mode: Cool |
| يا بنتي، تكييف ساخن | Heat mode | AC Mode: Heat |
| يا بنتي، سرعة المروحة عالية | High fan speed | Fan Speed: High |

### Navigation (التنقل)

| Arabic Command | English | Action |
|----------------|---------|--------|
| يا بنتي، خديني للبيت | Take me home | Navigate Home |
| يا بنتي، خديني للشغل | Take me to work | Navigate Work |
| يا بنتي، أقرب بنزين | Nearest gas station | POI: Gas Station |
| يا بنتي، أقرب شحن | Nearest charging | POI: EV Charging |
| يا بنتي، أقرب مطعم | Nearest restaurant | POI: Restaurant |
| يا بنتي، أقرب مستشفى | Nearest hospital | POI: Hospital |
| يا بنتي، أقرب موقف | Nearest parking | POI: Parking |
| يا بنتي، خديني لـ[مكان] | Take me to [place] | Custom Destination |
| يا بنتي، إلغاء التنقل | Cancel navigation | Cancel Navigation |

### Media Control (الوسائط)

| Arabic Command | English | Action |
|----------------|---------|--------|
| يا بنتي، شغلي موسيقى | Play music | Media Play |
| يا بنتي، وقفة الأغنية | Pause the song | Media Pause |
| يا بنتي، اللي بعدها | Next one | Media Next |
| يا بنتي، اللي قبلها | Previous one | Media Previous |
| يا بنتي، صوت عالي | Volume up | Volume Increase |
| يا بنتي، صوت واطي | Volume down | Volume Decrease |

### Phone (الهاتف)

| Arabic Command | English | Action |
|----------------|---------|--------|
| يا بنتي، كلم [اسم] | Call [name] | Contact Call |
| يا بنتي، كلم [رقم] | Call [number] | Number Call |
| يا بنتي، رد عالمكالمة | Answer the call | Answer Call |
| يا بنتي، أقفل المكالمة | End the call | End Call |
| يا بنتي، أعادة الاتصال | Redial last number | Redial |

### Information (معلومات)

| Arabic Command | English | Action |
|----------------|---------|--------|
| يا بنتي، الساعة كام | What time is it? | Tell Time |
| يا بنتي، حرارة بره إيه | Outside temperature? | Weather Info |
| يا بنتي، البطارية كام | Battery level? | Battery Status |
| يا بنتي، التاريخ إيه | What's the date? | Tell Date |
| يا بنتي، حالة السيارة | Vehicle status? | Status Summary |

---

## ⚙️ Configuration Options

### Settings Available

#### Voice Settings
- **Wake Word Sensitivity:** 0.5 - 1.0 (default: 0.85)
- **Voice Speed:** 0.5 - 2.0 (default: 0.95)
- **Voice Pitch:** 0.5 - 2.0 (default: 1.0)
- **Silence Timeout:** 1-5 seconds (default: 1.5)

#### Model Settings
- **Model Source:** Google Drive / B2 / Local
- **Auto-update Models:** On/Off
- **WiFi Only Downloads:** On/Off (default: On)

#### Accessibility Settings
- **Overlay Position:** Top/Center/Bottom
- **Overlay Duration:** 2-10 seconds
- **Vibration Feedback:** On/Off

#### Location Settings
- **Home Address:** String
- **Work Address:** String
- **Saved Places:** List of custom locations

### SharedPreferences Keys

```kotlin
// Access preferences
val prefs = getSharedPreferences("binti_prefs", Context.MODE_PRIVATE)

// Voice settings
val sensitivity = prefs.getFloat("wake_word_sensitivity", 0.85f)
val voiceSpeed = prefs.getFloat("voice_speed", 0.95f)

// Model settings
val modelSource = prefs.getString("model_source", "google_drive")
val wifiOnly = prefs.getBoolean("wifi_only_downloads", true)

// Location settings
val homeAddress = prefs.getString("home_address", null)
val workAddress = prefs.getString("work_address", null)
```

---

## 🔄 Updates

### Checking for Updates

Binti checks for updates automatically on launch. You can also check manually:
- Settings → About → Check for Updates

### Update Methods

1. **Automatic (Recommended)**
   - APK downloaded automatically
   - Prompt to install

2. **Manual via USB**
   - Download new APK from GitHub Releases
   - Install via USB as described above

3. **Model Updates**
   - Models update automatically when new versions available
   - Settings → Models → Check for Updates

---

*Last Updated: January 2025*
