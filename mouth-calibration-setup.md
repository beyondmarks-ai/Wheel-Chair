# Mouth detection update — 0.7

**Latest APK:** [0.8 stable gestures](android_phone/dist/WheelchairBlink-stable-gestures.apk)
adds a three-frame median and separate entry/release thresholds for the reported
open/closed flicker while holding still. Existing calibration is preserved.
The steps below still apply. Both the status labels and hold timer use the
stabilized signal; see the Android README for tests and limits.

The user reported live video works but the mouth status stays **not open enough**.
The previous detector required the model's `jawOpen` score to reach 0.65. That
single threshold was not validated for the user/camera. Version 0.7 adds guided,
individual mouth calibration based on the actual facial landmark geometry.

## Install and calibrate

Install [WheelchairBlink-mouth-calibration.apk](android_phone/dist/WheelchairBlink-mouth-calibration.apk)
over the existing app. Same application ID/signing certificate; version code 7.
No controller firmware or SD recording change is required for this update.

1. Join the camera Wi-Fi, open the app and start monitoring. Keep the whole face
   and mouth in view. The speaker can be tested separately; it is not needed to
   observe detection or complete calibration.
2. Rest the face naturally with eyes open. Tap **1. Calibrate and enable
   messages** and hold that position for two seconds, following the progress.
3. When prompted, open the mouth comfortably, then tap **2. Capture open mouth**.
   Hold this position for two seconds. Calibration must report success.
4. Relax again until **Ready**, then hold the mouth open for 1.5 seconds.
   The app should show **Mouth: open** and increasing hold progress, followed by
   **Gesture detected: Please help me**. Speaker delivery is reported separately.
5. If the gesture is detected but sound is absent, check the speaker status and
   use **Test: Please help me**. The DFPlayer needs the verified `0004.mp3` on its
   card and the existing `FACE AUDIO READY` setting. Disconnected/busy/disabled
   speaker requests are not queued for later playback.

Successful calibration enables facial messages and saves the reference numbers
on the phone. It does not store images. Repeat for a different user or changed
camera placement. If capture is interrupted or fails, the previous valid profile
is retained. **Cancel calibration** also keeps the previous profile.

## What changed

- With a saved profile, mouth opening uses the distance between landmarks 13
  and 14 divided by the distance between mouth corners 61 and 291. Coordinates
  are converted to pixels before calculating Euclidean distances, accounting for
  frame aspect ratio. Uniform image scale and roll do not change the ratio.
- Reject mouth corners/lip points outside the inner 96% of the frame and mouth
  widths under 18 pixels. Existing one-face, minimum face/eye size, sudden-position
  jump and 500 ms stale-inference checks remain.
- Each reference capture requires two seconds of consecutive usable samples,
  at least 12 frames. Invalid samples or gaps over 350 ms restart that capture;
  a 15-second deadline prevents indefinite capture. The user explicitly starts
  the open-mouth phase after the relaxed capture.
- The relaxed mouth reference is its 90th percentile; the open reference is its
  10th percentile. Require at least 0.10 ratio separation and no more than 0.12
  10th-to-90th percentile spread in either capture. Otherwise ask to recalibrate.
- The geometry ratio is normalized between these references. The existing
  detector's 0.65 mouth threshold now means 65% of the calibrated range, rather
  than a fixed neural-model jaw score. Before calibration, the old jaw score
  fallback remains available.
- Relaxed score medians normalize smile/eyebrow/eye baselines, avoiding a naturally
  elevated resting brow score blocking all neutral-face detection. Unstable
  relaxed scores and closed-eye relaxed references are rejected. Raw closed-eye
  scores >=0.50 cannot be normalized into an open-eye state. Triple-blink
  Emergency continues to use its original scores and timings.
- Calibration suppresses facial messages throughout capture/waiting and requires
  a fresh relaxed face afterward. It cannot announce a phrase merely because
  the open-mouth sample finished. Emergency blink processing remains available.
- Gesture recognition now runs independently of speaker readiness/busy status.
  The app reports **detected** separately from **sent** or **not sent**. Only a
  newly completed gesture attempts delivery; no playback after reconnection
  without a new gesture. Emergency still takes priority over facial phrases.

## Verification and limits

49 JVM tests passed (including 10 new calibration scenarios). Coverage includes
low jaw scores with a clear geometric opening, neutral brow bias, two explicit
capture phases, similar/unstable reference rejection, interrupted/reordered data,
timeouts, scale/roll invariance, profile persistence/validation, and closed-eye
protection. The existing blink, face-hold/cooldown and speaker protocol tests pass.
APK assembled; signature/version verified. Lint: 0 errors, 14 warnings, including
existing SDK/dependency/drawing/localization warnings and new localization warnings.
Log: `tmp/mouth-calibration-build.log`.

The phone was absent from ADB and the user said USB is unavailable. Version 0.7
has not been installed or calibrated on that phone by the assistant. No new
physical camera-to-speaker result is claimed. These tests validate software logic,
not accuracy for a particular person, face pose, lighting or camera. Live testing
after calibration is still required.

APK SHA-256:
`2bb5c25263aceeb37a65c620d960db4b64badff88d4791ab2a70d54027736ad7`.
