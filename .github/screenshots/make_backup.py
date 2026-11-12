# Builds a stand-in Hyperion backup shaped like a real one (same files, tables and keys), with apps
# that exist on the emulator: a renamed home app, a picture icon and a renamed drawer app.
import sqlite3, struct, sys, time, zlib, zipfile, os, tempfile

out = sys.argv[1]
work = tempfile.mkdtemp()

def png(size, rgb):
    raw = b''
    r2 = (size / 2) ** 2
    for y in range(size):
        raw += b'\x00'
        for x in range(size):
            inside = (x - size / 2 + .5) ** 2 + (y - size / 2 + .5) ** 2 <= r2
            raw += bytes(rgb + (255,)) if inside else b'\x00\x00\x00\x00'
    def chunk(t, d):
        return struct.pack('>I', len(d)) + t + d + struct.pack('>I', zlib.crc32(t + d) & 0xffffffff)
    return b'\x89PNG\r\n\x1a\n' + chunk(b'IHDR', struct.pack('>IIBBBBB', size, size, 8, 6, 0, 0, 0)) + \
        chunk(b'IDAT', zlib.compress(raw)) + chunk(b'IEND', b'')

def intent(pkg, cls=None):
    comp = f"{pkg}/{cls}" if cls else f"{pkg}/.Main"
    return f"#Intent;action=android.intent.action.MAIN;category=android.intent.category.LAUNCHER;launchFlags=0x10200000;component={comp};end"

prefs = {
    'string': {
        'dark_mode': 'follow_system', 'pref_home_down_action': 'sesame_search', 'pref_icon_badging_style': 'text',
        'pref_weather_owm_location': 'Dublin ', 'page_indicator_mode': 'hidden', 'migration_src_workspace_size': '7,8',
        'smartspace_calendar_click_action': 'custom_app;com.google.android.deskclock', 'pref_minus_one_theme': 'dark',
        'fingerprint_lock_timeout': '1', 'pref_app_launch_animation': 'default', 'pref_cached_folder_shape': 'Circle',
        'drawer_folders': '[{"title":"Google","tabId":-1,"tabIdForFolder":-1,"position":0,"apps":["com.google.android.apps.maps/com.google.android.maps.MapsActivity","com.google.android.gm/.ConversationListActivityGmail","com.google.android.youtube/.app.honeycomb.Shell$HomeActivity"]},{"title":"Tools","tabId":-1,"tabIdForFolder":-1,"position":0,"apps":["com.google.android.deskclock/com.android.deskclock.DeskClock","com.google.android.calculator/com.android.calculator2.Calculator"]}]',
    },
    'int': {
        'pref_drawer_opacity_level': 72, 'pref_glow_opacity': 52, 'pref_hotseat_columns': 6, 'migration_src_hotseat_count': 6,
        'pref_desktop_rows': 8, 'pref_desktop_columns': 7, 'pref_drawer_row_height_scale': 85, 'pref_drawer_columns': 6,
        'pref_version_code': 20216,
    },
    'boolean': {
        'pref_show_apps_in_folders_in_app_list': False, 'pref_smartspace_show_events': True, 'pages_dark_mode_gradient_summary': False,
        'pref_smartspace_show_time': True, 'pref_show_search_bar_colored_g': True, 'pages_remember_drawer_position': True,
        'pref_add_icon_to_home': False, 'pref_smartspace_show_weather': True, 'pages_show_drawer_indicator': False,
        'pref_enable_sesame_search': True, 'fingerprint_lock': True, 'pages_enable_bounce_physics': False, 'pref_smartspace': True,
        'pages_two_row_dock': True, 'pref_show_folders_first': True, 'gesture_haptic': True, 'pref_enable_sesame_longpress': True,
        'pref_enable_sesame_workspace_search': True, 'transparent_background': True, 'pref_use_fallback_search': True,
    },
    'set': {
        'locked_apps': ['com.android.settings/com.android.settings.Settings'],
        'all_apps_hide': ['com.google.android.calculator/com.android.calculator2.Calculator'],
        'pref_calendar_events_values': [],
    },
}
from xml.sax.saxutils import escape
lines = ["<?xml version='1.0' encoding='utf-8' standalone='yes' ?>", '<map>']
for k, v in prefs['string'].items(): lines.append(f'    <string name="{k}">{escape(v)}</string>')
for k, v in prefs['int'].items(): lines.append(f'    <int name="{k}" value="{v}" />')
for k, v in prefs['boolean'].items(): lines.append(f'    <boolean name="{k}" value="{str(v).lower()}" />')
for k, v in prefs['set'].items():
    lines.append(f'    <set name="{k}">' + ''.join(f'<string>{escape(i)}</string>' for i in v) + '</set>')
lines.append('</map>')
open(os.path.join(work, 'projekt.launcher_preferences.xml'), 'w').write('\n'.join(lines))

