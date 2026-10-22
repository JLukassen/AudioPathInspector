# Audio Path Inspector

![Audio Path Inspector icon](assets/branding/play-store-icon-512.png)

**Yoo Shastan · Android audio diagnostics · 0.4.0-dev**

A **read-only visual audio diagnostics app** for Android, started for the independent Yoo Shastan Android project line. This is a **starter source project**, not a tested APK or a finished audio analyzer.

## App icon and branding

- App launcher icon is configured in `AndroidManifest.xml` with standard density PNG fallbacks and Android adaptive icon XML resources.
- Adaptive icons include a separate monochrome foreground for supported themed launchers.
- Original studio icon, 512 × 512 store artwork, and an optional GitHub social preview live under `assets/branding/`.

## Included now

- A Compose dashboard with six views: **Signal path**, **Devices**, **Settings**, **Hardware**, and **Diagnostics**.
- Actual Android `AudioManager` / `AudioDeviceInfo` collection of connected inputs/outputs, declared encodings, sample rates, channel counts, audio profiles and a few framework properties.
- Refresh automatically when audio devices appear or disappear.
- A **user-triggered quiet 750 Hz / 400 ms test tone** that reports the output device actually used by this app's own `AudioTrack` when Android reports it. This does **not** prove the media app's current route or the DAC's native stream format.
- Optional Shizuku permission/access state. With permission, its **UserService** runs fixed, read-only commands as shell/root: `dumpsys media.audio_flinger`, `dumpsys media.audio_policy`, `service list`, `lshal` (if available), and `getprop`; it also tries bounded reads of ALSA procfs nodes, vendor audio XML, and (only with UID 0) `tinymix` with **no arguments**, which enumerates controls. No mixer writes. Commands time out, reads are bounded, and report sections include a status (OK, NOT_FOUND, PERMISSION_DENIED, etc.).
- User-triggered report sharing; no automatic network transmission, analytics, or background scanning.

## Publish to your existing GitHub repository

The remote repository `JLukassen/AudioPathInspector` has been created. This project ZIP includes all source code and launcher assets; the remote may still be empty. Run `bash scripts/publish-to-github.sh` from the extracted project folder after `gh auth login` to publish to that exact existing repository. The script checks whether the remote already has branches and refuses to overwrite existing history. It does not change repository visibility.

To update a repository already populated with an earlier version, clone the remote first, copy the new project files into the clone (preserving `.git`), then review `git diff`, commit, and push normally.

The project is packaged without a Gradle wrapper JAR. The supplied GitHub Actions workflow uses a pinned Gradle 8.13 installation and Android SDK 36. It is a first-build check, not proof of a tested app or release-ready binary.

## Version 0.4 — root-assisted Tinymix inspection

- Hardware tab adds an **explicitly confirmed** read-only root scan using `su -c` with authorized APatch/Magisk-compatible root managers. This is separate from Shizuku and works when Shizuku runs as ADB shell UID 2000.
- Only fixed executable paths are considered: `/system/bin/tinymix`, `/vendor/bin/tinymix`, `/system/vendor/bin/tinymix`, and `/system/xbin/tinymix`. Scan cards 0–3 with a read-only listing; when needed use the read-only `contents` syntax for newer TinyALSA versions.
- **No root permission is requested automatically.** The user confirms the operation in the Hardware tab and the installed root manager decides whether to grant it.
- No `tinymix set`, mixer writes, binary installation, SELinux changes, or permission modifications. Root results are bounded by time and output size, indexed as **MIXER_REPORTED** (not a verified end-to-end audio path), and available to existing search and comparison views.
- Without root or when Tinymix is missing, the app displays an explicit error/status rather than inventing values. Users may optionally install appropriate third-party Tinymix components separately.
- Root-assisted output may expose sensitive hardware labels; share reports only after review. Shizuku-only diagnostics remain available without root.

## Version 0.3 additions

- **Hardware search:** Indexed read-only facts from ALSA `/proc/asound`, VINTF declarations, service listing, selected vendor XML and audio properties. Each entry retains its source and evidence label; inaccessible data is never invented.
- **Compare tab:** Save one local, user-triggered baseline of extracted facts, scan again after changing headphones or settings, and inspect ADDED / REMOVED / CHANGED values.
- **Read-only scope:** Root-only Tinymix listing is optional; no mixer writes or modifications. Raw privileged dumps aren't persisted in the baseline.
- **Limitations:** No conclusive DAC bit-perfect verification or end-to-end HAL/PCM route tracing; same hardware may show different diagnostic visibility on Samsung and Pixel devices.

## Open in Android Studio

