# Blink Assist 0.17 — scheduling voice library

Generated with Sarvam MCP using `bulbul:v4-flash`, voice `simran_en_customer`,
English (India). Twelve new voices join the three existing scheduling voices.
All 15 presets are bundled for offline phone playback and selectable in the app.

Open **Today & care schedule → Add a reminder → Choose the spoken message**.
Choose a voice, preview it, set your own time and repeat days (or one date), then
save. Snooze, acknowledgement and optional guardian escalation remain available.
No care timings or medicine schedules are created automatically.

Installation update: version 0.17 was installed and launched on the Vivo V2031.
All 16 pack recordings (15 scheduling voices plus obstacle demo) were copied to
the mounted DFPlayer card `6434-6361` and verified with SHA-256. The five original
recordings were backed up and their card hashes remained unchanged. Verification
report: `speaker_audio/care-voices/latest-installation.json`. Wired playback still
needs checking after returning the card to the DFPlayer.

| Voice | DFPlayer file |
|---|---|
| Wake-up | `mp3/2900.mp3` |
| Morning care | `mp3/2901.mp3` |
| Breakfast | `mp3/2902.mp3` |
| Water break | `mp3/2903.mp3` |
| Position change | `mp3/2904.mp3` |
| Physiotherapy | `mp3/2905.mp3` |
| Lunch | `mp3/2906.mp3` |
| Toilet routine | `mp3/2907.mp3` |
| Health check | `mp3/2908.mp3` |
| Rest break | `mp3/2909.mp3` |
| Dinner | `mp3/2910.mp3` |
| Bedtime | `mp3/2911.mp3` |
| Tablet time | `mp3/2990.mp3` |
| Medicine time | `mp3/2991.mp3` |
| Doctor visit | `mp3/2992.mp3` |

**My own reminder text** still uses the installed Android speech voice; it does
not generate arbitrary Sarvam speech at runtime. A custom spoken file can be
exported for that reminder. New preset selections suggest Medicine, Meal,
Appointment or Activity as appropriate; user-entered titles are preserved.

For the wired speaker, power off the prototype and copy the ZIP's `mp3` folder to
the DFPlayer SD card. Keep the existing emergency/communication files. Reinsert
the card, reconnect the speaker, and mark the selected recording as copied in the
reminder. The app can also export the whole pack or a single saved reminder voice.
The voice files have now been copied to the card through the Android phone.

The pack also keeps the clearly labelled obstacle demo at `mp3/2999.mp3`.
Tracks 2900–2999 are now reserved for shared voices. The database upgrade preserves
plans/history and switches any older custom recording in that range to phone
speech, preventing a different new voice from being played for an old reminder.
Existing tablet/medicine/doctor readiness and track numbers are preserved.

Artifacts:

- `android_phone/dist/WheelchairBlink-scheduling-voices.apk`
- `speaker_audio/Scheduling-voices-v0.17.zip`
- `speaker_audio/care-voices/manifest.json` — exact phrases, durations and file hashes
- `speaker_audio/care-voices/wav/` — cached Sarvam source recordings

APK SHA-256: `BD6C9D7056D6A4020D55CFC2646CBF520E5EDBB527AA68E587E7C9CC6590EE93`.

Validation: 108 JVM tests passed and Android lint passed. All MP3 files passed
decoder checks and are between 1.04 and 4.32 seconds long. Twelve Android emulator
tests passed: ten database/history tests, all 15 preset resources decoded from the
APK, and an exact-time alarm completed the new health-check voice without marking
the activity done automatically. Physical phone
installation and DFPlayer playback of the new files still require the devices.
