# Changelog

## v1.2

### New
- **Gestures screen** (settings → gestures): double tap, long press, swipe left / right / up / down on empty home space. Each can:
  open an app, call a contact, run an app shortcut (e.g. WhatsApp "new chat"), open a specific app screen (Activity Launcher style),
  toggle the flashlight, open the camera, play/pause, skip track, open notifications or quick settings, open all apps, lock the screen, or do nothing.
- **Saved shortcuts** — "add to home screen" requests from other apps (Activity Launcher, Chrome, …) are saved and can be put on a gesture.
- **App shortcuts in the long-press menu**, above app info.
- **Screen time** (optional) — today's total under the date; tap for top apps and Digital Wellbeing; per-app time in the long-press menu.
- **Download fonts** — 22 free Google Fonts in the font menu, fetched by Google Play services (the app still has no internet permission).
- **Font options** — monospace, clean, phone default (One UI Sans on Samsung, or your chosen font style), downloaded, or imported .ttf/.otf; plus a **bold** toggle.
- **Welcome prompt** on first launch with "go to settings" / "later".
- **Enter / go** on the keyboard opens the top result; search results are ranked (name start → keyword → word start → anywhere).
- Keyword clashes ask before moving a keyword; hidden apps appear dimmed in all apps.
- GitHub link in settings.

### Smoother and faster
- Layout follows the keyboard animation instead of jumping.
- Apps load once in the background and refresh only when packages change (no reload on every return home).
- Apps open with a reveal animation from their icon; the search clears after the app covers the screen (no flicker).
- Music bar is event-driven instead of polling every 3 s.
- Lock-screen accessibility service no longer receives every UI event on the phone.
- Clock ticks exactly on the minute.

### Design
- One consistent line-icon set for menus, pickers, todo items, music bar and navigation.
- Pickers show the current choice; settings flag missing permissions (e.g. `[lock · needs access]`).
- Chosen font applies everywhere, including dialogs and settings; ripple feedback and larger touch targets.
- While searching or in all apps, the todo list and music bar step aside so results have room; with the keyboard open the todo list is capped at ~2 rows.

### Fixes
- Search bar disappeared after switching bottom → top.
- Search highlight didn't update while typing.
- Long-pressing an app also fired the home long-press gesture.
- Todo list grew without limit; a tall keyboard could hide all search results.
- Pull-down for notifications fired while scrolling results or swiping the music bar.
- Items could stay stuck in a pressed state after a pull-down.
- RAM text overlapped the clock ("PM").
- Importing an invalid font said "imported" and replaced a working font.
- Monospace / clean looked like the phone font when a Samsung font style was set.
- Pause icon showed as a colour emoji on Samsung.
- Tapping the clock did nothing on Samsung.
- Double-tap lock failed silently below Android 9.
- Settings showed "v1.0".

### Under the hood
- Removed `QUERY_ALL_PACKAGES`; the app list uses a `<queries>` declaration for launcher apps.
- Targets Android 16 (API 36).
- New optional permissions: `PACKAGE_USAGE_STATS` (screen time), `CALL_PHONE` (call-contact gesture); `SET_ALARM` (normal, for the clock tap on Samsung).
