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
import app.thelauncher.data.folder.service.FolderService
import app.thelauncher.gestures.config.GestureHandlerConfig
import app.thelauncher.preferences.PreferenceManager
import app.thelauncher.preferences2.PreferenceManager2
import app.thelauncher.util.kotlinxJson
import com.android.launcher3.GridType
import com.android.launcher3.InvariantDeviceProfile
import com.android.launcher3.LauncherSettings.Favorites
import com.android.launcher3.model.DatabaseHelper
import com.android.launcher3.model.DeviceGridState
import com.android.launcher3.model.ModelDbController
import com.android.launcher3.pm.UserCache
import com.android.launcher3.provider.RestoreDbTask
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
import org.w3c.dom.Element

class HyperionBackupConverter(
    private val context: Context,
    private val uri: Uri,
) {

    companion object {
        private const val TAG = "HyperionBackupConverter"
        private const val PREFS_XML = "projekt.launcher_preferences.xml"
        private const val LAUNCHER_DB = "launcher.db"
        private const val TEMP_DIR_PREFIX = "hyperion_"
        private const val STAGED_DB = "hyperion_workspace.db"
        private const val CONTAINER_DESKTOP = -100
        private const val CONTAINER_HOTSEAT = -101
        private const val MAX_LOCK_TIMEOUT_SECONDS = 1800
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
        val raw: Map<String, Any> = emptyMap(),
    ) {
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
    ) {
        val columns get() = settings.columns
        val rows get() = settings.rows
        val hotseatColumns get() = settings.hotseatColumns
    }

    private data class ItemCounts(val apps: Int, val folders: Int, val widgets: Int, val shortcuts: Int)

    private val user = Process.myUserHandle()

    suspend fun parseInfo(): HyperionBackupInfo = withContext(Dispatchers.IO) {
        val tempDir = File(context.cacheDir, "$TEMP_DIR_PREFIX${UUID.randomUUID()}")
        tempDir.mkdirs()
        try {
            extractFromZip(uri, tempDir, setOf(PREFS_XML, LAUNCHER_DB))
            val xmlFile = File(tempDir, PREFS_XML)
            val dbFile = File(tempDir, LAUNCHER_DB)
            require(xmlFile.exists() && dbFile.exists()) { "Missing $PREFS_XML or $LAUNCHER_DB" }
            val settings = parseSettings(xmlFile)
            val counts = countItems(dbFile)
            HyperionBackupInfo(
                settings = settings,
                appCount = counts.apps,
                folderCount = counts.folders,
                widgetCount = counts.widgets,
                shortcutCount = counts.shortcuts,
                iconPackLabel = settings.iconPackPackage?.let { resolveLabel(it) },
            )
        } finally {
            tempDir.deleteRecursively()
        }
    }

    suspend fun convertAndRestore(info: HyperionBackupInfo) = withContext(Dispatchers.IO) {
        val tempDir = File(context.cacheDir, "$TEMP_DIR_PREFIX${UUID.randomUUID()}")
        tempDir.mkdirs()
        try {
            extractFromZip(uri, tempDir, setOf(LAUNCHER_DB))
            val sourceDb = File(tempDir, LAUNCHER_DB)
            require(sourceDb.exists()) { "Missing $LAUNCHER_DB" }

            val settings = info.settings
            val hotseatColumns = settings.hotseatColumns
            val stagedDb = File(tempDir, STAGED_DB)
            createRestoredDb(sourceDb, stagedDb, hotseatColumns ?: 5, settings.twoRowDock)

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
                InvariantDeviceProfile.INSTANCE.get(context).dbFile = gridInfo.dbFile
            }

            runCatching { writeSettings(settings) }.onFailure { Log.w(TAG, "Settings import failed", it) }
            runCatching { importDrawerFolders(settings.drawerFolders) }.onFailure { Log.w(TAG, "Drawer folder import failed", it) }

            val restoredDbFile = context.getDatabasePath(TheLauncherBackup.RESTORED_DB_FILE_NAME)
            restoredDbFile.parentFile?.mkdirs()
            stagedDb.copyTo(restoredDbFile, overwrite = true)

            RestoreDbTask.performRestore(context, ModelDbController(context))
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
        val weatherAuto = bools["pref_weather_location_auto"] ?: true
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
            raw = buildMap<String, Any> {
                putAll(strings)
                putAll(ints)
                putAll(bools)
                putAll(floats)
            },
        )
    }

    private fun parseDrawerFolders(json: String?): List<HyperionDrawerFolder> {
        if (json.isNullOrBlank()) return emptyList()
        return try {
            val array = JSONArray(json)
            (0 until array.length()).mapNotNull { i ->
                val obj = array.optJSONObject(i) ?: return@mapNotNull null
                val title = obj.optString("title").trim()
                val apps = obj.optJSONArray("apps")
                val components = if (apps == null) emptyList() else (0 until apps.length()).map { apps.optString(it) }
                if (title.isEmpty()) null else HyperionDrawerFolder(title, components.filter { it.isNotBlank() })
            }
        } catch (t: Throwable) {
            Log.w(TAG, "Could not read drawer folders", t)
            emptyList()
        }
    }

    private fun toComponentKey(flattened: String): String? {
        val component = ComponentName.unflattenFromString(flattened.substringBefore('#')) ?: return null
        return ComponentKey(component, user).toString()
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
            settings.drawerOpacity?.let { putFloat(prefs.drawerOpacity.key, (it / 100f).coerceIn(0f, 1f)) }
            putInt(prefs.dockPages.key, (settings.int("pref_hotseat_pages") ?: 1).coerceAtLeast(1))
            settings.bool("pref_allowRotation")?.let { putBoolean(prefs.allowRotation.key, it) }
            settings.bool("pages_enable_drawer_blur")?.let { putBoolean(prefs.enableWallpaperBlur.key, it) }
            when (settings.string("dark_mode")) {
                "light" -> putString(prefs.launcherTheme.key, "light")
                "dark" -> putString(prefs.launcherTheme.key, "dark")
                "follow_system" -> putString(prefs.launcherTheme.key, "system")
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
                settings.feedEnabled?.let { store[prefs2.enableFeed.key] = it }
                settings.smartspaceEnabled?.let { store[prefs2.enableSmartspace.key] = it }
                settings.smartspaceShowTime?.let { store[prefs2.smartspaceShowTime.key] = it }
                settings.smartspaceShowWeather?.let { store[prefs2.smartspaceWeather.key] = it }
                settings.smartspaceShowEvents?.let { store[prefs2.smartspaceCalendarEvents.key] = it }
                settings.weatherLocation?.let { store[prefs2.smartspaceWeatherLocation.key] = it }
                settings.rememberDrawerPosition?.let { store[prefs2.rememberPosition.key] = it }
                settings.dockShadow?.let { store[prefs2.dockShadow.key] = it }
                settings.notificationCount?.let { store[prefs2.showNotificationCount.key] = it }
                fun gesture(hyperionKey: String, key: androidx.datastore.preferences.core.Preferences.Key<String>) {
                    val value = settings.string(hyperionKey) ?: return
                    val handler = gestureFor(value) ?: return
                    store[key] = kotlinxJson.encodeToString(handler)
                }
                gesture("pref_home_down_action", prefs2.homePressGestureHandler.key)
                gesture("pref_swipe_down_action", prefs2.swipeDownGestureHandler.key)
                gesture("pref_swipe_up_action", prefs2.swipeUpGestureHandler.key)
                gesture("pref_double_tap_action", prefs2.doubleTapGestureHandler.key)
                gesture("pref_two_finger_swipe_up_action", prefs2.twoFingerSwipeUpGestureHandler.key)
                gesture("pref_two_finger_swipe_down_action", prefs2.twoFingerSwipeDownGestureHandler.key)
                gesture("pref_back_down_action", prefs2.backPressGestureHandler.key)

                // Icon and label sizes: Hyperion keeps percentages and only saves ones changed from 100%.
                store[prefs2.homeIconSizeFactor.key] = (settings.int("pref_custom_workspace_icon_size") ?: 100) / 100f
                store[prefs2.drawerIconSizeFactor.key] = (settings.int("pref_custom_icon_size") ?: 100) / 100f
                store[prefs2.homeIconLabelSizeFactor.key] = (settings.int("pref_custom_workspace_icon_text_scale") ?: 100) / 100f
                store[prefs2.homeIconLabelFolderSizeFactor.key] = (settings.int("pref_custom_folder_icon_text_scale") ?: 100) / 100f
                store[prefs2.drawerIconLabelSizeFactor.key] = (settings.int("pref_custom_icon_text_scale") ?: 100) / 100f
                store[prefs2.drawerCellHeightFactor.key] = (settings.int("pref_drawer_row_height_scale") ?: 100) / 100f

                // Labels, with Hyperion's defaults for anything not saved.
                store[prefs2.showIconLabelsOnHomeScreen.key] = settings.bool("pref_enable_workspace_icon_text") ?: true
                store[prefs2.showIconLabelsOnHomeScreenFolder.key] = settings.bool("pref_enable_folder_icon_text") ?: true
                store[prefs2.enableLabelInDock.key] = settings.bool("pref_enable_dock_icon_text") ?: false
                store[prefs2.showIconLabelsInDrawer.key] = settings.bool("pref_enable_app_drawer_label_label") ?: true
                store[prefs2.twoLineAllApps.key] = settings.bool("pref_enable_icon_multiple_lines") ?: false
                settings.bool("pref_enable_icon_text_shadow")?.let { store[prefs2.desktopTextShadow.key] = it }

                // Drawer and search
                store[prefs2.hideAppDrawerSearchBar.key] = !(settings.bool("pref_show_drawer_search_bar") ?: true)
                settings.bool("auto_show_all_apps_search")?.let { store[prefs2.autoShowKeyboardInDrawer.key] = it }
                settings.bool("pref_show_predictions")?.let { store[prefs2.showSuggestedAppsInDrawer.key] = it }
                if (settings.bool("pref_show_dock_search_bar") == false) store[prefs2.hotseatMode.key] = "disabled"
                settings.bool("pages_lock_desktop")?.let { store[prefs2.lockHomeScreen.key] = it }
                settings.bool("pref_smartspace_show_date")?.let { store[prefs2.smartspaceShowDate.key] = it }
                settings.string("pref_temperature_unit")?.let {
                    store[prefs2.smartspaceWeatherFahrenheit.key] = it.contains("f", ignoreCase = true) && !it.contains("c", ignoreCase = true)
                }

                // Wallpaper dimmer: Hyperion's strength defaults to 20%.
                store[prefs2.wallpaperDimAmount.key] = if (settings.bool("wallpaper_dimmer_toggle") == true) {
                    (settings.float("wallpaper_dimmer_strength") ?: 0.2f).coerceIn(0f, 0.9f)
                } else {
                    0f
                }
            }
        }.onFailure { Log.w(TAG, "Could not import settings", it) }
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
        else -> null
    }

    private suspend fun importDrawerFolders(folders: List<HyperionDrawerFolder>) {
        if (folders.isEmpty()) return
        val service = FolderService.INSTANCE.get(context)
        val existingTitles = runCatching { service.getFoldersFlow().first().map { it.title.trim() }.toSet() }
            .getOrDefault(emptySet())
        folders.forEach { folder ->
            if (folder.title in existingTitles) return@forEach
            val keys = folder.components.mapNotNull { toComponentKey(it) }.distinct()
            runCatching {
                val id = service.saveFolderInfo(folder.title).toInt()
                service.updateFolderWithItems(id, folder.title, keys)
            }.onFailure { Log.w(TAG, "Could not import drawer folder ${folder.title}", it) }
        }
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

    private fun createRestoredDb(sourceFile: File, targetFile: File, hotseatColumns: Int, twoRowDock: Boolean) {
        val profileId = UserCache.INSTANCE.get(context).getSerialNumberForUser(user)
        SQLiteDatabase.openOrCreateDatabase(targetFile, null).use { target ->
            target.version = DatabaseHelper.SCHEMA_VERSION
            Favorites.addTableToDb(target, profileId, false)
            SQLiteDatabase.openDatabase(sourceFile.absolutePath, null, SQLiteDatabase.OPEN_READONLY).use { src ->
                val placedFolders = placedFolderIds(src)
                val screens = screenRanks(src)
                target.beginTransaction()
                try {
                    copyItems(src, target, profileId, placedFolders, screens, hotseatColumns, twoRowDock)
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
    ) {
        val now = System.currentTimeMillis()
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
                        var slot = row * hotseatColumns + cellX.coerceIn(0, hotseatColumns - 1)
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
