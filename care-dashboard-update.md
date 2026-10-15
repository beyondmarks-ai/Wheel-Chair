# Blink Assist 0.18 — care dashboard redesign

Installed and opened on the Vivo V2031. The app now starts on a dashboard with
four persistent tabs: Home, Health, Care and Alerts.

- Home: large feature tiles, current pulse/oxygen cards, monitoring entry and next reminder.
- Health: six measurement cards, metric/time-range controls, a saved-reading graph
  with low/average/high summaries, and a date-grouped timeline. Tap event cards to
  expand details. The default range is 24 hours. Missing data remains missing.
- Care: schedule/attention cards, a reminder grid, paired Done/Snooze actions and
  a simpler editor with weekday grid and collapsed speaker/guardian options.
  Care history has dated cards and status summaries; report sharing remains available.
- Alerts: four status cards for emergency, movement, reminders and guardian SMS.
  Notification tests and advanced help are collapsed. Guardian settings use the same styling.
- Live monitoring retains its camera and calibration controls with compact navigation.
- Hold the Blink Assist dashboard title to open the existing hidden manual panel.

The layout uses shared spacing, typography, rounded cards, line icons, navy and teal
colors, and adaptive grids. Narrow screens or larger system fonts use one column.
The care screen updates when data changes instead of rebuilding every five seconds,
so expanding options is not immediately undone. History is filtered to the selected
time range; the timeline displays up to 40 recent entries.

Existing reminder schedules, voice files, sensor processing, SMS settings and history
remain in place. This redesign does not create sensor readings or start monitoring
when a navigation tab is opened.

Validation: 108 JVM tests passed; build and lint passed. The on-device navigation test
passed across all four tabs and the hidden admin entry. Home, Health, chart, timeline,
Care, reminder editor and Alerts were visually inspected on the Vivo phone. A Vivo
status-bar initialization crash found during testing was fixed and retested.

APK: `android_phone/dist/WheelchairBlink-care-dashboard.apk`

SHA-256: `C8A8458512550D1366D164832D9A269EF7F17CB02F024BCF41F5B9B3151C0328`
