# Wheel-Chair

Blink Assist is an Android and ESP32 wheelchair-assistance prototype. The phone
shows the ESP32-CAM stream, detects deliberate blinks, sends recording commands
to the controller over Bluetooth, and keeps health and care history locally.

## Features

- Home, Health, Care and Alerts dashboards with sensor cards, graphs and a dated timeline.
- Three-blink emergency announcements through an ESP32-connected DFPlayer speaker.
- Editable daily, weekday or one-date reminders, with Done/Taken, Snooze and Skip actions.
- Fifteen recorded scheduling voices, plus phone text-to-speech for custom messages.
- Local notifications and optional automatic guardian SMS through the phone's SIM.
- Manual voice controls: hold **Blink Assist** on Home, then connect the speaker.
- Optional Azure IoT telemetry and experimental cloud gesture processing.

Current Android version: **0.18-care-dashboard** (version code 18).

## Build the Android APK

Install **JDK 17**, **Python 3.8+**, and Android SDK **platform 36** with
**build-tools 35.0.0**. Android Studio can install the SDK. Set `JAVA_HOME` and
`ANDROID_HOME`, or let Android Studio create `android_phone/local.properties`.
The Gradle wrapper downloads Gradle 8.14.5. An internet connection is needed for
the first build and model download; no Azure or Sarvam credentials are needed.

From the repository root on Windows:

```powershell
python tools/prepare_android_model.py
.\android_phone\gradlew.bat -p android_phone testDebugUnitTest lintDebug assembleDebug
```

On Linux or macOS:

```sh
python3 tools/prepare_android_model.py
./android_phone/gradlew -p android_phone testDebugUnitTest lintDebug assembleDebug
```

APK: `android_phone/app/build/outputs/apk/debug/app-debug.apk`.
Install with `adb install -r <apk-path>`. Debug builds use the local machine's
debug signing key; updating an existing installation requires its original key.
Release distribution needs a separately configured private signing key.

GitHub Actions runs Android unit tests/lint, builds the debug APK and publishes
it as a workflow artifact. It also runs the host-side protocol tests.
Device instrumentation tests require a configured phone/camera; they are not
part of unattended CI and some tests intentionally play audio.

## Build the controller

Use Arduino CLI with **esp32:esp32 3.3.11** and these libraries:

| Library | Version |
| --- | --- |
| Azure SDK for C | 1.1.8 |
| ArduinoJson | 7.4.3 |
| DHT sensor library | 1.4.7 |
| Adafruit Unified Sensor | 1.1.15 |
| SparkFun MAX3010x Pulse and Proximity Sensor Library | 1.1.2 |
| hd44780 | 1.3.2 |

```powershell
Copy-Item azure_iot_live/iot_configs.example.h azure_iot_live/iot_configs.h
# Fill in your own Wi-Fi / Azure device values in the ignored iot_configs.h.
arduino-cli compile --fqbn esp32:esp32:esp32:PartitionScheme=min_spiffs --output-dir tmp/firmware azure_iot_live
```

Use the **Minimal SPIFFS** partition scheme. Wiring and controller protocol are
documented in [azure_iot_live/README.md](azure_iot_live/README.md). The camera
expects compatible ESP32-CAM MJPEG firmware; camera firmware is not included.

## Set up scheduled speaker announcements

1. Copy `speaker_audio/mp3/0001.mp3` through `0005.mp3` and the files from
   `speaker_audio/care-voices/mp3/` into the SD card's `mp3` folder.
2. Put the card in the powered-off DFPlayer, then power the controller on.
3. Pair the phone with **Wheelchair-Speaker** and connect it inside Blink Assist.
   Manual speaker mode can keep the connection open without camera monitoring.
4. In **Care**, choose a recorded voice and time. Under **Speaker & guardian
   options**, enable **Matching recording has been copied to the DFPlayer SD card**.
5. Allow notifications and exact alarms. Keep the phone powered on and the
   app's speaker connection active when the reminder is due.

Without the matching-card setting or a ready connection, the app uses the phone
voice. Custom text needs its own exported recording on the SD card to play through
DFPlayer. Bluetooth carries playback commands, not streamed audio.
For guardian SMS, save the recipient, enable the desired alerts and grant SMS
permission; the phone needs a working SIM. Manual demo buttons do not send SMS.

## Project layout

| Directory | Contents |
| --- | --- |
| `android_phone/` | Native Java Android app and tests |
| `azure_iot_live/` | Main ESP32 firmware |
| `speaker_audio/` | Ready-to-copy MP3 recordings and voice manifests |
| `blink_monitor/` | Earlier laptop-based blink detector |
| `azure/` | Optional Azure scripts and gesture function |
| `tests/` | Firmware host tests and cloud protocol tests |
| `tools/` | Build, audio and development utilities |

Some development utilities contain paths and device identifiers from the original
test setup; review those before using them on another device. Root-level update
notes are historical records, not fresh-clone build instructions.

## Prototype limits

Three-blink and manual speaker playback have been demonstrated on the prototype.
Mouth/eyebrow recognition remains experimental. The ultrasonic hardware has not
passed live validation; its manual obstacle sound is explicitly a **demo**.
DHT measures room temperature, and the pulse/oxygen estimates are not medical
measurements. This is not a validated emergency or autonomous wheelchair system.

Credentials, phone databases, personal test captures, firmware dumps and signing
keys are excluded from Git. The face model is downloaded from Google's versioned
MediaPipe model store with a pinned SHA-256. Third-party libraries, model and
Sarvam-generated recordings remain subject to their respective terms; publishing
this repository does not grant additional rights to those materials.