db = sqlite3.connect(os.path.join(work, 'launcher.db'))
db.execute("""CREATE TABLE favorites (_id INTEGER PRIMARY KEY,title TEXT,intent TEXT,container INTEGER,screen INTEGER,cellX INTEGER,cellY INTEGER,spanX INTEGER,spanY INTEGER,itemType INTEGER,appWidgetId INTEGER NOT NULL DEFAULT -1,iconPackage TEXT,iconResource TEXT,icon BLOB,customIcon BLOB,titleAlias TEXT,iconName TEXT,iconPackName TEXT,isFolderMask INTEGER NOT NULL DEFAULT 0,showFolderMaskIcon INTEGER NOT NULL DEFAULT 0,folderMaskInfoComponent TEXT,sesameShortcut INTEGER NOT NULL DEFAULT 0,sesameShortcutCn TEXT,itemHotseatPageId INTEGER NOT NULL DEFAULT 0,appWidgetProvider TEXT,modified INTEGER NOT NULL DEFAULT 0,restored INTEGER NOT NULL DEFAULT 0,profileId INTEGER DEFAULT 0,rank INTEGER NOT NULL DEFAULT 0,options INTEGER NOT NULL DEFAULT 0)""")
db.execute("CREATE TABLE workspaceScreens (_id INTEGER PRIMARY KEY, screenRank INTEGER, modified INTEGER NOT NULL DEFAULT 0)")
db.execute("INSERT INTO workspaceScreens VALUES (0, 0, ?)", (int(time.time() * 1000),))
dock = [
    ('Phone', 'com.google.android.dialer', 'com.google.android.dialer.extensions.GoogleDialtactsActivity', 0),
    ('Messages', 'com.google.android.apps.messaging', 'com.google.android.apps.messaging.ui.ConversationListActivity', 1),
    ('Chrome', 'com.android.chrome', 'com.google.android.apps.chrome.Main', 0),
    ('YouTube', 'com.google.android.youtube', 'com.google.android.youtube.app.honeycomb.Shell$HomeActivity', 0),
    ('Photos', 'com.google.android.apps.photos', 'com.google.android.apps.photos.home.HomeActivity', 0),
    ('Gmail', 'com.google.android.gm', 'com.google.android.gm.ConversationListActivityGmail', 0),
    ('Contacts', 'com.google.android.contacts', 'com.android.contacts.activities.PeopleActivity', 0),
    ('Play Store', 'com.android.vending', 'com.android.vending.AssetBrowserActivity', 1),
    ('Calendar', 'com.google.android.calendar', 'com.android.calendar.AllInOneActivity', 0),
    ('Files', 'com.google.android.documentsui', 'com.android.documentsui.LauncherActivity', 0),
    ('Camera', 'com.android.camera2', 'com.android.camera.CameraLauncher', 0),
    ('Settings', 'com.android.settings', 'com.android.settings.Settings', 0),
]
_id = 1
for i, (title, pkg, cls, itype) in enumerate(dock):
    x, y = i % 6, i // 6
    db.execute("INSERT INTO favorites (_id,title,intent,container,screen,cellX,cellY,spanX,spanY,itemType,rank) VALUES (?,?,?,?,?,?,?,1,1,?,0)",
               (_id, title, intent(pkg, cls), -101, x, x, y, itype)); _id += 1
# Home: a renamed app and an app with a picture icon on the bottom row, like the real setup.
db.execute("INSERT INTO favorites (_id,title,intent,container,screen,cellX,cellY,spanX,spanY,itemType,titleAlias) VALUES (?,?,?,?,?,?,?,1,1,0,?)",
           (_id, 'Maps', intent('com.google.android.apps.maps', 'com.google.android.maps.MapsActivity'), -100, 0, 0, 7, 'My Maps')); _id += 1
db.execute("INSERT INTO favorites (_id,title,intent,container,screen,cellX,cellY,spanX,spanY,itemType,customIcon) VALUES (?,?,?,?,?,?,?,1,1,0,?)",
           (_id, 'Clock', intent('com.google.android.deskclock', 'com.android.deskclock.DeskClock'), -100, 0, 6, 7, png(96, (220, 60, 60)))); _id += 1
# Leftover rows Hyperion keeps outside any container.
db.execute("INSERT INTO favorites (_id,title,intent,container,screen,cellX,cellY,spanX,spanY,itemType) VALUES (?,?,?,?,?,?,?,1,1,0)",
           (_id, 'Chrome', intent('com.android.chrome', 'com.google.android.apps.chrome.Main'), -1, 0, 1, 0)); _id += 1
db.commit(); db.close()

icons = sqlite3.connect(os.path.join(work, 'app_icons.db'))
icons.execute("CREATE TABLE icons (componentName TEXT NOT NULL, profileId INTEGER NOT NULL, lastUpdated INTEGER NOT NULL DEFAULT 0, version INTEGER NOT NULL DEFAULT 0, icon BLOB, icon_low_res BLOB, icon_color INTEGER NOT NULL DEFAULT 0, label TEXT, system_state TEXT, has_custom_icon INTEGER NOT NULL DEFAULT 0, has_custom_title INTEGER NOT NULL DEFAULT 0, custom_icon_NAME TEXT, custom_icon_package_name TEXT, PRIMARY KEY (componentName, profileId))")
icons.execute("INSERT INTO icons (componentName,profileId,label,has_custom_title) VALUES ('com.android.chrome/com.google.android.apps.chrome.Main',0,'Browser',1)")
icons.execute("INSERT INTO icons (componentName,profileId,label) VALUES ('com.android.settings/com.android.settings.Settings',0,'Settings')")
icons.commit(); icons.close()

with zipfile.ZipFile(out, 'w', zipfile.ZIP_DEFLATED) as z:
    for name in ['projekt.launcher_preferences.xml', 'launcher.db', 'app_icons.db']:
        z.write(os.path.join(work, name), name)
    z.writestr('preview.png', png(32, (0, 0, 0)))
print('wrote', out, os.path.getsize(out))
