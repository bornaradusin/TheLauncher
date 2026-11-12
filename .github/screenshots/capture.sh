#!/bin/bash
set -x
# Captures the launcher on an emulator: TheLauncher.apk in the current folder, pictures in $GITHUB_WORKSPACE/shots.
PKG=app.thelauncher
PREFS="$PKG/app.thelauncher.ui.preferences.PreferenceActivity"
OUT="$GITHUB_WORKSPACE/shots"
mkdir -p "$OUT"

dump() { adb shell uiautomator dump /sdcard/ui.xml >/dev/null 2>&1 && adb pull /sdcard/ui.xml "$1" >/dev/null 2>&1; }
shot() { sleep "${2:-3}"; adb exec-out screencap -p > "$OUT/$1.png"; dump "$OUT/$1.xml"; }
find_xy() {
  dump /tmp/ui.xml || return 1
  python3 - "$1" <<'PY'
import re, sys, xml.etree.ElementTree as ET
pat = re.compile(sys.argv[1], re.I)
try:
    root = ET.parse('/tmp/ui.xml').getroot()
except Exception:
    sys.exit(1)
for n in root.iter('node'):
    for k in ('text', 'content-desc'):
        v = n.get(k) or ''
        if v and pat.fullmatch(v):
            b = list(map(int, re.findall(r'\d+', n.get('bounds'))))
            print((b[0] + b[2]) // 2, (b[1] + b[3]) // 2)
            sys.exit(0)
sys.exit(1)
PY
}
scroll_to() { for i in 1 2 3 4 5 6 7 8; do find_xy "$1" >/dev/null && return 0; scroll; done; return 1; }
tap_text() { xy=$(find_xy "$1") && { adb shell input tap $xy; sleep 2; return 0; }; echo "tap_text: '$1' not found"; return 1; }
tap_last() {
  dump /tmp/ui.xml || return 1
  xy=$(python3 - "$1" <<'PY'
import re, sys, xml.etree.ElementTree as ET
pat = re.compile(sys.argv[1], re.I)
found = None
for n in ET.parse('/tmp/ui.xml').getroot().iter('node'):
    for k in ('text', 'content-desc'):
        v = n.get(k) or ''
        if v and pat.fullmatch(v):
            b = list(map(int, re.findall(r'\d+', n.get('bounds'))))
            found = ((b[0] + b[2]) // 2, (b[1] + b[3]) // 2)
if not found:
    sys.exit(1)
print(*found)
PY
  ) && { adb shell input tap $xy; sleep 2; return 0; }; echo "tap_last: '$1' not found"; return 1
}
tap_field() {
  dump /tmp/ui.xml || return 1
  xy=$(python3 - <<'PY'
import re, sys, xml.etree.ElementTree as ET
for n in ET.parse('/tmp/ui.xml').getroot().iter('node'):
    if 'EditText' in (n.get('class') or ''):
        b = list(map(int, re.findall(r'\d+', n.get('bounds'))))
        print((b[0] + b[2]) // 2, (b[1] + b[3]) // 2)
        sys.exit(0)
sys.exit(1)
PY
  ) && { adb shell input tap $xy; sleep 1; return 0; }; return 1
}
type_setting() { scroll_to "$1" && tap_text "$1" && tap_field && adb shell input text "$2" && sleep 1 && tap_last 'OK'; }
long_text() { xy=$(find_xy "$1") && { set -- $xy; adb shell input swipe $1 $2 $1 $2 1200; sleep 2; return 0; }; echo "long_text: not found"; return 1; }
long_xy() { adb shell input swipe $1 $2 $1 $2 1200; sleep 2; }
home() { adb shell input keyevent KEYCODE_HOME; sleep 2; }
back() { adb shell input keyevent KEYCODE_BACK; sleep 1; }
scroll() { adb shell input swipe 540 1900 540 700 500; sleep 2; }
nudge() { adb shell input swipe 540 1600 540 1000 500; sleep 2; }
drawer() { home; long_xy 540 1200; tap_text 'Apps list'; sleep 3; }
dismiss() { sleep 4; tap_text 'Cancel' || true; }
settings() {
  if [ -n "$1" ]; then
    adb shell am start -W --activity-clear-task -n "$PREFS" -a android.intent.action.VIEW -d "thelauncher://settings/$1" >/dev/null
  else
    adb shell am start -W --activity-clear-task -n "$PREFS" >/dev/null
  fi
  sleep 3
}

# Device setup: dark theme, gesture navigation, clean status bar, on battery.
adb install -r -g TheLauncher.apk
adb shell cmd role add-role-holder android.app.role.HOME $PKG
adb shell cmd overlay enable com.android.internal.systemui.navbar.gestural
adb shell cmd uimode night yes
adb emu geo fix -0.1276 51.5072
adb emu power ac off
adb emu power status discharging
adb emu power capacity 82
adb shell settings put global sysui_demo_allowed 1
demo() { adb shell am broadcast -a com.android.systemui.demo -e command "$@" >/dev/null; }
demo enter
demo clock -e hhmm 0930
demo battery -e level 82 -e plugged false
demo network -e wifi show -e level 4
demo network -e mobile show -e datatype none -e level 4
demo notifications -e visible false
home; sleep 8
shot 00-home-first 2

# At a Glance: Open-Meteo weather on, onboarding card off.
settings smartspace
dismiss
shot 20-glance
scroll_to 'Weather \(Open-Meteo, uses location\)' && tap_text 'Weather \(Open-Meteo, uses location\)'
dismiss
shot 21-glance-weather
scroll_to 'Onboarding' && tap_text 'Onboarding'
dismiss
shot 22-glance-more
back; back; back

# Weather for the widget: a fixed place, since the emulator has no real location.
settings smartspace
dismiss
type_setting 'Weather location' 'Dublin'
back; back

# Material At a Glance (builds that have it): settings, then the widget in a few states.
settings smartspace
dismiss
if scroll_to 'Material card'; then
  nudge
  shot 24-glance-style
  scroll; shot 26-glance-style-more
  back; back
  home; sleep 8; shot 29-glance-widget
  adb emu power ac on
  adb emu power status charging
  adb emu power capacity 82
  home; sleep 6; shot 30-glance-charging
  adb emu power ac off
  adb emu power status discharging
  adb emu power capacity 12
  home; sleep 6; shot 31-glance-low-battery
  adb emu power capacity 82
  # An alarm 40 minutes from now, for a second row.
  now=$(adb shell date +%H:%M | tr -d '\r')
  total=$(( 10#${now%%:*} * 60 + 10#${now##*:} + 40 ))
  adb shell am start -a android.intent.action.SET_ALARM --ei android.intent.extra.alarm.HOUR $(( (total / 60) % 24 )) --ei android.intent.extra.alarm.MINUTES $(( total % 60 )) --ez android.intent.extra.alarm.SKIP_UI true >/dev/null
  sleep 3
  adb emu power ac on
  adb emu power status charging
  settings smartspace
  dismiss
  scroll_to 'Size' && nudge && tap_text 'Size' && tap_text 'Large: two rows'
  shot 32-glance-large-setting
  back; back
  home; sleep 8; shot 33-glance-large
  adb emu power ac off
  adb emu power status discharging
else
  back; back
fi

# At a Glance in the widget list (builds that have it).
home
long_xy 540 1200
if tap_text 'Widgets'; then
  sleep 3
  tap_field && adb shell input text 'glance' && sleep 3
  shot 34-widget-list
  back; back
fi
home

# Seerr in search (builds that have it), against the stand-in server.
settings seerr
if find_xy 'Address' >/dev/null; then
  type_setting 'Address' 'http://10.0.2.2:5055'
  type_setting 'API key' 'stand-in-key'
  tap_text 'Test connection'; sleep 4
  shot 50-seerr-settings
  adb logcat -c
  home; drawer
  shot 51a-seerr-drawer 1
  adb shell input tap 540 230; sleep 2
  adb shell input text 'add'; sleep 2
  adb shell input text '%sbatman'; sleep 8
  shot 51-seerr-add
  adb logcat -d -b crash > "$OUT/crash.txt" 2>&1
  adb logcat -d -t 400 '*:W' > "$OUT/logcat-seerr.txt" 2>&1
  tap_text 'Batman: The Animated Series' && shot 52-seerr-info
  tap_last 'Add' && shot 53-seerr-seasons
  back; sleep 2
  shot 51b-seerr-after-sheet
  tap_text 'Add' && sleep 3 && shot 54-seerr-added 1
  home; sleep 2; home; drawer
  adb shell input tap 540 230; sleep 2
  adb shell input text 'delete%sbatman'; sleep 8
  shot 55-seerr-delete
  tap_text 'Delete' && sleep 3 && shot 56-seerr-delete-sheet
  back; sleep 1; home
fi

# Hyperion-style drawer (builds that have it): full screen instead of a sheet.
settings app-drawer
dismiss
if scroll_to 'Drawer on a sheet'; then
  tap_text 'Drawer on a sheet'
  shot 27-drawer-setting
fi
back; back

# Drawer indicator above the dock (builds that have it).
settings dock
dismiss
if scroll_to 'Drawer indicator'; then
  nudge
  tap_text 'Drawer indicator'
  tap_text 'Arrow'
  shot 28-dock-indicator-setting
fi
back; back

# Home screen.
home; sleep 20
shot 01-home
long_xy 540 1200
shot 02-home-menu
back; home
long_text '.*Chrome.*'
shot 03-app-menu
tap_text '(Customize|Edit)'
shot 04-customize
back; back; home
tap_text '(Folder: )?Google'
shot 05-folder
back; home

# Widgets.
long_xy 540 1200
tap_text 'Widgets'
shot 06-widgets 4
back; back; home

# App drawer and search.
drawer
shot 07-drawer
scroll
shot 08-drawer-scrolled
home; drawer
adb shell input tap 540 230; sleep 2
adb shell input text 'ca'
shot 09-search 3
back; back; home

# Settings.
settings
shot 10-settings
scroll
shot 11-settings-more
scroll
shot 12-settings-end
# Every settings page, for the wiki.
for p in general general-iconpack general-themedicons home-screen home-screen-grid home-screen-popup-editor \
         dock dock-search-provider app-drawer app-drawer-hidden-apps app-drawer-locked-apps app-drawer-tabs \
         app-drawer-folder search folders gestures smartspace backup-restore about experimental-features; do
  settings "$p"
  shot "s-$p"
  scroll
  shot "s-$p-2"
  scroll
  shot "s-$p-3"
done
settings general
tap_text 'Icon shape'
shot s-icon-shape
settings gestures
tap_text 'Double tap'
shot s-gesture-picker
home

# Hyperion restore, last as it replaces the whole setup: a stand-in backup shaped like a real one.
if [ -f "$RUNNER_TEMP/hyperion_test.backup" ]; then
  adb push "$RUNNER_TEMP/hyperion_test.backup" /sdcard/Download/hyperion_test.backup
  adb shell am broadcast -a android.intent.action.MEDIA_SCANNER_SCAN_FILE -d file:///sdcard/Download/hyperion_test.backup >/dev/null
  sleep 3
  adb logcat -c
  settings backup-restore
  scroll_to 'Restore Hyperion backup' && tap_text 'Restore Hyperion backup'
  sleep 4; shot 60-picker 1
  tap_text 'hyperion_test.backup' || { tap_text 'Show roots' && tap_text 'Downloads' && sleep 3 && tap_text 'hyperion_test.backup'; } || shot 60b-picker-roots 1
  sleep 6; shot 61-restore 1
  scroll; shot 62-restore-more 1
  tap_last 'Restore'
  sleep 25
  adb logcat -d -b crash > "$OUT/crash.txt" 2>&1
  adb logcat -d -t 600 '*:W' > "$OUT/logcat-restore.txt" 2>&1
  adb logcat -d | grep -i -E 'thelauncher|launcher3|ModelDb|RestoreDb|GridSize|LoaderTask|DatabaseHelper|Hyperion|SQLite' > "$OUT/logcat-launcher.txt" 2>&1
  # No Home key here: the restored setup turns a Home press into a Sesame search.
  sleep 10; shot 63-home-restored
  long_xy 540 900; tap_text 'Apps list' || adb shell input swipe 540 1600 540 300 800; sleep 3
  shot 64-drawer-restored
  scroll; shot 65-drawer-restored-2
  back; sleep 2
  settings app-drawer; scroll; scroll; scroll; shot 66-drawer-settings
  settings folders; nudge; shot 67-folder-settings
fi


adb shell am broadcast -a com.android.systemui.demo -e command exit >/dev/null
ls -la "$OUT"
