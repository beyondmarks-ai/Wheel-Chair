# Blink Assist 0.9: optional second face tracker

Install `android_phone/dist/WheelchairBlink-dual-tracker.apk` over the existing
Blink Assist app. The controller firmware, SD recordings, Bluetooth pairing and
camera Wi-Fi setup do not need changing for this app update.

## Try the new model

1. Stop monitoring, then select **New tracker (ML Kit contours)** under
   **Mouth and eyebrow tracker**.
2. Select **Quick: mouth 0.5 s / eyebrows 0.7 s** under **Gesture confirmation**.
   Existing users keep their old tracker and standard timing until they choose
   these options. Smile still requires a 1.5-second hold.
3. Start monitoring. Keep the whole face visible, with the camera straight ahead.
4. Tap **1. Calibrate and enable messages** while relaxed with eyes open. Hold
   still for the capture. When prompted, open the mouth comfortably and tap
   **2. Capture open mouth**. Each capture needs at least two seconds of usable
   observations. Completing calibration enables facial messages.
5. Let the face relax until the app says **Ready**, then try one gesture at a time:
   open mouth for **Please help me**, raise eyebrows for **Please come here**, or
   smile for **Yes**. Relax after each message; the five-second cooldown remains.
6. Test three deliberate blinks separately. The existing blink detector and
   emergency speaker priority are retained.

The displayed hold times begin after the tracker recognizes the movement. Frame
arrival, model processing, filtering and the controller's playback checks add
delay. Quick mode can also accept unintended movements; compare settings while
testing comfortable gestures with this user.

## Camera detail and fallback

The new tracker requires an uncropped face bounding box at least 200 pixels wide
and high. Google's contour guidance recommends a face around 200 x 200 pixels
and input images at least 480 x 360. The current ESP32-CAM stream has previously
been observed at 320 x 240, so it may not provide enough detail. Moving closer
only helps while the whole face stays visible. Upscaling does not restore detail.
The app reports missing contours, a small/cropped face, or stale results.
This update does not change camera resolution automatically.

If ML Kit cannot obtain a clear face, stop monitoring and select **Existing
tracker (MediaPipe)**. Quick confirmation also works with that tracker. Its old
calibration is preserved; ML Kit uses a separate saved calibration. The working
eye tracker runs in both modes. Using two models adds phone CPU/memory load, so
speed and accuracy must be compared on the actual phone, including blink FPS.

## Implementation and verification limits

- Bundled ML Kit Face Detection `16.1.7` runs locally without a model download or
  Azure connection. It supplies face contours, not a ready-made mouth/eyebrow
  gesture classifier or emotion/intent inference.
- ML Kit mouth aperture is divided by mouth width and calibrated against this
  user's relaxed/open references. Eyebrow height uses the brow-to-eye distance
  along the face's vertical axis, divided by eye-center spacing. A rise of 7% of
  that spacing is full scale; the existing entry/release filter uses these
  normalized values. This eyebrow mapping is a starting heuristic, not trained
  or validated for this user's facial mobility.
- MediaPipe still provides the existing eye/blink and smile signals. The
  `TrackingModel.java` configuration and `Gesture.java` are unchanged. The
  original model is still responsible for rejecting multiple visible faces.
- The optional ML Kit worker accepts one inference at a time, with at least
  120 ms between source-frame timestamps. Busy submissions are dropped. There
  is no image backlog and the blink thread never waits for a contour result.
- Contour samples must be no older than 500 ms, within 300 ms of the current
  MediaPipe frame, and spatially match its face. Each result is consumed once.
  Current eye closure or a completed blink alert cancels facial holds.
- Model errors and missing/stale contours pause facial messages. No automatic
  fallback changes model/calibration silently. Bluetooth reconnects do not replay
  missed facial messages. Existing controller delivery rules still apply.
- Unit tests cover contour scale/roll geometry, calibration normalization,
  sample freshness/reuse, quick hold timing, transient motion, eye closure,
  cooldown and the existing blink/speaker behavior. They do not establish live
  camera accuracy, patient suitability or end-to-end speaker latency.

Build log: `tmp/dual-tracker-build.log`. No phone was present in ADB during this
update; installation and live testing remain pending.

Verified on 2026-10-14: 65 JVM tests passed, zero failures/skips; lint reported
zero errors and 14 existing warnings. APK signature and version 9 were verified.
The signing certificate matches the previous APK, and both MediaPipe and ML Kit
model assets are bundled in the APK. `Gesture.java` and `TrackingModel.java`
SHA-256 hashes match their pre-change values.

APK size: 63,862,119 bytes. SHA-256:
`bb5768b1090deed23f7470a0df16cae1082e39222ced6fea9043edb579b66632`.

References:
- [ML Kit Android face detection](https://developers.google.com/ml-kit/vision/face-detection/android)
- [Face detection concepts and Euler-angle limitations](https://developers.google.com/ml-kit/vision/face-detection/face-detection-concepts)
- [Face contour API](https://developers.google.com/android/reference/com/google/mlkit/vision/face/FaceContour)
