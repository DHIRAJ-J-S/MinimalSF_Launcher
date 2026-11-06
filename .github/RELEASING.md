# Releasing

Ordinary commits never release anything. A commit on `main` releases only when its first line starts with a version.

## Make a release

1. In `app/build.gradle`, set `versionName` to the new version and raise `versionCode`.
2. Add `fastlane/metadata/android/en-US/changelogs/<versionCode>.txt` (500 characters at most). This is what F-Droid shows.
3. Commit with the version first and your release notes in the body, then push:

```
v1.4: Short title of the release

Your release notes, in Markdown.
They appear on the GitHub release exactly as written.

- Added this
- Fixed that
```

That creates the release **v1.4 - Short title of the release**, with those notes and `MinimalSF_v1.4.apk` attached. The `v1.4` tag is created at the same time.

## What counts as a release commit

| First line | Releases? |
|---|---|
| `v1.4: Title` or `v1.4 - Title` | Yes, titled `v1.4 - Title` |
| `v1.4` or `v1.4.1` | Yes, titled with just the version |
| `Fix typo`, `Update README` | No |
| `v1.4 fix`, `Add v1.4 notes`, `1.4: Title` | No: it must start with `v`, then the version, then `:` or ` - ` or nothing |

If you push several commits together, the newest release commit is the one released. Lines like `Co-Authored-By:` are left out of the notes. With no notes, the release just says `Release v1.4`.

## When it stops without releasing

The run fails, and no tag or release is created, if:

- the version in the commit doesn't match `versionName`
- `versionCode` isn't higher than the previous release's
- a signing secret is missing
- the APK isn't signed with the certificate F-Droid expects

Fix the cause and push a new release commit. If the version already has a tag, the run does nothing, so a version can't be published twice.

## F-Droid

There's nothing separate to trigger. F-Droid watches this repository for new `v…` tags and builds them from source. It publishes only if its build matches `MinimalSF_v<version>.apk` from the release, and this usually takes a few days. The store summary and description come from `fastlane/` at the tagged commit.

Keep each line of `full_description.txt` as one block with no line breaks inside lists: F-Droid's site turns every line break into a blank line.

## Signing secrets

Set under Settings → Secrets and variables → Actions:

| Secret | Value |
|---|---|
| `KEYSTORE_BASE64` | The release keystore, base64 encoded |
| `KEYSTORE_PASSWORD` | Keystore password |
| `KEY_ALIAS` | Key alias |
| `KEY_PASSWORD` | Key password |
