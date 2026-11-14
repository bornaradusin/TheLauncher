#!/bin/bash
set -x
# Puts each of the launcher's own widgets on the home screen and takes a picture of the result.
PKG=app.thelauncher
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
tap_text() { xy=$(find_xy "$1") && { adb shell input tap $xy; sleep 2; return 0; }; echo "tap_text: '$1' not found"; return 1; }
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
tap_id() {
  dump /tmp/ui.xml || return 1
  xy=$(python3 - "$1" <<'PY'
import re, sys, xml.etree.ElementTree as ET
for n in ET.parse('/tmp/ui.xml').getroot().iter('node'):
    if (n.get('resource-id') or '').endswith('/' + sys.argv[1]):
        b = list(map(int, re.findall(r'\d+', n.get('bounds'))))
        print((b[0] + b[2]) // 2, (b[1] + b[3]) // 2)
        sys.exit(0)
sys.exit(1)
PY
  ) && { adb shell input tap $xy; sleep 2; return 0; }; echo "tap_id: '$1' not found"; return 1
}
long_xy() { adb shell input swipe $1 $2 $1 $2 1200; sleep 2; }
home() { adb shell input keyevent KEYCODE_HOME; sleep 2; }

adb install -r -g TheLauncher.apk
adb shell cmd role add-role-holder android.app.role.HOME $PKG
adb shell cmd uimode night yes
adb emu geo fix -6.2603 53.3498
adb logcat -c
home; sleep 10
shot 00-home 2

n=1
for widget in 'Clock' 'Weather' 'Battery' 'Now playing'; do
  home
  long_xy 540 1200
  tap_text 'Widgets' || { shot "$n-no-menu"; continue; }
  sleep 3
  tap_field && adb shell input text "$(echo "$widget" | sed 's/ /%s/g')" && sleep 4
  shot "$n-a-search"
  tap_text "$widget" || tap_text "$widget.*"
  shot "$n-b-picked"
  # Tapping the preview brings up its Add button.
  tap_id widget_preview_container
  shot "$n-c-preview" 2
  tap_text 'Add' || tap_text 'Add.*'
  sleep 4
  home; sleep 6
  shot "$n-d-home"
  n=$((n + 1))
done

home; sleep 20
shot 9-home-final
adb logcat -d > "$OUT/logcat-all.txt"
grep -E "TheLauncherWidgets|AndroidRuntime|FATAL|WeatherStore|LauncherAppWidgetHostView" "$OUT/logcat-all.txt" > "$OUT/logcat.txt"
