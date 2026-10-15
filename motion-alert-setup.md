# Motion announcement

The main ESP32 now has code to read the IMU's accelerometer and gyroscope and
request **“Alert, motion detected.”** from the DFPlayer. It works locally; the
phone, camera and internet are not required for this motion alert.

The recording was copied to SD card `6434-6361` through Android ADB on
2026-10-14 and its SHA-256 verified. The Emergency recording was verified unchanged.
The card was safely unmounted from Android and the user subsequently confirmed
readiness with the prototype powered. Motion voice is now enabled and saved.
A live movement triggered track 2 at volume 30/30; audible verification is pending.
Emergency remains track 1.

## SD card setup and activation

1. Switch off prototype power before removing the DFPlayer card.
2. Use the Android phone or a card reader to copy
   [`speaker_audio/mp3/0002.mp3`](speaker_audio/mp3/0002.mp3) into the card's existing
   `mp3` folder. The destination must be `/mp3/0002.mp3`.
3. Keep `/mp3/0001.mp3` in place; it says **Emergency**. No formatting is needed.
4. Eject the card, put it back in the DFPlayer, and power the prototype.
5. After verifying the copy, send `MOTION AUDIO READY` followed by a newline on
   COM9 at 115200 baud. This readiness flag survives a restart. Then use
   `AUDIO MOTION TEST` to test the new recording once.

Recording: Microsoft Zira Desktop, generated offline, mono 44.1 kHz MP3 at
128 kbit/s, about 2.60 seconds. SHA-256:
`473d8ba996fd1d6a883589c6a12771ac36c7b7a15fc156280affa9cb8d21be48`.

## Detection behavior

- Keep the board still for two seconds after starting or reconnecting the IMU.
  Firmware learns gravity and the stationary gyro offset.
- Movement means acceleration, tilting or rotation of the board/chair where the
  sensor is attached. The IMU cannot establish constant-speed travel with no
  acceleration, or detect a person's movement if that does not move the sensor.
- Initial thresholds are 0.12 g above the slowly tracked gravity vector, or
  15 degrees/second after gyro-bias correction, lasting at least 100 ms.
- One movement episode produces one event. It needs 1.5 seconds of quiet to
  rearm and at least 10 seconds between events. Alerts suppressed while busy
  are not replayed later.
- A blink/USB Emergency preempts motion audio. Motion never interrupts an
  Emergency. The motion recording uses the approved volume 30/30 and a seven-
  second maximum playback window; Emergency keeps its existing five-second limit.
- Missing/invalid IMU samples reset calibration and suppress detection until
  valid, stationary readings return. Unsupported identity/configuration is rejected.

## USB controls

| Command | Effect |
|---|---|
| `MOTION ON` | Enable detection and start a fresh still calibration |
| `MOTION OFF` | Disable detection for this boot; stop active motion audio |
| `MOTION AUDIO READY` | Persist confirmation that `/mp3/0002.mp3` has been installed |
| `MOTION AUDIO OFF` | Persistently disable motion voice; use this if changing/removing the card |
| `AUDIO MOTION TEST` | Play track 2 once if motion audio was enabled |
| `AUDIO TEST` | Existing track-1 Emergency test |
| `AUDIO STOP` | Cancel the current audio operation |

The readiness flag is a setup confirmation, not automatic SD-file verification.
The DFPlayer UART cannot upload this recording to its card. In particular, this
implementation does not substitute the Emergency recording for a missing motion
recording.

## Implementation and checks

The observed IMU identity is `WHO_AM_I=0x70` at address `0x68` (MPU6500 family).
Firmware verifies configuration writes: PLL/wake, all axes on, +/-2 g,
+/-250 degrees/second, approximately 20 Hz low-pass filters and 100 Hz internal
sampling. It reads all six motion axes at 50 Hz without a blocking polling loop.
Azure telemetry receives current valid acceleration, motion state and event count
when the controller has an internet connection.

Host tests cover 13 motion scenarios, 15 audio scenarios, and the phone protocol
including Emergency priority over motion. These simulated tests do not establish
real-world detection accuracy.

### Hardware verification — 2026-10-14

- Compiled and uploaded to the main ESP32 on COM9; upload hashes verified.
  Firmware uses 1,799,997 bytes of flash and 72,260 bytes of static RAM.
- Startup configured the IMU successfully and completed stationary calibration.
- During the user's movement test, recorded event 1 with valid IMU samples.
  Continued movement remained one episode without repeated events.
- A subsequent 15-second stationary check showed `moving=0`, `calibrated=1`,
  and `events=1` throughout, confirming return to still and no extra events.
- Motion speech remains disabled (`audio_ready=0`) because the user deferred
  copying track 2 to the SD card. The new phrase has not been tested through the
  wired speaker. Copy and verify the file before enabling it as described above.

Logs: `tmp/motion-check/startup.log`, `tmp/motion-check/movement.log`, and
`tmp/motion-check/stationary-recheck.log`.

### SD copy completed — 2026-10-14

ADB detected the V2031 phone with public volume `6434-6361` mounted. Copied
42,674-byte track 2 to `/storage/6434-6361/mp3/0002.mp3`, synchronized writes,
and verified its SHA-256 against the value above. Track 1 remained unchanged:
`e503d63d210ecbad9cac6cceafbd7f6176d9c377de7611b9df4818ea3767d2d4`.
Android confirmed `public:179,1 unmounted 6434-6361` after safe ejection.
No format was performed. Awaiting physical reinsertion before enabling and testing
motion speech; no new voice command was sent to the controller during this copy.

### Motion voice activation and live test — 2026-10-14

After the user confirmed readiness, `MOTION AUDIO READY` returned
`audio ready=1 saved=1`. Stationary calibration completed. During the 40-second
live test, movement produced event 1, DFPlayer volume readback confirmed 30/30,
and firmware requested `/mp3/0002.mp3`. The board then returned to still without
additional events. Logs: `tmp/motion-check/activation.log` and
`tmp/motion-check/motion-voice-live.log`.

The module sent two finish notifications (`0x3D`) with value 45 rather than the
requested MP3-folder number 2. The driver did not accept these as matching
completion and reached its seven-second stop timeout. This may be a global file
index; that interpretation is not verified. Audible phrase confirmation from the
user remains necessary; playback completion is not confirmed by this driver.
