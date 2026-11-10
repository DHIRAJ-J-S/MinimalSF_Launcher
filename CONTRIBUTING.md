# Contributing to MinimalSF

Thanks for helping out! A few things to know first.

## Before you start

- **Open an issue first** for anything bigger than a small fix, so we can agree on it before you spend time on it.
- **MinimalSF stays minimal.** New features need to fit a small, distraction-free launcher. Extras that only some people want, or that need Shizuku or root, usually belong in the [MinimalSF Extended](https://github.com/DHIRAJ-J-S/MinimalSF_Extended) plugin instead.
- **No internet, no trackers.** MinimalSF has no internet permission, ads, analytics or non-free libraries, and must stay that way so it can stay on F-Droid.

## Reporting bugs

Use the [bug report form](https://github.com/DHIRAJ-J-S/MinimalSF_Launcher/issues/new/choose). Include your MinimalSF version, phone and Android version, and steps to reproduce.

## Building

Open the project in Android Studio, or run:

```bash
./gradlew assembleRelease
```

Release builds are signed by GitHub Actions; see [.github/RELEASING.md](.github/RELEASING.md).

## Pull requests

- Keep each pull request to one change.
- Follow the style of the surrounding code (Kotlin, no new dependencies unless really needed: every library adds to the download size).
- Test on a real phone if you can, and say what you tested on.
- Update [CHANGELOG.md](CHANGELOG.md) under a new "Unreleased" heading if the change is visible to users.

By contributing, you agree that your work is released under the [MIT License](LICENSE).
