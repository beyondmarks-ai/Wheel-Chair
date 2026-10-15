# Wheelchair sensor telemetry

The MAX30102 and LCD share SDA 21 / SCL 22 at 100 kHz. DHT11 uses GPIO 4.
Wi-Fi credentials and the Azure device key are in the ignored `iot_configs.h`.

For a new checkout, copy `iot_configs.example.h` to `iot_configs.h` and fill in
your own values. The root [README](../README.md#build-the-controller) lists the
tested Arduino core and library versions. Never commit the filled-in file.

The current firmware supports scheduled recordings with `CARE HELLO` /
`CARE_MESSAGES 1 READY` and `REMIND <track> <event-id>`. The phone schedules the
alarm and sends the command; the controller does not store a standalone agenda.

## IMU motion announcement

The motion implementation and SD setup are described in
[`../motion-alert-setup.md`](../motion-alert-setup.md). `MotionDetector.h` handles
rest calibration, gravity/gyro motion thresholds, rearming and cooldown. The
main loop configures and verifies the MPU6500-family registers and samples at
50 Hz. DFPlayer track 2 is the separate motion recording; Emergency track 1 takes
priority. Voice is gated by the persistent `wheelchair/motionaudio` setup flag,
which defaults false until the new file is copied to the DFPlayer card.

## Android emergency speaker

Android 0.11 adds requested-only sensor snapshots over the same paired link.
After HELLO, `HEALTH?` returns the versioned pulse/oxygen/room/distance/movement
record described in [health and guardian setup](../health-guardian-setup.md).
The existing validity gates are reused; unavailable readings are `-`. No sensor
query plays a recording. Guardian SMS is sent by the phone, not this firmware.

Android 0.5 adds deliberate facial messages through `PHRASE <3|4|5> <uuid>`.
The new controller advertises recording readiness after HELLO, and defaults
facial playback off until `FACE AUDIO READY` persists the `wheelchair/faceaudio`
flag. Emergency preempts other audio; facial messages cannot interrupt it.
See [facial-message setup](../facial-messages-setup.md) for the three recordings,
APK and validation. Existing SOS commands remain compatible.

`PhoneSpeaker.h` adds paired Bluetooth Classic SPP named **Wheelchair-Speaker**.
The Android 0.2 APK keeps camera Wi-Fi connected while sending a local `SOS <uuid>`
command. No internet is needed for this path. DFPlayer remains on RX2 GPIO16 / TX2
GPIO17 and plays `/mp3/0001.mp3` at volume 30/30 using the existing verified UART
driver. No recording plays automatically at boot, pairing, or reconnect.

The protocol requires `HELLO` / `WHEELCHAIR_SPEAKER 1` before commands. Responses
are `SPEAKER <uuid> CHECKING|PLAY_REQUESTED|FINISHED|UNCONFIRMED|ERROR|BUSY|DUPLICATE|STOPPED`.
Only one playback can be active. The most recent 16 IDs are remembered in RAM;
no command is persisted or queued. Disconnect cancels a phone's active request.
Bluetooth uses Secure Simple Pairing without controller keyboard/display input;
pair only the intended phone. Android connects to a selected bonded device using
secure RFCOMM. The wired USB `AUDIO TEST` / `AUDIO STOP` commands still work.

Build with the **Minimal SPIFFS** partition scheme to fit Bluetooth and Azure:

```powershell
arduino-cli compile --fqbn esp32:esp32:esp32:PartitionScheme=min_spiffs --output-dir .\tmp\speaker-check\phone-bluetooth-firmware .\azure_iot_live
arduino-cli upload --fqbn "esp32:esp32:esp32:PartitionScheme=min_spiffs,UploadSpeed=115200" --port COM9 --input-dir .\tmp\speaker-check\phone-bluetooth-firmware .\azure_iot_live
```

The partition scheme reserves 1.875 MiB per app slot, preserves the NVS offsets,
and reduces the unused SPIFFS area. This project does not use SPIFFS. Host tests
in `tests/dfplayer/phone_test.cpp` cover handshake, request/deduplication, busy
rejection, response correlation, malformed input, disconnect and stale commands.

2026-10-08 verification: final build uses 1,794,961 bytes of application space
and 72,140 bytes of static RAM. Uploaded to COM9 with verified flash hashes.
Bluetooth startup succeeded; a USB audio test confirmed DFPlayer volume 30/30
and acknowledged playback. LCD RAM verification remained 32/32 and DHT readings
continued. Optical sampling had one startup interruption during Bluetooth
initialization, then remained about 25 samples/s with no further fault increments
during the 25-second check. Actual phone Bluetooth/video coexistence and sound
from a phone blink remain unverified. Logs are in
`tmp/speaker-check/phone-bluetooth-upload.log` and `phone-firmware-check.log`.

## Ultrasonic distance

- User confirmed HC-SR04 TRIG on GPIO5 and ECHO on GPIO18. Initial testing used
  3.3 V; the user subsequently reported connecting the sensor as instructed
  after discussing a 5 V supply and ECHO divider. Actual levels and resistor
  values require physical verification. Standard HC-SR04 modules specify 5 V.
- Before changing the sensor supply to 5 V, protect ECHO: sensor ECHO through
  1 kOhm to GPIO18, and 2 kOhm from GPIO18 to common GND, or use an appropriate
  level shifter. Never feed a 5 V echo directly into the ESP32.
- ECHO uses plain INPUT mode. An internal pull-down would load the external
  divider and could make its HIGH signal too low, particularly with 10 kOhm
  divider resistors.
- Trigger at most every 100 ms. Capture ECHO edges with an interrupt, avoiding
  a blocking wait that could interrupt optical sampling. Discard an in-flight
  measurement before the DHT driver's interrupt-masked read.
- Reject stuck-high ECHO, missing responses after 30 ms, out-of-range pulses,
  and measurements older than 500 ms. Invalid distance is null in Azure;
  it must not be interpreted as an obstacle-free path.
- Serial `ULTRA` diagnostics report validity, centimeters, pulse width, and
  status once per second. Azure includes `ultrasonicReadingValid`,
  `ultrasonicStatus`, and `obstacleCm`. `ultrasonicConnected` reflects a fresh
  usable echo, not definitive physical connection/disconnection.
- The LCD continues showing the existing temperature/pulse displays. This
  update measures distance only and does not stop motors or sound an alarm.
- Eight host tests exercise the actual sensor class with simulated GPIO/time:
  normal distance followed by timeout, stuck high, out-of-range pulses,
  freshness, late edges, DHT interruption, clock rollover, and delayed service.
  These tests do not establish physical sensor accuracy.

### Ultrasonic test, 2026-10-07

The build passed and was uploaded to COM9 at 115200 baud; the flasher verified
the written hashes. With the user reporting a flat object about 30 cm away,
15 seconds of serial capture returned no valid ultrasonic echoes (14 reports
of `no_echo`, one `sampling_interrupted`). The sensor remained at the user's
reported 3.3 V supply. This is below the standard HC-SR04 supply specification;
test again with correct power and ECHO voltage protection before concluding
the sensor is faulty. GPIO wiring was user-confirmed, not continuity-tested.

DHT readings were 31.0-31.9 C / 40% humidity; the LCD RAM check remained 32/32.
Optical acquisition remained approximately 24-26 samples/s with zero reported
sample faults. No finger was present, so this did not revalidate pulse estimates.
The new Azure distance fields were not independently checked in Azure during
this local test. Raw filtered logs are in ignored `tmp/ultrasonic-check/`.

Full flash backup attempts at 460800 and 230400 baud failed with read corruption;
neither is a valid new backup. The earlier full backup remains available, and
the pre-ultrasonic source was preserved in the ignored
`tmp/ultrasonic-check/rollback/azure_iot_live` directory for rebuilding.

### Follow-up with two 10 kOhm divider resistors

The user subsequently confirmed two 10 kOhm resistors and a flat target about
30 cm away. Initial capture showed mostly no echo and one isolated 348.9 cm
reading before target confirmation. The confirmed-target capture produced no
valid distance. Review found ECHO was configured with INPUT_PULLDOWN, which
loads the external divider; it was changed to plain INPUT in both sketches.
The eight timing tests and main firmware build passed. COM9 upload at 115200
baud completed with verified flash hashes.

After that change, a further 20-second capture still reported no valid target
distance: 18 no_echo reports, one sampling_interrupted, and one rejected
26328 us pulse. Thus removal of the pull-down was appropriate but did not
resolve the observed fault. LCD RAM remained 32/32, DHT updated normally, and
optical sampling remained approximately 24-26 samples/s with zero faults.
Logs: `tmp/ultrasonic-check/input-mode-30cm.log` and the two preceding
`rewired-*.log` captures. Actual 5 V power, common ground, divider junction,
and GPIO continuity still require a physical check. No new Azure check was
performed in this follow-up.

Two equal 10 kOhm resistors ideally provide 2.5 V from 5 V, leaving little
margin above the ESP32's specified HIGH threshold at a 3.3 V supply. Supply
drop and resistor tolerance can matter. Prefer the 1 kOhm / 2 kOhm divider
described above, or 10 kOhm / 20 kOhm, when available. Neither a valid range
check nor this divider calculation proves reliable obstacle detection.

## Room temperature and humidity

- The connected DHT11 data pin is GPIO 4. Read a temperature/humidity frame every
  2.5 seconds in local sensor servicing, including Wi-Fi/time-sync wait loops.
- With no finger on the MAX30102, the LCD displays `Room: 32.6 C` and
  `Humidity: 41%` (example values). Finger contact switches to pulse acquisition.
- A failed read clears the cached values and displays a DHT error. Samples older
  than six seconds are unavailable. Azure gets null values when unavailable and
  a `dhtReadingValid` flag; telemetry uses the same cache as the LCD.
- These are ambient readings, not body temperature. DHT11 accuracy is limited;
  displaying one decimal place does not establish that level of accuracy.

## Pulse acquisition

- Start with LED amplitude 60 (about 12 mA), ADC range 16384 and 411 us pulses.
  Amplitude 24 detected finger contact but produced unstable pulse estimates;
  the higher setting increases signal while retaining clipping protection.
- Average four samples at 100 Hz for 25 paired red/IR readings per second.
- Read each six-byte pair from the hardware FIFO. Do not use separate `getRed()`
  and `getIR()` calls: those wait for different new samples.
- Calculate over four seconds, refreshing once per second. Clear results after
  finger removal, clipping, read failure, or loss of sampling continuity.
- Reduce LED amplitude if either optical channel reaches 250000 counts.
- LCD and Azure use the same freshness and validity checks. Each vital is shown
  independently, so unavailable SpO2 does not suppress valid heart rate.
- Require three consecutive estimates within 8 BPM (or 10%, whichever is larger)
  for heart rate and within 3 percentage points for SpO2. Display their median.
  Invalid or inconsistent windows immediately hide that vital. Initial results
  need at least six seconds of uninterrupted finger contact. Consistency alone
  does not establish medical accuracy; overlapping windows are not independent.
- While estimates are unstable the LCD says `Finger detected / Hold still...`.
- With no finger, the LCD displays the DHT room temperature and humidity.
- Startup explicitly enables the LCD display and checks 32 visible display-memory
  positions by writing and reading both rows. Serial diagnostics report the
  initialization status, matched character count and text sent to the display.
- Network messages are queued asynchronously. Local sampling and display continue
  during Wi-Fi/time synchronization waits.

This particular board returned FIFO overflow=31 even immediately after resetting
the FIFO with read/write pointers at zero. The firmware records that register but
uses a 500 ms polling deadline and backlog checks to reject sample loss. A 32-entry
FIFO holds 1.28 seconds at the configured 25 Hz rate.

`OPT` serial lines show optical levels, clipping, LED amplitude, samples/second,
IR window range, accepted vitals (-1 means unavailable), raw algorithm estimates,
and sampling faults. Raw estimates are diagnostic only and may be artifacts.
These estimates have not been validated against a reference medical instrument.

## Live verification, 2026-10-07

- DHT LCD update compiled and uploaded with verified flash hashes. Serial showed
  room temperature 32.1–32.6 C and humidity 41%, updating on the LCD. Azure
  received `dhtReadingValid=true`, 32.2 C / 41% at timestamp 1791350852 and
  32.6 C / 41% at 1791350857. Optical sampling remained around 25 Hz with zero
  reported sampling faults during the test. Wi-Fi outage and DHT-disconnection
  behavior were reviewed in code but not physically exercised.
- ESP32 build and COM9 upload passed; the flasher verified written data hashes.
- At LED 24, finger contact gave IR about 20000–21700, compared with roughly
  100 without contact. Sampling stayed around 25 Hz with zero reported faults.
  Raw BPM varied widely; consistency checks suppressed those unstable windows.
- At LED 60, contact gave IR about 55000–56500 without saturation. Azure received
  heartRate 78 / spo2 99 at timestamp 1791350126, and 78 / 100 at 1791350131.
  A subsequent inconsistent heart-rate window correctly transmitted null.
- Removing the finger cleared both vitals; Azure received fingerDetected=false
  and null heartRate/spo2 during the LED-24 test.
- LCD output uses the same accepted values as Azure. The user subsequently
  confirmed the screen works after adjusting the backpack contrast trimmer.
- Follow-up for a yellow backlight with no visible text: the updated firmware
  passed LCD initialization (status 0) and read back 32/32 test characters.
  The current text sent is `Place finger / HR:-- SpO2:--`. Communication with
  the LCD controller works; visible output still needs confirmation. Adjust
  the physical contrast trimmer on the backpack. If text remains invisible,
  check the display's supply and panel/backpack connections; passing RAM
  readback does not prove those analog/display functions work.

This establishes acquisition and telemetry, not continuous or accurate vital
measurement. Estimates remain intermittent. Even three agreeing estimates can
be artifacts (an earlier motion/transient window passed at 187 BPM). A reference
pulse oximeter and more robust signal-quality validation are needed before
these readings can support health alerts or decisions.

## Build and upload

```powershell
arduino-cli compile --fqbn esp32:esp32:esp32 .\azure_iot_live
# Run only after compilation succeeds; verify the current serial port first.
arduino-cli upload --fqbn esp32:esp32:esp32 --port COM9 .\azure_iot_live
```
