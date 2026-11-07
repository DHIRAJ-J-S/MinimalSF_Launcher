# Changelog

## v1.3.1

### New
- **List without icons** - all apps view has a third choice, "list - without icons", for a list of just app names. It also applies to search results. Grid view always shows icons.
- **RAM indicator** - back as an option: settings > home screen > show ram. Off by default.
- **A-Z scrubber** - an optional letter strip on the right of all apps; touch or drag a letter to jump there. Turn it on in settings. Off by default.
- **Search in the app picker** - pick an app for a gesture by typing part of its name.
- **Undo for removed tasks** - removing a todo shows "removed · undo" for a few seconds.
- **Edit tasks** - long press a task to read all of it and change it.

### Changed
- **New app icon** - a sharper M with a raised SF and a cursor, with a themed (monochrome) version for Android 13+.
- **Smaller download** - the APK is about 0.87 MB, down from 1.6 MB, after removing an unused library.
- **One welcome prompt** instead of three in a row. It offers to set MinimalSF as home (only if it isn't already); the now playing bar stays in settings.
- **Easier to read** - faint text and icons (tips, todo buttons, dialog hints, RAM) are brighter, at least 4.5:1 contrast.
- **Dialogs follow the font size setting**, and long messages scroll instead of being cut off in landscape.
- **Music bar** works with TalkBack (tap to play or pause, hold to open, actions for next and previous).
- **Bottom search bar** - the best result now sits right above the search bar, closest to your thumb.
- **Double tap and long press** work anywhere on empty space, not just over the app list.

### Fixed
- Gestures (swipes, double tap, long press) running while search results or all apps are showing - a double tap there could lock the phone
- Keyboard not coming back when you closed it with back and pressed home
- Hardware keyboard Enter in the todo box showing a grey block over the results
- Saved shortcuts can now be removed (gestures > saved shortcuts > remove a saved shortcut)
- An uninstalled app keeping its keyword, so no other app could use it
- Clock wrapping onto two lines with a large clock and a big system font
- Lock prompt always saying "double-tap", whichever gesture you used
- Notification and quick settings gestures now say so when the phone doesn't allow them
- Turning bold off in the font picker left the picker's own text bold until it was reopened
- An empty gap on the home screen when both tip gestures were set to none
- A leaked window error when pressing home with a settings dialog open

## v1.3

### New
- **Number shortcuts** - search results are numbered, and typing the number opens that app: `w2` opens the 2nd result for `w`. An app with a number in its name still wins, so `fifa 2` keeps listing "FIFA 22" and "FIFA 23".
- **Landscape layout** - clock, music and todo on the left, search and apps on the right. Makes room for a floating keyboard (choose its side in settings) and adapts to a full keyboard.
- **Work profile and dual apps** - work apps are listed as `Name · work` and dual apps (Samsung Dual Messenger, Xiaomi Dual Apps...) as `Name · dual`, each opening in its own profile. Apps with several launcher icons now show all of them.
- **Hide anything** - the clock, date, screen time, now playing bar and todo list can each be turned off, and so can the all apps and settings buttons. With the settings button hidden, "MinimalSF settings" appears in all apps and search.
- **Button style** - bottom buttons as icon + text, icon only or text only.
- **Keyboard option** - choose whether the keyboard opens on its own on the home screen.
- **All apps grid** - show all apps as a list or a grid of 2-7 columns. Search results stay a list.
- **App details** - long press shows the package name and version.

### Changed
- Settings are grouped into a short menu: search & keyboard, home screen, all apps view, font and gestures. Font size moved into the font picker.
- The RAM indicator is removed.
- Long lists in dialogs show a scroll bar and fade at the bottom, so it's clear they scroll.
- In landscape, search and apps use the full width when everything on the left is hidden.
- Default auto-launch delay is 100ms (was 200ms). A delay you already chose is kept.
- Preinstalled apps that can't be removed no longer offer uninstall.
- The 404ms easter egg is blue.

### Fixes
- Clock kept the old time after a timezone change.
- Typing went into the todo box after returning home if it had focus.
- Now playing bar read "no music playing" while the home screen was partly covered.
- Android 13+ blocks accessibility and notification access for some sideloaded installs; the app now explains how to allow it.
- First flashlight toggle did nothing if the torch was already on.
- Crash when searching an app name containing letters like "İ".
- Crash when the camera service refused a flashlight request.
- Crash on every start if the saved todo list was damaged.

## v1.2.2
- New app ID: `io.github.dhiraj_j_s.minimalsf` (was `com.minimal.launcher`, which another app on Google Play already uses)
- Because the ID changed, this installs as a separate app. Set it as your home app, then uninstall the old version. Settings do not carry over.

## v1.2.1
- Fixed: screen time over-counted after the phone restarted or crashed while an app was open
- F-Droid ready: store listing and screenshots in `fastlane/`, no embedded dependency metadata in the APK

## v1.2

### New
- **Gestures screen** (settings → gestures): double tap, long press, swipe left / right / up / down on empty home space. Each can:
  open an app, call a contact, run an app shortcut (e.g. WhatsApp "new chat"), open a specific app screen (Activity Launcher style),
  toggle the flashlight, open the camera, play/pause, skip track, open notifications or quick settings, open all apps, lock the screen, or do nothing.
- **Saved shortcuts** - "add to home screen" requests from other apps (Activity Launcher, Chrome, …) are saved and can be put on a gesture.
- **App shortcuts in the long-press menu**, above app info.
- **Screen time** (optional) - today's total under the date; tap for top apps and Digital Wellbeing; per-app time in the long-press menu.
- **Download fonts** - 22 free Google Fonts in the font menu, fetched by Google Play services (the app still has no internet permission).
- **Font options** - monospace, clean, phone default (One UI Sans on Samsung, or your chosen font style), downloaded, or imported .ttf/.otf; plus a **bold** toggle.
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
