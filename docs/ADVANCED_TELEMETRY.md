# Advanced Telemetry (Tier 2) — Design & Attribution

> **Status:** experimental, opt-in, enthusiast flavor only.
> **License:** MIT (this project). Reference research used: MIT (wheregoes/byd-apps).
> **No affiliation with BYD Auto Co., Ltd.**

This document describes the optional Advanced Telemetry module in Binti's
`enthusiast` product flavor: what it reads, how it reads it, what it
explicitly refuses to read, and how the build is structured so the AppGallery
(`store`) flavor never contains any of this code.

## 1. Scope and safety boundaries

**Allowed (Tier 2 reads only these comfort / info fields):**

| Field                         | Source service prefix        | Method (best-effort)              |
|-------------------------------|------------------------------|-----------------------------------|
| Tyre pressure (4 corners)     | `bydauto.tyre.*`             | `getTyrePressure(corner)`         |
| HV bus voltage                | `bydauto.hvbus.*`            | `getHvBusVoltage()`               |
| 12V auxiliary battery voltage | `bydauto.auxbattery.*`       | `getAuxVoltage()`                 |
| Traction motor RPM            | `bydauto.motor.*`            | `getMotorRpm()`                   |
| Battery cell voltage max/min  | `bydauto.battery.*`          | `getMaxCellVoltageMv()` / `getMinCellVoltageMv()` |
| Cell voltage spread           | derived                      | `max - min`                       |

**Hard-blocked (never read, never written):**

- Steering / power steering
- Braking, ABS, ESP, regenerative-brake commands
- Propulsion torque commands
- Airbags / SRS
- ADAS / driving-assist (cruise control, lane keep, AEB, etc.)
- Any service whose name contains a blocked substring (`steer`, `brake`,
  `abs`, `esp`, `torque`, `airbag`, `srs`, `adas`, `cruise`, `control`,
  `actuator`, `drive`).

The block list is enforced in `AdvancedTelemetryProbe.BLOCKED_SERVICE_SUBSTRINGS`
and is the primary safety boundary. It is a deny-list — anything not on the
allow list of service prefixes (`ALLOWED_SERVICE_PREFIXES`) is also rejected
by default.

## 2. Architecture

```
                                  main source (shared by both flavors)
                                  ────────────────────────────────────
   DiLinkAccessibilityService  ──publishAccessibility──▶  VehicleDataRepository
        (Tier 1)                                                 │
                                                                 │ StateFlow<VehicleSnapshot>
                                                                 ▼
                                              ┌──────────────────────────────┐
                                              │  VehicleSnapshot             │
                                              │  (Tier 1 + Tier 2 fields)    │
                                              └──────────────────────────────┘
                                                                 ▲
                                                                 │ mergeAdvanced()
                                                                 │
   AdvancedTelemetryLoader.get().probe()  ───────────────────────┘
        ▲
        │ register(bridge)
        │
   ┌────┴─────────────────────────────┐
   │                                  │
   │   store flavor                   │   enthusiast flavor
   │   ────────────                   │   ──────────────────
   │   NoOpAdvancedTelemetryBridge    │   AdvancedTelemetryProbe
   │   (always returns empty)         │   (reflection against
   │                                  │    android.hardware.bydauto.*)
   └──────────────────────────────────┘
```

Main source never imports the enthusiast probe class. The bridge contract
is the only boundary, and the loader is the only registry. The store flavor's
`TelemetryInitializer.init()` is intentionally a no-op — it does NOT call
`AdvancedTelemetryLoader.register(...)`, so the no-op default stays in place.

## 3. Consent model

Consent is managed by `TelemetryConsentManager` (in main source, so the
developer debug screen and voice response formatter can query it without
importing enthusiast classes).

| State      | Meaning                                                         |
|------------|-----------------------------------------------------------------|
| NOT_SHOWN  | User has never been asked. Probe is a no-op.                    |
| GRANTED    | User accepted the warning AND the consent version matches.      |
| REVOKED    | User explicitly turned the toggle off. Probe is a no-op.        |

