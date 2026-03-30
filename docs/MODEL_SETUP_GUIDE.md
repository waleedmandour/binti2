# Google Drive Model Setup Guide for Binti2

This guide provides step-by-step instructions for setting up AI models on Google Drive for the Binti voice assistant.

## Table of Contents

- [Overview](#overview)
- [Required Models](#required-models)
- [Google Drive Setup](#google-drive-setup)
- [Uploading Models](#uploading-models)
- [Getting Direct Download Links](#getting-direct-download-links)
- [Configuring Binti App](#configuring-binti-app)
- [Local Model Setup (USB/SD Card)](#local-model-setup-usb-sd-card)
- [Troubleshooting](#troubleshooting)

---

## Overview

Binti uses Google Drive for hosting AI models because:
- ✅ **Free storage**: 15GB free tier (plenty for ~1.5GB of models)
- ✅ **Reliable downloads**: Google's infrastructure is highly available
- ✅ **No bandwidth limits**: Unlike some free hosting services
- ✅ **Easy updates**: Replace files without changing URLs
- ✅ **User self-hosting**: Users can host their own models

---

## Required Models

### Model Summary

| Model | File | Size | Required | Purpose |
|-------|------|------|----------|---------|
| Arabic ASR | `vosk-model-ar-mgb2` | ~1.2 GB | ✅ Yes | Speech recognition + wake word |
| Intent Classifier | `egybert_tiny_int8.onnx` | ~25 MB | ✅ Yes | Command understanding |
| Intent Patterns | `dilink_intent_map.json` | ~10 KB | ✅ Yes | Command pattern matching |

**Total size: ~1.3 GB**

### Where to Get Models

#### 1. Vosk Arabic ASR Model

**Official Download:**
```
https://alphacephei.com/vosk/models
```

Look for: `vosk-model-ar-mgb2-0.4.zip` (or latest version)

**Direct download:**
```bash
# Download the model
wget https://alphacephei.com/vosk/models/vosk-model-ar-mgb2-0.4.zip

# Extract the ZIP
unzip vosk-model-ar-mgb2-0.4.zip

# The extracted folder contains the model
# Folder structure:
# vosk-model-ar-mgb2-0.4/
# ├── am/
# ├── conf/
# ├── graph/
# └── ivector/
```

**Important:** The model needs to stay in its extracted folder structure. Don't flatten it.

#### 2. Intent Classifier Model

This is a custom fine-tuned model. If you don't have it:
- The app will fall back to rule-based intent matching
- You can train your own (see [Training Guide](#training-custom-models))

#### 3. Intent Patterns

This file is included in the project:
```
app/src/main/assets/commands/dilink_intent_map.json
```

---

## Google Drive Setup

### Step 1: Create a Google Account (if needed)

If you don't have one:
1. Go to [accounts.google.com](https://accounts.google.com)
2. Click "Create account"
3. Follow the setup process

### Step 2: Create a Models Folder

1. Go to [drive.google.com](https://drive.google.com)
2. Click "+ New" → "New folder"
3. Name it "Binti Models" (or any name you prefer)
4. Click "Create"

### Step 3: Create Folder Structure

Inside your "Binti Models" folder, create subfolders:

```
Binti Models/
├── asr/           # For Vosk model
├── nlu/           # For intent classifier
└── nlp/           # For intent patterns
```

---

## Uploading Models

### Option A: Upload via Web Interface

#### Upload Vosk ASR Model

The Vosk model is too large to upload as a regular file. You have two options:

**Option 1: Upload as ZIP (Recommended)**
```bash
# Keep the model as ZIP
# File: vosk-model-ar-mgb2.zip (~1.2 GB)

# Upload:
1. Open your "Binti Models/asr" folder in Google Drive
2. Click "+ New" → "File upload"
3. Select vosk-model-ar-mgb2.zip
4. Wait for upload (may take 10-30 minutes depending on speed)
```

**Option 2: Upload Extracted Folder**
```bash
# Using Google Drive desktop app or browser
1. Upload the entire vosk-model-ar-mgb2 folder
2. Google Drive will create a folder structure
3. Note: This is more complex to download programmatically
```

#### Upload Intent Classifier

```
1. Open your "Binti Models/nlu" folder
2. Upload egybert_tiny_int8.onnx
```

#### Upload Intent Patterns

```
1. Open your "Binti Models/nlp" folder
2. Upload dilink_intent_map.json
```

### Option B: Upload via rclone (Advanced)

For automated or command-line uploads:

```bash
# Install rclone
# macOS: brew install rclone
# Linux: curl https://rclone.org/install.sh | sudo bash

# Configure rclone with Google Drive
rclone config

# Upload models
rclone copy ./vosk-model-ar-mgb2.zip drive:"Binti Models/asr/"
rclone copy ./egybert_tiny_int8.onnx drive:"Binti Models/nlu/"
rclone copy ./dilink_intent_map.json drive:"Binti Models/nlp/"
```

---

## Getting Direct Download Links

### Understanding Google Drive URLs

Google Drive has several URL formats:

| URL Type | Format | Use Case |
|----------|--------|----------|
| View URL | `drive.google.com/file/d/FILE_ID/view` | Viewing in browser |
| Preview URL | `drive.google.com/file/d/FILE_ID/preview` | Preview in browser |
| Direct Download | `drive.google.com/uc?export=download&id=FILE_ID` | Programmatic download |

### Step 1: Get File ID

**Method 1: From URL**
1. Right-click on your uploaded file in Google Drive
2. Select "Get link" or "Share"
3. Copy the link - it looks like:
   ```
   https://drive.google.com/file/d/1AbCdEfGhIjKlMnOpQrStUvWxYz/view
   ```
4. The File ID is: `1AbCdEfGhIjKlMnOpQrStUvWxYz`

**Method 2: From File Details**
1. Right-click on file → "File information" → "Details"
2. The ID is shown in the details panel

### Step 2: Create Direct Download URL

Replace `FILE_ID` in this URL:
```
https://drive.google.com/uc?export=download&id=FILE_ID
```

**Example:**
```
File ID: 1AbCdEfGhIjKlMnOpQrStUvWxYz

Direct Download URL:
https://drive.google.com/uc?export=download&id=1AbCdEfGhIjKlMnOpQrStUvWxYz
```

### Step 3: Handle Large File Virus Scan Warning

Google Drive shows a virus scan warning for files larger than 100MB. Binti handles this automatically, but if you're testing manually:

```bash
# First request returns HTML with confirm token
curl -L "https://drive.google.com/uc?export=download&id=FILE_ID" -o response.html

# Extract confirm token from HTML (look for "confirm=XXXXX")
# Then add to URL:
curl -L "https://drive.google.com/uc?export=download&id=FILE_ID&confirm=XXXXX" -o model.zip
```

### Sharing Settings

For the app to download files:

1. **Public sharing** (easiest):
   - Right-click file → "Share"
   - Change to "Anyone with the link"
   - Role: "Viewer"

2. **Restricted sharing**:
   - Only people with access can download
   - Requires Google account authentication
   - Not recommended for public distribution

---

## Configuring Binti App

### Method 1: Using Default Developer URLs

The app comes pre-configured with developer-hosted model URLs. Simply:
1. Open Binti app
2. Go to Settings → Models
3. Tap "Download Models"
4. Wait for download to complete

### Method 2: Using Your Own Google Drive

1. **Get your Google Drive folder ID:**
   - Open your "Binti Models" folder
   - URL looks like: `https://drive.google.com/drive/folders/FOLDER_ID`
   - Copy `FOLDER_ID`

2. **Configure in app:**
   - Open Binti app
   - Go to Settings → Model Sources
   - Tap "Add Custom Source"
   - Enter your folder ID
   - Tap "Save"

3. **Download models:**
   - The app will use your custom URLs

### Method 3: Edit Source Code

For developers, update `ModelManager.kt`:

```kotlin
// Replace these with your Google Drive file IDs
private const val ASR_MODEL_FILE_ID = "YOUR_ASR_FILE_ID_HERE"
private const val NLU_MODEL_FILE_ID = "YOUR_NLU_FILE_ID_HERE"
private const val INTENT_MAP_FILE_ID = "YOUR_INTENT_MAP_FILE_ID_HERE"
```

---

## Local Model Setup (USB/SD Card)

For offline installation or when Google Drive is unavailable.

### Step 1: Prepare USB Drive

Format as FAT32 or exFAT (for larger files).

### Step 2: Create Directory Structure

```
USB_DRIVE/
└── binti_models/
    ├── asr/
    │   └── vosk-model-ar-mgb2/     # Extracted model folder
    │       ├── am/
    │       ├── conf/
    │       ├── graph/
    │       └── ivector/
    ├── nlu/
    │   └── egybert_tiny_int8.onnx
    └── nlp/
        └── dilink_intent_map.json
```

### Step 3: Copy Models to USB

```bash
# On your computer
# Copy Vosk model (extracted)
cp -r vosk-model-ar-mgb2 /path/to/usb/binti_models/asr/

# Copy other models
cp egybert_tiny_int8.onnx /path/to/usb/binti_models/nlu/
cp dilink_intent_map.json /path/to/usb/binti_models/nlp/
```

### Step 4: Load from USB on BYD DiLink

1. Insert USB drive into your BYD vehicle
2. Open Binti app
3. Go to Settings → Model Sources
4. Tap "Use Local Path"
5. Navigate to the binti_models folder
6. Tap "Select"
7. Models will be copied to internal storage

### Step 5: Verify Models

After copying:
1. Go to Settings → Models
2. Check that all models show as "Ready"
3. Test voice recognition

---

## File Format Requirements

### Vosk ASR Model

| Property | Requirement |
|----------|-------------|
| Format | Extracted folder or ZIP |
| Sample Rate | 16kHz |
| Language | Arabic (Modern Standard) |
| Structure | Must contain: am/, conf/, graph/, ivector/ |

### Intent Classifier (ONNX)

| Property | Requirement |
|----------|-------------|
| Format | ONNX (.onnx) |
| Input | Tokenized Arabic text |
| Output | Intent probabilities |
| Quantization | int8 (recommended for mobile) |

### Intent Patterns (JSON)

| Property | Requirement |
|----------|-------------|
| Format | JSON |
| Encoding | UTF-8 |
| Structure | See dilink_intent_map.json |

---

## File Size Expectations

### Download Times

| Connection | Vosk Model (1.2 GB) | Intent Model (25 MB) |
|------------|---------------------|----------------------|
| WiFi (50 Mbps) | ~3-5 minutes | ~5 seconds |
| 4G LTE (10 Mbps) | ~15-20 minutes | ~20 seconds |
| 3G (1 Mbps) | ~3 hours | ~3 minutes |

### Storage Requirements

| Item | Size |
|------|------|
| Models (downloaded) | ~1.3 GB |
| App installation | ~50 MB |
| Cache and data | ~100 MB |
| **Total needed** | **~1.5 GB** |

---

## Training Custom Models

### Intent Classifier Training

```python
# Using Hugging Face Transformers
from transformers import AutoModelForSequenceClassification, AutoTokenizer, TrainingArguments, Trainer

# Load Arabic BERT base model
model = AutoModelForSequenceClassification.from_pretrained(
    "aubmindlab/bert-base-arabertv02",
    num_labels=10  # Number of intents
)
tokenizer = AutoTokenizer.from_pretrained("aubmindlab/bert-base-arabertv02")

# Prepare your training data
# Format: {"text": "شغلي التكييف", "label": 0}

# Train the model
training_args = TrainingArguments(
    output_dir="./results",
    num_train_epochs=10,
    per_device_train_batch_size=16,
)

trainer = Trainer(
    model=model,
    args=training_args,
    train_dataset=train_dataset,
    eval_dataset=eval_dataset,
)

trainer.train()

# Export to ONNX
from transformers import onnx
onnx.export(model, tokenizer, "egybert_tiny.onnx")
```

### Adding New Intent Patterns

Edit `dilink_intent_map.json`:

```json
{
  "intents": {
    "AC_CONTROL": {
      "patterns": [
        "شغلي التكييف",
        "تشغيل التكييف",
        "افتحي المكيف",
        "YOUR NEW PATTERN HERE"
      ],
      "response": "تمام، شغلت التكييف"
    }
  }
}
```

---

## Troubleshooting

### Download Fails with Virus Scan Warning

**Solution:** The app handles this automatically, but if it fails:
1. Use a different browser to test the download link
2. Make sure the file is publicly shared
3. Try downloading manually and using USB method

### Model Corrupted Error

**Symptoms:**
- "Failed to load model" error
- App crashes on voice recognition

**Solutions:**
1. Delete models and re-download
2. Check SHA256 checksum if available
3. Try USB installation method

### Google Drive Quota Exceeded

**Symptoms:**
- "Download quota exceeded" error
- Downloads fail after some time

**Solutions:**
1. Wait 24 hours for quota reset
2. Use a different Google account
3. Host models on your own server
4. Use USB installation method

### File Not Found

**Symptoms:**
- "404 Not Found" error
- "File doesn't exist" error

**Solutions:**
1. Check file sharing settings
2. Verify file ID is correct
3. Make sure file wasn't deleted

### Slow Download Speeds

**Solutions:**
1. Use WiFi instead of mobile data
2. Download during off-peak hours
3. Use USB installation for large models
4. Use a mirror server

---

## Model URL Template

For quick reference, here's the URL template for Google Drive:

```
# Direct Download URL
https://drive.google.com/uc?export=download&id=YOUR_FILE_ID

# For large files (with confirm token)
https://drive.google.com/uc?export=download&id=YOUR_FILE_ID&confirm=CONFIRM_TOKEN

# Folder URL
https://drive.google.com/drive/folders/YOUR_FOLDER_ID
```

---

## Summary Checklist

- [ ] Download Vosk Arabic model from alphacephei.com
- [ ] Create "Binti Models" folder in Google Drive
- [ ] Upload model files to Google Drive
- [ ] Set sharing to "Anyone with the link"
- [ ] Get file IDs from URLs
- [ ] Configure app with file IDs or use USB method
- [ ] Verify models are loaded correctly in app
- [ ] Test voice recognition

---

*Last Updated: January 2025*  
*For Binti2 Voice Assistant*
