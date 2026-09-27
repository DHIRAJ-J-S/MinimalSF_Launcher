
<h1><img src="assets/MSF_icon.png" width="50" align="absmiddle" alt="icon">&nbsp;MinimalSF</h1>

**Super Fast. Minimal. Monochrome. Just 1.5Mb**

A lightweight, black & white Android home launcher built purely for speed. Type to search, auto-launch apps instantly. No bloat, no ads, no tracking, no network calls.

<a href="https://github.com/DHIRAJ-J-S/MinimalSF_Launcher/releases/latest"><img src="https://raw.githubusercontent.com/Kunzisoft/Github-badge/main/get-it-on-github.png" alt="Get it on GitHub" height="54"></a>
<a href="https://apps.obtainium.imranr.dev/redirect?r=obtainium://add/https://github.com/DHIRAJ-J-S/MinimalSF_Launcher"><img src="https://raw.githubusercontent.com/ImranR98/Obtainium/main/assets/graphics/badge_obtainium.png" alt="Get it on Obtainium" height="54"></a>

[![Latest release](https://img.shields.io/github/v/release/DHIRAJ-J-S/MinimalSF_Launcher?label=release)](https://github.com/DHIRAJ-J-S/MinimalSF_Launcher/releases/latest)
[![Downloads](https://img.shields.io/github/downloads/DHIRAJ-J-S/MinimalSF_Launcher/total?label=downloads)](https://github.com/DHIRAJ-J-S/MinimalSF_Launcher/releases)
[![License: MIT](https://img.shields.io/badge/license-MIT-blue)](LICENSE)
![Android 8.0+](https://img.shields.io/badge/Android-8.0%2B-3DDC84?logo=android&logoColor=white)

---

## Features

### ⌨️ Type-to-Launch
- Keyboard always ready - start typing immediately to search apps
- Real-time filtering as you type
- Single match auto-launches after configurable delay
- Press enter / go on the keyboard to open the top result
- Results ranked: name prefix → keyword → word start → anywhere
- Matched letters highlighted in white
- Custom keywords per app (long press → set keyword), e.g. `wa` → WhatsApp
- Search bar position configurable: top or bottom of screen

### 🚀 Auto-Launch
- When only one app matches your search, it opens automatically
- Adjustable delay

### 📱 All Apps
- Tap "all apps" to browse every installed app in a scrollable list
- Long press any app for app info, uninstall, keyword, or hide from search
- Hidden apps stay in all apps, dimmed

### 🔽 Pull-Down Notifications
- Swipe down anywhere on the home screen to open the notification panel

### 🔒 Double-Tap to Lock Screen
- Double-tap the empty black space to lock your device
- Uses Android Accessibility Service - no data is read or collected
- Configurable: can be changed to open any app instead of locking

### 👆 Long Press Gesture
- Disabled by default
- Configurable in settings to launch any app on long press of empty space

### 🎵 Now Playing Music Bar
- Optional bar showing currently playing song and artist
- Works with any music app (YT Music, Spotify, Musicolet, etc.)
- Tap to play/pause
- Swipe left for next track, right for previous
- Long press to open the music player

### ☐ Inline Todo Checklist
- Always visible at the bottom of the home screen
- Add tasks, mark as done, mark as important (red), or remove
- Tap the `!` button to toggle important

### 🕐 Clock
- Large monospace bold clock on home screen
- Tap to open the system clock/alarms app
- 12-hour AM/PM by default, switchable to 24-hour

### 👆 Gestures
- Settings → gestures: double tap, long press, swipe left / right / up / down on empty home space
- Actions: open an app, call a contact, an app shortcut (e.g. WhatsApp "new chat"), a specific app screen
  (Activity Launcher style), flashlight, camera, play/pause, next track, notifications, quick settings,
  all apps, lock screen, or nothing
- "Add to home screen" shortcuts from other apps (Chrome, Activity Launcher, …) are saved and can be put on a gesture
- Long press an app to see its shortcuts at the top of the menu

### ⏱️ Screen Time (optional)
- Today's screen time under the date, e.g. `screen time 2h 14m`
- Tap for today's top apps; opens Digital Wellbeing if the phone has it
- Long press an app to see its time today
- Needs "usage access" (Settings → Usage access). Read on-device only; totals can differ from Digital Wellbeing by a few minutes

### 💾 RAM Display
- Small text in the top-right showing free/total RAM
- Updates every 2 seconds while the home screen is visible

### 🎨 Custom UI
- Every dialog, prompt, menu, and picker uses a custom-built monochrome UI system
- Black background, thin grey borders, monospace font throughout

---

## ⚙️ Settings

| Setting | Options |
|---|---|
| 🏠 Set as default launcher | Shows ✓ when active |
| 🔄 Change default launcher | Opens Android home settings |
| 🎵 Now playing bar | On / Off |
| ⏱️ Auto-launch delay | 0–600ms step slider |
| 🕐 Clock format | 12h / 24h |
| 🔍 Search bar position | Top / Bottom |
| 🔎 Search matching | Starts with / Contains |
| 🔤 Font | Monospace / Clean / Google Fonts download / Import .ttf/.otf |
| ⏱️ Screen time | On / Off |
| 🔠 Font size / Clock size | Small / Default / Large |
| 👆 Double tap action | Lock screen / Open app |
| ✊ Long press action | None / Open app |

---

## 🆕 What's new in 1.2

- **Smoother:** the layout now moves with the keyboard animation instead of jumping when the keyboard opens or closes
- **Faster:** apps load once in the background and refresh only when something is installed or removed, so returning home no longer re-reads every app icon on the main thread
- Apps open with a reveal animation from their icon
- Music bar updates from media events instead of polling every 3 seconds
- Lock-screen accessibility service no longer listens to every UI event on the phone
- Fixed: switching the search bar bottom → top made it disappear
- Fixed: match highlight didn't update while typing
- Fixed: long-pressing an app also fired the home long-press gesture
- Fixed: the todo list grew without limit and pushed results off screen
- Fixed: pull-down for notifications triggered while scrolling results
- Fixed: font choice only applied to some text; it now applies everywhere, including dialogs and settings
- Settings show the current choice in every picker, flag missing permissions, and show the real version
- No more `QUERY_ALL_PACKAGES`: the app list uses a `<queries>` declaration instead
- Targets Android 16 (API 36)
- Screen time on the home screen (optional)
- Download free fonts from Google Fonts right in the font menu (via Google Play services, so the app itself still has no internet permission)
- One consistent line-icon set for menus, pickers, todo items and navigation
- Gestures screen with swipe left/right/up/down, app shortcuts, app screens, contacts and more
- Saves "add to home screen" shortcuts from other apps (works with Activity Launcher)
- GitHub link in settings

---

## 🔐 Permissions

| Permission | Why |
|---|---|
| `<queries>` for launcher apps | List apps that have a launcher icon (no `QUERY_ALL_PACKAGES` needed) |
| `EXPAND_STATUS_BAR` | Pull down notification panel from home screen |
| `REQUEST_DELETE_PACKAGES` | Uninstall apps from long-press menu |
| `SET_ALARM` | Tap the clock to open alarms (Samsung requires it; granted automatically) |
| `PACKAGE_USAGE_STATS` | Screen time (optional, user-granted "usage access") |
| `CALL_PHONE` | "Call contact" gesture calls directly (optional; without it the dialer opens) |
| `BIND_NOTIFICATION_LISTENER_SERVICE` | Read currently playing music info (optional, user-enabled) |
| `BIND_ACCESSIBILITY_SERVICE` | Lock screen on double-tap (optional, user-enabled) |

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
- **F-Droid:** submitted and awaiting review.

---

## 📄 License

MIT License
```
You’re free to use, tweak, and build on this project.  
Have fun with it, break things, improve things 🙂  
