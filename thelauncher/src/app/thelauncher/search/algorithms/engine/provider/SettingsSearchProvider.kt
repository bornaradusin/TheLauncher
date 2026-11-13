package app.thelauncher.search.algorithms.engine.provider

import android.content.Context
import android.provider.Settings
import android.util.Log
import app.thelauncher.preferences.PreferenceManager
import app.thelauncher.preferences2.PreferenceManager2
import app.thelauncher.preferences2.firstCached
import app.thelauncher.search.algorithms.data.SettingInfo
import app.thelauncher.search.algorithms.engine.SearchProvider
import app.thelauncher.search.algorithms.engine.SearchResult
import java.lang.reflect.Modifier
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.withContext

object SettingsSearchProvider : SearchProvider {

    override val id: String = "settings"

    override fun search(
        context: Context,
        query: String,
    ): Flow<List<SearchResult>> = flow {
        // We get the preference manager instance when needed, using the passed context.
        val prefs = PreferenceManager.getInstance(context)
        val prefs2 = PreferenceManager2.getInstance(context)

        if (query.isBlank()) {
            emit(emptyList())
            return@flow
        }

        val maxResults = prefs2.maxSettingsEntryResultCount.firstCached()
        val settingsInfoList = findSettingsByNameAndAction(query, maxResults)

        val searchResults = settingsInfoList.map { settingInfo ->
            SearchResult.Setting(data = settingInfo)
        }
        emit(searchResults)
    }
}

private suspend fun findSettingsByNameAndAction(query: String, max: Int): List<SettingInfo> = try {
    if (query.isBlank() || max <= 0) {
        emptyList()
    } else {
        withContext(
            Dispatchers.IO + CoroutineExceptionHandler { _, e ->
                Log.e("SettingSearch", "Something went wrong ", e)
            },
        ) {
            Settings::class.java.fields
                .asSequence()
                .filter {
                    it.type == String::class.java &&
                        Modifier.isStatic(it.modifiers) &&
                        it.name.startsWith("ACTION_")
                }
                .map { it.name to it.get(null) as String }
                .filter { (name, action) ->
                    matchesSetting(name, query) &&
                        !action.contains("REQUEST", ignoreCase = true) &&
                        !name.contains("REQUEST", ignoreCase = true) &&
                        !action.contains("PERMISSION", ignoreCase = true) &&
                        !name.contains("DETAIL", ignoreCase = true) &&
                        !name.contains("REMOTE", ignoreCase = true)
                }
                .map { (name, action) ->
                    val id = name + action
                    val requiresUri = action.contains("URI")
                    SettingInfo(id, name, action, requiresUri)
                }
                .sortedBy { (name, _) -> if (aliasesFor(query).contains(name)) 0 else 1 }
                .toList().take(max)
        }
    }
} catch (e: Exception) {
    Log.e("SettingSearch", "Something went wrong ", e)
    emptyList()
}

private fun normalise(text: String) = text.lowercase().filter { it.isLetterOrDigit() }

/**
 * Whether a Settings page (its ACTION_… name) matches what was typed: by its own words, ignoring
 * spaces and dashes ("wi-fi" finds Wi-Fi), or by the everyday words people use for it.
 */
private fun matchesSetting(name: String, query: String): Boolean {
    val q = normalise(query)
    if (q.isEmpty()) return false
    return normalise(name.removePrefix("ACTION_")).contains(q) || aliasesFor(query).contains(name)
}

private fun aliasesFor(query: String): Set<String> {
    val q = normalise(query)
    if (q.length < 3) return emptySet()
    return SETTING_WORDS.filterKeys { it.startsWith(q) }.values.flatten().toSet()
}

/** Everyday words for the main Settings pages. */
private val SETTING_WORDS: Map<String, List<String>> = mapOf(
    "brightness" to listOf("ACTION_DISPLAY_SETTINGS"),
    "screen" to listOf("ACTION_DISPLAY_SETTINGS"),
    "darktheme" to listOf("ACTION_DISPLAY_SETTINGS"),
    "darkmode" to listOf("ACTION_DISPLAY_SETTINGS"),
    "font" to listOf("ACTION_DISPLAY_SETTINGS"),
    "volume" to listOf("ACTION_SOUND_SETTINGS"),
    "ringtone" to listOf("ACTION_SOUND_SETTINGS"),
    "vibration" to listOf("ACTION_SOUND_SETTINGS"),
    "donotdisturb" to listOf("ACTION_ZEN_MODE_PRIORITY_SETTINGS"),
    "internet" to listOf("ACTION_WIFI_SETTINGS", "ACTION_WIRELESS_SETTINGS"),
    "network" to listOf("ACTION_WIRELESS_SETTINGS", "ACTION_NETWORK_OPERATOR_SETTINGS"),
    "mobiledata" to listOf("ACTION_DATA_ROAMING_SETTINGS", "ACTION_DATA_USAGE_SETTINGS"),
    "hotspot" to listOf("ACTION_WIRELESS_SETTINGS"),
    "sim" to listOf("ACTION_NETWORK_OPERATOR_SETTINGS"),
    "battery" to listOf("ACTION_BATTERY_SAVER_SETTINGS"),
    "apps" to listOf("ACTION_APPLICATION_SETTINGS", "ACTION_MANAGE_APPLICATIONS_SETTINGS"),
    "defaultapps" to listOf("ACTION_MANAGE_DEFAULT_APPS_SETTINGS"),
    "launcher" to listOf("ACTION_HOME_SETTINGS"),
    "homeapp" to listOf("ACTION_HOME_SETTINGS"),
    "notifications" to listOf("ACTION_ALL_APPS_NOTIFICATION_SETTINGS", "ACTION_NOTIFICATION_LISTENER_SETTINGS"),
    "storage" to listOf("ACTION_INTERNAL_STORAGE_SETTINGS"),
    "gps" to listOf("ACTION_LOCATION_SOURCE_SETTINGS"),
    "location" to listOf("ACTION_LOCATION_SOURCE_SETTINGS"),
    "lockscreen" to listOf("ACTION_SECURITY_SETTINGS"),
    "fingerprint" to listOf("ACTION_SECURITY_SETTINGS"),
    "password" to listOf("ACTION_SECURITY_SETTINGS"),
    "pin" to listOf("ACTION_SECURITY_SETTINGS"),
    "keyboard" to listOf("ACTION_INPUT_METHOD_SETTINGS"),
    "language" to listOf("ACTION_LOCALE_SETTINGS"),
    "time" to listOf("ACTION_DATE_SETTINGS"),
    "accounts" to listOf("ACTION_SYNC_SETTINGS"),
    "google" to listOf("ACTION_SYNC_SETTINGS"),
    "flightmode" to listOf("ACTION_AIRPLANE_MODE_SETTINGS"),
    "aboutphone" to listOf("ACTION_DEVICE_INFO_SETTINGS"),
    "update" to listOf("ACTION_DEVICE_INFO_SETTINGS"),
    "developer" to listOf("ACTION_APPLICATION_DEVELOPMENT_SETTINGS"),
    "cast" to listOf("ACTION_CAST_SETTINGS"),
    "printing" to listOf("ACTION_PRINT_SETTINGS"),
    "payment" to listOf("ACTION_NFC_PAYMENT_SETTINGS"),
    "tap" to listOf("ACTION_NFC_SETTINGS"),
)
