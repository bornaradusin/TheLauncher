package app.thelauncher.ui.preferences.destinations

import android.widget.Toast
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import app.thelauncher.backup.SavedLayouts
import app.thelauncher.preferences.PreferenceManager
import app.thelauncher.preferences2.PreferenceManager2
import androidx.datastore.preferences.core.edit
import app.thelauncher.util.restartLauncher
import com.android.launcher3.LauncherAppState
import com.android.launcher3.util.Executors
import kotlinx.coroutines.launch
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import app.thelauncher.backup.ui.restoreBackupOpener
import app.thelauncher.backup.ui.restoreHyperionBackupOpener
import app.thelauncher.backup.ui.restoreNovaBackupOpener
import app.thelauncher.ui.preferences.LocalIsExpandedScreen
import app.thelauncher.ui.preferences.components.NavigationActionPreference
import app.thelauncher.ui.preferences.components.controls.ClickablePreference
import app.thelauncher.ui.preferences.components.layout.PreferenceGroup
import app.thelauncher.ui.preferences.components.layout.PreferenceLayout
import app.thelauncher.ui.preferences.navigation.CreateBackup
import com.android.launcher3.R

@Composable
fun BackupAndRestorePreference(
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var layouts by remember { mutableStateOf(SavedLayouts.list(context)) }
    var busy by remember { mutableStateOf(false) }
    PreferenceLayout(
        label = stringResource(R.string.backup_and_restore_label),
        backArrowVisible = !LocalIsExpandedScreen.current,
        modifier = modifier,
    ) {
        PreferenceGroup {
            NavigationActionPreference(
                label = stringResource(R.string.create_backup),
                subtitle = stringResource(R.string.create_backup_description),
                destination = CreateBackup,
            )
            ClickablePreference(
                label = stringResource(R.string.restore_backup),
                subtitle = stringResource(R.string.restore_backup_description),
                onClick = restoreBackupOpener(),
            )
        }
        PreferenceGroup {
            ClickablePreference(
                label = stringResource(R.string.restore_nova_backup),
                subtitle = stringResource(R.string.restore_nova_backup_description),
                onClick = restoreNovaBackupOpener(),
            )
            ClickablePreference(
                label = stringResource(R.string.restore_hyperion_backup),
                subtitle = stringResource(R.string.restore_hyperion_backup_description),
                onClick = restoreHyperionBackupOpener(),
            )
        }
        PreferenceGroup(heading = stringResource(R.string.saved_layouts)) {
            ClickablePreference(
                label = stringResource(R.string.saved_layouts_save),
                subtitle = stringResource(R.string.saved_layouts_save_desc),
                onClick = {
                    if (!busy) {
                        busy = true
                        scope.launch {
                            val ok = runCatching { SavedLayouts.save(context) }.isSuccess
                            layouts = SavedLayouts.list(context)
                            busy = false
                            Toast.makeText(
                                context,
                                if (ok) R.string.saved_layouts_saved else R.string.saved_layouts_failed,
                                Toast.LENGTH_SHORT,
                            ).show()
                        }
                    }
                },
            )
            layouts.forEach { file ->
                ClickablePreference(
                    label = SavedLayouts.displayName(file),
                    subtitle = stringResource(R.string.saved_layouts_restore_hint),
                    confirmationText = stringResource(R.string.saved_layouts_restore_confirm),
                    onClick = {
                        if (!busy) {
                            busy = true
                            scope.launch {
                                val ok = runCatching { SavedLayouts.restore(context, file) }.isSuccess
                                busy = false
                                if (ok) {
                                    restartLauncher(context)
                                } else {
                                    Toast.makeText(context, R.string.backup_restore_error, Toast.LENGTH_SHORT).show()
                                }
                            }
                        }
                    },
                )
            }
            if (layouts.isNotEmpty()) {
                ClickablePreference(
                    label = stringResource(R.string.saved_layouts_delete_all),
                    confirmationText = stringResource(R.string.saved_layouts_delete_all_confirm),
                    onClick = {
                        layouts.forEach { it.delete() }
                        layouts = SavedLayouts.list(context)
                    },
                )
            }
        }
        PreferenceGroup(heading = stringResource(R.string.reset_label)) {
            ClickablePreference(
                label = stringResource(R.string.reset_icon_cache),
                subtitle = stringResource(R.string.reset_icon_cache_desc),
                onClick = {
                    Executors.MODEL_EXECUTOR.execute {
                        val app = LauncherAppState.INSTANCE.get(context)
                        app.iconCache.clearMemoryCache()
                        app.model.forceReload()
                    }
                    Toast.makeText(context, R.string.reset_icon_cache_done, Toast.LENGTH_SHORT).show()
                },
            )
            ClickablePreference(
                label = stringResource(R.string.restart_launcher),
                onClick = { restartLauncher(context) },
            )
            ClickablePreference(
                label = stringResource(R.string.reset_home_screen),
                subtitle = stringResource(R.string.reset_home_screen_desc),
                confirmationText = stringResource(R.string.reset_home_screen_confirm),
                onClick = {
                    Executors.MODEL_EXECUTOR.execute {
                        LauncherAppState.INSTANCE.get(context).model.modelDbController.createEmptyDB()
                        Executors.MAIN_EXECUTOR.execute { restartLauncher(context) }
                    }
                },
            )
            ClickablePreference(
                label = stringResource(R.string.reset_settings),
                subtitle = stringResource(R.string.reset_settings_desc),
                confirmationText = stringResource(R.string.reset_settings_confirm),
                onClick = {
                    if (!busy) {
                        busy = true
                        scope.launch {
                            resetAllSettings(context)
                            restartLauncher(context)
                        }
                    }
                },
            )
            ClickablePreference(
                label = stringResource(R.string.reset_fonts),
                onClick = {
                    val prefs = PreferenceManager.getInstance(context)
                    listOf(prefs.fontWorkspace, prefs.fontHeading, prefs.fontHeadingMedium, prefs.fontBody, prefs.fontBodyMedium)
                        .forEach { it.reset() }
                    Toast.makeText(context, R.string.reset_fonts_done, Toast.LENGTH_SHORT).show()
                },
            )
        }
    }
}

/**
 * Puts every setting back to its default. The grid, hidden and locked apps, drawer tabs and the
 * weather location are kept: they are the user's data rather than how the launcher looks.
 */
private suspend fun resetAllSettings(context: android.content.Context) {
    val prefs = PreferenceManager.getInstance(context)
    val keepPrefs = listOf(
        prefs.workspaceColumns, prefs.workspaceRows, prefs.hotseatColumns, prefs.hotseatColumnsUnfolded,
        prefs.hotseatRows, prefs.dockPages, prefs.hasOpenedSettings, prefs.theLauncherMajorVersion,
    ).map { it.key }.toSet()
    runCatching { prefs.resetAll(except = keepPrefs) }
    val keepPrefs2 = setOf(
        "hidden_apps", "locked_apps", "drawer_tabs", "legacy_popup_options_migrated",
        "smartspace_weather_location", "smartspace_calendar_ids",
    )
    runCatching {
        PreferenceManager2.getInstance(context).preferencesDataStore.edit { store ->
            store.asMap().keys.filter { it.name !in keepPrefs2 }.forEach { store.remove(it) }
        }
    }
}
