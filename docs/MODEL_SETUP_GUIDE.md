# Model Setup Guide for Binti2

This guide describes the AI models Binti uses, where they're hosted, and how
to manage them. **Updated for v2.6.0-beta** — the previous version of this
guide described Google Drive hosting and ~1.3 GB total; both were inaccurate.

---

## Overview

Binti downloads its AI models at runtime. The primary model host is
[alphacephei.com](https://alphacephei.com/vosk/models) (the official Vosk
model repository), which is highly available and has no bandwidth limits.
Optional neural TTS models are fetched from the
[sherpa-onnx releases](https://github.com/k2-fsa/sherpa-onnx/releases) on
GitHub.

**Total model footprint: ~318 MB** (required) + ~65 MB (optional neural TTS).

---

## Required Models

### Model Summary

| Model | File | Size | Host | Purpose |
|-------|------|------|------|---------|
| Arabic ASR + Wake Word | `vosk-model-ar-mgb2-0.4.zip` | ~318 MB | alphacephei.com | Egyptian Arabic speech recognition + wake word detection |
| Intent Patterns | `dilink_intent_map.json` | ~10 KB | Bundled in APK | Command pattern matching (rule-based) |
| Model Config | `model_config.json` | ~2 KB | Bundled in APK | Model download manifest |

**Total required download: ~318 MB**

> **Note:** The `egybert_tiny_int8.onnx` ML intent classifier and the
> `ya_binti_detector.tflite` wake-word model referenced in older versions of
> this guide have been **removed** in v2.6.0-beta. The intent classifier is
> now purely rule-based (sufficient for the Egyptian-Arabic vocabulary), and
> wake-word detection uses Vosk grammar matching (no separate TFLite model
> needed). This reduces the total download from ~1.3 GB to ~318 MB.

### Optional Models

| Model | File | Size | Host | Purpose |
|-------|------|------|------|---------|
| Neural Arabic TTS | `vits-mms-ara.tar.bz2` | ~65 MB | GitHub (k2-fsa/sherpa-onnx) | High-quality neural Arabic text-to-speech via Sherpa-ONNX VITS/MMS |

The neural TTS is optional — Binti falls back to Android system TTS or
Huawei HMS ML Kit TTS if it's not downloaded. It produces significantly
better Arabic voice quality than the fallback engines.

---

## How Model Download Works

Binti uses Android's `DownloadManager` (system-level, survives app restarts)
to download the Vosk model on first launch. The download flow:

1. **First launch** — Binti checks if the Vosk model exists in
   `context.filesDir/models/`.
2. **If missing** — Binti prompts the user to download (~318 MB, recommends
   WiFi). The user taps "Start download" and a system download notification
   appears.
3. **Download completes** — Binti extracts the ZIP to a temp directory,
   validates it (zip-slip path traversal guard), and atomically renames to
   the final `models/` directory.
4. **Model is ready** — The wake-word detector and ASR engine initialize
   using the extracted model files.

The download URL is hardcoded in `ModelManager.kt`:
```
https://alphacephei.com/vosk/models/vosk-model-ar-mgb2-0.4.zip
```

---

## Sherpa-ONNX Neural TTS (Optional, Manual Setup)

Sherpa-ONNX is not available on Maven Central. To enable neural Arabic TTS:

1. Download the JAR/AAR from the
   [sherpa-onnx releases page](https://github.com/k2-fsa/sherpa-onnx/releases).
2. Place it in `app/libs/`.
3. Download the Arabic VITS model:
   ```
   https://github.com/k2-fsa/sherpa-onnx/releases/download/tts-models/vits-mms-ara.tar.bz2
   ```
4. Extract and place `model.onnx` in `context.filesDir/voices/sherpa/`.
5. Select "Neural (Sherpa-ONNX)" in Settings → TTS Engine.

If the Sherpa-ONNX library is not present, Binti gracefully falls back to
Android TTS. No build error occurs.

---

## Local Model Setup (USB / SD Card)

If the head unit has no internet connection, models can be transferred
manually:

1. Download `vosk-model-ar-mgb2-0.4.zip` on a computer from
   [alphacephei.com](https://alphacephei.com/vosk/models/vosk-model-ar-mgb2-0.4.zip).
2. Copy the ZIP to a USB drive.
3. Insert the USB drive into the BYD head unit.
4. Use a file manager to copy the ZIP to
   `Android/data/com.binti.dilink/files/models/` (or
   `.enthusiast` for the enthusiast flavor).
5. Launch Binti — it will detect the ZIP and extract it automatically.

---

## Troubleshooting

### Download fails

- Check WiFi connection (Binti recommends WiFi for the ~318 MB download).
- Check that `alphacephei.com` is reachable from the head unit's browser.
- If the download stalls at 100%, the extraction may have failed — delete
  the `models/` directory and retry.
- The `network_security_config.xml` whitelists `alphacephei.com` for HTTPS
  downloads. If you're using a custom DNS or proxy, ensure the domain
  resolves correctly.

### Model not detected after download

- Verify the model directory exists:
  `Android/data/com.binti.dilink/files/models/vosk-model-ar-mgb2-0.4/`
- The directory should contain `conf`, `am`, `graph`, `ivector`, and other
  Vosk model subdirectories.
- If the directory is empty, the ZIP extraction failed — delete and retry.

### Wake word not detected

- The Vosk grammar includes "يا بنتي", "يابنتي", "يا بنتى", "يابنتى"
  (with/without space, different alef/maqsura variants).
- Speak clearly — the MGB2 model is trained on Egyptian Arabic broadcast
  data, so Egyptian pronunciation works best.
- Check the microphone permission is granted.
- Check that no other app is holding the microphone (e.g., a phone call).

### Intent classification returns UNKNOWN

- The rule-based classifier requires keywords from `dilink_intent_map.json`.
- If you're getting "UNKNOWN" for a valid command, the command may not be
  in the intent map. Check the Sample Commands list in the app or
  `app/src/main/assets/commands/dilink_intent_map.json` for the full list.
- Arabic diacritics (Tashkeel) are stripped before matching, so vocalized
  and unvocalized commands both work.

---

## Model Hosting for Forks

If you fork Binti and want to host models yourself:

1. Host the Vosk model on your own server or CDN.
2. Update `ModelManager.VOSK_MODEL_URL` in
   `app/src/main/java/com/binti/dilink/utils/ModelManager.kt`.
3. Add your domain to `app/src/main/res/xml/network_security_config.xml`
   (cleartext traffic is disabled by default; HTTPS is required).

The `model_config.json` asset (bundled in the APK) describes all model
downloads and is the single source of truth for model metadata.

> **Integrity (P1 fix):** the canonical download source is alphacephei.com. Expected SHA-256 of `vosk-model-ar-mgb2-0.4.zip`: `357469ae1bb4d7a3810c9cd6b86d33bc135898dfc134e6df8bc2ddd28c5fe77a`. ModelManager verifies this hash before extraction; do not download the model from unverified mirrors.
