package app.thelauncher.backup

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import android.net.Uri
import android.os.Process
import android.util.Log
import androidx.datastore.preferences.core.edit
import app.thelauncher.DeviceProfileOverrides
import app.thelauncher.allapps.HIDDEN_DASHBOARDS_ALL
import app.thelauncher.allapps.HIDDEN_DASHBOARDS_ICON_PACKS
import app.thelauncher.allapps.HIDDEN_DASHBOARDS_NONE
import app.thelauncher.allapps.HIDDEN_DASHBOARDS_THEMES
import app.thelauncher.hotseat.DOCK_SHAPE_FLAT
import app.thelauncher.hotseat.DOCK_SHAPE_ROUND
import app.thelauncher.hotseat.DockShape
import app.thelauncher.hotseat.dockShapePresets
import app.thelauncher.util.DOT_PLACEMENT_BOTTOM_LEFT
import app.thelauncher.util.DOT_PLACEMENT_BOTTOM_RIGHT
import app.thelauncher.util.DOT_PLACEMENT_TOP_LEFT
import app.thelauncher.util.DOT_PLACEMENT_TOP_RIGHT
import app.thelauncher.util.DRAWER_INDICATOR_ARROW
import app.thelauncher.util.DRAWER_INDICATOR_LINE
import app.thelauncher.util.DRAWER_INDICATOR_NONE
import app.thelauncher.util.SUGGESTIONS_DIVIDER_LABEL
import app.thelauncher.util.SUGGESTIONS_DIVIDER_NONE
import app.thelauncher.data.folder.service.FolderService
import app.thelauncher.gestures.config.GestureHandlerConfig
import app.thelauncher.gestures.handlers.OpenAppTarget
import app.thelauncher.gestures.handlers.SleepMode
import app.thelauncher.preferences.PreferenceManager
import app.thelauncher.extras.DrawerTab
import app.thelauncher.preferences2.PreferenceManager2
import app.thelauncher.preferences2.firstCached
import app.thelauncher.qsb.QSB_END_ASSISTANT
import app.thelauncher.qsb.QSB_END_NONE
import app.thelauncher.smartspace.model.SMARTSPACE_DATE_LONG
import app.thelauncher.smartspace.model.SMARTSPACE_LAYOUT_ONE_CARD
import app.thelauncher.theme.color.ColorOption
import app.thelauncher.ui.preferences.components.HiddenAppsInSearch
import app.thelauncher.util.kotlinxJson
import com.android.launcher3.GridType
import com.android.launcher3.InvariantDeviceProfile
import com.android.launcher3.LauncherAppState
import com.android.launcher3.LauncherPrefs
import com.android.launcher3.LauncherSettings.Favorites
import com.android.launcher3.model.DatabaseHelper
import com.android.launcher3.model.DeviceGridState
import com.android.launcher3.pm.UserCache
import com.android.launcher3.util.ComponentKey
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.net.URISyntaxException
import java.util.UUID
import java.util.zip.ZipInputStream
import javax.xml.parsers.DocumentBuilderFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import org.json.JSONArray
import app.thelauncher.data.iconoverride.IconOverrideRepository
import app.thelauncher.font.FontCache
import app.thelauncher.icons.PictureIcons
import app.thelauncher.icons.picker.IconPickerItem
import app.thelauncher.icons.picker.IconType
import com.android.launcher3.R
import org.json.JSONObject
import org.w3c.dom.Element

