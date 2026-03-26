# Arabic Wake Word Detection Research for Binti2

## Executive Summary

This research covers available options for Arabic wake word detection, specifically for detecting "يا بنتي" (Ya Binti) in the Binti2 Android app.

---

## 1. Available Pre-Trained Models

### 1.1 TensorFlow Lite Micro Speech Model

**Best Option for Custom Training**

- **Repository:** https://github.com/tensorflow/tflite-micro/tree/main/tensorflow/lite/micro/examples/micro_speech
- **Description:** Official TFLite example for keyword spotting
- **Model Size:** ~20KB (tiny), scalable
- **Pros:**
  - Official Google/TensorFlow support
  - Well-documented training pipeline
  - Works on microcontrollers (very efficient)
  - Can be trained on custom keywords
- **Cons:**
  - Requires training data collection
  - No pre-trained Arabic models
- **Training Pipeline:** Yes, included

**Training Guide:** https://github.com/tensorflow/tflite-micro/blob/main/tensorflow/lite/micro/examples/micro_speech/train/README.md

### 1.2 Porcupine (Picovoice)

**Commercial Option with Arabic Support**

- **Website:** https://picovoice.ai/platform/porcupine/
- **Documentation:** https://picovoice.ai/docs/quick-start/porcupine-android/
- **Pricing:** Free tier available, paid for commercial use
- **Arabic Support:** Yes (Arabic language models available)
- **Model Size:** Small (~1MB per wake word)
- **Pros:**
  - Excellent accuracy (industry-leading)
  - Low latency, low power
  - Cross-platform (Android, iOS, Web, etc.)
  - Pre-built Arabic models
  - Can train custom wake words via Picovoice Console
- **Cons:**
  - Commercial license required for production
  - Not open source
  - Custom wake words require paid plan

**Custom Wake Word Training:** https://picovoice.ai/platform/porcupine/#custom-wake-words

**GitHub Examples:** https://github.com/Picovoice/porcupine

### 1.3 Vosk Keyword Spotting

**Open Source Option**

- **Website:** https://alphacephei.com/vosk/
- **GitHub:** https://github.com/alphacep/vosk-api
- **Arabic Models:** 
  - `vosk-model-ar-mgb2` (Modern Standard Arabic)
  - `vosk-model-small-ar` (Smaller Arabic model)
- **Model Sizes:** 50MB - 1.2GB
- **Pros:**
  - Completely open source (Apache 2.0)
  - Works offline
  - Arabic models available
  - Can detect any keyword via grammar
- **Cons:**
  - Larger model size
  - Higher latency than specialized wake word models
  - Uses more memory/CPU

**Keyword Spotting Approach:**
```kotlin
// Vosk can be configured to spot specific phrases
val grammar = """
{
  "type": "grammar",
  "words": ["يا بنتي", "يا بنتي شغلي التكييف"]
}
"""
```

**Android Integration:** https://github.com/alphacep/vosk-api/tree/master/android

### 1.4 Snowboy (Deprecated but Alternatives Exist)

**Note:** Snowboy was discontinued in 2020. Alternatives:

1. **Porcupine** (mentioned above) - Best replacement
2. **OpenWakeWord** - Open source alternative
3. **NVIDIA NeMo** - For custom training

### 1.5 OpenWakeWord

**Modern Open Source Alternative**

- **GitHub:** https://github.com/dscripka/openWakeWord
- **Description:** Open-source wake word detection
- **Model Type:** TensorFlow Lite compatible
- **Pros:**
  - Completely open source (MIT license)
  - Pre-trained models available
  - Can train custom wake words
  - TensorFlow Lite compatible
- **Cons:**
  - Limited pre-trained wake words
  - Requires training data for Arabic

**Training Guide:** https://github.com/dscripka/openWakeWord#training-custom-wake-word-models

### 1.6 Coqui STT / Vosk for Keyword Spotting

**Alternative Approach: Speech-to-Text with Grammar**

Instead of a dedicated wake word model, use speech recognition with limited grammar:

```kotlin
// Configure Vosk to only recognize wake word phrases
val model = Model("path/to/vosk-model-small-ar")
val recognizer = Recognizer(model, 16000f, """
["يا بنتي", "[unk]"]
""".trimIndent())
```

---

## 2. How to Create Custom Wake Word Models

### 2.1 TensorFlow Lite Micro Speech Training

**Complete Training Pipeline:**

```bash
# Clone the repository
git clone https://github.com/tensorflow/tflite-micro.git
cd tflite-micro/tensorflow/lite/micro/examples/micro_speech

# Install dependencies
pip install -r requirements.txt

# Create training data structure
mkdir -p data/wake_word data/noise data/other

# Add your Arabic wake word recordings
# data/wake_word/ - recordings of "يا بنتي"
# data/noise/ - background noise samples
# data/other/ - other speech samples
```

**Training Data Requirements:**
- 500-1000 positive samples ("يا بنتي")
- 1000+ negative samples (other speech/noise)
- Various speakers, accents, environments
- 16kHz sample rate, mono audio

