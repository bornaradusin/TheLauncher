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
import app.thelauncher.DeviceProfileOverrides
import app.thelauncher.data.folder.service.FolderService
import app.thelauncher.gestures.config.GestureHandlerConfig
import app.thelauncher.preferences.PreferenceManager
import app.thelauncher.preferences2.PreferenceManager2
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
    )

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

            writeSettings(settings)
            importDrawerFolders(settings.drawerFolders)

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
        val sets = mutableMapOf<String, Set<String>>()
        val children = root.childNodes
        for (i in 0 until children.length) {
            val node = children.item(i) as? Element ?: continue
            val name = node.getAttribute("name").ifEmpty { continue }
            when (node.tagName) {
                "string" -> strings[name] = node.textContent.orEmpty()
                "int", "long" -> node.getAttribute("value").toIntOrNull()?.let { ints[name] = it }
                "boolean" -> bools[name] = node.getAttribute("value") == "true"
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
            notificationCount = strings["pref_icon_badging_style"]?.let { it == "text" },
            drawerFolders = parseDrawerFolders(strings["drawer_folders"]),
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

        prefs.sp.edit().apply {
            settings.columns?.let { putInt(prefs.workspaceColumns.key, it) }
            settings.rows?.let { putInt(prefs.workspaceRows.key, it) }
            settings.hotseatColumns?.let { putInt(prefs.hotseatColumns.key, it) }
            putInt(prefs.hotseatRows.key, if (settings.twoRowDock) 2 else 1)
            settings.iconPackPackage?.let { putString(prefs.iconPackPackage.key, it) }
            settings.themedIcons?.let { putBoolean(prefs.themedIcons.key, it) }
            settings.drawerOpacity?.let { putFloat(prefs.drawerOpacity.key, (it / 100f).coerceIn(0f, 1f)) }
        }.commit()

        runCatching {
            settings.drawerColumns?.let {
                prefs2.drawerColumns.set(it, InvariantDeviceProfile.INSTANCE.get(context).closestProfile)
            }
        }.onFailure { Log.w(TAG, "Could not set drawer columns", it) }

        val hidden = settings.hiddenApps.mapNotNull { toComponentKey(it) }.toSet()
        if (hidden.isNotEmpty()) prefs2.hiddenApps.set(prefs2.hiddenApps.get().first() + hidden)

        if (settings.appLockEnabled) {
            val locked = settings.lockedApps.mapNotNull { toComponentKey(it) }.toSet()
            if (locked.isNotEmpty()) prefs2.lockedApps.set(prefs2.lockedApps.get().first() + locked)
            settings.appLockTimeoutMinutes?.let {
                prefs2.appLockTimeoutSeconds.set((it * 60).coerceIn(0, MAX_LOCK_TIMEOUT_SECONDS))
            }
        }

        settings.feedEnabled?.let { prefs2.enableFeed.set(it) }
        settings.smartspaceEnabled?.let { prefs2.enableSmartspace.set(it) }
        settings.smartspaceShowTime?.let { prefs2.smartspaceShowTime.set(it) }
        settings.smartspaceShowWeather?.let { prefs2.smartspaceWeather.set(it) }
        settings.smartspaceShowEvents?.let { prefs2.smartspaceCalendarEvents.set(it) }
        settings.weatherLocation?.let { prefs2.smartspaceWeatherLocation.set(it) }
        settings.rememberDrawerPosition?.let { prefs2.rememberPosition.set(it) }
        settings.dockShadow?.let { prefs2.dockShadow.set(it) }
        settings.notificationCount?.let { prefs2.showNotificationCount.set(it) }
        when (settings.swipeDownAction) {
            "sesame_search", "search", "app_search" -> prefs2.swipeDownGestureHandler.set(GestureHandlerConfig.OpenSearch)
            "notifications", "expand_notifications" -> prefs2.swipeDownGestureHandler.set(GestureHandlerConfig.OpenNotifications)
            "quick_settings" -> prefs2.swipeDownGestureHandler.set(GestureHandlerConfig.OpenQuickSettings)
            "app_drawer" -> prefs2.swipeDownGestureHandler.set(GestureHandlerConfig.OpenAppDrawer)
            "none" -> prefs2.swipeDownGestureHandler.set(GestureHandlerConfig.NoOp)
        }
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
