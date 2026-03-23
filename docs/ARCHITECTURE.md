# Binti Architecture

This document provides a detailed technical overview of Binti's architecture.

## System Overview

```
┌─────────────────────────────────────────────────────────────────┐
│                        Binti Application                         │
├─────────────────────────────────────────────────────────────────┤
│                                                                  │
│  ┌──────────────┐    ┌──────────────┐    ┌──────────────┐       │
│  │   Voice      │    │    NLP       │    │   Response   │       │
│  │   Layer      │───▶│    Core      │───▶│    Layer     │       │
│  └──────────────┘    └──────────────┘    └──────────────┘       │
│         │                   │                   │               │
│         ▼                   ▼                   ▼               │
│  ┌──────────────┐    ┌──────────────┐    ┌──────────────┐       │
│  │ Wake Word    │    │ Intent       │    │ Egyptian     │       │
│  │ Detector     │    │ Classifier   │    │ TTS          │       │
│  │ (TFLite)     │    │ (EgyBERT)    │    │              │       │
│  └──────────────┘    └──────────────┘    └──────────────┘       │
│                                                                  │
├─────────────────────────────────────────────────────────────────┤
│                      DiLink Integration Layer                    │
│  ┌──────────────┐    ┌──────────────┐    ┌──────────────┐       │
│  │ Accessibility│    │     ADB      │    │   Intent     │       │
│  │   Service    │    │   Bridge     │    │  Broadcasts  │       │
│  └──────────────┘    └──────────────┘    └──────────────┘       │
└─────────────────────────────────────────────────────────────────┘
                              │
                              ▼
                    ┌──────────────────┐
                    │   BYD DiLink     │
                    │   System         │
                    └──────────────────┘
```

## Component Details

### 1. Voice Layer

#### Wake Word Detector
- **Model**: Custom TFLite CNN (~5MB)
- **Trigger**: "يا بنتي" (Ya Binti)
- **Latency**: <10ms inference
- **Accuracy**: 95%+ precision, 90%+ recall
- **Implementation**: `WakeWordDetector.kt`

```kotlin
// Wake word detection pipeline
Audio Input (16kHz PCM) 
    → Preprocessing (noise reduction)
    → Feature Extraction (MFCCs)
    → CNN Classification
    → Confidence Threshold (>0.85)
    → Wake Event
```

#### ASR (Automatic Speech Recognition)
- **Model**: HuBERT-Egyptian ONNX (~150MB)
- **Features**: Log-mel spectrogram, 80 bins
- **Language**: Egyptian Arabic
- **Implementation**: `VoiceProcessor.kt`

```kotlin
// ASR pipeline
Audio Chunk
    → Noise Reduction
    → Feature Extraction (log-mel)
    → HuBERT Encoder
    → Token Decoding
    → Text Output
```

### 2. NLP Core

#### Text Normalizer
- **Purpose**: Normalize Egyptian dialect to standard form
- **Features**:
  - Egyptian → MSA mapping
  - Phonetic normalization
  - Number word conversion
  - Slang removal
- **Implementation**: `EgyptianArabicNormalizer.kt`

#### Intent Classifier
- **Model**: EgyBERT-tiny INT8 (~80MB)
- **Intents**: 12 core categories
- **Confidence Threshold**: 0.7
- **Implementation**: `IntentClassifier.kt`

```kotlin
// Intent classification pipeline
Transcribed Text
    → Text Normalization
    → Tokenization
    → EgyBERT Embedding
    → Classification Head
    → Intent + Confidence
    → Slot Extraction
```

### 3. DiLink Integration Layer

#### Accessibility Service
- **Purpose**: UI automation for DiLink control
- **Capabilities**:
  - Node traversal and interaction
  - Gesture simulation
  - Screen state monitoring
- **Implementation**: `DiLinkAccessibilityService.kt`

#### Command Executor
- **Methods**:
  1. **Accessibility**: UI automation (non-root)
  2. **ADB**: Shell commands (requires ADB)
  3. **Intent**: Broadcast intents
  4. **KeyEvent**: Media key injection
- **Implementation**: `DiLinkCommandExecutor.kt`

### 4. Response Layer

