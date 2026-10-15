# Health history and guardian SMS - version 0.11

The app adds **Health & history** and **Guardian alerts** buttons to the simple
home screen. The existing blink detector, facial calibration and speaker settings
are retained.

## What is included

| Feature | Behaviour |
| --- | --- |
| Current readings | Pulse, oxygen estimate, room temperature, humidity, obstacle distance and movement |
| History graphs | Last hour, 24 hours, 7 days, 30 days, or all recorded readings |
| Stored readings | Local phone database, sampled about every 5 seconds while monitoring; no automatic deletion |
| Missing readings | Shown as unavailable, with gaps in graphs; stale values are not presented as current |
| Event timeline | Earlier saved blink events, new facial messages, movement events and guardian SMS results |
| Export | All saved sensor measurements to a CSV file chosen by the user |
| Guardian alert | An explicitly enabled automatic SMS after three deliberate blinks |

These are prototype sensor measurements. The DHT sensor measures the **room**, not
body temperature. MAX30102 oxygen is an estimate, not a clinical measurement.
No automatic medical threshold triggers were added. The history contains sensor
values and events, not stored video or audio recordings.

## Install and enable sensor data

APK: `android_phone/dist/WheelchairBlink-health-guardian.apk`.

Live sensor readings need the updated main ESP32 firmware, built in
`tmp/health-guardian-firmware`. The previous controller firmware only exchanged
speaker commands with the phone; installing the APK alone cannot supply BPM data.
No new hardware or cloud subscription is needed for the Bluetooth data link.

When the main controller is connected by USB, check its port and upload:

```powershell
arduino-cli board list
arduino-cli upload --fqbn "esp32:esp32:esp32:PartitionScheme=min_spiffs,UploadSpeed=115200" --port COM9 --input-dir .\tmp\health-guardian-firmware .\azure_iot_live
```

Use the actual main-controller port; do not use the ESP32-CAM programming port.
The existing Minimal SPIFFS partition layout and saved settings are retained.

In the app, select the paired Wheelchair-Speaker as before, then start monitoring.
Open **Health & history**. Place a fingertip gently on the pulse sensor to obtain
valid optical readings. A disconnected or unsupported sensor remains unavailable.
Opening the history screen does not stop the foreground monitoring service.

Older blink events already stored by the previous APK are visible. Historical
BPM cannot be backfilled from periods when it was not recorded. New sensor history
starts when the updated controller is connected. Longer graph periods use time
bucket averages; partial/missing buckets are gaps. CSV contains the stored samples.

## Set up automatic guardian SMS

1. Open **Guardian alerts**.
2. Enter the guardian's number with its country code, such as `+91` followed by
   the mobile number. Use a guardian who has agreed to receive alerts.
3. Tap **Save guardian number**. Saving a number leaves automatic alerts off.
4. Enable **Automatically SMS this guardian after three blinks** and allow Android
   SMS permission. On dual-SIM phones, select a default SMS SIM in Android settings.
5. Tap **Send a test SMS to saved number**. Review the recipient, then confirm
   **Send test**. This deliberately sends a real SMS labelled `TEST ONLY`.
6. Verify receipt on the guardian's phone before relying on the setup. The app's
   **Health & history** page shows the request and carrier response.

The user's phone needs a working SIM, cellular signal and sufficient SMS balance.
Camera Wi-Fi can stay connected. SMS does not require Azure or an internet data
connection, and the guardian does not need this APK. Carrier charges may apply.

Only completed three-blink signals send automatic guardian SMS. Facial phrases,
motion announcements, sensor values and the speaker-test button do not. SMS and
the wired speaker are independent, so a Bluetooth speaker failure does not prevent
the SMS request. A blocked or incomplete blink detection still cannot generate an
alert. Automatic SMS is off until explicitly configured and permitted.

The SMS identifies a wheelchair prototype help request, its time and a short event
reference. It does not include the user's medical readings or camera images.

- **Requested**: Android has been asked to send; success is not yet confirmed.
- **Sent**: the carrier accepted it; delivery to the guardian is not confirmed.
- **Delivered**: a successful carrier delivery report arrived; this is not a read
  receipt or a guardian acknowledgement.
- **Not sent / delivery failed**: the request failed or the carrier reported failure.

Some carriers do not provide delivery reports. Missing reports remain unconfirmed.
The app does not replay old alerts after restart/reconnection or silently retry
failed SMS. Each event ID is inserted once before submission; delayed queued work
older than 30 seconds is recorded as expired rather than dispatched.

## Implementation and checks

The paired Bluetooth connection polls `HEALTH?` about every two seconds while
audio is idle. After the existing HELLO handshake, the controller replies:

```text
HEALTH 1 <uptime_ms> <bpm> <oxygen> <room_C> <humidity> <distance_cm> <moving> <finger> <motion_event_count>
```

Missing numeric values and unknown movement are `-`. Booleans are `0` or `1`.
The controller uses the same freshness/validity checks as its LCD and Azure data.
The phone validates values, rejects repeated snapshots, and marks current data
stale after six seconds. Speaker commands keep priority; queries never play audio.

73 JVM tests passed, including missing/out-of-range sensor input, freshness,
international phone-number validation, one-SMS message lengths and conservative
delivery-report interpretation. Existing blink tests passed. Native host tests
passed for DFPlayer playback, phone protocol, facial messages and motion detection.
The firmware compiled to 1,802,141 bytes (91% of the app slot), with 72,268 bytes
of static RAM. Build logs are in `tmp/health-guardian-build.log` and
`tmp/health-guardian-firmware-build.log`.

At the initial installation check, version 0.11 installed on the Vivo V2031
(Android API 33); SMS permission remained ungranted. No SMS was sent by the coding
agent. The phone was locked, and no main ESP32 serial port was present. Physical
sensor streaming, completed phone UI checks and carrier delivery therefore remain
pending until the devices are ready.

Follow-up phone check: after the user unlocked the Vivo, the final APK was
reinstalled over the existing app and version 11 was verified. Main, Health &
history, and Guardian alerts opened successfully. The Health page correctly
showed unavailable current readings and an empty measurement graph; automatic
guardian SMS remained off. The Guardian page was left open for user setup.
Live controller data, populated graphs and SMS delivery remain untested.

Final artifact checks: APK signature verified, version 11 verified on the phone,
73 tests passing with no failures/skips, and lint 0 errors / 27 warnings (resource
strings, existing drawing allocation and tool/version configuration warnings).

APK SHA-256:
`6b45e35c0b42ab93a87ae6072e5087d9dc1cf17e7a1e1fbaa166f4f6edf467e8`.
Controller application binary SHA-256:
`fb243c9b7b5dd9e4199ced2291a5abe51fe53482534dc2c0963b739747166810`.

Useful later additions are a guardian acknowledgement button, escalation to a
second agreed contact, and a guardian companion app with push alerts. Those need
additional recipient/backend setup and are not represented as delivered features.
