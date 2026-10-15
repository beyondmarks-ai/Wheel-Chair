# DFPlayer announcement

## Current recordings: Sarvam Simran voice, 2026-10-14

The user approved the conversational `simran_en_customer` voice from Sarvam
`bulbul:v4-flash`. The five wired-speaker recordings were regenerated through
Sarvam MCP and installed through ADB on card `6434-6361`:

| File | Spoken message |
| --- | --- |
| `mp3/0001.mp3` | Emergency. |
| `mp3/0002.mp3` | Alert, motion detected. |
| `mp3/0003.mp3` | Yes. |
| `mp3/0004.mp3` | Please help me. |
| `mp3/0005.mp3` | Please come here. |

Each recording uses 44.1 kHz mono 128 kbit/s MP3, with speech loudness normalized
to -16 LUFS and a -1.5 dB true-peak target. Durations are 0.64–1.52 seconds, within
the existing controller playback windows. Existing track numbers and firmware
volume settings are unchanged. All five SD files were verified with SHA-256
after copying; the card was safely unmounted and confirmed unmounted.

Original SD and project files are backed up in
`backups/pre-sarvam-20261014-193857/`. Sources, final checksums and copy time are
in `sarvam-simran/manifest.json`. The current full pack is
`Sarvam-Simran-recordings.zip`; `FacialMessages-recordings.zip` also contains the
new voice for tracks 3–5. Preparation and installation scripts are in `../tools/`.

Switch off the prototype before inserting the card into the DFPlayer. Then
power it on and use the existing app's **Test speaker** to verify audible
Emergency playback. These five recordings do not require a new APK or new
firmware. Live playback of this new pack is pending reinsertion. Custom schedule
recordings and the phone's built-in text-to-speech have not been changed by this
SD-card update. The following sections describe earlier recordings and tests.

## Facial recordings added 2026-10-14

`0003.mp3`: Yes; `0004.mp3`: Please help me; `0005.mp3`: Please come here.
Prepared locally in `mp3/` and packaged in `FacialMessages-recordings.zip`.
Copied through ADB to SD card `6434-6361` on 2026-10-14; all three hashes verified.
Emergency `0001.mp3` and motion `0002.mp3` were verified unchanged. The card was
safely unmounted. Reinsertion, facial-audio activation and audible tests are pending.
See [facial-message setup](../facial-messages-setup.md) for copy and activation.

## Motion recording added 2026-10-14

`mp3/0002.mp3` says **Alert, motion detected.** It was copied through Android
ADB to SD card `6434-6361` on 2026-10-14 and SHA-256 verified. The existing
`mp3/0001.mp3` Emergency recording was verified unchanged. The card was safely
unmounted. After the user's readiness confirmation, motion voice was enabled and
a real movement requested track 2 at volume 30/30. Audible confirmation is pending;
the module's finish index differed from the requested folder track number (see setup log).
No formatting was needed. See
[`../motion-alert-setup.md`](../motion-alert-setup.md) for copying, activation,
priority behavior and the distinction between preparation and audible verification.

## Current prepared card

A different card was connected later on 2026-10-07: SanDisk, 15,931,539,456
bytes (marketed as 16 GB), UUID `6434-6361`. Android mounted it as `vfat`,
which is a FAT filesystem suitable for the DFPlayer's supported FAT16/FAT32
family. It did not need reformatting. Its approximately 4.4 GiB of existing
files were preserved. `mp3/0001.mp3` was missing and has now been copied and
SHA-256 verified against the local recording. Physical DFPlayer playback and
UART pin connections remain unverified. Eject the card in phone storage
settings before removal; insert into the DFPlayer with its power disconnected.

The 64 GB/exFAT notes below describe the earlier card, not this prepared card.

`mp3/0001.mp3` says "Emergency" once. It is a 44.1 kHz mono, 128 kbit/s MP3 generated offline with the installed Microsoft Zira voice. Duration: approximately 1.73 seconds.

Place the `mp3` directory at the root of a compatible microSD card. Firmware should select MP3-folder track 1 (for example, `playMp3Folder(1)` with the DFRobot library), rather than relying on FAT file ordering.

