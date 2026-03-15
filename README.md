# MinimalSF

**Super Fast. Minimal. Monochrome.**

A lightweight, black & white Android launcher built for speed. Type to search, auto-launch when there's a single match. No bloat, no distractions.

100% Open Source.

---

## Features

### ⌨️ Type-to-Launch
- Keyboard is always open — start typing immediately
- App list filters in real-time as you type
- When only one app matches your query, it **auto-launches** after a configurable delay
- Matched letters are highlighted bold white in search results

### ⏱ Auto-Launch
- Single match auto-opens the app without needing to tap
- Configurable delay: 300ms, 400ms, 500ms, 600ms (default), 800ms, 1000ms, 1500ms
- Cancel by continuing to type or clearing the search

### 🔽 Pull-Down Notifications
- Swipe down from the top area of the screen to instantly open the notification panel
- No animation delay — triggers immediately via touch detection on the top 35% of the screen
- Works even with the keyboard open

### 🎵 Now Playing Bar
- Optional music control bar at the bottom of the home screen
- Shows currently playing song title and artist from any music app (YT Music, Spotify, Musicolet, etc.)
- **Tap** → play / pause
- **Swipe left** → next track
- **Swipe right** → previous track
- **Long press** → open the music player app
- Live playback state icon (▶ / ▮▮)
- Requires notification listener permission (prompted on first launch or when enabling in settings)

### ☐ Inline Todo Checklist
- Always visible at the bottom of the home screen — no separate screen
- Add tasks with the input field and `+` button
- Tap checkbox to mark done (strikethrough)
- Tap `!` button to mark as important (turns red)
- Tap `×` to remove
- Persisted across app restarts

### 📱 App Management
- Long press any app in search results for a context menu:
  - **App info** — opens Android's app details screen
  - **Uninstall** — triggers system uninstall prompt
- App list refreshes automatically after uninstall

### 🕐 Clock
- Large monospace bold clock on the home screen
- Tap the clock to open the system clock/alarms app
- 12-hour (AM/PM) format by default, switchable to 24-hour in settings

### 💾 Free RAM Display
- Small dim text in the top-right corner showing available/total RAM
- Updates every 2 seconds
- Minimal visual footprint

### ⚙️ Settings
- **Set as default launcher** — opens Android home app settings
- **Change default launcher** — switch back to another launcher
- **Now playing bar** — toggle on/off with notification access prompt
- **Auto-launch delay** — pick from 7 preset values
- **Clock format** — toggle between 12h and 24h

### 🚀 First Launch Setup
- Prompts to set MinimalSF as default launcher (with "Later" option that won't ask again)
- Asks if you want the now playing music bar enabled
- Clear "no data is tracked" disclosure
- Non-intrusive — if you decline, you're never asked again

---

## Design

- Pure black & white monochrome aesthetic
- Monospace font throughout
- Grayscale app icons in search results
- Zero ads, zero tracking, zero analytics
- Minimal memory footprint — no background services except the optional notification listener for music

---

## Icon

White square inside a black square. Minimal.

---

## Build

1. Clone the repository
2. Open in Android Studio
3. Sync Gradle
4. Run on device (API 26+ / Android 8.0+)

---

## Permissions

| Permission | Purpose |
|---|---|
| `QUERY_ALL_PACKAGES` | List all installed apps for search |
| `EXPAND_STATUS_BAR` | Pull-down notification panel |
| `REQUEST_DELETE_PACKAGES` | Uninstall apps from long-press menu |
| `BIND_NOTIFICATION_LISTENER_SERVICE` | Read now playing music info (optional) |

---

## Credits

**By THE.404GUY ;)**

100% Open Source

---

## License

Open source. Do whatever you want with it.
