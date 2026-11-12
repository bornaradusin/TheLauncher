# Works out which screens a change needs captured, from the changed file paths on stdin.
# Prints SECTIONS=... and PAGES=... for $GITHUB_ENV. "all" anywhere captures everything.
import re
import sys

PAGE_FILES = {
    'GeneralPreferences': 'general',
    'IconPickerPreference': 'general-iconpack',
    'IconShapePreference': 'general',
    'HomeScreenPreferences': 'home-screen',
    'HomeScreenGridPreferences': 'home-screen-grid',
    'LauncherPopupPreference': 'home-screen-popup-editor',
    'DockPreferences': 'dock',
    'SearchProviderPreferences': 'dock-search-provider',
    'AppDrawerPreferences': 'app-drawer',
    'HiddenAppsPreferences': 'app-drawer-hidden-apps',
    'LockedAppsPreferences': 'app-drawer-locked-apps',
    'DrawerTabsPreferences': 'app-drawer-tabs',
    'AppDrawerFoldersPreference': 'app-drawer-folder',
    'SearchPreferences': 'search',
    'FolderPreferences': 'folders',
    'GesturePreferences': 'gestures',
    'SmartspacePreferences': 'smartspace',
    'BackupAndRestorePreference': 'backup-restore',
    'ExperimentalFeaturesPreferences': 'experimental-features',
}

# (path pattern, sections, settings pages)
RULES = [
    (r'smartspace|glance', ['glance'], ['smartspace']),
    (r'seerr', ['seerr'], []),
    (r'allapps|drawer', ['drawer', 'restore'], ['app-drawer']),
    (r'search', ['drawer'], ['search']),
    (r'hotseat|qsb|dock', ['home', 'dock'], ['dock']),
    (r'folder', ['home'], ['folders']),
    (r'backup|hyperion|restoredb|modeldbcontroller|iconoverride', ['restore'], ['backup-restore']),
    (r'gestures', [], ['gestures']),
    (r'ui/preferences/about', [], ['about']),
    (r'ui/preferences/(components|navigation|preferenceactivity)|settingscategories|preferencesdashboard', ['settings'], []),
    (r'/theme/|/icons/|res/values/(colors|dimens|styles)', ['home', 'drawer'], []),
]

sections, pages = set(), set()
for path in (line.strip() for line in sys.stdin):
    if not path:
        continue
    if path == 'all':
        sections.add('all')
        continue
    low = path.lower()
    for pattern, add_sections, add_pages in RULES:
        if re.search(pattern, low):
            sections.update(add_sections)
            pages.update(add_pages)
    name = re.sub(r'\.kt$', '', path.rsplit('/', 1)[-1])
    if 'preferences/destinations/' in path and name in PAGE_FILES:
        pages.add(PAGE_FILES[name])

if 'all' in sections:
    print('SECTIONS=all')
    print('PAGES=all')
else:
    # A quick look at the home screen is always useful; it costs a few seconds.
    sections.add('home-first')
    print('SECTIONS=' + ' '.join(sorted(sections)))
    print('PAGES=' + ' '.join(sorted(pages)))
