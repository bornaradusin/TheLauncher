package app.thelauncher.ui.preferences.destinations

import androidx.compose.animation.Crossfade
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import app.thelauncher.extras.DrawerTab
import app.thelauncher.preferences.getAdapter
import app.thelauncher.preferences2.preferenceManager2
import app.thelauncher.ui.preferences.LocalIsExpandedScreen
import app.thelauncher.ui.preferences.LocalNavController
import app.thelauncher.ui.preferences.components.AppItem
import app.thelauncher.ui.preferences.components.AppItemPlaceholder
import app.thelauncher.ui.preferences.components.controls.SwitchPreference
import app.thelauncher.ui.preferences.components.layout.PreferenceGroup
import app.thelauncher.ui.preferences.components.layout.PreferenceLayout
import app.thelauncher.ui.preferences.components.layout.PreferenceLazyColumn
import app.thelauncher.ui.preferences.components.layout.PreferenceScaffold
import app.thelauncher.ui.preferences.components.layout.PreferenceTemplate
import app.thelauncher.ui.preferences.components.layout.preferenceGroupItems
import app.thelauncher.ui.preferences.navigation.AppDrawerTabApps
import app.thelauncher.ui.util.bottomSheetHandler
import app.thelauncher.util.App
import app.thelauncher.util.appComparator
import app.thelauncher.util.appsState
import com.android.launcher3.R
import java.util.Comparator.comparing

@Composable
fun DrawerTabsPreferences(
    modifier: Modifier = Modifier,
) {
    val prefs2 = preferenceManager2()
    val adapter = prefs2.drawerTabs.getAdapter()
    val tabs = adapter.state.value
    val navController = LocalNavController.current
    val sheet = bottomSheetHandler
    val resources = LocalResources.current

    PreferenceLayout(
        label = stringResource(id = R.string.drawer_tabs_label),
        backArrowVisible = !LocalIsExpandedScreen.current,
        modifier = modifier,
    ) {
        PreferenceGroup(heading = stringResource(id = R.string.settings)) {
            SwitchPreference(
                adapter = prefs2.drawerTabsRemoveFromAll.getAdapter(),
                label = stringResource(id = R.string.drawer_tabs_remove_from_all),
                description = stringResource(id = R.string.drawer_tabs_remove_from_all_description),
            )
        }
        PreferenceGroup(heading = stringResource(id = R.string.drawer_tabs_label)) {
            val defaultName = stringResource(id = R.string.drawer_tab_new)
            PreferenceTemplate(
                title = {
                    Text(
                        text = stringResource(id = R.string.drawer_tab_add),
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                },
                startWidget = { Icon(Icons.Rounded.Add, contentDescription = null) },
                onClick = {
                    sheet.show {
                        FolderEditSheet(
                            folderId = -1,
                            initialTitle = defaultName,
                            itemCount = 0,
                            onRename = { _, title ->
                                if (title.isNotBlank()) adapter.onChange(tabs + DrawerTab(title.trim()))
                            },
                            onNavigate = {},
                            onDismiss = { sheet.hide() },
                            hideAppPicker = true,
                        )
                    }
                },
            )
            tabs.forEachIndexed { index, tab ->
                key(index, tab.name) {
                    PreferenceTemplate(
                        title = { Text(text = tab.name) },
                        description = {
                            Text(text = resources.getQuantityString(R.plurals.apps_count, tab.apps.size, tab.apps.size))
                        },
                        endWidget = {
                            IconButton(
                                onClick = { adapter.onChange(tabs.filterIndexed { i, _ -> i != index }) },
                                shapes = IconButtonDefaults.shapes(),
                            ) {
                                Icon(
                                    Icons.Rounded.Delete,
                                    contentDescription = stringResource(id = R.string.delete_label),
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        },
                        onClick = {
                            sheet.show {
                                FolderEditSheet(
                                    folderId = index,
                                    initialTitle = tab.name,
                                    itemCount = tab.apps.size,
                                    onRename = { i, title ->
                                        if (title.isNotBlank()) {
                                            adapter.onChange(
                                                tabs.mapIndexed { j, t -> if (j == i) t.copy(name = title.trim()) else t },
                                            )
                                        }
                                    },
                                    onNavigate = {
                                        navController.navigate(AppDrawerTabApps(it))
                                        sheet.hide()
                                    },
                                    onDismiss = { sheet.hide() },
                                )
                            }
                        },
                    )
                }
            }
        }
    }
}

@Composable
fun DrawerTabAppsPreferences(
    index: Int,
    modifier: Modifier = Modifier,
) {
    val adapter = preferenceManager2().drawerTabs.getAdapter()
    val tabs by adapter.state
    val tab = tabs.getOrNull(index) ?: return
    val selected = tab.apps
    val comparator = remember { comparing<App, Int> { if (selected.contains(it.key.toString())) 0 else 1 }.then(appComparator) }
    val apps by appsState(comparator = comparator)
    val state = rememberLazyListState()
    PreferenceScaffold(
        label = tab.name,
        modifier = modifier,
        isExpandedScreen = LocalIsExpandedScreen.current,
    ) {
        Crossfade(targetState = apps.isNotEmpty(), label = "") { present ->
            if (present) {
                PreferenceLazyColumn(it, state = state) {
                    preferenceGroupItems(items = apps, isFirstChild = true) { _, app ->
                        val appKey = app.key.toString()
                        AppItem(
                            app = app,
                            onClick = {
                                val newApps = if (appKey in selected) selected - appKey else selected + appKey
                                adapter.onChange(tabs.mapIndexed { j, t -> if (j == index) t.copy(apps = newApps) else t })
                            },
                        ) {
                            Checkbox(checked = appKey in selected, onCheckedChange = null)
                        }
                    }
                }
            } else {
                PreferenceLazyColumn(it, enabled = false) {
                    preferenceGroupItems(count = 20, isFirstChild = true) {
                        AppItemPlaceholder { Spacer(Modifier.width(24.dp)) }
                    }
                }
            }
        }
    }
}
