# Troubleshooting

### Pressing Home goes back to my old launcher

TheLauncher isn't your home app. Set it under **Settings → Apps → Default apps → Home app**. See [Install](Install).

### "Failed to restore backup"

The message after the colon says why. If the file came from Hyperion, use **Restore Hyperion backup**, not **Restore backup**; the same goes for Nova. A `.backup` file that won't open may have been only partly downloaded; copy it again and retry.

### After a Hyperion restore, widgets are blank

Android ties each widget to the launcher that created it. Tap each one once to set it up again. See [Backup and restore](Backup-and-restore#coming-from-hyperion).

### An app opens without asking for my fingerprint

App lock works when you open apps from TheLauncher. Opening an app from Recents, a notification or another app skips it. Also check the re-lock timeout: after you unlock an app, it stays open for that long. See [App lock](App-lock).

### Weather doesn't show

- Check **At a Glance → What to show → Weather** is on.
- With **Weather location** empty, TheLauncher needs location permission and a recent location. Type your town instead to skip that.
- Weather needs an internet connection and refreshes every 30 minutes.

### Calendar events don't show

Allow calendar access when asked, and check the calendar is on under **Calendars to be shown**. All-day events and events more than 24 hours away aren't shown.

### Double tap to sleep does nothing

It needs TheLauncher's accessibility service. See [Gestures](Gestures#sleep-and-recents).

### An update won't install

Android needs permission to let TheLauncher install apps: **Allow from this source**. If it says the app conflicts with one already installed, the installed copy was signed differently (the very first test builds). Uninstall TheLauncher once, then install the new version; every release after that installs over the last.

### The icons look too big or small

Change **Icon size** under Home screen, App drawer or Folders. See [Icons and look](Icons-and-look#sizes-and-labels).
