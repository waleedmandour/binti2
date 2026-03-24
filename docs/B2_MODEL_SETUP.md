# Binti2 Model Setup Guide

## Quick Start: Upload Models to Backblaze B2

### Step 1: Create Backblaze B2 Account

1. Go to [Backblaze B2](https://www.backblaze.com/b2/sign-up.html)
2. Sign up (Free tier: 10GB storage, 1GB/day downloads)
3. Verify your email

### Step 2: Create a Bucket

1. Go to [B2 Buckets](https://secure.backblaze.com/b2_buckets.htm)
2. Click "Create a Bucket"
3. Settings:
   - **Name**: `binti2-models` (or your preferred name)
   - **Type**: Public (models are not sensitive)
   - **Default Encryption**: Disable (for faster downloads)
4. Click "Create Bucket"

### Step 3: Get API Keys

1. Go to [App Keys](https://secure.backblaze.com/appkeys.htm)
2. Click "Add a New Application Key"
3. Settings:
   - **Name**: `binti2-upload`
   - **Allow access to**: All buckets (or select `binti2-models`)
   - **Type**: Read and Write
4. Copy **keyID** and **applicationKey** (save securely!)

### Step 4: Install B2 CLI

```bash
# macOS
brew install b2

# Linux
pip install b2

# Windows
pip install b2
```

### Step 5: Authenticate

```bash
b2 authorize-account <keyID> <applicationKey>
```

### Step 6: Prepare Model Files

Create the models directory structure:

```bash
mkdir -p models_to_upload/{wake,asr,nlu,tts,nlp}
```

#### Required Models:

| Model | File | Size | Source |
|-------|------|------|--------|
| Wake Word | `ya_binti_detector.tflite` | ~5MB | Train custom (see below) |
| ASR | `vosk-model-ar-mgb2.zip` | ~1.2GB | [Download](https://alphacephei.com/vosk/models) |
| NLU | `egybert_tiny_int8.onnx` | ~25MB | Train custom (see below) |
| TTS | `ar-eg-female.zip` | ~80MB | Train custom (see below) |
| Intent Map | `dilink_intent_map.json` | ~10KB | Already in project |

#### Quick Download - Vosk Arabic ASR:

```bash
cd models_to_upload
wget https://alphacephei.com/vosk/models/vosk-model-ar-mgb2-0.4.zip
mv vosk-model-ar-mgb2-0.4.zip vosk-model-ar-mgb2.zip
```

### Step 7: Upload Models

```bash
# Make script executable
chmod +x scripts/upload_models_to_b2.sh

# Set bucket name
export B2_BUCKET=binti2-models

# Run upload script
./scripts/upload_models_to_b2.sh
```

Or manually upload:

```bash
# Upload individual files
b2 upload-file binti2-models models_to_upload/ya_binti_detector.tflite wake/ya_binti_detector.tflite
b2 upload-file binti2-models models_to_upload/vosk-model-ar-mgb2.zip asr/vosk-model-ar-mgb2.zip
b2 upload-file binti2-models models_to_upload/egybert_tiny_int8.onnx nlu/egybert_tiny_int8.onnx
b2 upload-file binti2-models models_to_upload/ar-eg-female.zip tts/ar-eg-female.zip

# Upload manifest
b2 upload-file binti2-models manifest.json manifest.json
```

### Step 8: Update App Configuration

Edit `ModelManager.kt`:

```kotlin
private const val B2_BASE_URL = 
    "https://f001.backblazeb2.com/file/binti2-models"  // Your bucket URL
```

---

## Model Training Guides

### Wake Word Detector

Train a TFLite model for "يا بنتي":

```python
# 1. Collect training data
# - Record 1000+ samples of "يا بنتي"
# - Record 2000+ negative samples

# 2. Use TensorFlow Speech Commands
import tensorflow as tf
from tensorflow.keras import layers

model = tf.keras.Sequential([
    layers.Input(shape=(98, 40)),  # MFCC features
    layers.Conv1D(64, 3, activation='relu'),
    layers.MaxPooling1D(2),
    layers.Conv1D(128, 3, activation='relu'),
    layers.MaxPooling1D(2),
    layers.LSTM(128),
    layers.Dense(64, activation='relu'),
    layers.Dropout(0.3),
    layers.Dense(2, activation='softmax')  # [not_wake, wake]
])

# 3. Convert to TFLite
converter = tf.lite.TFLiteConverter.from_keras_model(model)
tflite_model = converter.convert()
with open('ya_binti_detector.tflite', 'wb') as f:
    f.write(tflite_model)
```

### Intent Classifier (EgyBERT)

Fine-tune BERT for Egyptian Arabic:

```python
from transformers import AutoModelForSequenceClassification, AutoTokenizer

# Load Arabic BERT
model = AutoModelForSequenceClassification.from_pretrained(
    "aubmindlab/bert-base-arabertv02",
    num_labels=10  # Number of intents
)
tokenizer = AutoTokenizer.from_pretrained("aubmindlab/bert-base-arabertv02")

# Fine-tune on your data
# ... training code ...

# Export to ONNX
from transformers import onnx
onnx.export(model, tokenizer, "egybert_tiny.onnx")
```

### TTS Voice

Train Egyptian female voice with Coqui:

```bash
# Install Coqui TTS
pip install TTS

# Train on Egyptian Arabic dataset
tts --text "مرحبا بكم في بينتي" --out_path output.wav

# For training custom voice:
tts --restore_path <checkpoint> --mode train --config_path config.json
```

---

## Alternative: Use Pre-trained Models

If you don't want to train models, use these alternatives:

### 1. Wake Word → Android SpeechRecognizer
Use Google's built-in hotword detection (requires internet)

### 2. ASR → Vosk Arabic Model
```bash
# Download pre-trained Arabic model
wget https://alphacephei.com/vosk/models/vosk-model-ar-mgb2-0.4.zip
```

### 3. NLU → Rule-based Matching
The app already has rule-based intent matching in `dilink_intent_map.json`

### 4. TTS → Android TTS
Use Android's built-in Arabic TTS (no download needed)

---

## Storage Costs

| Provider | Free Tier | Paid Tier |
|----------|-----------|-----------|
| Backblaze B2 | 10GB storage, 1GB/day | $0.005/GB storage, $0.01/GB download |
| GitHub Releases | 2GB limit per file | Free for public repos |
| Firebase Storage | 5GB | $0.026/GB storage |

**Estimated monthly cost for Binti2:**
- Storage: 1.4GB × $0.005 = $0.007/month
- Downloads: 10 users × 1.4GB × $0.01 = $0.14/month
- **Total: ~$0.15/month**

---

## Verification

After uploading, verify your models:

```bash
# Check file exists
curl -I "https://f001.backblazeb2.com/file/binti2-models/manifest.json"

# Download and test
curl -O "https://f001.backblazeb2.com/file/binti2-models/manifest.json"
cat manifest.json
```

---

## Security Notes

1. **Public bucket**: Models are public (not sensitive data)
2. **App signing**: Never upload keystore to B2
3. **API keys**: Keep B2 keys private, use GitHub Secrets for CI/CD
4. **Model integrity**: SHA256 checksums verified on download
