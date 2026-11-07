
<h1><img src="assets/MSF_icon.png" width="50" align="absmiddle" alt="icon">&nbsp;MinimalSF</h1>

**Super Fast. Minimal. Monochrome. Under 1 MB.**

A lightweight, black & white Android home launcher built purely for speed. Type to search, auto-launch apps instantly. No bloat, no ads, no tracking, no network calls.

<a href="https://github.com/DHIRAJ-J-S/MinimalSF_Launcher/releases/latest"><img src="https://raw.githubusercontent.com/Kunzisoft/Github-badge/main/get-it-on-github.png" alt="Get it on GitHub" height="54"></a>
<a href="https://apps.obtainium.imranr.dev/redirect?r=obtainium://add/https://github.com/DHIRAJ-J-S/MinimalSF_Launcher"><img src="https://raw.githubusercontent.com/ImranR98/Obtainium/main/assets/graphics/badge_obtainium.png" alt="Get it on Obtainium" height="54"></a>
<a href="https://f-droid.org/packages/io.github.dhiraj_j_s.minimalsf/"><img src="https://fdroid.gitlab.io/artwork/badge/get-it-on.png" alt="Get it on F-Droid" height="54"></a>

[![Latest release](https://img.shields.io/github/v/release/DHIRAJ-J-S/MinimalSF_Launcher?label=release)](https://github.com/DHIRAJ-J-S/MinimalSF_Launcher/releases/latest)
[![F-Droid](https://img.shields.io/f-droid/v/io.github.dhiraj_j_s.minimalsf?label=F-Droid)](https://f-droid.org/packages/io.github.dhiraj_j_s.minimalsf/)
[![Downloads](https://img.shields.io/github/downloads/DHIRAJ-J-S/MinimalSF_Launcher/total?label=downloads)](https://github.com/DHIRAJ-J-S/MinimalSF_Launcher/releases)
[![License: MIT](https://img.shields.io/badge/license-MIT-blue)](LICENSE)
![Android 8.0+](https://img.shields.io/badge/Android-8.0%2B-3DDC84?logo=android&logoColor=white)

⭐ **Like MinimalSF? Star the repo** - it helps more people find it.

---

## Features

### ⌨️ Type-to-Launch
- Keyboard ready as soon as you're home - start typing immediately
- Real-time filtering as you type
- Single match auto-launches after configurable delay
- Press enter / go on the keyboard to open the top result
- Results are numbered: type the number after your search to open that app, e.g. `w2` opens the 2nd result for `w`
- Results ranked: name prefix → keyword → word start → anywhere
- Matched letters highlighted in white
- Custom keywords per app (long press → set keyword), e.g. `wa` → WhatsApp
- Search bar position configurable: top or bottom of screen

### 🚀 Auto-Launch
- When only one app matches your search, it opens automatically
- Adjustable delay (Default: 100ms)

### 📱 All Apps
- Tap "all apps" to browse every installed app, as a list, a list of names only (no icons), or a grid of 2-7 columns
- Optional A-Z strip on the right: touch or drag a letter to jump there (settings, off by default)
- Work profile and dual apps are included, labelled `· work` and `· dual`
- Long press any app for its shortcuts, package name, version, app info, uninstall, keyword, or hide from search
- Hidden apps stay in all apps, dimmed

### 🎵 Now Playing Music Bar
- Bar showing currently playing song and artist
- Works with any music app (YT Music, Spotify, Apple Music, etc.)
- Tap to play/pause
- Swipe left for next track, right for previous
- Long press to open the music player

### ☐ Inline Todo Checklist
- At the bottom of the home screen
- Add tasks, mark as done, mark as important (red), or remove
- Tap the `!` button to toggle important
- Removed a task by mistake? Tap "undo" within a few seconds
- Long press a task to read all of it and edit it

### 🕐 Clock
- Large bold clock on home screen
- Tap to open the system clock/alarms app
- 12-hour AM/PM by default, switchable to 24-hour

### 👆 Gestures
- Settings → Gestures: double tap, long press, swipe left / right / up / down on empty home space
- Defaults: double tap → lock screen, swipe down → notifications
- Actions: open an app, call a contact, app shortcut (e.g. WhatsApp "new chat"), specific app screen (Activity Launcher style), flashlight, camera & more
- Pick an app for a gesture by typing part of its name
- "Add to home screen" shortcuts from other apps (Chrome, Activity Launcher, etc.) are saved and can be assigned to gestures
- Long-press an app to see its shortcuts at the top of the menu

### ⏱️ Screen Time
- Today's screen time under the date, e.g. `screen time 2h 14m`
- Tap for today's top apps; opens Digital Wellbeing if the phone has it
- Long press an app to see its time today
- Needs "usage access" (Settings → Usage access). Read on-device only; totals can differ from Digital Wellbeing by a few minutes

### ✨ Make Your Home Screen Yours
- Hide the clock, date, screen time, now playing bar or todo list
- Optional RAM use in the top corner, e.g. `ram 3988/5388mb` (off by default)
- Bottom buttons as icon + text, icon only or text only, or hide them
- With the settings button hidden, "MinimalSF settings" appears in all apps and search

### 🎨 Custom UI
- Every dialog, prompt, menu, and picker uses a custom-built monochrome UI system
- Black background, thin grey borders, monospace font throughout

---

## ⚙️ Settings

| Menu | Options |
|---|---|
| 🏠 Default launcher | Shows ✓ when set; opens Android's home app settings |
| ⌨️ Search & keyboard | Auto-launch delay (0–600ms), matching (starts with / contains), open keyboard on home, floating keyboard side (landscape) |
| 🖥️ Home screen | Show clock / date / RAM / screen time / now playing bar / todo, clock format and size, search bar position, bottom buttons and their style |
| 📱 All apps view | List, list without icons, or grid with 2–7 columns |
| 🔠 A-Z scrubber | Letter strip on the right of all apps (off by default) |
| 🔤 Font | Monospace / clean / phone default / Google Fonts / import .ttf/.otf, bold, and size |
| 👆 Gestures | Double tap, long press and four swipes, each with its own action |

---

## 🆕 What's new in 1.3.1

- **List without icons:** all apps and search results as plain app names
- **RAM indicator** is back as an option, showing used / total memory
- **A-Z scrubber** for all apps (optional) and **search in the app picker**
- **Todo:** undo a removed task, and long press a task to read and edit it
- **New app icon**, with a themed version for Android 13+
- **Under 1 MB:** the download is about 0.87 MB, down from 1.6 MB
- **One welcome prompt** instead of three, brighter faint text, and dialogs that follow the font size
- Bottom search bar puts the best result next to your thumb; double tap and long press work on any empty space
- Fixed: gestures running while results were showing, keyboard not coming back after back, saved shortcuts that couldn't be removed, and more

See [CHANGELOG.md](CHANGELOG.md) for the full list.



---

## 🔐 Permissions

| Permission | Why |
|---|---|
| `<queries>` for launcher apps | List apps that have a launcher icon (no `QUERY_ALL_PACKAGES` needed) |
| `EXPAND_STATUS_BAR` | Pull down notification panel from home screen |
| `REQUEST_DELETE_PACKAGES` | Uninstall apps from long-press menu |
| `SET_ALARM` | Tap the clock to open alarms |
| `PACKAGE_USAGE_STATS` | Screen time (optional, user-granted "usage access") |
| `CALL_PHONE` | "Call contact" gesture calls directly (optional; without it the dialer opens) |
| `BIND_NOTIFICATION_LISTENER_SERVICE` | Read currently playing music info (optional, user-enabled) |
| `BIND_ACCESSIBILITY_SERVICE` | Lock the screen with a gesture (optional, user-enabled) |

---

## 🛡️ Privacy

- 🚫 **Zero network** - the app has no internet permission. Optional font downloads are fetched by Google Play services, not by the app
- 🚫 **Zero tracking** - no analytics, no telemetry, no data collection
- 🚫 **Zero ads** - completely ad-free, forever
- 🚫 **Zero location** - no GPS or location access
- 💾 All data stored locally on device in SharedPreferences
- 🔓 Full source code available for audit

---

## 🔨 Build

1. Clone the repository
2. Open in Android Studio
3. Sync Gradle
4. Build → Run on device (Android 8.0+ / API 26+)

From the command line: `./gradlew assembleRelease`

---

## 📦 Install

- **Obtainium** (recommended): tap the Obtainium badge at the top on your phone, or add `https://github.com/DHIRAJ-J-S/MinimalSF_Launcher` in [Obtainium](https://github.com/ImranR98/Obtainium). You get updates automatically from each new release.
- **Direct download:** get the latest APK from [Releases](../../releases).
- **F-Droid:** [get it on F-Droid](https://f-droid.org/en/packages/io.github.dhiraj_j_s.minimalsf/). New versions appear there a few days after each release.

---

## 📄 License

MIT License
```
You’re free to use, tweak, and build on this project.  
Have fun with it, break things, improve things 🙂  
