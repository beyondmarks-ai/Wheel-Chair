# Care schedules and custom alerts — version 0.12

This update uses the existing phone, ESP32, DFPlayer and SD card. It adds no
hardware and does not require Azure. Existing health history, graphs, blink
detection, facial-message settings and recordings are retained.

## Make a reminder

1. Install `android_phone/dist/WheelchairBlink-care-schedules.apk` over the existing app.
2. Open **Today & care schedule**. Allow **Alarms & reminders** for precise times
   and notifications so the reminder actions and custom sounds can appear.
3. Tap **Add a reminder**, enter the words you want announced, choose the time,
   and select a single date or repeat weekdays. Choose Activity, Meal,
   Appointment or Medicine. No example medicines or times are preloaded.
4. Choose an acknowledgement deadline between 5 and 120 minutes. A guardian
   escalation is optional. Save the reminder.
5. When due, use **Done / Taken**, **Snooze 10 minutes**, or **Skip** on Today or
   the notification. Taken is a user/caregiver report; the app cannot establish
   whether a medicine was swallowed. Enter only an already prescribed schedule.

Editing or deleting cancels that plan's outstanding actions and future alarms.
History remains stored. The simple Today page links to care history, a shareable
daily report, existing health graphs and guardian settings. The report includes
recorded care outcomes, valid BPM statistics, oxygen estimates, events and
guardian-alert attempts. Missing measurements are not replaced with zero.

## Speak through the existing wired speaker

The DFPlayer cannot receive live phone speech or new audio files over its UART
connection. Its SD card must contain a matching recording for each custom title.

1. Save the reminder, reopen **Edit reminder**, then tap **Export spoken recording
   for SD card**. Android text-to-speech creates the words “Reminder” plus your
   saved title. An installed voice for the phone's language is needed.
2. Save the WAV file. Copy it into the existing card's `mp3` folder using the
   phone/card access method. For example, `mp3/0101.wav`; each plan has
   its own displayed number. Keep that exact filename and avoid another file
   with the same four-digit prefix. Keep existing files `0001` through `0005`.
3. Switch off the prototype before moving the SD card. Return it to the DFPlayer,
   power on, then select **Matching recording has been copied...** in that
   reminder and save. Changing the title clears this selection so an old
   recording is not knowingly used for new words.
4. The main ESP32 needs the new care firmware (see below). Start monitoring in
   the app and keep its paired Bluetooth connection active. The phone owns the
   schedule and sends a track command at the scheduled time.

Exports are 16-bit mono PCM WAV at 22.05 kHz, at most 18 seconds. These settings
target DFPlayer WAV support; playback still needs a real-card listening test on
the particular module. A matching-recording checkbox is a user confirmation,
not an automatic SD-file inspection. `FINISHED` is a module response, not proof
that someone heard it.

If no matching recording is selected, the controller firmware lacks care
support, Bluetooth is unavailable, or a request is busy, the phone announces the
reminder using its own text-to-speech. Once a wired request has been accepted,
an unconfirmed/failed result is shown in history; it is not replayed as a second
announcement. Three-blink emergency audio preempts routine wired playback and
phone reminder speech. Interrupted routine playback is not automatically resumed.

## Custom sounds and guardian alerts

The app includes four original sounds: scheduled care, unacknowledged care,
three-blink emergency, and movement. Android notification-channel, volume and
Do Not Disturb settings still apply. Ordinary monitoring status stays quiet.
Movement sounds are limited to one per 30 seconds and can be disabled in
**Sounds & reminder setup**. Movement data needs compatible main-controller
firmware and active monitoring.

In **Guardian alerts**, save the consenting guardian's international number and
enable guardian SMS, granting Android SMS permission. Three-blink emergencies
then use the phone's SIM. Additional switches enable:

- Skipped/unacknowledged care notices. Also select the guardian option on each
  applicable reminder. There is at most one care SMS attempt per occurrence.
- Movement notices, at most one attempt per five minutes. Movement is described
  as a notice, not a confirmed emergency.

