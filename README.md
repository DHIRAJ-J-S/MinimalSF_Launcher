
<h1><img src="assets/MSF_icon.png" width="50" align="absmiddle" alt="icon">&nbsp;MinimalSF</h1>

**Super Fast. Minimal. Monochrome. Just 1.5Mb**

A lightweight, black & white Android home launcher built purely for speed. Type to search, auto-launch apps instantly. No bloat, no ads, no tracking, no network calls.


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
- Adjustable delay

### 📱 All Apps
- Tap "all apps" to browse every installed app in a scrollable list
- Long press any app for app info or uninstall

### 🔽 Pull-Down Notifications
- Swipe down anywhere on the home screen to open the notification panel

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

### ☐ Inline Todo Checklist
- Always visible at the bottom of the home screen
- Add tasks, mark as done, mark as important (red), or remove
- Tap the `!` button to toggle important

### 🕐 Clock
- Large monospace bold clock on home screen
- Tap to open the system clock/alarms app
- 12-hour AM/PM by default, switchable to 24-hour

### 💾 RAM Display
- Small text in the top-right showing free/total RAM
- Updates every 2 seconds

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

---

## 🛡️ Privacy

- 🚫 **Zero network** — the app makes no internet requests whatsoever
- 🚫 **Zero tracking** — no analytics, no telemetry, no data collection
- 🚫 **Zero ads** — completely ad-free, forever
- 🚫 **Zero location** — no GPS or location access
- 💾 All data stored locally on device in SharedPreferences
- 🔓 Full source code available for audit

---

## 🔨 Build

1. Clone the repository
2. Open in Android Studio
3. Sync Gradle
4. Build → Run on device (Android 8.0+ / API 26+)

---

## 📦 Install

Download the latest APK from [Releases](../../releases).

---

## 📄 License

MIT License
```
Permission is hereby granted, free of charge, to any person obtaining a copy of this software and associated documentation files (the “Software”), to deal in the Software without restriction, including without limitation the rights to use, copy, modify, merge, publish, distribute, sublicense, and/or sell copies of the Software, and to permit persons to whom the Software is furnished to do so, subject to the following conditions:

The above copyright notice and this permission notice shall be included in all copies or substantial portions of the Software.

THE SOFTWARE IS PROVIDED “AS IS”, WITHOUT WARRANTY OF ANY KIND, EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
