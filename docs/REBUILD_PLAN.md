# Binti Rebuild Plan — post-review fix pass

**Date:** 2026-10-14
**Scope:** Full rebuild pass following the independent code review + independent verification of every load-bearing claim (against the repo, DiPlay's GPL-3.0 source, and the DiLink 3 community research).
**Status of this pass:** all P0 code fixes + P1 hygiene fixes are implemented in this branch. Items requiring the physical car are listed in §7 and intentionally left open.

---

## 1. What was wrong (verified findings)

| # | Finding | Severity | Verified against |
|---|---------|----------|------------------|
| 1 | Wake-word events dropped: blocking `startListening()` ran before `collect` on a `replay=0` SharedFlow | P0 — headline feature dead | `BintiService.kt`, `WakeWordDetectorVosk.kt` |
| 2 | Speed announced 3.6× too high: raw autoservice value is km/h (DiPlay vector `0x42100000` → 36 km/h), Binti multiplied by 3.6 | P0 — wrong data | DiPlay `BydWheelSpeedTest` |
| 3 | Missing DiLink 3 speed fid `-1176502256` (only SDK 1.0.5 address shipped); no range check, no probing | P0 — no speed on DiLink 3 | DiPlay `BydVehicleCapabilityProbe.kt` |
| 4 | Repository split: `BintiDiLinkBridge` published Tier 2 data into a private `VehicleDataRepository()` nobody read; `readAndPublishVehicleSnapshot()` was never called | P0 — Tier 2 dead code | `BintiDiLinkBridge.kt`, executor call sites |
| 5 | AC blind toggle ("شغل" on a running AC switched it off); fan phrases unreachable (`زود` branch first); unrecognized AC phrases defaulted to ON; clamp 16–32 instead of 17–33 °C | P0 — wrong control | `DiLinkCommandExecutor.kt` |
| 6 | 318 MB Vosk model loaded twice (wake word + command ASR) | P1 — memory | `VoiceProcessor.kt:66`, `WakeWordDetectorVosk.kt:97` |
| 7 | `model_config.json` sha256 empty; three docs named three different download sources | P1 — integrity | `model_config.json`, README, MODEL_SETUP_GUIDE |
| 8 | `binti://` exported trampoline accepted triggers from any installed app | P1 — security | `BintiAutomationActivity.kt`, manifest |
| 9 | CI trigger YAML corrupted (`branches: ain, master, develop]`); unit tests ran store flavor only | P1 — CI | `.github/workflows/ci.yml` |
| 10 | README claimed "no root or ADB required" while the repo ships `enable-accessibility-adb.sh`; `YuanPlus2023` flagged `isConfirmed = true` with zero hardware behind it | P1 — honesty | README, `BYDModels.kt` |
| 11 | 9 files ported from GPL-3.0 DiPlay carried Apache-2.0 headers and no attribution in NOTICE | P1 — legal | DiPlay LICENSE (fetched live), NOTICE |
| 12 | `FirmwareGate` only opens on the DiLink 5.1 / Android-13 fingerprint → privileged path dead on Egyptian DiLink 3 units | Known limitation | `FirmwareGate.kt` |

---

## 2. P0 fixes implemented in this pass

### 2.1 Wake-word loop (`BintiService.kt`)
- `startWakeWordDetection()` restructured into a supervised loop:
  - a collector launched with `CoroutineStart.UNDISPATCHED` subscribes **before** the audio loop starts, so `tryEmit` can never fire with zero subscribers;
  - after each command session the loop re-arms automatically;
  - while `isListening` is true the loop waits, so the command session owns the microphone exclusively (two concurrent `AudioRecord`s steal frames).
- `onWakeWordDetected()`:
  - stops the wake-word recorder before `listenAndTranscribe()` opens its own mic;
  - guards against external triggers arriving before components are initialised (all fields are `lateinit`);
  - `onDestroy()` now releases the native detector + processor (mic handle was previously left open until GC).

### 2.2 Speed pipeline (`BydVehicleReader.kt`, `BydVehicleFields.kt`, new `BydSpeedDecoder.kt`)
- New main-sourceSet `BydSpeedDecoder`: pure km/h decode + 0–300 sanity range. Unit-tested with DiPlay's recorded vectors (`BydSpeedDecoderTest`).
- `readSpeedKmh()` no longer multiplies by 3.6.
- Added the DiLink 3 / controller-13 speed fid `-1176502256` as a fallback candidate; the first address returning a sane reading is cached (`invalidateSpeedProbe()` exposed for diagnostics).
- Battery SoC now range-checked (0–100) instead of trusting garbage from wrong-firmware reads.
- ⚠ **Open caveat:** while parked, both speed addresses read 0.0, so the probe cannot pick a firmware generation at standstill — verify during the first drive (§7).

### 2.3 Tier 2 data path (`VehicleDataRepository.kt`, `BintiDiLinkBridge.kt`, `ControlBridge.kt`, `DiLinkCommandExecutor.kt`)
- `VehicleDataRepository.getShared()` — process-wide singleton; accessibility service, bridge, Hilt module and executor now share ONE instance (public constructor kept for tests).
- `BintiDiLinkBridge.init()` publishes a startup snapshot; `readAndPublishVehicleSnapshot()` is no longer dead code.
- New `ControlBridge.refreshVehicleData()` contract; `DirectControlBridge` implements it by re-running the ADB reads. `getBatteryInfo()` / `getRangeInfo()` invoke it before degrading to the head-unit battery / "open the app" fallback.

### 2.4 AC command layer (`DiLinkCommandExecutor.kt`, new `BydAcBridge.kt`)
- **Routing order fixed:** compound fan phrases (`سرعة` + `زود/قلل`) are tested before the bare temperature phrases.
- **Idempotent power:** new read-only `BydAcBridge` (`android.hardware.bydauto.ac.BYDAutoAcDevice` via fail-soft reflection) reads the real AC state; "شغل" on a running AC now answers "المكيف شغال بالفعل" instead of toggling it off. Post-click read-back verification when the API is present.
- **No default-to-on:** unrecognized AC phrases return an Arabic clarification with examples.
- **Clamp 17–33 °C** per DiLink's documented range.
- `getTemperatureInfo()` prefers the BYD framework outside-temperature sensor.

### 2.5 Honesty (`BYDModels.kt`, README)
- Removed the false "IDs verified" marker and `isConfirmed = true` — every `com.byd.auto.*` package and view ID remains **unverified** until `pm list packages` is run on the car (§7).

---

## 3. P1 fixes implemented in this pass

- **Vosk model dedup:** new `VoskModelProvider` loads the 318 MB model once; wake-word and command ASR share it (each keeps its own cheap `Recognizer`). Neither consumer closes the shared model.
- **sha256 integrity:** real hash of `vosk-model-ar-mgb2-0.4.zip` computed and recorded:
  `357469ae1bb4d7a3810c9cd6b86d33bc135898dfc134e6df8bc2ddd28c5fe77a`
  `ModelManager` now verifies downloads before extraction and rejects mismatches. All three docs now name alphacephei.com as canonical (GitHub release demoted to "unofficial mirror").
- **`binti://` guard:** same-signature callers, Binti itself, and referrer-less ADB/system starts pass; every other caller requires the user to set `allow_external_automation` in `binti_prefs` (documented in the activity KDoc).
- **CI:** trigger branches repaired to `[main, master, develop]` / `[main, master]`; `testEnthusiastDebugUnitTest` added alongside the store-flavor job.
- **Licensing:** NOTICE gains a full DiPlay/GPL-3.0 section listing all 9 ported files and the three legal resolution options. **Until one is chosen, the enthusiast flavor must be treated as GPL-encumbered and not distributed** (§6).
- **Arabic-first UI:** default `values/strings.xml` replaced with the clean (non-tashkeel) Arabic set; English moves fully to `values-en/`; the two hardcoded English layout labels ("System Maintenance", "Accessibility Diagnostics") localized. `supportsRtl` already enabled.

---

## 4. Launch greeting (new feature)

- **Bundled clip:** `res/raw/launch_greeting.wav` — "أهلا بيك", female voice, 24 kHz mono, 0.9 s, generated via neural TTS and ASR-verified during generation.
- **Playback:** new `LaunchGreeting` object — plays immediately in `MainActivity.onCreate()` without waiting for any TTS engine warm-up; requests transient audio focus (may-duck) via `AudioFocusRequest`; self-guards against replay on rotation (once per process); releases the player on completion.
- **Fallback:** if the clip is missing or playback fails, `EgyptianTTS.speak(R.string.launch_greeting)` speaks the same phrase.
- **Note:** the bundled voice is neural but should be auditioned by a human ear; replace the WAV with a studio recording if desired — the code only depends on the resource existing.

---

## 5. Architecture after this pass

```
Voice:  WakeWord(Vosk grammar, shared Model) ──┐
        VoiceProcessor(Vosk, shared Model) ────┤
                                               ▼
                                    BintiService (supervised wake loop,
                                    exclusive mic ownership per session)
                                               │
                                    IntentClassifier → DiLinkCommandExecutor
                                               │
        ┌──────────────────────────────────────┼──────────────────────────────┐
        ▼                                      ▼                              ▼
Accessibility scraping          BydAcBridge (READ-only           ControlLoader bridge
(fallback; IDs still            BYD framework API)               → refreshVehicleData()
unverified)                     isOn / outsideTemp               → BintiDiLinkBridge
        │                                      │                              │
        └──────────────► VehicleDataRepository.getShared() ◄──────────────────┘
                                  (single source of truth)
```

Deliberately **not** done yet (per the read-first decision):
- AC WRITE via the BYD API (`start/stop/setAcTemperature`) exists as private scaffold in `BydAcBridge` but is not wired. Flip to production only after §7.3 verifies the method signatures on the car.
- `DirectControlBridge`'s guessed AIDL stubs/method names remain as-is; they are fail-soft but should be treated as experimental.

---

## 6. Licensing status

- NOTICE now documents the DiPlay (GPL-3.0) provenance of 9 enthusiast-flavor files.
- **Decision required before distribution** (any one of):
  1. Relicense the app (or at minimum the enthusiast flavor) GPL-3.0;
  2. Written permission from the DiPlay author;
  3. Clean-room reimplementation from the AOSP ADB protocol docs.
- Until then: the **store** flavor is distributable; the **enthusiast** flavor is for personal use only.

---

## 7. Verification checklist on the car (10 minutes — decides everything)

1. `adb shell pm list packages | grep byd` — compare against `BYDModels.Packages`; record the real `com.byd.*` set (expect `com.byd.airconditioning`, `com.byd.bluetoothcall`, `com.byd.carsettings`, `com.byd.mediacenter`, providers…).
2. `getprop sys.byd.countrycode` — Egypt (20?) route for sideloading; test USB folder `Third Party Apps 20` vs WiFi ADB.
3. Accessibility: try Settings → Accessibility; if blocked, run `scripts/enable-accessibility-adb.sh`.
4. `adb shell pm list permissions | grep -i bydauto` — record exact permission strings; align `BydAcBridge` manifest declarations.
5. Read-only probe: with the enthusiast flavor installed, confirm `BydAcBridge.isOn()` logs a resolved device (logcat: `BydAcBridge`).
6. **While driving:** confirm speed answers (wake word → "إيه السرعة؟") read plausibly (~dashboard value), proving the probed address; watch logcat `Speed address probed OK`.
7. AC write-path trial (only when ready to enable SETs): call `start(0)`/`setAcTemperature(1, 22, 1, 1)` via a test hook and compare with the UI state.
8. Launch greeting: cold-start the app → "أهلا بيك" should play instantly; rotate the screen → must not replay.

---

## 8. Test additions in this pass

- `app/src/test/.../BydSpeedDecoderTest.kt` — 10 tests locking the km/h decode semantics with DiPlay's recorded vectors (runs in **both** flavors' CI jobs now).
- Recommended next (needs instrumentation or refactoring): wake-loop integration test with a fake detector flow; executor routing table test; `BydParcel` decode vectors.