SMS requires a default SMS SIM, signal and balance, but no internet. New care and
movement options start off. Care SMS omits medication names and doses. Delivery
history distinguishes requested, sent and delivered; delivered is not read.
No SMS is inferred from a BPM value or facial expression. Ordinary SMS sound on
the guardian's phone is controlled by their SMS settings: this APK cannot force
a different sound on that other phone. No SMS is sent by the build/test scripts.

## Timing and background operation

Reminders use Android `setAlarmClock`, local phone time, and exact-alarm
permission. On Vivo, allow background activity/autostart for the app. Keep the
phone powered on; Force stop blocks scheduled work until the app is reopened.
Bluetooth/wired announcements also require monitoring to remain active.

Restart, app-update and time-zone receivers rearm saved schedules. Alarms received
over two minutes late are marked delayed without late speech or replayed SMS.
On recovery, expired acknowledgement deadlines are recorded without replaying
old guardian messages. Snoozes retain the original history entry and get a new
playback ID; duplicate broadcasts do not repeat a recording or SMS. During DST,
an absent local time shifts forward and a repeated time uses its first occurrence.
These are prototype reminders, not a guaranteed medical alarm service.

## Controller firmware

Prepared files: `tmp/care-firmware/`. Identify the **main ESP32** port first; do
not flash the ESP32-CAM. For a confirmed main-controller COM9 connection:

```powershell
arduino-cli upload --fqbn 'esp32:esp32:esp32:PartitionScheme=min_spiffs,UploadSpeed=115200' --port COM9 --input-dir .\tmp\care-firmware .\azure_iot_live
```

The backward-compatible additions are `CARE HELLO` → `CARE_MESSAGES 1 READY`
and `REMIND <101..2999> <UUID>`. Existing SOS, phrase and health commands remain.
Reminder playback has a 20-second controller deadline and a 30-second app reply
deadline; emergency playback retains priority. No audio is streamed over UART.

## Verification

The build runs JVM timing, WAV conversion and speaker protocol checks, Android
lint, host DFPlayer/phone/face/motion protocol checks and the ESP32 compiler.
Device storage tests use `care-validation-only.db`, which is deleted after each
test; they do not modify the user's schedule, send notifications or send SMS.
Actual scheduled phone speech, SD playback and guardian delivery need separate
device tests; see the final delivery notes for which were completed.

### Delivery verification — 14 October 2026

- Version 12 (`0.12-care-schedules`) installed successfully over version 11 on
  Vivo V2031. Today, the reminder editor, daily report and guardian options were
  opened and checked. The schedule is empty after testing.
- 91 JVM tests passed; lint reported 0 errors and 34 warnings (principally
  existing/programmatic UI translation warnings and dependency updates).
- Seven isolated SQLite device tests passed. Two additional live device tests
  passed: an exact alarm created a reminder and completed phone speech without
  marking the activity Done, and Android speech exported valid mono 22.05 kHz
  WAV. The user confirmed hearing the labelled test reminder. Temporary test
  plans, occurrences and test-only activity rows were removed.
- Guardian SMS remains disabled, with no SMS permission granted and no test SMS
  sent. Custom sounds on the guardian's separate phone are not controlled here.
- Host tests passed: 17 DFPlayer playback scenarios, phone/care command checks,
  facial command checks, and 13 movement cases. Main-controller firmware builds
  at 1,802,329 bytes (91%) flash and 72,268 bytes (22%) static RAM.
- Initially no main ESP32 serial port was detected. During the later connected
  component check on 14 October, the care firmware was uploaded to COM9 and
  flash hashes verified. Phone health readings and Bluetooth speaker commands
  were checked successfully. Scheduled wired WAV playback still needs a matching
  recording on the existing DFPlayer SD card and an end-to-end scheduled test.
  See [the latest component check](component-check-2026-10-14-latest.md).

APK SHA-256:
`04c4c2686785827303e8692c72b82faf506e0650f66c2ff6ebb5007680e4bd57`.

Controller application binary SHA-256:
`4336ed95beaadbc9adbff9f5ac9c33a2bff6e358b6d860f2a32cfeb2fcf53799`.

Build logs: `tmp/care-final-build.log`, `tmp/care-firmware-build.log`,
`tmp/care-alarm-device-test.log`. Physical Today screenshot: `tmp/care-today.png`.
