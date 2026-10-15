# Eye-gesture prototype

The ESP32-CAM supplies video. This laptop runs Google's pretrained MediaPipe
Face Landmarker, using `eyeBlinkLeft` and `eyeBlinkRight` scores to recognize
eye closure. Azure IoT Hub receives small test events; video stays local.
This detects visible facial movement, not emotions or emergency intent.

## Run a camera test

Point the camera at one face in good light, close enough to see both eyes.
From `D:\Wheelchair`:

```powershell
powershell -NoProfile -File .\blink_monitor\start-camera-test.ps1
```

The script temporarily joins `ESP32-CAM-MB`, shows a one-minute preview, then
restores the previous Wi-Fi and uploads queued test events. It also opens the
main ESP32 on COM9 and sends `AUDIO TEST 30` for each confirmed eye gesture.
For the default triple-blink gesture, this requests the DFPlayer's Emergency
recording immediately over USB. No internet connection is needed for sound.
Keep the main ESP32 connected by USB and the prepared SD card in the DFPlayer.
Press Q in the preview to stop early; any active speaker test is cancelled.
The laptop needs to stay on and run this software.

The controller must have the audio-enabled firmware with DFPlayer RX on
ESP32 TX2/GPIO17 and DFPlayer TX on RX2/GPIO16. It checks the module reply and
volume readback before requesting `/mp3/0001.mp3`. The prototype user confirmed
that volume 30/30 is suitable. Speaker status appears in the preview and logs.
Busy or disconnected speaker commands are not queued or automatically replayed.
Repeated event IDs are suppressed. Azure event delivery remains separately queued.

Use `-NoSpeaker` for a camera-only test, `-SpeakerPort COMx` if the controller's
port changes, or `-SpeakerVolume 16` for a quieter test. When calling `monitor.py`
directly, supply `--speaker-port COM9` to enable sound. This integration is for
the laptop test; the Android APK has not yet been connected to controller audio.

Default gesture (selected by the user): look with both eyes open for at least
0.4 seconds, deliberately blink both eyes three times, then keep them open for
roughly a quarter-second (60 ms reopening check plus 150 ms confirmation, rounded
to available frames). The preview confirms each counted blink and explains
resets. A three-second cooldown prevents immediate repeats. The scores are model estimates,
not probabilities of an emergency; thresholds need validation for the user.
The initial user test tracked 1,340 face frames but recognized no full sequence
at a closure threshold of 0.65. Observed open-eye scores were typically around
0.02–0.06 and repeated closure scores around 0.60. The prototype closure cutoff
was adjusted to 0.50; the open-eye cutoff remains 0.35. This is preliminary
tuning, not a validated calibration.

The default requires three closures lasting 0.06–1.2 seconds with at least two
closed-eye frames and a stable reopening. Allow up to two seconds between
confirmed blinks and five seconds for the full sequence. Unclear individual
closures no longer discard previously confirmed blinks within that deadline.
The stream reader consumes available bytes instead of waiting for a fixed-size
buffer to fill, and gesture timing uses frame-receipt timestamps. Natural blinks can
still match this pattern. Face loss, multiple faces, a small face, a substantial
face position change, or a frame gap over 0.6 seconds cancels a pending gesture.

Optional alternative, only when explicitly selected:

```powershell
powershell -NoProfile -File .\blink_monitor\start-camera-test.ps1 -Gesture long_close
```

The alternative requires a 2–5 second closure followed by reopening.

## Azure

Azure CLI provisioned `wheelchair-blink-gateway-01` in the existing F1 hub
`wheelchair-iot-7bc2336b`, resource group `rg-wheelchair-prototype`. No additional
paid Azure compute or notification service was provisioned. The ignored
`local/connection-string.txt` holds the device credential; do not share it.

```powershell
az iot hub monitor-events --hub-name wheelchair-iot-7bc2336b --device-id wheelchair-blink-gateway-01 --timeout 60
.\.venv-blink\Scripts\python.exe .\blink_monitor\outbox.py
```