On 2026-10-07 the connected Android phone exposed a removable card at `/storage/0008-1010`. It has approximately 58 GiB usable capacity and an exFAT filesystem, with approximately 11 GiB occupied. The recording was copied to `/storage/0008-1010/mp3/0001.mp3` without formatting or changing existing files. This does not make the card compatible: standard DFPlayer Mini support is FAT16/FAT32 and up to 32 GB. Use a compatible card for playback testing.

The photos identify a DFPlayer Mini and a 4-ohm speaker. Actual wiring, audible
playback, and the phone-to-controller blink command remain unverified.

## Manual controller speaker test

The main firmware source now includes a nonblocking DFPlayer diagnostic using UART2
at 9600 baud: DFPlayer TX to ESP32 RX2/GPIO16, DFPlayer RX to ESP32 TX2/GPIO17.
These are the explicitly stated test pins; the user's description "RX and TX"
did not establish which numbered UART is physically connected. GPIO1/3 remain
reserved for USB diagnostics. Common ground and a suitable regulated module
supply are required. The speaker connects between DFPlayer SPK1 and SPK2,
not between a speaker output and ground.

At 115200 baud on the ESP32 USB port, send `AUDIO TEST` followed by a newline,
or `AUDIO TEST N` to choose a volume from 1 to 30. The controller queries the
module's volume, requires a checksum-valid volume reply, sets the requested
volume (default 30/30, explicitly requested by the user), reads it back, and only then requests MP3-folder
track 1. `AUDIO STOP` cancels a pending test or stops active playback. A five
second timeout sends stop if no playback-finished message arrives. Booting
does not automatically start playback. The laptop blink monitor can now invoke
this command over USB for a confirmed three-blink sequence. The Android app
and Azure do not currently invoke speaker playback.

The packet commands match DFRobotDFPlayerMini 1.0.6. The diagnostic implements
the small UART exchange directly to avoid that library's blocking query waits
in the existing sensor loop. Ten simulated-UART tests passed. The user has
confirmed audible output at 8/30; the higher setting needs its own listening check.

First upload attempt: the build passed (1,076,265 bytes program, 55,392 bytes globals),
but COM9 upload failed before writing: "Download mode successfully detected,
but getting no sync reply: The serial TX path seems to be down." Therefore
the new audio diagnostic was not installed by that attempt. This failure is
consistent with external UART wiring contending
with the USB UART, but does not prove which physical pins are connected.
Verify/move the signal wires to DFPlayer RX -> GPIO17/TX2 and DFPlayer TX ->
GPIO16/RX2 with power disconnected, then retry the prepared build in
`tmp/speaker-check/firmware`. A hardware reset was requested after the failed
upload to return the ESP32 to its existing program.

On the user's next "now check", the retry succeeded at 115200 baud and the
flasher verified the written hashes. The audio diagnostic is now installed.
The manual `AUDIO TEST` command was received, but there was no expected
DFPlayer reply on RX2=16/TX2=17 within the timeout, so playback was not started.
The subsequent 15-second capture showed normal DHT/LCD operation and roughly
24-25 optical samples/s with zero sample faults. The next check is physical
DFPlayer power, common ground, and crossed RX/TX wiring; no audible result
can be claimed. Logs: `tmp/speaker-check/retry-upload.log` and
`tmp/speaker-check/playback-test.log`.

On the next requested retest, UART communication succeeded: the module replied
to the volume query with 30, accepted a change to 8, and read back volume 8.
It acknowledged the `/mp3/0001.mp3` playback command, but no playback-finished
reply arrived within five seconds; the diagnostic sent STOP and received an
ACK. This establishes UART command/response and volume control, not audible
playback. The user subsequently confirmed hearing the recording, but quietly.
This establishes audible playback at volume 8/30 for that test. The confirmed
capture is preserved as `tmp/speaker-check/volume8-confirmed.log`.

The updated source defaults to volume 16/30 and also accepts `AUDIO TEST N`
for an integer N from 1 through 20. Volume is read back before playback. This
allows later adjustment over USB without rebuilding firmware. Ten host tests
passed, including requested-volume verification and out-of-range rejection.