**Training Script:**
```python
# train_micro_speech.py
import tensorflow as tf
from tensorflow_examples.lite.model_maker.core.task import audio_classifier

# Define your wake word
WAKE_WORD = "ya_binti"

# Load training data
data = audio_classifier.DataLoader.from_folder(
    'data/', 
    cache_dir='cache'
)
train_data, test_data = data.split(0.8)

# Create model
model = audio_classifier.create(
    train_data,
    model_spec='micro_speech',
    batch_size=32,
    epochs=50
)

# Export to TFLite
model.export('ya_binti_detector.tflite')
```

**Full Tutorial:** 
- https://www.tensorflow.org/lite/models/modify/model_maker/speech
- https://github.com/tensorflow/tflite-micro/tree/main/tensorflow/lite/micro/examples/micro_speech/train

### 2.2 TensorFlow Model Maker for Keyword Spotting

**Easier Alternative:**

```python
# Using TensorFlow Lite Model Maker
!pip install tflite-model-maker

from tflite_model_maker import audio_classifier

# Prepare data
data_dir = './audio_data/'
# Structure:
# audio_data/
#   ya_binti/
#     recording1.wav
#     recording2.wav
#     ...
#   _background_noise_/
#     noise1.wav
#     ...

# Create dataset
train_data = audio_classifier.DataLoader.from_folder(data_dir)
train_data, test_data = train_data.split(0.8)

# Train model
model = audio_classifier.create(
    train_data,
    model_spec='micro_speech',
    epochs=50
)

# Evaluate
loss, accuracy = model.evaluate(test_data)

# Export for Android
model.export(
    export_dir='./',
    tflite_filename='ya_binti_detector.tflite'
)
```

**Documentation:** https://www.tensorflow.org/lite/models/modify/model_maker/speech

### 2.3 Picovoice Console (Paid, Easiest)

**Steps to Create Custom Wake Word:**

1. Create account at https://console.picovoice.ai/
2. Go to "Porcupine" section
3. Click "Train Wake Word"
4. Enter "يا بنتي" or phonetic equivalent
5. Select language: Arabic
6. Train and download model
7. Integrate in Android app

**Pricing:** 
- Free tier: 3 custom wake words
- Paid: Starting at $0.001 per detection

### 2.4 NVIDIA NeMo (Advanced)

**For High-Quality Custom Models:**

```python
# Install NeMo
pip install nemo_toolkit[all]

# Use NeMo for keyword spotting
import nemo.collections.asr as nemo_asr

# Load pre-trained model
model = nemo_asr.models.EncDecClassificationModel.from_pretrained(
    'MatchboxNet-VGG'
)

# Fine-tune on Arabic wake word data
# Requires creating a manifest file with your data
```

**Documentation:** https://docs.nvidia.com/deeplearning/nemo/user-guide/docs/en/stable/asr/kws.html

---

## 3. Alternative Approaches for Binti2

### 3.1 Vosk + Grammar-Based Detection (Recommended for Offline)

**Pros:**
- Already using Vosk for ASR
- Same model can be used
- No additional model needed
- Open source

**Implementation:**
```kotlin
class VoskWakeWordDetector(context: Context) {
    private val model: Model
    private var recognizer: Recognizer? = null
    
    init {
        Vosk.setModelPath(context.filesDir.path + "/models/vosk-model-small-ar")
        model = Model(Vosk.getModelPath())
        // Create grammar for wake word only
        recognizer = Recognizer(model, 16000f, """
            ["يا بنتي", "[unk]"]
        """.trimIndent())
    }
    
    fun processAudio(audioData: ShortArray): Boolean {
        val result = recognizer?.result ?: return false
        return result.contains("يا بنتي")
    }
}
```

### 3.2 Hybrid Approach: Light Wake Word + ASR Verification

**Two-Stage Detection:**

1. **Stage 1:** Simple energy-based trigger (very low CPU)
2. **Stage 2:** Vosk verification with grammar

```kotlin
class HybridWakeWordDetector {
    fun onAudioEnergyDetected(audioFrame: ShortArray) {
        // Quick energy check
        if (calculateEnergy(audioFrame) > THRESHOLD) {
            // Verify with Vosk
            if (verifyWithVosk(audioFrame)) {
                // Wake word confirmed!
                onWakeWordDetected()
            }
        }
    }
}
```

### 3.3 TensorFlow Lite Micro Speech (Best Balance)

**Recommended Approach:**

1. Train micro_speech model on "يا بنتي"
2. Convert to TFLite
3. Integrate with existing WakeWordDetector.kt

**Training Data Collection:**
```bash
# Create dataset structure
mkdir -p dataset/{ya_binti,not_ya_binti,noise}

# Collect recordings
# - Use Android app to record samples
# - Ask native Arabic speakers
# - Include various accents, speeds, volumes
# - Record in different environments (car, home, street)
```

---

## 4. Recommended Solution for Binti2

### Option A: Custom TFLite Model (Recommended)

**Steps:**

1. **Collect Training Data**
   - Record 500+ samples of "يا بنتي"
   - Include multiple speakers
   - Various environments (car interior is crucial)
   - Different speeds and intonations

