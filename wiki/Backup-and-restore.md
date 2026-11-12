# Backup and restore

Settings: **Home settings → Backup and restore**.

## Backing up

**Create backup** saves your layout and settings into one file, with a preview of your home screen. Choose where to save it, for example a cloud drive folder, so it survives a reset or a new phone.

## Restoring

**Restore backup** opens a TheLauncher backup, shows what's in it, and lets you choose what to restore: layout, settings, or both. The launcher restarts with your setup.

## Coming from Hyperion

**Restore Hyperion backup** brings your Hyperion Launcher setup across.

1. In Hyperion: **Settings → Backup and restore → Create backup**.
2. In TheLauncher: **Restore Hyperion backup** and pick the `.backup` file.
3. Check the summary, then tap **Restore**. TheLauncher restarts with your setup.

What comes across:

- Home screen pages and icon positions, folders, the dock (one or two rows), widgets' places, renamed apps
- Grid size, dock size and pages, drawer columns, row height and opacity
- Icon pack, themed icons, icon and label sizes, which labels show, label shadow
- Drawer folders, hidden apps, locked apps and the re-lock timeout
- Gestures, including the Home button action
- The feed, At a Glance (date, time, weather, calendar), a fixed weather town and the temperature unit
- Notification dot style, dark mode, rotation, desktop lock, drawer search bar, suggestions, drawer blur and the wallpaper dimmer

Settings you never changed in Hyperion come across as Hyperion's defaults, so the result looks like your Hyperion screen, not TheLauncher's defaults.

Widgets come back in the right place, but Android ties every widget to the launcher that made it, so each needs one tap to set up again. Hyperion-only features with nothing similar here are skipped.

## Coming from Nova

**Restore Nova backup** imports a Nova Launcher backup (`.novabackup`): your home screen, dock, folders, widgets, shortcuts, grid size and icon pack.