1. Extract the ZIP and open the **AudioPathInspector** directory with **File → Open**.
2. Install **Android SDK 36** (needed to compile AndroidX Activity 1.12.4) and select **JDK 21** for Gradle. The app still targets Android 15 (`targetSdk = 35`).
3. Use **Gradle 8.13**, **Android Gradle Plugin 8.13.2**, and Kotlin **2.2.21** as pinned in the build scripts.
4. If Android Studio reports that a Gradle wrapper JAR/script is missing, select a local/IDE-managed Gradle 8.13 distribution, then use `gradle wrapper --gradle-version 8.13` with that Gradle installation to create the usual `gradlew` and JAR. The ZIP deliberately does not include a fabricated wrapper binary.
5. Sync the project, select `app`, and run on an Android device (Android 15 is the primary test target; min API 29).
6. The public API features work without Shizuku. For optional diagnostics, install/start Shizuku, authorize this app under **Diagnostics**, then tap **Collect**.

### Build from a host with Gradle installed

```bash
gradle wrapper --gradle-version 8.13
./gradlew :app:assembleDebug
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

Gradle requires network access the first time to download Android/Jetpack/Shizuku dependencies. **This ZIP was not compiled or installed here** because this environment has no Android SDK, Gradle distribution, or Maven network access.

## Evidence model

| Label | Meaning |
|---|---|
| `REPORTED` | The device/framework advertises that capability. It may support many configurations. |
| `CONFIGURED` | Android reports a framework configuration property; not necessarily a hardware measurement. |
| `OBSERVED` | An API reports a runtime route, only for the applicable stream/context. |
| `UNKNOWN` | The app cannot confidently access the data. It must not fabricate a measurement. |

The visual chain is conceptual, not a literal data trace through every DSP, mixer, HAL or DAC stage. `AudioManager.PROPERTY_OUTPUT_SAMPLE_RATE` reports the **framework** default, not guaranteed DAC output. The communication device refers to **calls/communication**, not music routing. Device audio profiles are advertised capabilities.

## New in 0.2 — HAL / ALSA collector

- **Hardware tab:** expands ALSA cards/PCM devices, active PCM `hw_params` (when exposed), visible audio HAL service registrations and audio system properties, vendor XML audio/mixer configuration snippets, and optionally root-only Tinymix control listing. A probe records **OK**, **NOT_FOUND**, **ROOT_REQUIRED**, **PERMISSION_DENIED**, **EMPTY**, or an explicit error instead of guessing. Vendor XML *configuration* is not a confirmed current hardware rate.
- **Shizuku ADB mode:** best-effort access with UID 2000; SELinux often hides `/dev/snd`, `/proc/asound`, vendor-private config, and live mixer controls. The app never escalates privileges itself.
- **Shizuku root mode:** optional use of a root-running Shizuku service. If a `tinymix` executable is available at a known system/vendor location, it is invoked **without control/value arguments**. Results are read-only and may still be blocked by device/SELinux policy.
- **Safety:** no `su` command, permission mutations, SELinux changes, HAL restarts, driver configuration writes, or audio gain adjustments. Hardware diagnostics are an *inventory*, not proof of a bit-perfect signal path.

## Planned next steps

1. Parse vendor-specific audio policy and AIDL HAL configuration into stable, typed fields, with per-field evidence and provenance; the current Hardware view shows bounded raw snippets.
2. Add search, snapshot comparison, privacy-aware redaction, and SAF export. Raw privileged reports may include identifiers and should be reviewed before sharing.
3. Identify and correlate applicable `AudioFlinger` mixer thread, policy output profile, active PCM params and selected DAC **only when identifiers actually match**.
4. Expand validation on Pixel 6 Pro, Pixel 8a and Samsung A36, rooted and Shizuku ADB mode, headphone/USB DAC/Bluetooth cases.
5. Consider a **separate** app for intentional low-level Tinymix control, with strong safety mechanisms and proper upstream attribution.

**Safety:** No changes to system mixer volume, ALSA controls, or audio policy files are included. An eventual Tinymix **control** app should be developed as a separate, explicitly root-only product, with per-device checks and reversible changes. Excessive headphone or speaker gain can damage hearing or hardware.

## Notes on Shizuku

The starter uses Shizuku 13.1.5 and a UserService (not the deprecated `Shizuku.newProcess` mechanism). Shizuku running via ADB has UID 2000, which is limited by Android's service permission checks and SELinux. Shizuku running via root can have UID 0, but vendor restrictions still apply. Some dumpsys services may return `Permission Denial` or device-specific fields; the UI displays raw results without treating them as verified hardware settings.

### Technical references

- [Android AudioManager](https://developer.android.com/reference/android/media/AudioManager)
- [Android AudioDeviceInfo](https://developer.android.com/reference/android/media/AudioDeviceInfo)
- [Shizuku API and UserService](https://github.com/RikkaApps/Shizuku-API)
- [Shizuku API published artifacts](https://central.sonatype.com/artifact/dev.rikka.shizuku/api/13.1.5)

**AIDL caveat:** On newer AIDL Audio HAL implementations, significant configuration is supplied at runtime by HAL interfaces rather than static XML. The current version discovers service registrations and accessible config files; it does not query every vendor-private HAL method.
