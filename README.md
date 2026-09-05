# Beema's FINCON — Cyclic Alarms

> A simple, **100% ad-free** alarm app built for people who work shift schedules, rotating rosters, or any repeating cycle that doesn't fit a standard Mon–Fri pattern.

---

## What this app does

Most alarm apps only support weekly repeats — same days every week. If your work schedule repeats every 3 days, every 5 days, or any other interval, you're stuck setting alarms manually each time.

**Cyclic Alarms** solves exactly that. You pick a start date and an interval, and the alarm fires on that cycle forever — no manual resetting needed.

It also supports regular weekly alarms (pick any days of the week), so it covers everyday use too.

That's it. No subscriptions, no ads, no accounts, no permissions beyond what's needed to actually ring an alarm.

---

## Features

- **Cyclic alarms** — repeat every N days from a start date (e.g. every 3 days, every 14 days)
- **Weekly alarms** — pick any combination of weekdays
- **Rings on lock screen** — works even when the phone is asleep
- **8 built-in alarm sounds** — High Pitch, Zen Bowl, Sunrise Chime, Digital Beeps, Morning Forest, Synth Wave Beat, Cyber Alert, Lofi Chord
- **Pick your own audio** — use any audio file from your device
- **Volume control** — per-alarm volume slider, defaults to maximum
- **Vibration toggle** — per alarm
- **Snooze** — configurable 5 / 10 / 15 / 20 minutes
- **Alarm history** — log of all dismissed and snoozed alarms
- **Dark / Light / System theme** — switchable in Settings
- **Zero ads. Zero tracking. Zero sign-in.**

---

## Screenshots

> *(Coming soon — feel free to add your own)*

---

## Installation

This app is **not on the Play Store**. Install it by sideloading the APK:

1. Download `app-release.apk` from the [Releases](../../releases) page
2. On your Android phone, go to **Settings → Apps → Special app access → Install unknown apps**
3. Allow installs from your browser or file manager
4. Open the downloaded APK and tap Install

### ⚠️ Google Play Protect warning

When you install this APK, Google Play Protect may show a warning saying the app is unrecognized. This is **normal and expected** for any APK that isn't distributed through the Play Store.

The app is completely safe. Here's the honest context:

- I'm not a professional developer — I'm an amateur who built this for my own use
- The app is not signed with a Play Store key, which triggers the warning automatically
- There is no malware, no tracking, no network calls of any kind — it's a local alarm app
- You're welcome to read the full source code in this repository

You can tap **"Install anyway"** safely.

---

## Tech stack

- **Language:** Kotlin
- **UI:** Jetpack Compose (Material 3)
- **Data:** Room database (SQLite)
- **Audio:** Android AudioTrack + MediaPlayer — all synth sounds are generated in code, no audio files bundled
- **Scheduling:** AlarmManager with exact alarms
- **Min SDK:** Android 8.0 (API 26)

---

## AI disclosure

This app was built **100% using AI assistance** (Kiro AI / Claude). Every line of Kotlin, every layout, every fix — written by AI based on my descriptions of what I wanted.

I'm not a software developer. I'm just someone who needed a specific alarm app, couldn't find one that did exactly what I wanted, and used AI to build it. The ideas, requirements, and testing are mine — the code is the AI's.

---

## What's new — v1.1

- 🌙 Dark / Light / System theme selector in Settings
- 🕐 Alarm editor now defaults to current time
- 👁 Minute spinner visibility fix
- 🔔 New **High Pitch** alarm sound — loud dual-tone alert (2400 Hz + 3200 Hz)
- 🔊 Volume defaults to maximum for new alarms
- 🏷 "Beema's FINCON" branding on home screen
- ✉️ Tap the header to send feedback

## v1.0 — Initial release

- Cyclic alarms (every N days)
- Weekly alarms (any day combination)
- 7 synth sounds + custom audio file support
- Lock screen ringing
- Snooze, vibration, alarm history

---

## Feedback

Tap **"Beema's FINCON"** at the top of the home screen to send feedback directly by email. Or open an issue here on GitHub.

---

## License

This project has no formal license. Use it, fork it, do whatever you want with it.