class HyperionBackupConverter(
    private val context: Context,
    private val uri: Uri,
) {

    companion object {
        private const val TAG = "HyperionBackupConverter"
        private const val PREFS_XML = "projekt.launcher_preferences.xml"
        private const val LAUNCHER_DB = "launcher.db"
        private const val APP_ICONS_DB = "app_icons.db"
        private const val PRIMARY_FONT = "primary_font.ttf"
        private const val SECONDARY_FONT = "secondary_font.ttf"
        private const val SMARTSPACE_FONT = "smartspace_font.ttf"
        private val FONT_FILES = setOf(PRIMARY_FONT, SECONDARY_FONT, SMARTSPACE_FONT)
        private const val TEMP_DIR_PREFIX = "hyperion_"
        private const val STAGED_DB = "hyperion_workspace.db"
        private const val CONTAINER_DESKTOP = -100
        private const val CONTAINER_HOTSEAT = -101
        private const val MAX_LOCK_TIMEOUT_SECONDS = 1800

        // Hyperion sizes its icons from a different base than this launcher; these turn Hyperion's
        // 100% into the size it actually draws (measured on a 7-column grid, 6-icon two-row dock).
        private const val HOME_ICON_SCALE = 0.77f
        private const val DRAWER_ICON_SCALE = 0.75f
        private const val DOCK_ICON_SCALE = 0.93f

        // Hyperion's drawer opacity lets more wallpaper through than the same number here.
        private const val DRAWER_OPACITY_SCALE = 0.65f

        // Hyperion saves Int.MAX_VALUE for a colour that was never picked.
        private const val HYPERION_UNSET_COLOR = Int.MAX_VALUE

        // Hyperion's folder preview icons next to this launcher's (measured from screenshots).
        private const val HYPERION_FOLDER_PREVIEW_SCALE = 0.9f

        // Hyperion's drawer labels are smaller than this launcher's at the same percentage.
        private const val DRAWER_LABEL_SCALE = 0.9f

        // Hyperion's drawer blur is lighter than this launcher's at the same strength.
        private const val DRAWER_BLUR_SCALE = 0.5f

        // How opaque Hyperion's dock search bar is when no colour was picked (measured from screenshots).
        private const val HYPERION_DOCK_QSB_ALPHA = 70

        // Hyperion's "rounded rectangle" search bar, as a share of a fully round corner.
        private const val ROUNDED_RECT_QSB_RADIUS = 0.3f
    }

    data class HyperionDrawerFolder(val title: String, val components: List<String>)

    data class HyperionSettings(
        val columns: Int?,
        val rows: Int?,
        val hotseatColumns: Int?,
        val twoRowDock: Boolean,
        val drawerColumns: Int?,
        val drawerOpacity: Int?,
        val iconPackPackage: String?,
        val themedIcons: Boolean?,
        val hiddenApps: Set<String>,
        val lockedApps: Set<String>,
        val appLockEnabled: Boolean,
        val appLockTimeoutMinutes: Int?,
        val feedEnabled: Boolean?,
        val smartspaceEnabled: Boolean?,
        val smartspaceShowTime: Boolean?,
        val smartspaceShowWeather: Boolean?,
        val smartspaceShowEvents: Boolean?,
        val weatherLocation: String?,
        val swipeDownAction: String?,
        val rememberDrawerPosition: Boolean?,
        val dockShadow: Boolean?,
        val notificationCount: Boolean?,
        val drawerFolders: List<HyperionDrawerFolder>,
        val drawerTabs: List<HyperionDrawerFolder> = emptyList(),
        val raw: Map<String, Any> = emptyMap(),
        val rawSets: Map<String, Set<String>> = emptyMap(),
    ) {
        fun set(key: String): Set<String>? = rawSets[key]
        fun int(key: String): Int? = raw[key] as? Int
        fun bool(key: String): Boolean? = raw[key] as? Boolean
        fun string(key: String): String? = raw[key] as? String
        fun float(key: String): Float? = raw[key] as? Float
    }

    data class HyperionBackupInfo(
        val settings: HyperionSettings,
        val appCount: Int,
        val folderCount: Int,
        val widgetCount: Int,
        val shortcutCount: Int,
        val iconPackLabel: String?,
        val customizedApps: Int = 0,
        val fonts: Int = 0,
        val notCarriedOver: List<String> = emptyList(),
    ) {
        val columns get() = settings.columns
        val rows get() = settings.rows
        val hotseatColumns get() = settings.hotseatColumns
    }

    private data class ItemCounts(val apps: Int, val folders: Int, val widgets: Int, val shortcuts: Int)

    /** A name or icon the user gave an app in Hyperion. */
    private data class AppCustomization(
        val name: String? = null,
        val iconPack: String? = null,
        val iconName: String? = null,
        val picture: ByteArray? = null,
    ) {
        val isEmpty get() = name == null && iconName == null && picture == null
    }

    private val user = Process.myUserHandle()

    suspend fun parseInfo(): HyperionBackupInfo = withContext(Dispatchers.IO) {
        val tempDir = File(context.cacheDir, "$TEMP_DIR_PREFIX${UUID.randomUUID()}")
        tempDir.mkdirs()
        try {
            extractFromZip(uri, tempDir, setOf(PREFS_XML, LAUNCHER_DB, APP_ICONS_DB) + FONT_FILES)
            val xmlFile = File(tempDir, PREFS_XML)
            val dbFile = File(tempDir, LAUNCHER_DB)
            require(xmlFile.exists() && dbFile.exists()) { "Missing $PREFS_XML or $LAUNCHER_DB" }
            val settings = parseSettings(xmlFile)
            val counts = countItems(dbFile)
            val fonts = FONT_FILES.filter { File(tempDir, it).isFile }.toSet()
            HyperionBackupInfo(
                settings = settings,
                appCount = counts.apps,
                folderCount = counts.folders,
                widgetCount = counts.widgets,
                shortcutCount = counts.shortcuts,
                iconPackLabel = settings.iconPackPackage?.let { resolveLabel(it) },
                customizedApps = readCustomizations(File(tempDir, APP_ICONS_DB), dbFile).size,
                fonts = (fonts - SMARTSPACE_FONT).size,
                notCarriedOver = notCarriedOver(settings, dbFile, fonts),
            )
        } finally {
            tempDir.deleteRecursively()
        }
    }

    suspend fun convertAndRestore(info: HyperionBackupInfo) = withContext(Dispatchers.IO) {
        val tempDir = File(context.cacheDir, "$TEMP_DIR_PREFIX${UUID.randomUUID()}")
        tempDir.mkdirs()
        try {
            extractFromZip(uri, tempDir, setOf(LAUNCHER_DB, APP_ICONS_DB) + FONT_FILES)
            val sourceDb = File(tempDir, LAUNCHER_DB)
            require(sourceDb.exists()) { "Missing $LAUNCHER_DB" }

            val settings = info.settings
            val hotseatColumns = settings.hotseatColumns
            val stagedDb = File(tempDir, STAGED_DB)
            createRestoredDb(sourceDb, stagedDb, hotseatColumns ?: 5, settings.twoRowDock, dockPages(settings))

            val columns = settings.columns
            val rows = settings.rows
            if (columns != null && rows != null && hotseatColumns != null) {
                val gridInfo = DeviceProfileOverrides.DBGridInfo(
                    numHotseatColumns = hotseatColumns,
                    numRows = rows,
                    numColumns = columns,
                )
                val gridState = DeviceGridState(
                    columns,
                    rows,
                    hotseatColumns,
                    InvariantDeviceProfile.TYPE_PHONE,
                    gridInfo.dbFile,
                    GridType.GRID_TYPE_ANY,
                )
                gridState.writeToPrefs(context, true)
                gridState.writeToPrefs(context)
                // Saved now: the launcher restarts right after, before a delayed write would land.
                LauncherPrefs.get(context).putSync(LauncherPrefs.DB_FILE.to(gridInfo.dbFile))
                InvariantDeviceProfile.INSTANCE.get(context).dbFile = gridInfo.dbFile
            }

            runCatching { writeSettings(settings) }.onFailure { Log.w(TAG, "Settings import failed", it) }
            runCatching { importDrawerFolders(settings.drawerFolders) }.onFailure { Log.w(TAG, "Drawer folder import failed", it) }
            runCatching { importDrawerTabs(settings.drawerTabs) }.onFailure { Log.w(TAG, "Drawer tab import failed", it) }
            runCatching { importCustomizations(readCustomizations(File(tempDir, APP_ICONS_DB), sourceDb)) }
                .onFailure { Log.w(TAG, "App name and icon import failed", it) }
            runCatching { importFonts(tempDir) }.onFailure { Log.w(TAG, "Font import failed", it) }

            val restoredDbFile = context.getDatabasePath(TheLauncherBackup.RESTORED_DB_FILE_NAME)
            restoredDbFile.parentFile?.mkdirs()
            // The restored home screen is swapped in when the launcher restarts, before anything opens
            // the database; doing it now would race the running launcher, which may already have made
            // an empty database for the new grid.
            TheLauncherBackup.setPendingRestore(context)
            stagedDb.copyTo(restoredDbFile, overwrite = true)
        } finally {
            tempDir.deleteRecursively()
        }
    }

    private fun parseSettings(xmlFile: File): HyperionSettings {
        val root = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(xmlFile).documentElement
        val strings = mutableMapOf<String, String>()
        val ints = mutableMapOf<String, Int>()
        val bools = mutableMapOf<String, Boolean>()
        val floats = mutableMapOf<String, Float>()
        val sets = mutableMapOf<String, Set<String>>()
        val children = root.childNodes
        for (i in 0 until children.length) {
            val node = children.item(i) as? Element ?: continue
            val name = node.getAttribute("name").ifEmpty { continue }
            when (node.tagName) {
                "string" -> strings[name] = node.textContent.orEmpty()
                "int", "long" -> node.getAttribute("value").toIntOrNull()?.let { ints[name] = it }
                "boolean" -> bools[name] = node.getAttribute("value") == "true"
                "float" -> node.getAttribute("value").toFloatOrNull()?.let { floats[name] = it }
                "set" -> {
                    val values = mutableSetOf<String>()
                    val items = node.getElementsByTagName("string")
                    for (j in 0 until items.length) items.item(j).textContent?.let { values.add(it) }
                    sets[name] = values
                }
            }
        }

        val migratedSize = strings["migration_src_workspace_size"]?.split(",")?.mapNotNull { it.trim().toIntOrNull() }
        // Hyperion uses the typed-in location unless "Automatic location" was switched on (off by default).
        val weatherAuto = bools["pref_weather_location_auto"] ?: false
        val weatherLocation = strings["pref_weather_owm_location"]?.trim()?.takeIf { !weatherAuto && it.isNotEmpty() }

        return HyperionSettings(
            columns = ints["pref_desktop_columns"] ?: migratedSize?.getOrNull(0),
            rows = ints["pref_desktop_rows"] ?: migratedSize?.getOrNull(1),
            hotseatColumns = ints["pref_hotseat_columns"] ?: ints["migration_src_hotseat_count"],
            twoRowDock = bools["pages_two_row_dock"] ?: false,
            drawerColumns = ints["pref_drawer_columns"],
            drawerOpacity = ints["pref_drawer_opacity_level"],
            iconPackPackage = strings["pref_icon_pack"]?.takeIf { it.isNotBlank() },
            themedIcons = bools["themed_icons"],
            hiddenApps = sets["all_apps_hide"].orEmpty(),
            lockedApps = sets["locked_apps"].orEmpty(),
            appLockEnabled = bools["fingerprint_lock"] ?: false,
            appLockTimeoutMinutes = strings["fingerprint_lock_timeout"]?.toIntOrNull(),
            feedEnabled = bools["pref_enable_minus_one"],
            smartspaceEnabled = bools["pref_smartspace"],
            smartspaceShowTime = bools["pref_smartspace_show_time"],
            smartspaceShowWeather = bools["pref_smartspace_show_weather"],
            smartspaceShowEvents = bools["pref_smartspace_show_events"],
            weatherLocation = weatherLocation,
            swipeDownAction = strings["pref_home_down_action"],
            rememberDrawerPosition = bools["pages_remember_drawer_position"],
            dockShadow = bools["pages_enable_dock_shadow"],
            notificationCount = (strings["pref_icon_badging_style"] ?: "dot") == "text",
            drawerFolders = parseDrawerFolders(strings["drawer_folders"]),
            drawerTabs = parseDrawerFolders(strings["drawer_tabs"]),
            raw = buildMap<String, Any> {
                putAll(strings)
                putAll(ints)
                putAll(bools)
                putAll(floats)
            },
            rawSets = sets,
        )
    }

    /**
     * Reads Hyperion's drawer folders or drawer tabs. Hyperion has saved these two ways: a list of
     * flattened components under "apps", or full item objects under "contents".
     */
    private fun parseDrawerFolders(json: String?): List<HyperionDrawerFolder> {
        if (json.isNullOrBlank()) return emptyList()
        return try {
            val array = JSONArray(json)
            (0 until array.length()).mapNotNull { i ->
                val obj = array.optJSONObject(i) ?: return@mapNotNull null
                val title = obj.optString("title").trim()
                val components = mutableListOf<String>()
                obj.optJSONArray("apps")?.let { apps ->
                    for (j in 0 until apps.length()) components.add(apps.optString(j))
                }
                obj.optJSONArray("contents")?.let { contents ->
                    for (j in 0 until contents.length()) {
                        val item = contents.optJSONObject(j)
                        if (item == null) {
                            components.add(contents.optString(j))
                            continue
                        }
                        val component = item.optString("componentName").ifBlank {
                            item.optString("intent").let { uri ->
                                runCatching { Intent.parseUri(uri, 0).component?.flattenToString() }.getOrNull().orEmpty()
                            }
                        }
                        components.add(component)
                    }
                }
                if (title.isEmpty()) null else HyperionDrawerFolder(title, components.filter { it.isNotBlank() }.distinct())
            }
        } catch (t: Throwable) {
            Log.w(TAG, "Could not read drawer folders", t)
            emptyList()
        }
    }

    private val launcherApps by lazy { context.getSystemService(android.content.pm.LauncherApps::class.java) }
    private val launchActivities = mutableMapOf<String, List<ComponentName>>()

    private fun toComponentKey(flattened: String): String? {
        val component = ComponentName.unflattenFromString(flattened.substringBefore('#')) ?: return null
        return ComponentKey(installedComponent(component), user).toString()
    }

    /**
     * Hyperion saved the activity an app was launched with at the time. Apps rename that activity in
     * updates, so when it no longer exists the app's current launch activity is used instead.
     */
    private fun installedComponent(component: ComponentName): ComponentName {
        val activities = launchActivities.getOrPut(component.packageName) {
            runCatching {
                launcherApps?.getActivityList(component.packageName, user)?.map { it.componentName }
            }.getOrNull().orEmpty()
        }
        if (activities.isEmpty() || component in activities) return component
        return activities.first()
    }

    private suspend fun writeSettings(settings: HyperionSettings) {
        val prefs = PreferenceManager.getInstance(context)
        val prefs2 = PreferenceManager2.getInstance(context)

        runCatching {
            prefs.sp.edit().apply {
            settings.columns?.let { putInt(prefs.workspaceColumns.key, it) }
            settings.rows?.let { putInt(prefs.workspaceRows.key, it) }
            settings.hotseatColumns?.let { putInt(prefs.hotseatColumns.key, it) }
            putInt(prefs.hotseatRows.key, if (settings.twoRowDock) 2 else 1)
            settings.iconPackPackage?.let { putString(prefs.iconPackPackage.key, it) }
            settings.themedIcons?.let { putBoolean(prefs.themedIcons.key, it) }
            settings.drawerOpacity?.let { putFloat(prefs.drawerOpacity.key, (it / 100f * DRAWER_OPACITY_SCALE).coerceIn(0f, 1f)) }
            putInt(prefs.dockPages.key, dockPages(settings))
            settings.bool("pref_allowRotation")?.let { putBoolean(prefs.allowRotation.key, it) }
            settings.bool("pages_enable_drawer_blur")?.let { putBoolean(prefs.enableWallpaperBlur.key, it) }
            when (settings.string("dark_mode")) {
                "light" -> putString(prefs.launcherTheme.key, "light")
                "dark" -> putString(prefs.launcherTheme.key, "dark")
                "follow_system" -> putString(prefs.launcherTheme.key, "system")
            }
            // Everything below uses Hyperion's default when the backup doesn't have it, so the result
            // looks like the Hyperion setup rather than this launcher's defaults.
            putBoolean(prefs.wrapAdaptiveIcons.key, settings.bool("pref_draw_icon_back_shape") ?: false)
            putInt(prefs.iconBackgroundStaticColor.key, hyperionIconBackground(settings) ?: 0)
            putBoolean(prefs.addIconToHome.key, settings.bool("pref_add_icon_to_home") ?: true)
            putBoolean(prefs.wallpaperScrolling.key, settings.bool("key_scroll_wallpaper") ?: false)
            putBoolean(prefs.folderApps.key, !(settings.bool("pref_show_apps_in_folders_in_app_list") ?: true))
            putInt(prefs.wallpaperBlur.key, ((settings.int("pages_drawer_blur_strength") ?: 50) * DRAWER_BLUR_SCALE).toInt().coerceIn(0, 100))
            val themed = settings.themedIcons ?: false
            putBoolean(prefs.drawerThemedIcons.key, themed && !(settings.bool("themed_icons_desktop_only") ?: false))
            putFloat(prefs.hotseatQsbCornerRadius.key, if (settings.bool("qsb_new_style") == true) ROUNDED_RECT_QSB_RADIUS else 1f)
            val customDockQsb = hyperionColor(settings, "pref_workspace_qsb_color", "pref_workspace_qsb_color_toggle") != null
            putInt(prefs.hotseatQsbAlpha.key, if (customDockQsb) 100 else HYPERION_DOCK_QSB_ALPHA)
            val dockColor = hyperionColor(settings, "pref_dock_background_color", "pref_dock_background_color_toggle")
            putBoolean(prefs.hotseatBG.key, dockColor != null)
            hyperionDockShape(settings).let { shape ->
                putInt(prefs.hotseatBGHorizontalInsetLeft.key, shape.insetLeft)
                putInt(prefs.hotseatBGVerticalInsetTop.key, shape.insetTop)
                putInt(prefs.hotseatBGHorizontalInsetRight.key, shape.insetRight)
                putInt(prefs.hotseatBGVerticalInsetBottom.key, shape.insetBottom)
            }
            }.commit()
        }.onFailure { Log.w(TAG, "Could not import layout settings", it) }

        runCatching {
            settings.drawerColumns?.let { columns ->
                val gridOption = InvariantDeviceProfile.INSTANCE.get(context).closestProfile
                if (gridOption != null) {
                    prefs2.preferencesDataStore.edit { it[prefs2.drawerColumns.key] = columns }
                }
            }
        }.onFailure { Log.w(TAG, "Could not set drawer columns", it) }

        val hidden = settings.hiddenApps.mapNotNull { toComponentKey(it) }.toSet()
        val locked = if (settings.appLockEnabled) settings.lockedApps.mapNotNull { toComponentKey(it) }.toSet() else emptySet()

        runCatching {
            prefs2.preferencesDataStore.edit { store ->
                if (hidden.isNotEmpty()) store[prefs2.hiddenApps.key] = (store[prefs2.hiddenApps.key] ?: emptySet()) + hidden
                if (locked.isNotEmpty()) store[prefs2.lockedApps.key] = (store[prefs2.lockedApps.key] ?: emptySet()) + locked
                if (settings.appLockEnabled) {
                    settings.appLockTimeoutMinutes?.let {
                        store[prefs2.appLockTimeoutSeconds.key] = (it * 60).coerceIn(0, MAX_LOCK_TIMEOUT_SECONDS)
                    }
                }
                // Hyperion has the feed on and At a Glance off unless they were changed.
                store[prefs2.enableFeed.key] = settings.feedEnabled ?: true
                store[prefs2.feedTheme.key] = when (settings.string("pref_minus_one_theme")) {
                    "dark" -> "dark"
                    "light" -> "light"
                    else -> "auto"
                }
                store[prefs2.enableSmartspace.key] = settings.smartspaceEnabled ?: false
                // Weather on At a Glance comes from the Google app.
                settings.smartspaceShowWeather?.let { store[prefs2.smartspaceAagWidget.key] = it }
                store[prefs2.smartspaceWeather.key] = false
                settings.smartspaceShowEvents?.let { store[prefs2.smartspaceCalendarEvents.key] = it }
                settings.rememberDrawerPosition?.let { store[prefs2.rememberPosition.key] = it }
                settings.dockShadow?.let { store[prefs2.dockShadow.key] = it }
                settings.notificationCount?.let { store[prefs2.showNotificationCount.key] = it }
                fun gesture(hyperionKey: String, key: androidx.datastore.preferences.core.Preferences.Key<String>) {
                    val value = settings.string(hyperionKey) ?: return
                    val handler = gestureFor(value) ?: customAppGesture(settings, hyperionKey, value) ?: return
                    store[key] = kotlinxJson.encodeToString(handler)
                }
                gesture("pref_home_down_action", prefs2.homePressGestureHandler.key)
                gesture("pref_swipe_down_action", prefs2.swipeDownGestureHandler.key)
                gesture("pref_swipe_up_action", prefs2.swipeUpGestureHandler.key)
                gesture("pref_double_tap_action", prefs2.doubleTapGestureHandler.key)
                gesture("pref_two_finger_swipe_up_action", prefs2.twoFingerSwipeUpGestureHandler.key)
                gesture("pref_two_finger_swipe_down_action", prefs2.twoFingerSwipeDownGestureHandler.key)
                gesture("pref_back_down_action", prefs2.backPressGestureHandler.key)
                gesture("pref_two_finger_double_tap_action", prefs2.twoFingerDoubleTapGestureHandler.key)

                // Page indicators: Hyperion shows them on the home screen, the dock, both ("shared") or neither.
                val indicatorMode = settings.string("page_indicator_mode") ?: "shared"
                store[prefs2.pageIndicatorHidden.key] = indicatorMode == "hidden" || indicatorMode == "dock"
                store[prefs2.dockPageIndicatorHidden.key] = indicatorMode == "hidden" || indicatorMode == "workspace"
                store[prefs2.pageIndicatorLine.key] = (settings.string("page_indicator_style") ?: "line") == "line"
                settings.bool("blackout_dark_mode")?.let { store[prefs2.blackoutDarkMode.key] = it }
                settings.bool("pref_fade_on_desktop_swipe")?.let { store[prefs2.fadeOnSwipe.key] = it }
                settings.bool("pages_enable_bounce_physics")?.let { store[prefs2.drawerOverscroll.key] = it }
                settings.bool("pages_close_drawer_on_pause")?.let { store[prefs2.closeDrawerOnLaunch.key] = it }
                settings.bool("pref_show_zeroth_tab")?.let { store[prefs2.drawerTabsShowAll.key] = it }
                settings.bool("folder_show_folder_name")?.let { store[prefs2.showFolderTitle.key] = it }
                settings.bool("gesture_haptic")?.let { store[prefs2.gestureHaptics.key] = it }
                settings.bool("pref_enable_custom_gradient_glow")?.let { store[prefs2.desktopGlow.key] = it }
                settings.int("pref_glow_opacity")?.let { store[prefs2.desktopGlowOpacity.key] = (it / 100f).coerceIn(0.1f, 1f) }
                // Custom glow colours: each has an on/off switch and a colour in Hyperion.
                fun glowColor(edge: String): String {
                    if (settings.bool("pref_enable_custom_gradient_${edge}_glow_color") != true) return ColorOption.Default.toString()
                    val color = settings.raw.entries.firstOrNull { (key, value) ->
                        value is Int && key.contains("${edge}_glow_color") && !key.startsWith("pref_enable")
                    }?.value as? Int ?: return ColorOption.Default.toString()
                    return ColorOption.CustomColor(color).toString()
                }
                store[prefs2.desktopGlowTopColor.key] = glowColor("top")
                store[prefs2.desktopGlowBottomColor.key] = glowColor("bottom")
                store[prefs2.folderIconSizeFactor.key] = ((settings.int("pref_custom_folder_icon_size") ?: 100) / 100f).coerceIn(0.6f, 1.05f)
                store[prefs2.iconBackgroundColor.key] = hyperionIconBackground(settings)?.let { ColorOption.CustomColor(it).toString() }
                    ?: ColorOption.Default.toString()
                settings.string("pref_animation_speed")?.let {
                    if (it == "slow" || it == "fast" || it == "default") store[prefs2.animationSpeed.key] = it
                }
                settings.string("status_navigation_bars")?.let {
                    if (it == "hide_both") {
                        store[prefs2.showStatusBar.key] = false
                        store[prefs2.hideNavigationBar.key] = true
                    } else if (it.startsWith("hide") && it.contains("nav")) {
                        store[prefs2.hideNavigationBar.key] = true
                    } else if (it.startsWith("hide") && it.contains("status")) {
                        store[prefs2.showStatusBar.key] = false
                    }
                }

                // Icon and label sizes: Hyperion keeps percentages and only saves ones changed from 100%.
                val homePercent = (settings.int("pref_custom_workspace_icon_size") ?: 100) / 100f
                val dockPercent = (settings.int("pref_custom_dock_icon_size") ?: 100) / 100f
                val drawerPercent = (settings.int("pref_custom_icon_size") ?: 100) / 100f
                store[prefs2.homeIconSizeFactor.key] = HOME_ICON_SCALE * homePercent
                store[prefs2.dockIconSizeFactor.key] = (DOCK_ICON_SCALE * dockPercent) / (HOME_ICON_SCALE * homePercent)
                store[prefs2.drawerIconSizeFactor.key] = DRAWER_ICON_SCALE * drawerPercent
                store[prefs2.homeIconLabelSizeFactor.key] = (settings.int("pref_custom_workspace_icon_text_scale") ?: 100) / 100f
                store[prefs2.homeIconLabelFolderSizeFactor.key] = (settings.int("pref_custom_folder_icon_text_scale") ?: 100) / 100f
                store[prefs2.drawerIconLabelSizeFactor.key] = DRAWER_LABEL_SCALE * (settings.int("pref_custom_icon_text_scale") ?: 100) / 100f
                store[prefs2.drawerCellHeightFactor.key] = (settings.int("pref_drawer_row_height_scale") ?: 100) / 100f

                // Labels, with Hyperion's defaults for anything not saved.
                store[prefs2.showIconLabelsOnHomeScreen.key] = settings.bool("pref_enable_workspace_icon_text") ?: true
                store[prefs2.showIconLabelsOnHomeScreenFolder.key] = settings.bool("pref_enable_folder_icon_text") ?: true
                store[prefs2.enableLabelInDock.key] = settings.bool("pref_enable_dock_icon_text") ?: false
                store[prefs2.showIconLabelsInDrawer.key] = settings.bool("pref_enable_app_drawer_label_label") ?: true
                val twoLineDrawer = settings.bool("pref_enable_icon_multiple_lines") ?: false
                store[prefs2.twoLineAllApps.key] = twoLineDrawer
                // The drawer grid reads this copy, which the switch itself keeps in step.
                LauncherPrefs.get(context).put(LauncherPrefs.ENABLE_TWOLINE_ALLAPPS_TOGGLE, twoLineDrawer)
                store[prefs2.twoLineHome.key] = settings.bool("pref_enable_icon_multiple_lines_workspace") ?: false
                store[prefs2.twoLineFolder.key] = settings.bool("pref_enable_icon_multiple_lines_folder") ?: false
                store[prefs2.labelTextStyle.key] = (settings.string("pref_text_style")?.toIntOrNull() ?: 0).coerceIn(0, 3)
                store[prefs2.dockTextShadow.key] = settings.bool("pref_enable_icon_text_shadow_dock") ?: true
                store[prefs2.dockIconLabelSizeFactor.key] = ((settings.int("pref_custom_dock_icon_text_scale") ?: 100) / 100f).coerceIn(0.5f, 1.5f)
                // Drawer layout options, with Hyperion's defaults.
                store[prefs2.drawerFoldersFirst.key] = settings.bool("pref_show_folders_first") ?: false
                store[prefs2.showWorkTab.key] = settings.bool("pref_show_work_tab") ?: true
                store[prefs2.drawerTabsRemoveFromAll.key] = settings.bool("pref_hide_apps_main_page") ?: false
                store[prefs2.drawerSuggestionsDivider.key] =
                    if (settings.bool("app_search_prediction_divider_enabled") ?: true) SUGGESTIONS_DIVIDER_LABEL else SUGGESTIONS_DIVIDER_NONE
                store[prefs2.drawerIndicator.key] = when {
                    !(settings.bool("pages_show_drawer_indicator") ?: true) -> DRAWER_INDICATOR_NONE
                    settings.string("pages_style_indicator") == "indicator_line" -> DRAWER_INDICATOR_LINE
                    else -> DRAWER_INDICATOR_ARROW
                }
                hyperionColor(settings, "pref_dock_indicator_color", "pref_dock_indicator_color_toggle")?.let {
                    store[prefs2.drawerIndicatorColor.key] = it.toString()
                }
                if (settings.bool("pref_enable_custom_notification_dot_size") == true) {
                    store[prefs2.notificationDotSizeFactor.key] = ((settings.int("pref_notification_dot_size") ?: 100) / 100f).coerceIn(0.5f, 2f)
                }
                // "Comfortable view" adds side padding to the home screen; off removes it.
                if (settings.bool("pref_padding_page") == false) store[prefs2.workspacePaddingHorizontalFactor.key] = 0f
                // Hyperion's "coloured" G is the provider icon tinted with the accent (as themed icons are
                // here); only the "original" G keeps the provider's own colours.
                store[prefs2.themedHotseatQsb.key] = !(settings.bool("pref_show_search_bar_original_g") ?: false)
                hyperionIconShape(settings.string("pref_override_icon_shape"))?.let { store[prefs2.iconShape.key] = it }
                settings.bool("pref_enable_icon_text_shadow")?.let { store[prefs2.desktopTextShadow.key] = it }

                // Drawer and search
                store[prefs2.hideAppDrawerSearchBar.key] = !(settings.bool("pref_show_drawer_search_bar") ?: true)
                settings.bool("auto_show_all_apps_search")?.let { store[prefs2.autoShowKeyboardInDrawer.key] = it }
                store[prefs2.showSuggestedAppsInDrawer.key] = settings.bool("pref_show_predictions") ?: false
                // Hyperion's Sesame search is part of its own search, which falls back to Google; here
                // the dock bar opens Sesame only when the Sesame app is installed.
                val sesameInstalled = runCatching { context.packageManager.getPackageInfo("ninja.sesame.app.edge", 0) }.isSuccess
                if (settings.bool("pref_enable_sesame_search") == true && sesameInstalled) {
                    store[prefs2.hotseatQsbProvider.key] = "sesame"
                }
                store[prefs2.smartspaceCenteredDate.key] = true
                if (settings.bool("pref_show_dock_search_bar") == false) store[prefs2.hotseatMode.key] = "disabled"
                settings.bool("pages_lock_desktop")?.let { store[prefs2.lockHomeScreen.key] = it }
                // At a Glance the way Hyperion draws it: with calendar events on it shows the date only;
                // otherwise "Display time" shows the time and "Display both time and date" shows both.
                val glanceEvents = settings.bool("pref_smartspace_show_events") ?: false
                val glanceTime = settings.bool("pref_smartspace_show_time") ?: false
                val glanceBoth = settings.bool("pref_smartspace_show_date") ?: false
                store[prefs2.smartspaceShowTime.key] = !glanceEvents && (glanceTime || glanceBoth)
                store[prefs2.smartspaceShowDate.key] = glanceEvents || glanceBoth || !glanceTime
                store[prefs2.smartspaceLayout.key] = SMARTSPACE_LAYOUT_ONE_CARD
                store[prefs2.smartspacePageDots.key] = false
                store[prefs2.smartspaceDateStyle.key] = SMARTSPACE_DATE_LONG
                hyperionColor(settings, "pref_smartspace_color", "pref_smartspace_color_toggle")?.let {
                    store[prefs2.smartspaceTextColor.key] = it.toString()
                }
                settings.set("pref_calendar_events_values")?.takeIf { it.isNotEmpty() }?.let {
                    store[prefs2.smartspaceCalendarIds.key] = it
                }

                // Colours Hyperion keeps as a custom colour plus a switch.
                hyperionColor(settings, "pref_dock_background_color", "pref_dock_background_color_toggle")?.let {
                    store[prefs2.hotseatBackgroundColor.key] = it.toString()
                }
                hyperionColor(settings, "pref_folder_background_color", "pref_folder_background_color_toggle")?.let {
                    store[prefs2.folderColor.key] = it.toString()
                }
                hyperionColor(settings, "pref_custom_drawer_background_color", "pref_enable_custom_drawer_background")?.let {
                    store[prefs2.appDrawerBackgroundColor.key] = it.toString()
                }
                // Search bars: Hyperion's colours, its assistant (✦) button, and the provider icon in the drawer.
                // Unset, Hyperion's dock search bar is the accent's deepest tone, slightly see-through.
                val dockQsbColor = hyperionColor(settings, "pref_workspace_qsb_color", "pref_workspace_qsb_color_toggle")
                store[prefs2.hotseatQsbColor.key] = (
                    dockQsbColor ?: ColorOption.SystemAccentDeep.takeIf { it.isSupported } ?: ColorOption.Default
                    ).toString()
                // Unset, Hyperion fills the drawer's search bar with the accent colour.
                store[prefs2.drawerQsbColor.key] =
                    (hyperionColor(settings, "pref_drawer_qsb_color", "pref_drawer_qsb_color_toggle") ?: ColorOption.SystemAccent).toString()
                store[prefs2.qsbEndButton.key] = if (settings.bool("pref_show_search_bar_mic") ?: true) QSB_END_ASSISTANT else QSB_END_NONE
                store[prefs2.drawerQsbProviderIcons.key] = true
                // Hyperion's drawer is full screen, not a sheet.
                store[prefs2.drawerOnSheet.key] = false
                // Hyperion's folder previews have bigger icons that fill the circle.
                store[prefs2.folderPreviewIconScale.key] = HYPERION_FOLDER_PREVIEW_SCALE
                store[prefs2.folderPreviewInside.key] = true

                // Label colours: Hyperion turns each on with either of two switches.
                fun labelColor(key: String, vararg toggles: String): String {
                    val on = toggles.any { settings.bool(it) == true }
                    return (if (on) hyperionColor(settings, key, null) else null)?.toString() ?: ColorOption.Default.toString()
                }
                store[prefs2.homeLabelColor.key] = labelColor(
                    "pref_custom_workspace_icon_text_color",
                    "pref_custom_workspace_icon_text_color_toggle",
                    "pref_enable_custom_workspace_icon_text_color",
                )
                store[prefs2.dockLabelColor.key] = labelColor(
                    "pref_custom_dock_icon_text_color",
                    "pref_custom_dock_icon_text_color_toggle",
                    "pref_enable_custom_dock_icon_text_color",
                )
                store[prefs2.drawerLabelColor.key] = labelColor(
                    "pref_custom_icon_text_color",
                    "pref_custom_icon_text_color_toggle",
                    "pref_enable_custom_icon_text_color",
                )
                store[prefs2.folderLabelColor.key] = labelColor(
                    "pref_custom_folder_icon_text_color",
                    "pref_custom_folder_icon_text_color_toggle",
                    "pref_enable_custom_folder_tweaks",
                )

                val badgeColor = hyperionColor(settings, "pref_notification_badge_color", "pref_notification_badge_color_toggle")
                badgeColor?.let { store[prefs2.notificationDotColor.key] = it.toString() }
                // Without a badge colour of its own, Hyperion colours each dot from the app's icon, and it
                // draws counts above 9 as "9+".
                store[prefs2.notificationDotFromIcon.key] = badgeColor == null
                store[prefs2.notificationCountShort.key] = true
                // Hyperion's "follow system" and "wallpaper" colours both come from the wallpaper; the
                // system's own Material You palette is what matches the rest of the phone.
                val hyperionSeed = settings.int("theme_color_seed")
                store[prefs2.accentColor.key] = when {
                    settings.string("theme_color_mode") == "custom" && hyperionSeed != null ->
                        ColorOption.CustomColor(hyperionSeed).toString()
                    android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S -> ColorOption.SystemAccent.toString()
                    else -> ColorOption.WallpaperPrimary.toString()
                }

                // Interface settings with a direct counterpart, using Hyperion's defaults.
                store[prefs2.showScrollbar.key] = settings.bool("pages_show_drawer_scroll_indicator") ?: true
                val wallpaperGradient = settings.bool("pages_dark_mode_gradient_summary") ?: true
                store[prefs2.showTopShadow.key] = wallpaperGradient
                store[prefs2.systemBarGradient.key] = wallpaperGradient
                store[prefs2.wallpaperDepthEffect.key] = settings.bool("wallpaper_zoom") ?: true
                store[prefs2.isHotseatEnabled.key] = settings.bool("pages_enable_dock") ?: true
                // Hyperion's dock rows are home screen rows, scaled by its "Dock height" (100% by default).
                store[prefs2.dockRowsMatchHome.key] = true
                store[prefs2.dockHeightFactor.key] = ((settings.int("dock_height") ?: 100) / 100f).coerceIn(0.5f, 2f)
                store[prefs2.roundedWidgets.key] = settings.bool("enforce_widget_rounded_corners") ?: true
                if (settings.bool("pref_full_width_widgets") == true) store[prefs2.widgetPaddingFactor.key] = 0f
                store[prefs2.hiddenAppsInSearch.key] = if (settings.bool("hidden_apps_search") == true) {
                    HiddenAppsInSearch.NEVER
                } else {
                    HiddenAppsInSearch.IF_NAME_TYPED
                }
                store[prefs2.drawerNavBarTint.key] = settings.bool("pages_enable_drawer_navbar_tint") ?: true
                store[prefs2.drawerStatusBarIcons.key] = when (settings.string("status_bar_icon_drawer_color")) {
                    "status_bar_dark" -> "dark"
                    "status_bar_light" -> "light"
                    else -> "auto"
                }
                when (settings.string("status_bar_icon_color")) {
                    "status_bar_dark" -> store[prefs2.darkStatusBar.key] = true
                    "status_bar_light" -> store[prefs2.darkStatusBar.key] = false
                }
                when (settings.string("pref_lock_screen_mode")) {
                    "screen_lock_accessibility" -> store[prefs2.sleepMode.key] = SleepMode.ACCESSIBILITY.toString()
                    "screen_lock_device_admin", "device_admin" -> store[prefs2.sleepMode.key] = SleepMode.DEVICE_ADMIN.toString()
                    "screen_lock_root", "root" -> store[prefs2.sleepMode.key] = SleepMode.ROOT.toString()
                }
                when (settings.string("pref_temperature_unit")) {
                    "use_fahrenheit" -> {
                        store[prefs2.smartspaceWeatherFahrenheit.key] = true
                        store[prefs2.smartspaceWeatherKelvin.key] = false
                    }
                    "use_kelvin" -> {
                        store[prefs2.smartspaceWeatherFahrenheit.key] = false
                        store[prefs2.smartspaceWeatherKelvin.key] = true
                    }
                    "use_celsius" -> {
                        store[prefs2.smartspaceWeatherFahrenheit.key] = false
                        store[prefs2.smartspaceWeatherKelvin.key] = false
                    }
                }
                // Dot corner: Hyperion's names are the ones this launcher uses.
                settings.string("pref_icon_badging_placement")?.takeIf {
                    it in setOf(DOT_PLACEMENT_TOP_RIGHT, DOT_PLACEMENT_TOP_LEFT, DOT_PLACEMENT_BOTTOM_LEFT, DOT_PLACEMENT_BOTTOM_RIGHT)
                }?.let { store[prefs2.notificationDotPlacement.key] = it }
                // The app tapping the date opens, kept as a component or a package.
                settings.string("pref_calendar_app")?.takeIf { it.isNotBlank() }?.let {
                    store[prefs2.smartspaceCalendarApp.key] = it.substringBefore('/')
                }
                // Newer Hyperion keeps that app as "custom_app;<package>" under the At a Glance tap action.
                settings.string("smartspace_calendar_click_action")?.takeIf { it.startsWith("custom_app;") }?.let {
                    it.substringAfter(';').substringBefore('/').takeIf { pkg -> pkg.isNotBlank() }
                        ?.let { pkg -> store[prefs2.smartspaceCalendarApp.key] = pkg }
                }
                store[prefs2.hiddenDashboards.key] = when (settings.string("pref_hidden_dashboards")) {
                    "icon_pack_dashboards" -> HIDDEN_DASHBOARDS_ICON_PACKS
                    "substratum_dashboards" -> HIDDEN_DASHBOARDS_THEMES
                    "all_dashboards" -> HIDDEN_DASHBOARDS_ALL
                    else -> HIDDEN_DASHBOARDS_NONE
                }
                hyperionColor(settings, "pref_drawer_scroll_indicator", "pref_drawer_scroll_indicator_toggle")?.let {
                    store[prefs2.drawerScrollbarColor.key] = it.toString()
                }
                store[prefs2.hotseatBackgroundCornerRadius.key] = hyperionDockShape(settings).cornerRadiusDp

                // Wallpaper dimmer: Hyperion's strength defaults to 20%.
                store[prefs2.wallpaperDimAmount.key] = if (settings.bool("wallpaper_dimmer_toggle") == true) {
                    (settings.float("wallpaper_dimmer_strength") ?: 0.2f).coerceIn(0f, 0.9f)
                } else {
                    0f
                }
            }
        }.onFailure { Log.w(TAG, "Could not import settings", it) }

        // "Force English": Android 13 and up keep a language per app.
        if (settings.bool("force_english_locale") == true && android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
            runCatching {
                context.getSystemService(android.app.LocaleManager::class.java).applicationLocales =
                    android.os.LocaleList.forLanguageTags("en")
            }.onFailure { Log.w(TAG, "Could not force English", it) }
        }
    }

    /** Hyperion keeps its icon shape as an SVG path; these are the paths of its ten shapes. */
    private fun hyperionIconShape(path: String?): String? {
        if (path == null) return null
        val p = path.replace(" ", "").lowercase()
        return when {
            p.isEmpty() -> "system"
            p.startsWith("m50,0l100,0") -> "sharpSquare"
            p.startsWith("m250c50") -> "roundedSquare"
            p.startsWith("m34.042") -> "cupertino"
            p.startsWith("m20,080,0") -> "octagon"
            p.startsWith("m50,0c10,0") -> "squircle"
            p.startsWith("m500a50") -> "circle"
            p.startsWith("m50,0a50,50") -> "teardrop"
            p.startsWith("mm55,0") || p.startsWith("m55,0") -> "pebble"
            p.startsWith("m12.97,0") -> "vessel"
            else -> null
        }
    }

    /** Hyperion's "Flat" (dock_o) and "Round" (dock_p, its default) dock styles. */
    private fun hyperionDockShape(settings: HyperionSettings): DockShape =
        dockShapePresets.getValue(if (settings.string("pages_style_dock") == "dock_o") DOCK_SHAPE_FLAT else DOCK_SHAPE_ROUND)

    /** Hyperion's "Launch app" gesture keeps the app under the matching "_package" key. */
    private fun customAppGesture(settings: HyperionSettings, actionKey: String, action: String): GestureHandlerConfig? {
        if (!action.startsWith("custom_app")) return null
        val saved = settings.string(actionKey.replace("_action", "_package")).orEmpty()
            .ifBlank { action.substringAfter(';', "") }
        if (saved.isBlank()) return null
        val component = ComponentName.unflattenFromString(saved)
            ?: runCatching { launcherApps?.getActivityList(saved, user)?.firstOrNull()?.componentName }.getOrNull()
            ?: return null
        val installed = installedComponent(component)
        return GestureHandlerConfig.OpenApp(
            resolveLabel(installed.packageName),
            OpenAppTarget.App(ComponentKey(installed, user)),
        )
    }

    /** A colour Hyperion keeps as "custom colour" plus an on/off switch, or null when not in use. */
    private fun hyperionColor(settings: HyperionSettings, key: String, toggle: String?): ColorOption? {
        if (toggle != null && settings.bool(toggle) != true) return null
        val value = settings.int(key) ?: return null
        return if (value == HYPERION_UNSET_COLOR) null else ColorOption.CustomColor(value)
    }

    private fun gestureFor(action: String): GestureHandlerConfig? = when (action.substringBefore(';')) {
        "", "none" -> GestureHandlerConfig.NoOp
        "all_apps" -> GestureHandlerConfig.OpenAppDrawer
        "app_search" -> GestureHandlerConfig.OpenAppSearch
        "sesame_search", "google_search" -> GestureHandlerConfig.OpenSearch
        "assistant" -> GestureHandlerConfig.OpenAssistant
        "lock_screen" -> GestureHandlerConfig.Sleep
        "qs_pulldown" -> GestureHandlerConfig.OpenNotifications
        "qs_expand" -> GestureHandlerConfig.OpenQuickSettings
        "show_recent_apps", "show_overview" -> GestureHandlerConfig.Recents
        "default_page" -> GestureHandlerConfig.GoToDefaultPage
        "hide_content" -> GestureHandlerConfig.ToggleHomeContent
        "launcher_settings" -> GestureHandlerConfig.OpenLauncherSettings
        "show_iconpack" -> GestureHandlerConfig.OpenIconPackPicker
        else -> null
    }

    /**
     * Brings Hyperion's drawer folders across. A folder that already exists with the same name (from
     * an earlier restore) gets the backup's apps, so restoring again always matches the backup.
     */
    private suspend fun importDrawerFolders(folders: List<HyperionDrawerFolder>) {
        if (folders.isEmpty()) return
        val service = FolderService.INSTANCE.get(context)
        val existing = runCatching { service.getFoldersFlow().first() }.getOrDefault(emptyList())
            .associateBy { it.title.trim() }
        folders.forEach { folder ->
            val keys = folder.components.mapNotNull { toComponentKey(it) }.distinct()
            runCatching {
                val id = existing[folder.title]?.id ?: service.saveFolderInfo(folder.title).toInt()
                service.updateFolderWithItems(id, folder.title, keys)
            }.onFailure { Log.w(TAG, "Could not import drawer folder ${folder.title}", it) }
        }
    }

    /** Hyperion's drawer tabs become drawer tabs here, replacing tabs with the same name. */
    private suspend fun importDrawerTabs(tabs: List<HyperionDrawerFolder>) {
        if (tabs.isEmpty()) return
        val prefs2 = PreferenceManager2.getInstance(context)
        val imported = tabs.map { tab ->
            DrawerTab(name = tab.title, apps = tab.components.mapNotNull { toComponentKey(it) }.toSet())
        }
        val importedNames = imported.map { it.name }.toSet()
        val kept = prefs2.drawerTabs.firstCached().filter { it.name !in importedNames }
        prefs2.drawerTabs.set(kept + imported)
    }

    /**
     * Names and icons the user gave apps in Hyperion: from its icon database (changed from the drawer)
     * and from home screen items edited one by one, which win as they're what the home screen showed.
     */
    private fun readCustomizations(appIconsDb: File, launcherDb: File): Map<ComponentName, AppCustomization> {
        val result = mutableMapOf<ComponentName, AppCustomization>()
        val mySerial = UserCache.INSTANCE.get(context).getSerialNumberForUser(user).toInt()
        if (appIconsDb.isFile) {
            runCatching {
                SQLiteDatabase.openDatabase(appIconsDb.path, null, SQLiteDatabase.OPEN_READONLY).use { db ->
                    db.rawQuery("SELECT * FROM icons WHERE has_custom_title != 0 OR has_custom_icon != 0", null).use { c ->
                        while (c.moveToNext()) {
                            val profile = int(c, "profileId")
                            if (profile != 0 && profile != mySerial) continue
                            val component = string(c, "componentName")?.let { ComponentName.unflattenFromString(it) } ?: continue
                            var app = result[component] ?: AppCustomization()
                            if (int(c, "has_custom_title") != 0) {
                                string(c, "label")?.trim()?.takeIf { it.isNotEmpty() }?.let { app = app.copy(name = it) }
                            }
                            if (int(c, "has_custom_icon") != 0) app = withIcon(app, string(c, "custom_icon_package_name"), string(c, "custom_icon_NAME"), blob(c, "icon"))
                            result[component] = app
                        }
                    }
                }
            }.onFailure { Log.w(TAG, "Could not read $APP_ICONS_DB", it) }
        }
        runCatching {
            SQLiteDatabase.openDatabase(launcherDb.path, null, SQLiteDatabase.OPEN_READONLY).use { db ->
                val placedFolders = placedFolderIds(db)
                db.rawQuery("SELECT * FROM favorites", null).use { c ->
                    while (c.moveToNext()) {
                        if (!isPlaced(int(c, "container"), placedFolders)) continue
                        val intent = string(c, "intent")
                        if (!isAppLaunchIntent(intent)) continue
                        val component = runCatching { Intent.parseUri(intent, 0).component }.getOrNull() ?: continue
                        var app = result[component] ?: AppCustomization()
                        string(c, "titleAlias")?.trim()?.takeIf { it.isNotEmpty() && it != appLabel(component) }?.let {
                            app = app.copy(name = it)
                        }
                        app = withIcon(app, string(c, "iconPackName"), string(c, "iconName"), blob(c, "customIcon"))
                        if (!app.isEmpty) result[component] = app
                    }
                }
            }
        }.onFailure { Log.w(TAG, "Could not read home screen customisations", it) }
        return result.filterValues { !it.isEmpty }
    }

    private fun withIcon(app: AppCustomization, pack: String?, name: String?, picture: ByteArray?): AppCustomization = when {
        !pack.isNullOrBlank() && !name.isNullOrBlank() -> app.copy(iconPack = pack, iconName = name.substringAfterLast('/'), picture = null)
        picture != null && picture.isNotEmpty() -> app.copy(iconPack = null, iconName = null, picture = picture)
        else -> app
    }

    private fun appLabel(component: ComponentName): String? = runCatching {
        launcherApps?.getActivityList(component.packageName, user)
            ?.firstOrNull { it.componentName == component }?.label?.toString()
    }.getOrNull()

    private suspend fun importCustomizations(apps: Map<ComponentName, AppCustomization>) {
        if (apps.isEmpty()) return
        val prefs = PreferenceManager.getInstance(context)
        val overrides = IconOverrideRepository.INSTANCE.get(context)
        val names = JSONObject(prefs.sp.getString(prefs.customAppName.key, null) ?: "{}")
        apps.forEach { (saved, app) ->
            val key = ComponentKey(installedComponent(saved), user)
            app.name?.let {
                names.put(key.toString(), it)
                prefs.customAppName[key] = it
            }
            runCatching {
                val icon = when {
                    app.iconPack != null && app.iconName != null ->
                        IconPickerItem(app.iconPack, app.iconName, app.name.orEmpty(), IconType.Normal)
                    app.picture != null -> PictureIcons.save(context, key.toString(), app.picture)
                        ?.let { IconPickerItem(PictureIcons.PACK, it, app.name.orEmpty(), IconType.Normal) }
                    else -> null
                }
                icon?.let { overrides.setOverride(key, it) }
            }.onFailure { Log.w(TAG, "Could not import the icon for $saved", it) }
        }
        prefs.sp.edit().putString(prefs.customAppName.key, names.toString()).commit()
        // Refresh the cached names and icons now, so the drawer shows them straight after the restart.
        val model = LauncherAppState.INSTANCE.get(context).model
        apps.keys.map { installedComponent(it).packageName }.distinct().forEach { pkg ->
            runCatching { model.onAppIconChanged(pkg, user) }
        }
    }

    /** Hyperion's own font files: primary for tab and folder titles, secondary for app names and the rest. */
    private fun importFonts(dir: File) {
        val prefs = PreferenceManager.getInstance(context)
        val editor = prefs.sp.edit()
        fun copyFont(fileName: String, savedName: String, vararg targets: String) {
            val source = File(dir, fileName)
            if (!source.isFile) return
            val target = FontCache.TTFFont.getFile(context, savedName)
            source.copyTo(target, overwrite = true)
            if (FontCache.TTFFont.createTypeface(target) == null) {
                target.delete()
                return
            }
            val json = FontCache.TTFFont(context, target).toJsonString()
            targets.forEach { editor.putString(it, json) }
        }
        copyFont(PRIMARY_FONT, "Hyperion primary.ttf", prefs.fontHeading.key, prefs.fontHeadingMedium.key)
        copyFont(SECONDARY_FONT, "Hyperion secondary.ttf", prefs.fontWorkspace.key, prefs.fontBody.key, prefs.fontBodyMedium.key)
        editor.commit()
    }

    /**
     * The one colour Hyperion paints behind icons in its static mode (white unless changed), or null
     * when each icon takes its own colour.
     */
    private fun hyperionIconBackground(settings: HyperionSettings): Int? {
        if ((settings.string("pref_draw_icon_shape_color_mode") ?: "static") != "static") return null
        return settings.int("pref_draw_icon_shape_color_mode_color") ?: -1
    }

    /**
     * Hyperion features in use in this backup that this launcher has no counterpart for, so the restore
     * screen can say so instead of dropping them silently. Hyperion's default applies when a key is absent.
     */
    private fun notCarriedOver(settings: HyperionSettings, launcherDb: File, fonts: Set<String>): List<String> {
        val missing = mutableListOf<Int>()
        fun on(key: String, default: Boolean = false) = settings.bool(key) ?: default
        if (on("pages_enable_dock_blur")) missing += R.string.hyperion_missing_dock_blur
        if (on("pages_show_app_drawer_icon")) missing += R.string.hyperion_missing_drawer_icon
        if (on("pref_show_workspace_search_bar")) missing += R.string.hyperion_missing_home_search_bar
        if (on("pref_show_smartspace_in_dock")) missing += R.string.hyperion_missing_glance_in_dock
        if ((settings.string("pref_app_launch_animation") ?: "default") != "default") missing += R.string.hyperion_missing_launch_animation
        if (on("pref_glow_overflow")) missing += R.string.hyperion_missing_glow_colors
        if (listOf("pref_gicon_qsb_color", "pref_gicon_qsb_drawer_color").any { (settings.int(it) ?: HYPERION_UNSET_COLOR) != HYPERION_UNSET_COLOR }) {
            missing += R.string.hyperion_missing_g_color
        }
        if (on("hide_locked_apps_on_recents")) missing += R.string.hyperion_missing_locked_recents
        if (on("pref_enable_sesame_longpress")) missing += R.string.hyperion_missing_sesame_longpress
        if (SMARTSPACE_FONT in fonts) missing += R.string.hyperion_missing_glance_font
        runCatching {
            SQLiteDatabase.openDatabase(launcherDb.path, null, SQLiteDatabase.OPEN_READONLY).use { db ->
                val placedFolders = placedFolderIds(db)
                var covers = false
                var sesame = false
                db.rawQuery("SELECT * FROM favorites", null).use { c ->
                    while (c.moveToNext()) {
                        if (!isPlaced(int(c, "container"), placedFolders)) continue
                        if (int(c, "isFolderMask") != 0) covers = true
                        if (int(c, "sesameShortcut") != 0) sesame = true
                    }
                }
                if (covers) missing += R.string.hyperion_missing_folder_covers
                if (sesame) missing += R.string.hyperion_missing_sesame_items
            }
        }
        return missing.map { context.getString(it) }
    }

    private fun countItems(dbFile: File): ItemCounts {
        val db = SQLiteDatabase.openDatabase(dbFile.absolutePath, null, SQLiteDatabase.OPEN_READONLY)
        return db.use {
            val placedFolders = placedFolderIds(it)
            var apps = 0
            var folders = 0
            var widgets = 0
            var shortcuts = 0
            it.rawQuery("SELECT itemType, container FROM favorites", null).use { cursor ->
                while (cursor.moveToNext()) {
                    val container = cursor.getInt(1)
                    if (!isPlaced(container, placedFolders)) continue
                    when (cursor.getInt(0)) {
                        Favorites.ITEM_TYPE_APPLICATION, Favorites.ITEM_TYPE_SHORTCUT -> apps++
                        Favorites.ITEM_TYPE_FOLDER -> folders++
                        Favorites.ITEM_TYPE_APPWIDGET, Favorites.ITEM_TYPE_CUSTOM_APPWIDGET -> widgets++
                        Favorites.ITEM_TYPE_DEEP_SHORTCUT -> shortcuts++
                    }
                }
            }
            ItemCounts(apps, folders, widgets, shortcuts)
        }
    }

    private fun placedFolderIds(db: SQLiteDatabase): Set<Int> {
        val ids = mutableSetOf<Int>()
        db.rawQuery(
            "SELECT _id FROM favorites WHERE itemType = ${Favorites.ITEM_TYPE_FOLDER} " +
                "AND container IN ($CONTAINER_DESKTOP, $CONTAINER_HOTSEAT)",
            null,
        ).use { while (it.moveToNext()) ids.add(it.getInt(0)) }
        return ids
    }

    private fun isPlaced(container: Int, placedFolders: Set<Int>) =
        container == CONTAINER_DESKTOP || container == CONTAINER_HOTSEAT || container in placedFolders

    private fun screenRanks(db: SQLiteDatabase): Map<Int, Int> {
        val ranks = mutableMapOf<Int, Int>()
        runCatching {
            db.rawQuery("SELECT _id FROM workspaceScreens ORDER BY screenRank", null).use { cursor ->
                var index = 0
                while (cursor.moveToNext()) ranks[cursor.getInt(0)] = index++
            }
        }
        return ranks
    }

    private fun dockPages(settings: HyperionSettings) = (settings.int("pref_hotseat_pages") ?: 1).coerceIn(1, 5)

    private fun createRestoredDb(sourceFile: File, targetFile: File, hotseatColumns: Int, twoRowDock: Boolean, dockPages: Int) {
        val profileId = UserCache.INSTANCE.get(context).getSerialNumberForUser(user)
        SQLiteDatabase.openOrCreateDatabase(targetFile, null).use { target ->
            target.version = DatabaseHelper.SCHEMA_VERSION
            Favorites.addTableToDb(target, profileId, false)
            SQLiteDatabase.openDatabase(sourceFile.absolutePath, null, SQLiteDatabase.OPEN_READONLY).use { src ->
                val placedFolders = placedFolderIds(src)
                val screens = screenRanks(src)
                target.beginTransaction()
                try {
                    copyItems(src, target, profileId, placedFolders, screens, hotseatColumns, twoRowDock, dockPages)
                    target.setTransactionSuccessful()
                } finally {
                    target.endTransaction()
                }
            }
        }
    }

    private fun copyItems(
        src: SQLiteDatabase,
        target: SQLiteDatabase,
        profileId: Long,
        placedFolders: Set<Int>,
        screens: Map<Int, Int>,
        hotseatColumns: Int,
        twoRowDock: Boolean,
        dockPages: Int,
    ) {
        val now = System.currentTimeMillis()
        val dockRows = if (twoRowDock) 2 else 1
        val usedHotseatSlots = mutableSetOf<Int>()
        src.rawQuery("SELECT * FROM favorites ORDER BY container, screen, cellY, cellX", null).use { cursor ->
            while (cursor.moveToNext()) {
                val container = cursor.getInt(cursor.getColumnIndexOrThrow("container"))
                if (!isPlaced(container, placedFolders)) continue

                val id = cursor.getInt(cursor.getColumnIndexOrThrow("_id"))
                var itemType = cursor.getInt(cursor.getColumnIndexOrThrow("itemType"))
                val intent = string(cursor, "intent")
                if (itemType == Favorites.ITEM_TYPE_SHORTCUT && isAppLaunchIntent(intent)) {
                    itemType = Favorites.ITEM_TYPE_APPLICATION
                }
                val title = string(cursor, "titleAlias")?.takeIf { it.isNotBlank() } ?: string(cursor, "title")
                val rawScreen = int(cursor, "screen")
                var cellX = int(cursor, "cellX")
                var cellY = int(cursor, "cellY")
                val spanX = int(cursor, "spanX").coerceAtLeast(1)
                val spanY = int(cursor, "spanY").coerceAtLeast(1)
                var rank = int(cursor, "rank")
                var screen = rawScreen

                when (container) {
                    CONTAINER_HOTSEAT -> {
                        val row = if (twoRowDock) cellY.coerceIn(0, 1) else 0
                        // Each dock page holds columns x rows slots, one page after another.
                        val page = int(cursor, "itemHotseatPageId").coerceIn(0, dockPages - 1)
                        var slot = page * hotseatColumns * dockRows + row * hotseatColumns + cellX.coerceIn(0, hotseatColumns - 1)
                        while (slot in usedHotseatSlots) slot++
                        usedHotseatSlots.add(slot)
                        screen = slot
                        cellX = slot
                        cellY = 0
                        rank = slot
                    }
                    CONTAINER_DESKTOP -> {
                        screen = screens[rawScreen] ?: rawScreen
                        rank = 0
                    }
                }

                target.execSQL(
                    """INSERT INTO favorites (
                        _id, title, intent, container, screen, cellX, cellY,
                        spanX, spanY, itemType, appWidgetId, icon,
                        appWidgetProvider, modified, restored, profileId,
                        rank, options, appWidgetSource
                    ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, -1, ?, ?, ?, 0, ?, ?, 0, -1)""",
                    arrayOf(
                        id,
                        title,
                        intent,
                        container,
                        screen,
                        cellX,
                        cellY,
                        spanX,
                        spanY,
                        itemType,
                        blob(cursor, "icon"),
                        string(cursor, "appWidgetProvider"),
                        now,
                        profileId,
                        rank,
                    ),
                )
            }
        }
    }

    private fun isAppLaunchIntent(uri: String?): Boolean {
        if (uri.isNullOrEmpty()) return false
        val intent = try {
            Intent.parseUri(uri, 0)
        } catch (_: URISyntaxException) {
            return false
        }
        return intent.component != null && intent.action == Intent.ACTION_MAIN &&
            intent.hasCategory(Intent.CATEGORY_LAUNCHER)
    }

    private fun resolveLabel(packageName: String): String = try {
        val pm = context.packageManager
        pm.getApplicationInfo(packageName, 0).loadLabel(pm).toString()
    } catch (_: PackageManager.NameNotFoundException) {
        packageName
    }

    private fun string(cursor: Cursor, column: String): String? {
        val idx = cursor.getColumnIndex(column)
        return if (idx >= 0 && !cursor.isNull(idx)) cursor.getString(idx) else null
    }

    private fun int(cursor: Cursor, column: String): Int {
        val idx = cursor.getColumnIndex(column)
        return if (idx >= 0 && !cursor.isNull(idx)) cursor.getInt(idx) else 0
    }

    private fun blob(cursor: Cursor, column: String): ByteArray? {
        val idx = cursor.getColumnIndex(column)
        return if (idx >= 0 && !cursor.isNull(idx)) cursor.getBlob(idx) else null
    }

    private fun extractFromZip(uri: Uri, destDir: File, fileNames: Set<String>) {
        val pfd = context.contentResolver.openFileDescriptor(uri, "r")
            ?: throw IllegalStateException("Unable to open backup URI")
        val destCanonical = destDir.canonicalPath
        pfd.use {
            FileInputStream(it.fileDescriptor).use { fis ->
                ZipInputStream(fis).use { zis ->
                    var entry = zis.nextEntry
                    while (entry != null) {
                        if (!entry.isDirectory && entry.name in fileNames) {
                            val outFile = File(destDir, entry.name)
                            require(outFile.canonicalPath.startsWith(destCanonical + File.separator)) {
                                "Zip entry outside target dir: ${entry.name}"
                            }
                            FileOutputStream(outFile).use { fos -> zis.copyTo(fos) }
                        }
                        entry = zis.nextEntry
                    }
                }
            }
        }
    }
}
