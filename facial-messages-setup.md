# Facial messages — prototype 0.5

**Latest app:** 0.8, `android_phone/dist/WheelchairBlink-stable-gestures.apk`,
stabilizes mouth/smile/brow signals and preserves the 0.7 calibration procedure.
56 software tests pass; the user's live result is not yet verified.

**Current mouth fix:** version 0.7 adds individual calibration after the user
reported live video with **Mouth: not open enough**. Use
[mouth-calibration-setup.md](mouth-calibration-setup.md) and the 0.7 APK. It uses
the existing controller and recordings; installation/calibration/live testing
are pending. Earlier implementation and deployment history follows.

**Latest app update:** 0.6 (`android_phone/dist/WheelchairBlink-face-status.apk`)
shows facial controls and mouth status beside the preview and removes the extra
blink-sequence wait after an ordinary blink. It uses the same controller and SD
recordings. Built with 39 passing JVM tests; installation and live testing are
pending because the phone was absent from ADB. See the Android README for details.

The Android app uses the existing camera model's facial blendshape scores to
recognize deliberate gestures and request saved DFPlayer recordings over
Bluetooth. These are chosen communication gestures, not inferred emotions,
medical conditions, consent, or proof that someone needs assistance.

| Gesture | Recording | SD card path |
|---|---|---|
| Smile with both sides of the mouth | Yes | `/mp3/0003.mp3` |
| Hold mouth open | Please help me | `/mp3/0004.mp3` |
| Raise eyebrows | Please come here | `/mp3/0005.mp3` |

Three deliberate blinks still request Emergency (`0001.mp3`). Board motion
still uses `0002.mp3`. Emergency can interrupt a facial message or motion audio;
facial messages do not interrupt any active recording. No request is replayed
after a busy response or reconnection. The new gestures do not create Azure
events or contact anyone.

## Setup

1. Install `android_phone/dist/WheelchairBlink-facial-messages.apk` over the
   existing app. It has the same package/signing certificate, version code 5.
2. With prototype power off, put the DFPlayer SD card in the Android phone or
   reader. Copy `speaker_audio/mp3/0003.mp3`, `0004.mp3`, and `0005.mp3` to the
   card's existing `mp3` folder. Preserve `0001.mp3` and `0002.mp3`; no format.
   `speaker_audio/FacialMessages-recordings.zip` contains only the three new MP3s
   in an `mp3` directory.
3. Verify the files, safely eject the card, insert it in the powered-off
   DFPlayer, then power the prototype.
4. The controller needs the accompanying facial-message firmware. After file
   verification, send `FACE AUDIO READY` plus newline on COM9 at 115200 baud.
   This persists readiness; `FACE AUDIO OFF` disables new facial requests again.
   This is a setup flag, not automatic SD verification. Do not enable before
   the files are installed. Existing Emergency/motion flags are unaffected.
5. Join camera Wi-Fi, pair/select Wheelchair-Speaker as before, and start
   monitoring. Use the three phrase test buttons first and listen for the
   correct message. A request/acknowledgment alone does not prove audible sound.
6. Stop monitoring, check **Enable facial messages**, and start again. The
   toggle is off by default. Keep one face in view in good light. Relax with
   eyes open until ready, then hold one gesture for 1.5 seconds. A progress
   percentage and last-request outcome appear in the Facial messages card.
7. Relax after each message. There is a five-second cooldown followed by a
   fresh neutral-face period before another gesture can speak.

The phone and SD card were not connected at packaging time. The APK was
subsequently installed over the existing app on the V2031 phone through ADB;
Android confirmed version code 5 / `0.5-facial-messages`, and the activity opened.
The three new recordings were subsequently copied through ADB to SD card
`6434-6361`, and all five MP3 hashes matched the local files. Android confirmed
the card safely unmounted. Reinsertion, facial-audio activation and
camera-to-speaker testing remain pending.
Thresholds need a live trial with the intended user: facial mobility and camera
position affect recognition, and ordinary smiling/talking can resemble gestures.

## Timing and signal checks

Recognition requires 700 ms neutral expression with both eyes open, then
1,500 ms continuously above the selected thresholds. A sample gap over 350 ms,
invalid/missing scores, lost/multiple faces, a large position jump, eye closure,
   an overlapping gesture prevents a message. In 0.6, a completed Emergency request
   takes priority, but a pending one/two-blink sequence no longer adds a wait.
An inference result older than 500 ms remains unusable, as in 0.4.

