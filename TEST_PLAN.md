# First device-validation checklist

Run on your own phone and optionally one stock Android 15 non-root device.

- [ ] App launches, dashboard renders with correct spacing and safe-area padding.
- [ ] Built-in input/output devices appear with correct reported capabilities.
- [ ] Connect and disconnect USB DAC; list refreshes and a new device ID is shown.
- [ ] Pair and disconnect a Bluetooth A2DP headset; observe inventory changes.
- [ ] Play quiet tone through built-in speaker; reported route corresponds to app-owned stream.
- [ ] Play quiet tone with USB DAC; verify the reported route is USB when available.
- [ ] Audio format fields are labeled as **reported**, not claimed actual stream format.
- [ ] No Shizuku installed: app remains functional, no crash.
- [ ] Shizuku installed but stopped: app remains functional.
- [ ] Shizuku running, permission denied: denial is visible, no privileged command runs.
- [ ] Shizuku running, permission granted: AudioFlinger and policy dump results appear.
- [ ] Test UID 2000 service, then root UID 0 service on a rooted device, if available.
- [ ] Force-stop Shizuku during collection: app shows an error without crashing.
- [ ] Share standard report and inspect values for accuracy.
- [ ] Verify no network permission, trackers, or automated data uploads.
- [ ] Review any raw privileged dump before sharing because package/device identifiers can leak.


## 0.2 Hardware / HAL probes across devices

Check Pixel 6 Pro, Pixel 8a and Samsung A36 (Android 15/16 as applicable). On each:

1. Run **Hardware → Scan hardware (read only)** with Shizuku started in ADB mode. Verify that sections correctly say **OK**, **NOT_FOUND**, **PERMISSION_DENIED**, or **ROOT_REQUIRED**; do not treat all absent vendor XML as a device error.
2. Compare ALSA cards/PCM devices to `adb shell cat /proc/asound/cards` and `/proc/asound/pcm`. A kernel without exposed procfs audio is expected to show missing, not fake measurements.
3. Play local music while scanning. If per-stream `hw_params` are readable, verify the displayed values are sourced from live kernel entries and label them as **observed PCM**, not third-party-app bit-perfect output.
4. Verify visible HAL service names match filtered `adb shell service list` output and that vendor XML is read-only.
5. If and only if a root-running Shizuku is available, inspect **Tinymix: Controls** with default enumeration. Never change controls or set gain values.
6. Validate all 'Inspect' expand/collapse controls, sharing raw report only after checking privacy; verify the full report stays under Binder's transaction size limit.
7. Restart Shizuku; verify scans fail gracefully. Denied access must not crash or hang the app.


## 0.3 Search / baseline tests

- [ ] Search for `audio`, `rate`, `pcm`, `codec`, and a known device name; check source/evidence labels.
- [ ] Scan with Shizuku and save a baseline. Reopen app and confirm baseline persists without a raw privileged dump.
- [ ] Disconnect a DAC, rescan, and inspect changes; change the DAC back and compare again.
- [ ] Validate baseline saved on a Pixel is labeled as a different device when compared on a Samsung.
- [ ] Clear the baseline, verify app-private snapshot is deleted.
- [ ] Check that absent or denied HAL/ALSA values are shown as inaccessible, not invented.
- [ ] Verify no device-specific private information is saved inadvertently in indexed values before sharing reports.
