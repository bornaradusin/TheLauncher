# Install

1. On your phone, open the [latest release](https://github.com/bornaradusin/TheLauncher/releases/latest) and download the `.apk` file.
2. Open the downloaded file. If Android asks, allow your browser or file manager to install apps.
3. Tap **Install**.
4. Press the Home button. Android asks which app to use: pick **TheLauncher** and tap **Always**.

If Android doesn't ask, set it yourself: **Settings → Apps → Default apps → Home app → TheLauncher**.

## Opening the settings

Long-press an empty spot on the home screen and tap **Home settings**.

## Updating

From 1.1.0, TheLauncher updates itself. About once a day, when you open its settings, it looks on GitHub for a newer release and shows what's new, with **Update** and **Not now**:

- **Update** downloads the new APK, checks it against the release's `SHA256SUMS.txt`, and opens Android's installer. The first time, Android asks you to allow TheLauncher to install apps: turn on **Allow from this source** and come back. It installs over the version you have and keeps your layout and settings.
- The downloaded APK is deleted as soon as the update is in.
- **Not now** stops that version being offered by itself. You can still get it from **Settings → About → Check for updates**.

Only published releases count, never drafts. You can also update by hand: download the newer `.apk` from [Releases](https://github.com/bornaradusin/TheLauncher/releases) and install it the same way.

## Checking a download

Every release from 1.1.0 lists the APK's SHA-256 checksum in `SHA256SUMS.txt`, so you can check the file you downloaded is the one GitHub built.