Initial thresholds: both mouthSmile scores >=0.55; jawOpen >=0.65;
browInnerUp >=0.55 and both browOuterUp scores >=0.35. Neutral requires all six
expression scores below 0.30 and both eyeBlink scores <=0.35. A gesture held
through cooldown cannot repeat without returning to neutral.

The existing MediaPipe model already emits these scores; no second model or
cloud inference was added. The added Java logic is small, but real phone FPS
and recognition accuracy with this release have not been measured.

## Bluetooth compatibility

`HELLO` still receives only `WHEELCHAIR_SPEAKER 1` for old apps. New apps send
`FACE HELLO` after HELLO to request `FACE_MESSAGES 1 READY` or
`FACE_MESSAGES 1 NOT_READY`, so old apps do not receive unfamiliar setup replies.
New apps send `PHRASE <3|4|5> <UUID>` only after READY. Older firmware continues
to support three-blink Emergency but cannot activate facial messages.

Firmware accepts only those three track numbers, rejects malformed/duplicate
requests, and returns correlated status. A new SOS replaces a pending phrase
in the phone protocol and stops non-Emergency audio on the controller before
assigning the new request ID. Old completion replies cannot complete the SOS.

Some DFPlayer replies observed earlier use global file indices instead of MP3
folder track numbers. The driver conservatively reports completion unconfirmed
when they differ, then stops after its timeout (five seconds for facial phrases).
All three phrases are shorter than two seconds. This status alone does not
establish whether the sound was heard.

## Validation and artifacts

- Android: 37 JVM tests passed; APK assembled; lint: 0 errors, 12 warnings
  (see generated report). Version and signature verified. No physical phone
  or new emulator test was performed for this release.
- Native C++: existing 15 audio scenarios, 13 motion scenarios, phone protocol
  suite, and new phrase suite passed. New coverage includes readiness gating,
  track selection 3–5, invalid commands, busy/duplicate/disconnect behavior,
  and Emergency preemption with late phrase completion.
- Build/test logs: `tmp/face-messages/android-build.log`, test XML under
  `android_phone/app/build/test-results/testDebugUnitTest/`, and
  `tmp/face-messages/firmware-build.log`.

### Controller deployment completed — 2026-10-14

Final firmware compiled at 1,800,529 bytes (91%) flash and 72,268 bytes (22%)
static RAM, then uploaded to COM9 with hash verification. After restart, the
LCD remained initialized with all 32 startup test characters verified and showed
live room temperature/humidity. IMU samples were valid/calibrated at rest,
motion audio readiness remained 1, and optical sampling continued near 25 Hz
(no finger present; this does not verify heart-rate accuracy).

`FACE AUDIO OFF` returned `requested=0 saved=1`, confirming the new command
works and keeping facial audio disabled pending the three SD copies. No new
phrase was played from the physical speaker. Logs:
`tmp/face-messages/upload.log` and `tmp/face-messages/controller-check.log`.

### SD copy completed — 2026-10-14

Copied `0003.mp3` (22,612 bytes), `0004.mp3` (28,046 bytes), and `0005.mp3`
(28,882 bytes) to `/storage/6434-6361/mp3/` through the V2031 phone. After sync,
SHA-256 checks matched the recording table below. Existing Emergency and motion
recordings were unchanged, verified before and after copying. No formatting.
Android reported `public:179,1 unmounted 6434-6361` after safe ejection.
The card must be reinserted into the powered-off DFPlayer before testing audio.
After the user reported the mouth gesture was not working, `FACE AUDIO READY`
was sent and confirmed `requested=1 saved=1`. Log:
`tmp/face-messages/activation.log`. The phone was no longer connected to ADB,
so the app toggle, live gesture scores and physical SD placement could not be
checked. Audible playback and gesture recognition remain unverified.

APK SHA-256:
`57c4192b658d752874b979a1fa6326bd9673b2e4b13de746328fcf6783ed4708`.

Recordings: offline Microsoft Zira Desktop, mono 44.1 kHz, 128 kbit/s MP3.

| File | Duration | SHA-256 |
|---|---|---|
| 0003.mp3 | 1.34 s | `6553e759f5d2696e9750a78e7f5ee6ca5454df3b031bebc01716f5db7217ad8f` |
| 0004.mp3 | 1.69 s | `43077334cdb2d7d759b820f255ad5823320afe89ca506aafacb9e6fff09208b4` |
| 0005.mp3 | 1.74 s | `2e6bbfeadaa2b3fae0196a2e87fd7ee03f50c5a880a52ae17fde95415a7f304f` |
