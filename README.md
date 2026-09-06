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
- **Default Cyclic Days** — new alarms open on the Cyclic Days tab by default
- **Automatic Update Checker** — detects new GitHub release APKs on app launch & prompts user to download & update
- **Timer** — built-in countdown timer with circular progress ring, 1m–30m quick presets, alert sound & vibration
- **Stopwatch** — elapsed time tracker with lap recording, fastest/slowest lap highlights, and touch audio feedback
- **Strict Lock Screen Security & Privacy** — app ONLY displays over keyguard while actively ringing; dismiss or snooze immediately locks device & returns to prior state without exposing main dashboard
- **4-Tab Navigation** — Alarms, Timer, Stopwatch, Settings
- **12H / 24H Clock Toggle** — clock toggle with capital "AM"/"PM" indicator and full year display on home screen date
- **First-launch Theme Prompt & Light/Dark visibility** — select theme on install, with proper status bar icon contrast on devices like Galaxy S26
- **In-App Permissions & App Access Manager** — view and manage notification, exact alarm, full-screen alert, and overlay permissions live in Settings
- **Expandable Release Notes** — interactive collapsible release notes accordions on the About page
- **Support & Community Card** — dedicated feedback & bug reporting section on the About page (`bp.beema@outlook.com`)
- **8 built-in alarm sounds** — High Pitch, Zen Bowl, Sunrise Chime, Digital Beeps, Morning Forest, Synth Wave Beat, Cyber Alert, Lofi Chord
- **Pick your own audio** — use any audio file from your device
- **Volume & Vibration control** — per-alarm controls, volume defaults to maximum
- **Alarm history** — log of all dismissed and snoozed alarms with clear option (accessible via 📋 icon on the Alarms tab)
- **Pencil-sketched spiral launcher icon** — clean, symmetrical alarm clock icon design
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

This app was built **100% using AI assistance** (Google Antigravity AI / Claude). Every line of Kotlin, every layout, every fix — written by AI based on my descriptions of what I wanted.

I'm not a software developer. I'm just someone who needed a specific alarm app, couldn't find one that did exactly what I wanted, and used AI to build it. The ideas, requirements, and testing are mine — the code is the AI's.

---

## What's new — v1.3

- ⏱ **Timer** — built-in countdown timer with circular progress ring, quick presets (1m, 5m, 10m, 15m, 30m), pause & reset
- ⏱ **Stopwatch** — elapsed time display with lap tracking, fastest/slowest lap highlights, and reset
- 🔒 **Lock Screen Return** — after dismissing or snoozing an alarm, the phone returns to the lock screen (or whatever was open before), matching native alarm app behaviour
- 📱 **5-Tab Navigation** — Alarms, Timer, Stopwatch, Settings, About; History moved to a 📋 icon in the top-right of the Alarms tab
- 🌀 **Default Cyclic** — new alarms now open on Cyclic Days tab by default
- 🖼 **Icon Fit Fix** — app icon no longer appears cropped or zoomed in on the launcher

## What's new — v1.2

- 🌀 **Pencil-Sketched Spiral Alarm Icon** — brand new centered, symmetrical pencil-sketched spiral alarm clock launcher icon
- ⏱ **Per-Alarm Custom Snooze** — set desired snooze duration per alarm (quick preset chips + custom minute input) instead of global setting
- 🔒 **Native Lock Screen Ringing** — rings over keyguard cleanly without prompting for PIN/password unlock
- 📱 **Dedicated Page Navigation** — full 4-tab bottom navigation bar for Alarms, History, Settings, and About
- 🕒 **12H / 24H Clock Toggle** — choose 12-Hour (uppercase "AM"/"PM") or 24-Hour format on the home screen clock
- 📅 **Year display in date** — home screen date format now displays the full year (e.g. Sat, Sep 5, 2026)
- 🎨 **First-Launch Theme Prompt & Status Bar Visibility** — asks theme preference on first install and ensures status bar icons stay visible on light themes
- 🔑 **In-App Permissions Manager** — live permission status badges and direct settings management in Settings
- ✉️ **Support & Community Card** — moved to About page for easy feedback & bug reporting (`bp.beema@outlook.com`)
- 📂 **Expandable Release Notes** — collapsible release note cards on About page for clean readability

## v1.1

- 🌙 Dark / Light / System theme selector in Settings
- 🕐 Alarm editor now defaults to current time
- 👁 Minute spinner visibility fix
- 🔔 New **High Pitch** alarm sound — loud dual-tone alert (2400 Hz + 3200 Hz)
- 🔊 Volume defaults to maximum for new alarms
- 🏷 "Beema's FINCON" branding on home screen

## v1.0 — Initial release

- Cyclic alarms (every N days)
- Weekly alarms (any day combination)
- 7 synth sounds + custom audio file support
- Lock screen ringing
- Snooze, vibration, alarm history

---

## Feedback

Open the **About** page in the app and tap **"Feedbacks, Suggestions or Bugs Reporting"** to send feedback directly by email to `bp.beema@outlook.com`. Or open an issue here on GitHub.

---

## License

This project has no formal license. Use it, fork it, do whatever you want with it.

