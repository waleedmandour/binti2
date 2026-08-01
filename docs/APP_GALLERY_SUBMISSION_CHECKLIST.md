# Huawei AppGallery Submission Checklist (for the `store` flavor)

> Applies to the `store` product flavor only — the one that ships to
> Huawei AppGallery. The `enthusiast` flavor is distributed via GitHub
> Releases and is out of scope for this checklist.

This checklist is a living document. Re-verify against Huawei's live
developer documentation before each submission — review guidelines for
automotive / accessibility-service apps change over time.

## 1. App listing and in-app copy

- [ ] App name, short description, and full description all state clearly
      that Binti is an **independent, unofficial project** with no BYD
      affiliation or endorsement.
- [ ] No use of BYD logos, product images, or trademarked artwork that
      could imply an official relationship.
- [ ] In-app "About" screen (Settings → About) reproduces the same
      disclaimer and links to the open-source repository.
- [ ] Privacy policy URL is set in AppGallery Connect and matches the
      in-app link.

## 2. Data access (Tier 1 only)

- [ ] Only Tier 1 (Accessibility-based) data access ships in this flavor.
      Verified by grepping the compiled APK for `bydauto` — see
      `docs/ADVANCED_TELEMETRY.md` §6.
- [ ] No reflection into internal vehicle services (`android.hardware.bydauto.*`).
- [ ] No CAN bus access.
- [ ] No login flow for the user's BYD cloud account; no scraping of the
      BYD mobile app's private API.

## 3. Permissions

- [ ] Permissions declared in `AndroidManifest.xml` match what's actually
      used — no dormant permissions.
- [ ] Foreground service type matches the declared
      `FOREGROUND_SERVICE_*` permission (`microphone|specialUse`).
- [ ] Accessibility service has both `android:label` and
      `android:description` set (required for the Android Accessibility
      Settings screen).
- [ ] Package visibility `<queries>` block lists only BYD DiLink packages
      and the two intents actually used (`geo:`, `tel:`).

## 4. Privacy policy

- [ ] Privacy policy describes exactly what `VehicleDataRepository` reads
      (battery %, estimated range, charging state, AC on/off, AC target
      temp, outside temperature) and confirms all data stays on-device.
- [ ] Privacy policy explicitly states that no voice data is transmitted
      off-device (Vosk ASR runs locally; no cloud speech API is used).
- [ ] Privacy policy discloses the GitHub Releases model download
      (model files fetched via Android DownloadManager — IP address
      visible to GitHub, no other data sent).
- [ ] Privacy policy describes the optional charging-station remote
      fetch from `waleedmandour.github.io` (URL only, no user data sent).

## 5. Accessibility service justification

Huawei (like Google Play) reviews Accessibility Service apps carefully.
Be prepared to:

- [ ] Provide a clear justification for Accessibility Service use:
      "Binti is a voice assistant for drivers of BYD electric vehicles.
      The Accessibility Service is required to interact with the BYD
      DiLink infotainment system's pre-installed apps (AC, navigation,
      media, phone) — these apps do not expose public intents or a
      public SDK. The service is scoped to `com.byd.auto.*` packages
      only and does not monitor or interact with any other app."
- [ ] Provide a demo video showing the Accessibility Service in use.
- [ ] Confirm the service is **off by default** and requires the user to
      manually enable it in Android Settings → Accessibility.

## 6. Foreground service justification

- [ ] Provide a clear justification for the foreground service:
      "Binti runs as a foreground service with a persistent notification
      so the wake-word listener ('يا بنتي') remains active while the user
      is driving. The microphone is used only after the wake word is
      detected; no audio is recorded or transmitted at any other time."
- [ ] The `specialUse` foreground service type is declared with the
      `voice_assistant` subtype property per Android 14+ requirements.

## 7. Content rating and target audience

- [ ] Content rating questionnaire completed (target audience: drivers
      18+; no user-generated content; no chat; no e-commerce).
- [ ] Target SDK matches Huawei's current minimum requirement (verify
      against Huawei's live developer documentation at submission time).

## 8. APK signature and packaging

- [ ] Release APK signed with the project's release keystore (not the
      debug keystore).
- [ ] `release-keystore.jks` is NOT checked into the repository.
- [ ] Build is reproducible: `./gradlew :app:assembleStoreRelease`
      produces a deterministic APK given the same inputs.

## 9. Final pre-submission smoke test

- [ ] Install the `storeRelease` APK on a clean BYD head unit (or
      emulator with `com.byd.auto.*` packages mocked).
- [ ] Verify the wake word "يا بنتي" activates the assistant.
- [ ] Verify "البطارية فيها كام؟" returns the battery percentage from
      the vehicle info screen via Accessibility.
- [ ] Verify "الرينج فيه كام؟" returns the estimated range from the
      repository (no fresh Accessibility scrape required if the snapshot
      is fresh).
- [ ] Verify the Developer → Vehicle State screen shows live Tier 1
      data and the Tier 2 controls are HIDDEN (because this is the
      `store` flavor).
- [ ] Verify no `bydauto` references appear in the compiled APK
      (`unzip -p app-store-release.apk classes.dex | strings | grep bydauto`).

## 10. Out of scope for this flavor

The following features are explicitly NOT in the `store` flavor and must
not be added without re-reviewing this checklist:

- Tier 2 Advanced Telemetry (enthusiast flavor only).
- BYD cloud account login / pyBYD-style API scraping.
- CAN-bus write / actuation commands.
- Any feature that touches safety-critical vehicle systems (steering,
  braking, propulsion torque, airbags, ADAS).
