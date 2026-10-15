# Blink Assist 0.15 — hidden manual speaker panel

APK: `android_phone/dist/WheelchairBlink-admin-controls.apk`

Hold the **Blink Assist** title on the main screen to open **Admin controls**.
Tap **Connect speaker**, then tap a large red button:

| Button | Existing DFPlayer recording | Bluetooth command |
| --- | --- | --- |
| Emergency | `/mp3/0001.mp3` | `SOS <id>` |
| Please help me | `/mp3/0004.mp3` | `PHRASE 4 <id>` |
| Please come here | `/mp3/0005.mp3` | `PHRASE 5 <id>` |
| Yes | `/mp3/0003.mp3` | `PHRASE 3 <id>` |

The controller, DFPlayer, SD card and wired speaker must be powered. No camera,
calibration or internet connection is required for manual mode. A paired
Wheelchair-Speaker is selected automatically when already saved; otherwise the
panel offers the paired controller list.

If camera monitoring is already running, the panel shares its speaker connection.
To use manual mode alone, stop monitoring first, then connect from this panel.
Closing the panel leaves the connection running; Disconnect speaker or the
foreground notification's Stop button closes it.

Buttons are disabled until the controller is ready. Non-emergency playback waits
for the speaker to become idle; Emergency can interrupt another phrase. Presses
are never queued for a later reconnect. Controller status distinguishes a request
from a confirmed finish; an unconfirmed finish does not prove that audio was silent.

Each accepted button action is saved as **Manual speaker** in local health history.
These buttons do not invoke guardian SMS or create a gesture detection event.
Existing automatic monitoring and care notifications remain separate.

The panel is hidden from ordinary navigation and its Activity is not exported.
The long press is a hidden shortcut, not password authentication.

Motion track 2 is not exposed: the current Bluetooth firmware does not offer an
acknowledged command for it. Existing automatic motion announcements are unchanged.

Validation: 105 JVM tests passed; debug APK and Android test APK built; Android
lint passed. Version 15 / 0.15-admin-controls installed on the Vivo V2031.
The device test passed: long-press entry, all four button mappings, no automatic
service start on opening, disconnected buttons disabled, Activity not exported.
The live panel connected to the controller in manual mode with camera off.
Pressing Please help me produced PLAY_REQUESTED; the user confirmed hearing it.
The controller subsequently reported completion unconfirmed, which is displayed
truthfully rather than treated as evidence of silence. The other three button
commands passed protocol tests; their audio was not separately tested this turn.

APK SHA-256: `A718DEFAA4F1DF7A3C0121A2D0ACA986001FB4F9FEAFC2E10EE14E9F58D95032`.