The volume-16 firmware was uploaded to COM9 with verified hashes. The module
reported volume 8 before adjustment, then confirmed volume 16 and acknowledged
the playback request. As in the initial audible test, no finished message
arrived, and the five-second stop command was acknowledged. No resets or
optical sample faults appeared during this test. The user reported that 16/30
was still too quiet and explicitly requested maximum volume.
Log: `tmp/speaker-check/volume16-test.log`.

The latest source therefore allows the module's full 1-30 range and defaults
to 30/30. Ten host tests passed, including maximum-volume readback and
rejection of 31. The recording itself peaks at approximately -7.9 dBFS and
has mean level -24.4 dBFS (including silence); its on-card file is unchanged.

The maximum-volume firmware was uploaded and flash hashes verified. The
module initially reported volume 16, then read back volume 30 and acknowledged
the playback command. STOP after five seconds was also acknowledged; no
finished message arrived. The capture showed no resets or optical sample
faults. Audible loudness at maximum awaits user feedback.
Log: `tmp/speaker-check/volume30-test.log`.

After the user approved volume 30/30, laptop blink-to-USB integration was added.
A live camera test produced three recognized triple-blink events, each with an
`AUDIO TEST 30` command received by the ESP32. The DFPlayer failed to respond
to all three volume queries, so playback was not started. A subsequent manual
test also failed at the same query. No firmware changes were made between
the successful maximum-volume test and these failures. The user was asked to
power-cycle all prototype supplies. Sound triggered by an actual blink remains
unverified; do not confuse successful command delivery with audible playback.

After the user confirmed a ten-second power cycle, another manual test still
got no DFPlayer response. ESP32 diagnostics, LCD, DHT and optical acquisition
continued normally. No further camera run was started because speaker
communication remains unresolved. Check the physical DFPlayer supply/common
ground and UART connections that previously worked. Capture:
`tmp/speaker-check/after-power-cycle.log`.

On 2026-10-08, the repeated failure was investigated with UART byte counters
and sampled pin levels. The diagnostic firmware compiled, passed the ten
existing host tests, and uploaded with verified flash hashes. A fresh query
reported `rx_bytes=0 invalid_frames=0 rx16_level=1 tx17_level=1`, then timed
out without starting playback. Thus this failure did not involve rejection
of a corrupt received frame: no incoming UART bytes were observed in the query
window. Sampled HIGH levels are digital observations only, not a voltage or
continuity measurement, and do not prove the DFPlayer is powered or connected.
Physical power/ground/UART checks are still required to identify the root cause.
Log: `tmp/speaker-check/uart-diagnostic-2026-10-08.log`.

## Recovery and Android integration, 2026-10-08

A later manual test received valid DFPlayer replies, confirmed volume 30/30,
and sent `/mp3/0001.mp3`. The user explicitly confirmed the speaker was working.
The earlier zero-byte communication failure was therefore intermittent; its
physical cause was not established. Android 0.2 now supports a direct paired
Bluetooth speaker command through the main ESP32; see `../android_phone/README.md`.
Actual phone blink-to-speaker playback still needs a physical test.

## Earlier formatting attempt

The user subsequently authorized erasing this removable card. Before invoking Android's `sm format public:179,1`, its identity was verified against UUID `0008-1010`, disk `179,0`, the `SD` flag, and size `62534975488` bytes. The USB connection disappeared during that command; it returned exit code 255 with no completion output. The phone restarted and subsequently reported the card as UNMOUNTABLE.

After re-verifying the removable card identity, `sm partition disk:179,0 public` completed successfully and restored a mounted, usable portable card. Its new UUID is `6AD5-F154`. `/proc/mounts` confirms Android selected **exFAT again**, not FAT32. The former card contents have been erased. The Emergency recording was restored to `/storage/6AD5-F154/mp3/0001.mp3` and its SHA-256 verified as `e503d63d210ecbad9cac6cceafbd7f6176d9c377de7611b9df4818ea3767d2d4`.

The phone's exposed storage-manager commands do not offer a filesystem selector, and the ADB shell has no raw block-device access or installed `su`. No rooting or internal-storage changes were attempted. A suitable card reader or another FAT32-capable storage host is still needed to attempt FAT32 on this 64 GB card; capacity compatibility with the DFPlayer remains unverified even after such conversion.
