# TheLauncher

Open-source Android home screen launcher.

## Features

- Icon packs, themed icons and custom icon shapes
- One- and two-finger gestures, double-tap and home-button actions
- Hidden apps and locked apps (fingerprint/face or screen lock, adjustable re-lock timeout)
- At a Glance widget with Google weather, calendar events, battery, media and torch
- Google Discover feed, custom fonts, drawer blur
- App drawer folders and custom drawer tabs, hide duplicate apps
- Multi-row and paged dock, dock shadow, label shadow
- Wallpaper dimmer and dark-theme dimming, navigation bar gradient
- Desktop lock, notification dots and counts, app suggestions
- Sorted folders, return to first page on screen off
- Backup and restore

## Building

Every push to `main` builds the signed APK once on GitHub Actions. To release, set the new version in `version.txt`: the build for that push publishes it as a GitHub release with the APK and its checksum.

Locally: `./gradlew assembleOssWithQuickstepGithubRelease` (JDK 21, Android SDK, `keystore.properties` for signing).

## Privacy

TheLauncher sends nothing anywhere itself. Weather comes from the Google app on your phone.

## License

See `LICENSE.txt`.