The consent **version** is bumped (`TelemetryConsentManager.CONSENT_VERSION`)
whenever the warning text or the list of probed services changes materially.
A bumped version invalidates prior grants — the user is re-prompted the next
time they open the onboarding screen.

Reversibility: the onboarding screen's toggle button reads "Disable
Advanced Telemetry" once consent is GRANTED, and clicking it calls
`consent.revoke()` plus `vehicleDataRepository.reset()` to wipe the Tier 2
fields from the live snapshot. Tier 1 fields repopulate on the next
Accessibility scrape.

## 4. Tested models and firmware

> ⚠️ This section is intentionally left mostly blank. The probe is **best-effort**:
> a firmware update that renames a service or a method produces `null` for
> that field, not a crash. Verified-on-device entries below are user-contributed
> and should be appended as new models/firmware are confirmed.

| BYD Model      | DiLink firmware | Tested by        | Status                    |
|----------------|-----------------|------------------|---------------------------|
| Yuan Plus 2023 | (unverified)    | —                | Probe compiles, runtime TBD |
| Atto 3         | (unverified)    | —                | Probe compiles, runtime TBD |
| Dolphin        | (unverified)    | —                | Probe compiles, runtime TBD |
| Seal           | (unverified)    | —                | Probe compiles, runtime TBD |

If you have tested the probe on your vehicle, please open an issue with the
model, DiLink firmware version (Settings → About → Software version), and
which fields returned non-null values, so this table can be updated.

## 5. Attribution

The enumeration approach used by `AdvancedTelemetryProbe` — walking
`ServiceManager.listServices()`, fetching the `IBinder` for each
`bydauto.*` service, creating a proxy via `asInterface(IBinder)`, and
enumerating its methods — is modelled on the public MIT-licensed
**BYD Probe** diagnostic tool published at:

> https://github.com/wheregoes/byd-apps
> License: MIT
> Copyright (c) wheregoes contributors

The full MIT license text is reproduced in the `NOTICE` file at the
repository root.

**No source code from `wheregoes/byd-apps` is copied verbatim into this
repository.** The probe is implemented independently against that project's
published research reference. The MIT license permits this; the
attribution above satisfies the license's notice requirement.

**No source code from `angoikon/byd-trip-stats` (BUSL-1.1) is copied into
this repository**, in compliance with that license's non-commercial /
no-distribution restrictions. That project is referenced only as research
material for what data exists on the DiLink platform.

## 6. Verification: store flavor has no `bydauto.*` references

The acceptance criterion for Tier 2 is that the `store` flavor's compiled
APK contains zero references to `android.hardware.bydauto.*` or any
reflection-based probing. To verify locally:

```bash
# Build the store flavor's release APK
./gradlew :app:assembleStoreRelease

# Decompile classes.dex and grep for bydauto references
unzip -p app/build/outputs/apk/store/release/app-store-release.apk \
    classes.dex | strings | grep -i bydauto

# Expected: no output (zero matches).
# Any match is a build-time isolation failure — investigate before
# submitting to Huawei AppGallery.
```

The architectural guarantee is enforced by Gradle source-set isolation:
all Tier 2 source files live under `app/src/enthusiast/java/...`, which
Gradle simply does not compile into the `store` flavor. There is no
runtime guard that can be bypassed — the code is not in the APK.

## 7. Distribution

| Flavor       | Channel                          | Consent required?     | Tier 2 available? |
|--------------|----------------------------------|-----------------------|-------------------|
| `store`      | Huawei AppGallery                | N/A (not shipped)     | No                |
| `enthusiast` | GitHub Releases (sideload only)  | Yes, per-user opt-in  | Yes               |

The enthusiast APK's `applicationId` is `com.binti.dilink.enthusiast` so it
can be installed side-by-side with the store version on a developer device
without signature conflicts.
