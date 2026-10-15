# Wheelchair Blink Assist — Android prototype

This app uses the existing ESP32-CAM and an Android phone to detect three blinks. A laptop is not needed while it runs. The camera preview is optional; detection continues through a foreground service while monitoring is active.

## Current build: 0.18 care dashboard

For fresh-clone prerequisites, model download, build commands and APK output,
see the [project README](../README.md#build-the-android-apk). The current app has
Home, Health, Care and Alerts navigation, health graphs and a dated timeline,
fifteen scheduling voices, and the hidden manual speaker panel. Hold the
**Blink Assist** title on Home to open that panel.

Scheduled DFPlayer speech requires a matching SD recording, its checkbox enabled
in the reminder's **Speaker & guardian options**, and an active app-to-controller
Bluetooth connection. The phone speaks when a wired request cannot start.

The sections below are historical development notes. Their APK and temporary
build paths refer to the original development machine and are not distributed
in this source repository.

## Previous build: 0.12 care schedules and custom alerts

`dist/WheelchairBlink-care-schedules.apk` adds a simple Today agenda, freely
editable one-date/weekday schedules, Done/Taken/Snooze/Skip actions, care history,
a daily report, custom care/emergency/movement sounds, and opt-in guardian SMS
for skipped/unacknowledged care and movement notices. No medicine examples are
preloaded. New spoken titles can be exported to the existing DFPlayer SD card;
phone speech is used when a wired request cannot start. Wired scheduled playback
requires the main-controller update in `../tmp/care-firmware` and the matching
SD recording. See [care-schedules-setup.md](../care-schedules-setup.md).

## Previous build: 0.11 health history and guardian SMS

`dist/WheelchairBlink-health-guardian.apk` adds current sensor readings, time-range
graphs, persistent local measurement history, CSV export and an event timeline.
New data uses the existing paired Bluetooth connection and requires the controller
update in `../tmp/health-guardian-firmware`. Old blink history is retained; old BPM
data cannot be invented where it was never recorded.

Guardian alerts can send a single SMS automatically after three blinks, using the
phone's own SIM. The feature starts off and requires a saved international number,
explicit enablement and Android SMS permission. Delivery status distinguishes a
request, carrier acceptance and a delivery report. No read receipt is claimed.

See [health-guardian-setup.md](../health-guardian-setup.md) for setup, limits,
firmware upload and testing. Neither Azure nor a guardian companion app is needed
for SMS. The simple home screen and existing tracking choices remain available.

## Previous build: 0.10 simpler screen

Install `dist/WheelchairBlink-simple-ui.apk` (version code 10) over Blink Assist.
The home screen shows Start/Stop, video, eye and gesture status, the three-blink
counter, speaker status and Test speaker. Gesture setup expands on demand.
Advanced is collapsed by default and contains tracker/timing controls, camera and
Bluetooth configuration, detailed diagnostics and optional Azure uploads.

Saved connections, tracker choice, confirmation timing and calibration are
preserved. Camera-size errors and calibration blockers remain visible on the
home screen in simpler language. This is a presentation update; the previously
reported mouth-detection problem still needs confirmation with live phone data.

Validation: 65 JVM tests passed, lint 0 errors / 14 warnings, APK version 10 and
the existing signing certificate verified. Build log: `../tmp/simple-ui-build.log`.
APK SHA-256:
`b763c34514303acfa6aecde661385ec2c9d4bb0162310e52eb68b5450a9320f6`.

The APK installed and launched in the Android emulator. The initial layout was
inspected, but the final visual/interaction check was limited by the emulator's
System UI becoming unresponsive. Screenshots are in `../tmp/simple-ui-home.png`
and `../tmp/simple-ui-final.png`. These contain the emulator system error dialog;
they are not evidence of a completed UI acceptance test. Physical-phone testing
is pending.

## Previous build: 0.9 optional second tracker

`dist/WheelchairBlink-dual-tracker.apk` (version code 9) adds bundled, offline
ML Kit contours for mouth and eyebrows while retaining the existing MediaPipe
eye/blink detector and smile signals. Stop monitoring to choose the new tracker;
it has a separate calibration. The default remains the existing tracker.
An independent Quick option reduces mouth confirmation to 500 ms and eyebrow
confirmation to 700 ms; smile remains 1500 ms. Default standard timing is retained.

See [dual-tracker-setup.md](../dual-tracker-setup.md) for installation, calibration,
model selection and test steps. The current 320 x 240 camera stream may not meet
the new tracker's 200 x 200 face-size check. No camera resolution or controller
firmware changes are included. ML Kit errors leave the blink loop running, but
two models can increase device load. Phone installation and live performance
verification are pending; no phone was present in ADB.

Validation: 65 JVM tests passed, lint 0 errors / 14 warnings, version and signing
certificate verified. APK SHA-256:
`bb5768b1090deed23f7470a0df16cae1082e39222ced6fea9043edb579b66632`.

## Previous build: 0.8 stabilized gestures

Install `dist/WheelchairBlink-stable-gestures.apk` (version code 8). The user
confirmed the open/closed label flips even while holding the mouth still.
Previously every frame was classified independently, and crossing the entry
threshold reset the 1.5-second hold. Labels and the hold detector now share a
three-frame median and separate entry/release thresholds. This adds roughly one
frame of entry latency for a sustained change and filters isolated spikes/drops.

Mouth enters at 0.65 and releases at 0.45; smile enters at 0.55 and releases at
0.40 on either side; brows enter at inner 0.55/outer 0.35 and release at inner
0.40 or either outer 0.25. These are calibrated scores when a profile is saved.
The same filtered values determine neutral-face readiness. Invalid/missing faces,
sample gaps over 350 ms, and eye closure still cancel the gesture. Eye/blink
signals are not smoothed. Full-range alternating open/closed input remains
rejected rather than guessed into a message. Ambiguous simultaneous gestures
still do not speak. Existing calibration, speaker protocol and recordings remain.

56 JVM tests passed, including seven new stability scenarios: mouth/brow threshold
jitter, an isolated drop during a held gesture, true closures, repeated movement,
single spikes/full-range flicker, invalid gaps and release thresholds. APK signed
and version verified; lint 0 errors, 14 warnings. Build log:
`../tmp/stable-gestures-build.log`. The phone is absent from ADB; installation and
live testing are pending. These simulated checks do not establish on-camera
accuracy for this user. Test only one gesture at a time, hold for about two seconds
to allow settling plus the 1.5-second timer, then relax before repeating.

APK SHA-256: `7426b3f121ef3c416580ef0daeeeca9815e629586223b4a2064c853a93721a4b8`.

## Previous build: 0.7 individual mouth calibration

`dist/WheelchairBlink-mouth-calibration.apk` (version code 7) adds two guided
captures: relaxed face and comfortably open mouth. It replaces the fixed jaw
score with a lip-gap/mouth-width ratio calibrated to the user when a saved profile
exists, and adjusts neutral score baselines. Successful calibration saves the
profile and enables facial messages. A fresh relaxed position is required before
announcing. Recognition/hold progress runs independently of speaker setup, while
delivery still requires the ready controller and SD recordings.

See [mouth-calibration-setup.md](../mouth-calibration-setup.md) for the procedure,
checks, limitations and hash. Built/signed with 49 passing JVM tests; lint 0 errors,
14 warnings. Phone installation, calibration and real-world accuracy are pending.
Controller firmware and recordings are unchanged.

## Previous build: 0.6 facial status and blink-wait fix

`dist/WheelchairBlink-face-status.apk` (version code 6) addresses the report
that the app only says it is tracking eyes. The facial-message switch, live
mouth/smile/brow indicators, hold progress and last message are now beside
the preview. Main status names blink-only mode versus facial messages, and
speaker busy/setup blockers are explicit. Indicators run even when the speaker
is unavailable; playback remains gated by setup and the enable switch.

Previously one ordinary blink blocked facial neutral/hold tracking until the
two-second blink-sequence timeout. Facial tracking now resumes when eyes reopen,
without that extra wait. It still requires 700 ms neutral and 1.5 s hold. An eye
closure cancels the hold, and completed three-blink Emergency is processed first
and can interrupt a phrase. No thresholds, recordings or firmware changed.

Built and signed; 39 JVM tests passed, lint 0 errors/12 warnings. The phone was
absent from ADB during diagnosis/build, so installation, the current phone toggle,
live model scores and actual mouth-to-speaker behavior remain unverified. The
specific cause on the user's phone is not yet established. Build log:
`../tmp/face-status-build.log`.

APK SHA-256: `5b4599240226c7de6d0f0906530aa9a3cc120b75ef57a5b6c0a0953633cd037c`.

## Previous build: 0.5 facial messages

Install `dist/WheelchairBlink-facial-messages.apk` (version code 5). Adds held
smile → Yes, open mouth → Please help me, and raised eyebrows → Please come
here, alongside three-blink Emergency. Facial messages are off by default;
they require the new controller firmware and three new SD recordings. Includes
hold progress, setup status, manual phrase tests and Emergency priority.
See [facial-messages-setup.md](../facial-messages-setup.md) for installation,
validation, limitations and recording hashes. The APK was installed on the V2031
phone through ADB on 2026-10-14 and version 5 verified; the activity opened.
The three facial recordings were copied to the SD card and hash-verified.
Card reinsertion, controller activation and a real face-to-speaker test are pending.

## Previous build: 0.4 live video

Install **`dist/WheelchairBlink-live-video.apk`** over the existing app (version
code 4, same application ID and signing key). The controller firmware is unchanged.

- **Show live video** is enabled by default and appears at the top, beside the
  monitoring controls. The preference is saved; video can still be hidden.
- Camera frames reach the preview independently of inference. The preview redraws
  up to 15 times/second instead of waiting for model processing and a 400 ms UI
  refresh. This does not increase the camera's own capture rate.
- Recent eye positions are marked on the video, green for usable tracking and
  amber when positioning needs improvement. Old/misaligned markers are hidden.
  The app reports both eyes open/closed, face positioning, and multiple faces.
- The tracker attempts GPU acceleration and falls back to CPU on initialization
  or inference failure. Creation, inference and cleanup stay on the same thread.
  Two-face detection, confidence thresholds and the blink checks are retained.
- Separate video and tracking FPS help distinguish a slow camera stream from
  slow inference. Results older than 500 ms cannot trigger an alert. Old video
  disappears rather than leaving a frozen image on screen.

The user reported 8.9–12 tracking frames/s on the previous app. Actual speed and
blink accuracy on the phone with this update still need measurement; GPU support
does not guarantee a speed increase. Use good light and face the camera until
both eyes are tracked, then make three deliberate blinks with reopening between.
No internet or visible preview is required for detection or speaker Bluetooth.

Verification on 2026-10-08: final build passed 27 JVM tests, lint with zero errors,
APK signature/version checks, and both `VideoSmokeTest` emulator tests (independent
preview rendering/stale-image clearing and CPU video model/bitmap ownership).
The emulator's System UI repeatedly became unresponsive, blocking a full visual
walkthrough; the smaller emulator was shut down after the checks. Final checks
were run sequentially with a 1 GiB Gradle heap after a host memory shortage.
Logs: `tmp/speaker-check/android-video-low-memory-build.log` and
`tmp/speaker-check/video-instrument-tests.log`. GPU performance, camera feed,
and blink-to-speaker behavior in 0.4 still need a physical-phone test.

APK SHA-256: `d04179266a9f421c277adb3c4398fb743655b9aeaba741d980c497be478cab90`.

## Previous build: 0.3 blink speaker fix

Install **`dist/WheelchairBlink-speaker-fix.apk`** over the existing app. It uses
the same signing key and application ID, version code 3. No new ESP32 firmware
is needed. The user confirmed that the 0.2 app's **Test speaker** button produces
audible Emergency playback, establishing that phone Bluetooth and the DFPlayer
path can work together; blink-triggered sound remained unverified.

Two code issues were corrected: previously the UI could count a third blink
before an additional 150 ms confirmation stage cancelled it, and an overlapping
speaker request could be rejected without showing why. The third counted blink
now emits the alert immediately after its already-required reopening (at least
60 ms and two open frames). The closure/face-quality/sequence-window checks and
three-second cooldown remain. Counting pauses with a visible message while an
earlier manual or blink audio request is active; missed requests are not queued.
A separate **Blink alert** line records whether the latest sequence started a
speaker request or was rejected because the speaker was busy or disconnected.

After a manual speaker test, wait for the wait message to clear, look at the
camera with eyes open until ready, then blink three times and reopen each time.
The previous extra confirmation hold is no longer needed. Software verification:
24 JVM tests pass, including regression tests for the third-count transition,
exactly one speaker request, and busy-state release; Android lint has zero errors;
the APK signature and version were verified. A physical test of this updated
blink behavior on the phone remains pending installation.

APK SHA-256: `33368d7ae13df57311f298695d8a0908d6ce0ad736ec0b9aa0f1f12355750017`.

**Version 0.2 adds the wired emergency speaker over Bluetooth.** It still sends only labeled test events to Azure and does not contact caregivers or emergency services. Detection and background reliability still need testing on the actual phone. Ordinary blinks can also match the gesture, so this prototype does not establish emergency intent.

## Speaker setup (one time)

1. Install `dist/WheelchairBlink-speaker-test.apk` over the existing app; its application ID and signing key are unchanged.
2. Power the main ESP32 with the matching Bluetooth firmware described in `../azure_iot_live/README.md`. Keep the SD card containing `/mp3/0001.mp3` in the DFPlayer.
3. On the phone, open Bluetooth settings and pair **Wheelchair-Speaker**. This is the main controller, separate from the camera.
4. In the app's **Emergency speaker** card, tap **Select paired speaker**, allow **Nearby devices**, and select **Wheelchair-Speaker**.
5. Keep phone Wi-Fi on **ESP32-CAM-MB**. Tap **Start monitoring**, wait for **Speaker connected**, then tap **Test speaker · maximum volume**.
6. When eyes are tracked, blink both eyes three times and reopen between blinks. The app requests the SD recording at the approved volume 30/30 immediately, without waiting for Azure. Preview can be hidden.

Bluetooth carries the speaker command while Wi-Fi carries video. No laptop, mobile data, internet, or Azure setup is needed for the sound. Leave Bluetooth and monitoring enabled. After connection loss, the app reconnects but never replays missed alerts. Stop monitoring disconnects Bluetooth and cancels that phone's pending playback. A playback request or controller acknowledgement does not prove audible sound; the module can finish without reporting completion, as observed in earlier tests.

The app exposes connection, checking, playback-requested, timeout and module-error states. Each live alert has an ID; duplicate or busy requests are discarded. The controller only accepts the speaker protocol over its paired Bluetooth link; no cloud command is used to drive sound.

## Install later

1. Copy `dist/WheelchairBlink-speaker-test.apk` and, if using Azure, `local/azure-device.json` to your phone using USB or a private file transfer. The older `WheelchairBlink-test.apk` is retained as the 0.1 rollback build.
2. Open the APK in the phone's Files app. If prompted, allow that Files app to install apps, then install **Blink Assist**. This build supports Android 8 or later on a 64-bit ARM phone. It also includes x86_64 for emulator testing.
3. Power the ESP32-CAM. In the phone's Wi-Fi settings, connect to **ESP32-CAM-MB**. Choose to stay connected if Android says the network has no internet.
4. Open Blink Assist. The camera address is already `http://192.168.4.1:81/stream`.
5. Tap **Import Azure setup** and select `azure-device.json`. This file contains a private key for this phone's Azure device; keep it private. After import, you can remove the transferred copy from Downloads. The app keeps its own private copy.
6. Enable mobile data if you want to upload while connected to the camera. Tap **Send connection test** to check Azure connectivity. If no usable internet route exists, the test stays queued.
7. Tap **Start monitoring** and allow notifications. Use **Show preview for positioning** to aim the camera at one face with both eyes clearly visible. You can turn the preview off afterward.
8. Wait for **Eyes tracked**, then blink both eyes three times within five seconds, opening them between blinks. Reopen after the third blink. A recognized sequence vibrates the phone, increments the test-gesture count, and requests the connected speaker recording.
9. Use **Stop monitoring**, or Stop in the running notification, when finished.

## Connections and offline use

- Camera video travels over the phone's Wi-Fi connection to the ESP32-CAM. The model is bundled in the APK and runs locally, without internet or a visible preview.
- Azure uploads use an available internet connection. The app requests a separate mobile-data route while camera Wi-Fi remains connected. Whether both routes work together depends on the phone and its settings.
- Without internet, test events remain in an on-phone queue. They retry while monitoring is active. After reconnecting to internet, you can also tap **Send connection test** to attempt delivery of the queue.
- The current camera firmware uses its own Wi-Fi network. It has not been changed to join your home router.
- Some phones cannot provide a hotspot to the main ESP32 while also joining camera Wi-Fi. The main controller may need a separate router for its own Azure telemetry; speaker Bluetooth remains independent. The matching controller update adds Bluetooth and preserves existing Wi-Fi credentials.
- Video is processed in phone memory. The app does not save images or upload them to Azure; it sends small labeled test-event records.

## First phone checks

Start with the phone screen on. Check tracking, the blink counter, processing frame rate, and Azure delivery. Then test with the preview hidden, another app open, and the screen locked. Vivo battery management can restrict background work; allow background activity for Blink Assist if needed. Restart monitoring after force-stop or reboot. It does not restart automatically.

If video is unavailable, check camera power, the selected Wi-Fi, and the camera URL. If tracking is unreliable, improve lighting, move closer, keep both eyes visible, and use a comfortable, deliberate blink with a short reopening between each. Low frame rates can miss fast blinks; actual phone performance has not yet been measured.

## Build and verification

Native Android/Java project, application ID `org.wheelchair.blink`, version `0.2-speaker-test` (code 2). The APK is debug-signed for prototype installation, not a Play Store release. Azure credentials are supplied separately and are not embedded in the APK. Device identity: `wheelchair-phone-01`.

Build from the project directory with a JDK 17 or later and an Android SDK configured in ignored `local.properties`:

```powershell
.\gradlew.bat :app:assembleDebug :app:testDebugUnitTest :app:lintDebug
```

The model asset is `app/src/main/assets/face_landmarker.task`. Restore it from the following source if setting up a fresh checkout:

https://storage.googleapis.com/mediapipe-models/face_landmarker/face_landmarker/float16/1/face_landmarker.task

Model SHA-256: `64184e229b263107bc2b804c6625db1341ff2bb731874b0bcc2fe6544e0bc9ff`.

Verification completed during development:

- 20 JVM tests: 12 gesture tests and 8 speaker-protocol tests covering three-blink integration, correlated replies, duplicates, busy state, failures, timeouts, and disconnects without replay.
- All 3 Android emulator tests passed on the final build: loading the bundled model without recycling the preview bitmap, local camera address validation, and authenticated Azure delivery.
- Azure CLI independently observed Android connectivity-test event `f29345dd-f818-4257-8a4d-7c390e0e2a95` on 2026-10-07. This checks transport, not a camera gesture.
- Android lint passed with no errors; advisory warnings remain for SDK/dependency currency and localization.
- APK signature verification passed; the packaged model hash matches, and the device key is absent from the APK.

The emulator's System UI repeatedly became unresponsive, so a complete visual walkthrough was not verified there.

The earlier 0.1 APK was installed on the Vivo Y51A. Installation of 0.2, phone Bluetooth pairing, simultaneous camera Wi-Fi/Bluetooth, actual blink-triggered sound, and long-running background operation remain to be verified on the phone.

On 2026-10-08, the matching controller firmware was uploaded to COM9 with flash
hash verification. Its boot log reported `PHONE Bluetooth ready: Wheelchair-Speaker`.
A subsequent USB speaker test confirmed DFPlayer volume 30/30 and received the
play-command acknowledgement; the module did not report completion. This verifies
controller startup and the existing speaker command path, not a phone Bluetooth
connection or audible playback from a phone gesture. The user chose to install
the APK later. Build, 20 JVM tests, lint (zero errors), APK signature and upgrade
signer checks passed. APK SHA-256:
`b42ece0f116000492e54fadd26fb94a9fa7ff48b29660e57b2dc6d258ed286ec`.
