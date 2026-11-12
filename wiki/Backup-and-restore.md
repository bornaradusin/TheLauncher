# Backup and restore

Settings: **Home settings → Backup and restore**.

<img src="https://raw.githubusercontent.com/wiki/bornaradusin/TheLauncher/images/settings-backup.png" width="260" alt="Backup and restore">

## Backing up

**Create backup** saves your layout and settings into one file, with a preview of your home screen. Choose where to save it, for example a cloud drive folder, so it survives a reset or a new phone.

## Restoring

**Restore backup** opens a TheLauncher backup, shows what's in it, and lets you choose what to restore: layout, settings, or both. The launcher restarts with your setup.

## Saved layouts

**Save current layout** keeps a copy of your home screen and settings inside the launcher, with no file to pick. Tap a saved layout to go back to it; the launcher restarts with that setup. **Delete saved layouts** removes them all.

## Reload icons

**Reload icons** clears the cached icons and loads them again. Use it when an icon pack update or a changed app icon doesn't show.

## Coming from Hyperion

**Restore backup** brings your Hyperion Launcher setup across. It reads the file and works out on its own whether it came from TheLauncher, Hyperion or Nova.

1. In Hyperion: **Settings → Backup and restore → Create backup**.
2. In TheLauncher: **Restore backup** and pick the `.backup` file.
3. Check the summary, then tap **Restore**. TheLauncher restarts with your setup.

What comes across:

- Home screen pages and icon positions, folders, the dock (one or two rows), widgets' places, renamed apps
- Grid size, dock size and pages, drawer columns, row height and opacity
- Icon pack, themed icons, icon and label sizes, which labels show, label shadow
- Drawer folders, hidden apps, locked apps and the re-lock timeout
- Gestures, including the Home button action
- The feed, At a Glance (date, time, weather, calendar), a fixed weather town and the temperature unit
- Page indicator, blackout, fade on swipe, animation speed, hidden status or navigation bar, glow, drawer bounce, close drawer on launch, the All tab, folder names, two-finger double tap and gesture haptics
- Notification dot style, dark mode, rotation, desktop lock, drawer search bar, suggestions, drawer blur and the wallpaper dimmer
- The Google feed theme, glow colours, folder icon size and the icon background colour

Settings you never changed in Hyperion come across as Hyperion's defaults, so the result looks like your Hyperion screen, not TheLauncher's defaults.

Widgets come back in the right place, but Android ties every widget to the launcher that made it, so each needs one tap to set up again. Hyperion-only features with nothing similar here are skipped.

## Coming from Nova

**Restore backup** also imports a Nova Launcher backup (`.novabackup`): your home screen, dock, folders, widgets, shortcuts, grid size and icon pack.