#### Egyptian TTS
- **Voice**: Egyptian Female
- **Prosody**: Warm, welcoming tone
- **Speed**: 0.9x default
- **Implementation**: `EgyptianTTS.kt`

```kotlin
// Response generation
Intent + Result
    → Template Selection
    → Egyptian Phrasing
    → Prosody Configuration
    → TTS Synthesis
    → Audio Output
```

## Data Flow

### Voice Command Flow

```
1. User says "يا بنتي"
   └─▶ WakeWordDetector.processAudioChunk() → true

2. Service wakes up, plays greeting
   └─▶ EgyptianTTS.speak(greeting)

3. User speaks command
   └─▶ VoiceProcessor.recordCommand() → audioData

4. ASR transcribes
   └─▶ VoiceProcessor.transcribe(audioData) → text

5. NLP classifies intent
   └─▶ IntentClassifier.classify(text) → Intent

6. Command executed
   └─▶ DiLinkCommandExecutor.execute(intent) → result

7. Response generated
   └─▶ EgyptianTTS.speak(response)
```

## Model Specifications

| Model | Format | Size | Quantization | Framework |
|-------|--------|------|--------------|-----------|
| Wake Word | TFLite | 5 MB | INT8 | TensorFlow Lite |
| ASR | ONNX | 150 MB | INT8 | ONNX Runtime |
| NLU | TFLite | 80 MB | INT8 | TensorFlow Lite |
| TTS Voice | Custom | 120 MB | N/A | System TTS |

## Performance Targets

| Metric | Target | Typical |
|--------|--------|---------|
| Wake Word Latency | <10ms | 5-8ms |
| ASR Latency | <500ms | 200-400ms |
| Intent Classification | <50ms | 20-40ms |
| TTS Latency | <100ms | 50-80ms |
| Memory Usage | <300MB | 200-250MB |
| CPU Usage (idle) | <5% | 2-3% |

## Security & Privacy

### On-Device Processing
- All voice processing happens locally
- No audio data leaves the device
- No cloud dependencies after initial setup

### Data Storage
- Models stored in app-private directory
- Preferences encrypted with AndroidKeyStore
- No PII collected or transmitted

### Permissions
| Permission | Purpose | Required |
|------------|---------|----------|
| RECORD_AUDIO | Voice recognition | Yes |
| SYSTEM_ALERT_WINDOW | Overlay UI | Yes |
| BIND_ACCESSIBILITY_SERVICE | DiLink control | Yes* |
| INTERNET | Model downloads | Initial setup |
| FOREGROUND_SERVICE | Background listening | Yes |
| WAKE_LOCK | Continuous listening | Yes |

*Accessibility is optional but required for full DiLink control

## Extensibility

### Adding New Intents

1. Add intent to `IntentClassifier.kt`:
```kotlin
"NEW_INTENT" -> Intent(
    action = "NEW_INTENT",
    confidence = confidence,
    parameters = slots
)
```

2. Add handler in `DiLinkCommandExecutor.kt`:
```kotlin
"NEW_INTENT" -> executeNewIntent(intent)
```

3. Add response in response generation:
```kotlin
intent.action == "NEW_INTENT" -> "Response text here"
```

### Adding New Languages

1. Create new normalizer: `XLanguageNormalizer.kt`
2. Train/fine-tune ASR model for language
3. Add language-specific TTS voice
4. Update intent classifier with language patterns

## Testing

### Unit Tests
- Located in `tests/` directory
- Run with `./gradlew test`

### Integration Tests
- Voice pipeline tests
- DiLink command tests
- Run with `./gradlew connectedAndroidTest`

### Performance Tests
- Latency benchmarks
- Memory profiling
- Battery impact analysis

## Deployment

### Build Variants
- **debug**: Development builds with logging
- **release**: Production builds with ProGuard

### Release Process
1. Update version in `build.gradle.kts`
2. Update `CHANGELOG.md`
3. Build release APK: `./gradlew assembleRelease`
4. Sign with release key
5. Upload to GitHub Releases
6. Models auto-uploaded to release assets

---

For more information, see:
- [README.md](README.md) - User documentation
- [CONTRIBUTING.md](CONTRIBUTING.md) - Development guidelines
- [DILINK_REVERSE_ENGINEERING.md](DILINK_REVERSE_ENGINEERING.md) - DiLink API details
