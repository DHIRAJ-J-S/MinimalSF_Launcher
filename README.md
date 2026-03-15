# ⚡ MinimalSF

**Super Fast. Minimal. Monochrome.**

A lightweight, black & white Android home launcher built purely for speed. Type to search, auto-launch apps instantly. No bloat, no ads, no tracking, no network calls.

> 100% Open Source · By THE.404GUY ;)

---

## Features

### ⌨️ Type-to-Launch
- Keyboard always ready — start typing immediately to search apps
- Real-time filtering as you type
- Single match auto-launches after configurable delay
- Matched letters highlighted in white
- Search bar position configurable: top or bottom of screen

### 🚀 Auto-Launch
- When only one app matches your search, it opens automatically
- Adjustable delay via step slider: 0ms, 100ms, 200ms, 300ms, 404ms, 500ms, 600ms
- 404ms is a little easter egg

### 📱 All Apps
- Tap "all apps" to browse every installed app in a scrollable list
- Displays inline on the home screen — no separate drawer or popup
- Long press any app for app info or uninstall

### 🔽 Pull-Down Notifications
- Swipe down anywhere on the home screen to open the notification panel
- Instant — no animation or delay

### 🔒 Double-Tap to Lock Screen
- Double-tap the empty black space to lock your device
- Uses Android Accessibility Service — no data is read or collected
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
- First-time usage tip appears inline with controls hint

### ☐ Inline Todo Checklist
- Always visible at the bottom of the home screen
- Add tasks, mark as done, mark as important (red), or remove
- Tap the `!` button to toggle important
- Persisted across app restarts

### 🕐 Clock
- Large monospace bold clock on home screen
- Tap to open the system clock/alarms app
- 12-hour AM/PM by default, switchable to 24-hour

### 💾 RAM Display
- Small text in the top-right showing free/total RAM
- Updates every 2 seconds

### 💡 First-Use Tips
- Inline hints on the home screen on first launch: pull down for notifications, double tap to lock
- Inline music control hint on first music play with close button
- Tips auto-dismiss after first use

### 🏁 First Launch Setup
- Prompt to set MinimalSF as default launcher (dismissible, won't ask again)
- Prompt to enable music bar with privacy notice
- Lock screen permission prompt with clear explanation on first double-tap

### 🎨 Custom UI
- Every dialog, prompt, menu, and picker uses a custom-built monochrome UI system
- Black background, thin grey borders, monospace font throughout
- No Android system dialogs — fully consistent visual language

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
| 👆 Double tap action | Lock screen / Open app |
| ✊ Long press action | None / Open app |

---

## 🔐 Permissions

| Permission | Why |
|---|---|
| `QUERY_ALL_PACKAGES` | List all installed apps for search-to-launch |
| `EXPAND_STATUS_BAR` | Pull down notification panel from home screen |
| `REQUEST_DELETE_PACKAGES` | Uninstall apps from long-press menu |
| `BIND_NOTIFICATION_LISTENER_SERVICE` | Read currently playing music info (optional, user-enabled) |
| `BIND_ACCESSIBILITY_SERVICE` | Lock screen on double-tap (optional, user-enabled) |
| `BIND_DEVICE_ADMIN` | Alternative lock screen method (optional, user-enabled) |

---

## 🛡️ Privacy

- 🚫 **Zero network** — the app makes no internet requests whatsoever
- 🚫 **Zero tracking** — no analytics, no telemetry, no data collection
- 🚫 **Zero ads** — completely ad-free, forever
- 🚫 **Zero location** — no GPS or location access
- 💾 All data stored locally on device in SharedPreferences
- 🔓 Full source code available for audit

---

## 🔍 Static Analysis Note

Automated scanners (VirusTotal, Zenbox, etc.) may flag this app for:
- **"Queries installed applications"** — required for any launcher to list apps
- **"Deletes other packages"** — the uninstall feature uses the standard Android uninstall intent
- **"Uses reflection"** — used to expand the notification panel via StatusBarManager
- **"Obfuscates method names"** — R8/ProGuard minification, standard for release builds
- **"Device administrator"** — optional lock screen feature, only locks the screen
- **"Networking/Location"** — false positives from AndroidX/Material library internals, not from app code

These are all expected behaviors for a home launcher application. The app contains no malicious code.

---

## 🔨 Build

1. Clone the repository
2. Open in Android Studio
3. Sync Gradle
4. Build → Run on device (Android 8.0+ / API 26+)

---

## 📦 Install

Download the latest APK from [Releases](../../releases).

> ⚠️ Play Protect may show a warning for sideloaded apps. This is normal for apps not distributed via Play Store. Tap "More details" → "Install anyway".

---

## 👤 Credits

**By THE.404GUY ;)**

100% Open Source

---

## 📄 License

MIT License