Events are persisted to an ignored local SQLite outbox and marked sent only
after Azure acknowledges them. Consumers should deduplicate by `eventId`.
`blink_sos_test` means a camera gesture matched; `connectivity_test` is explicitly
synthetic transport validation. All events have `testMode=true`. No caregiver
messages, emergency calls or wheelchair movements are triggered.

The camera currently provides its own access point without internet. With the
laptop's single Wi-Fi adapter, Azure delivery happens after reconnection, not
immediately. Immediate delivery requires camera station-mode firmware on the
same internet-connected LAN, or a separate internet connection for the laptop.
Camera station-mode firmware has not been installed: it currently has power
wires only and no identified programming connection.

For an already reachable camera on the same LAN, run the monitor with its URL:

```powershell
.\.venv-blink\Scripts\python.exe .\blink_monitor\monitor.py --url http://CAMERA_IP:81/stream --preview
```

Run `outbox.py` to upload after stopping, or use `--flush`. Continuous live
cloud delivery is not implemented by this command yet.

## Model and verification

- Live test on 2026-10-07 processed 1,091 camera frames with zero detected usable
  faces and zero gesture events. This was an initial no-face check.
- The subsequent user-facing test tracked one face in all 1,340 processed frames,
  but did not recognize a complete triple blink at the initial 0.65 cutoff.
  After adjusting the cutoff to 0.50, a repeat captured 1,396 frames with no face
  and no events. A fresh snapshot showed room objects rather than the user's
  face, confirming camera positioning still blocks validation of the new cutoff.
  These earlier runs produced no gesture events.
- After the responsiveness update, a supervised live test processed 1,438 usable
  face frames, counted 26 blinks and recognized six triple-blink test sequences.
  All six events were acknowledged by Azure after Wi-Fi reconnection. The first
  event was `9e570c57-576c-4f14-8a40-97bccf877716`; these events came from the camera,
  not synthetic gesture input. This is successful prototype testing, not a
  measured false-positive/false-negative rate or validation of emergency intent.
- Eighteen gesture-state tests passed, including short score transitions, natural
  blinks, winks, frame gaps, loss of face, prolonged closure and cooldown.
- Eight serial-speaker tests passed, including a real gesture-state sequence
  that sends a command only after the third blink, deduplication, busy handling,
  response interpretation, disconnects, timeout, and stop cleanup. Together
  with the gesture tests, 26 tests passed for the speaker integration.
- First live speaker-integration test processed 1,912 camera frames (1,846
  usable face frames), counted 19 blinks and recognized three complete
  triple-blink sequences. All three speaker commands reached the main ESP32,
  but each failed because the DFPlayer did not return its volume response.
  Therefore no speaker playback was requested in this camera test. Azure
  acknowledged all three labeled test events after the usual Wi-Fi was restored.
  A subsequent manual speaker test also got no DFPlayer reply, indicating the
  failure persists independently of camera/gesture processing. The user was
  asked to power-cycle the prototype before retrying. End-to-end audible
  blink-triggered playback is not yet verified.
- Azure CLI monitor confirmed receipt of connectivity-test event
  `11fa70c7-48df-455f-9135-760a8b1942fd` from the new gateway identity. This was
  transport validation, not a detected blink or an emergency notification.
- Official documentation: https://ai.google.dev/edge/mediapipe/solutions/vision/face_landmarker
- Model: https://storage.googleapis.com/mediapipe-models/face_landmarker/face_landmarker/float16/1/face_landmarker.task
- SHA256: `64184e229b263107bc2b804c6625db1341ff2bb731874b0bcc2fe6544e0bc9ff`
- Python 3.11 environment: `.venv-blink`; pinned direct packages in `requirements.txt`.
- Run tests with `.\.venv-blink\Scripts\python.exe -m unittest discover -s blink_monitor -p 'test_*.py' -v`.

Logic tests and a model load are not clinical or user validation. Glasses,
lighting, head pose, camera frame rate and individual eye movement can affect
detection. Keep this as a supervised prototype and retain an independent way
to request help.