2. **Train Model**
   ```bash
   # Use TensorFlow Model Maker
   python train_wake_word.py --data_dir=./dataset --output=ya_binti_detector.tflite
   ```

3. **Integrate with Existing Code**
   - Your WakeWordDetector.kt is already set up
   - Just need to provide the trained model

4. **Test and Iterate**
   - Test in actual BYD vehicle
   - Collect false positives/negatives
   - Retrain with more data

### Option B: Vosk Grammar-Based (Fastest Implementation)

**Steps:**

1. Use existing Vosk Arabic model
2. Configure grammar for wake word only
3. Run continuously in background

```kotlin
// Modify VoiceProcessor.kt
class WakeWordRecognizer(context: Context) {
    private val grammar = """["يا بنتي", "[unk]"]"""
    private val recognizer = Recognizer(model, 16000f, grammar)
    
    suspend fun listenForWakeWord() {
        // Continuous listening with grammar constraint
        // Very lightweight compared to full ASR
    }
}
```

### Option C: Porcupine (Easiest, but Paid)

**Steps:**

1. Create Picovoice account
2. Train custom wake word "يا بنتي"
3. Download Android SDK
4. Integrate with app

**Cost:** ~$0.001 per detection (free tier available)

---

## 5. Implementation Resources

### Download URLs

| Resource | URL |
|----------|-----|
| TensorFlow Lite Micro Speech | https://github.com/tensorflow/tflite-micro/tree/main/tensorflow/lite/micro/examples/micro_speech |
| TensorFlow Model Maker | https://www.tensorflow.org/lite/models/modify/model_maker/speech |
| Vosk Arabic Models | https://alphacephei.com/vosk/models |
| Picovoice Porcupine | https://picovoice.ai/platform/porcupine/ |
| OpenWakeWord | https://github.com/dscripka/openWakeWord |
| NVIDIA NeMo KWS | https://docs.nvidia.com/deeplearning/nemo/user-guide/docs/en/stable/asr/kws.html |

### Training Data Sources

| Source | URL |
|--------|-----|
| Mozilla Common Voice (Arabic) | https://commonvoice.mozilla.org/ar |
| Arabic Speech Corpus | https://data.baai.ac.cn/#/speech |
| Google Speech Commands | https://www.tensorflow.org/datasets/catalog/speech_commands |
| OpenSLR | https://www.openslr.org/ |

### Tutorials

| Tutorial | URL |
|----------|-----|
| TensorFlow Keyword Spotting | https://www.tensorflow.org/tutorials/audio/simple_audio |
| TFLite Micro Speech Training | https://github.com/tensorflow/tflite-micro/blob/main/tensorflow/lite/micro/examples/micro_speech/train/README.md |
| Picovoice Android Guide | https://picovoice.ai/docs/quick-start/porcupine-android/ |
| Vosk Android Demo | https://github.com/alphacep/vosk-api/tree/master/android/VoskDemo |

---

## 6. Training Data Collection Script

For convenience, here's a script to help collect training data:

```python
# collect_training_data.py
import sounddevice as sd
import numpy as np
import wave
import os
from datetime import datetime

SAMPLE_RATE = 16000
DURATION = 2  # seconds per sample

def record_sample(speaker_name, sample_type="wake_word"):
    """Record a single audio sample"""
    print(f"Recording for {speaker_name} - {sample_type}")
    print("Speak now...")
    
    recording = sd.rec(
        int(DURATION * SAMPLE_RATE),
        samplerate=SAMPLE_RATE,
        channels=1,
        dtype=np.int16
    )
    sd.wait()
    
    # Save sample
    timestamp = datetime.now().strftime("%Y%m%d_%H%M%S")
    filename = f"dataset/{sample_type}/{speaker_name}_{timestamp}.wav"
    
    with wave.open(filename, 'w') as wf:
        wf.setnchannels(1)
        wf.setsampwidth(2)
        wf.setframerate(SAMPLE_RATE)
        wf.writeframes(recording.tobytes())
    
    print(f"Saved: {filename}")
    return filename

# Collect 100 samples per session
if __name__ == "__main__":
    speaker = input("Enter speaker name: ")
    
    for i in range(100):
        input(f"Sample {i+1}/100 - Press Enter to record...")
        record_sample(speaker, "ya_binti")
```

---

## 7. Conclusion

**Best Option for Binti2:** Train a custom TensorFlow Lite model using the micro_speech architecture.

**Why:**
1. Already compatible with your existing WakeWordDetector.kt
2. Small model size (~5MB or less)
3. Low latency (<500ms)
4. Works offline
5. Open source, no licensing fees
6. Good accuracy with proper training data

**Timeline:**
1. Data collection: 1-2 weeks (crowdsource recordings)
2. Model training: 1-2 days
3. Integration: Done (already implemented)
4. Testing: 1 week

**Alternative for Quick Implementation:** Use Vosk with grammar constraint - leverages your existing ASR model.

---

*Research compiled for Binti2 Voice Assistant*
*Author: Dr. Waleed Mandour*
*Date: January 2025*
