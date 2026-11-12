package app.thelauncher.ui.preferences.destinations

import androidx.compose.animation.Crossfade
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.Checkbox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import app.thelauncher.preferences.getAdapter
import app.thelauncher.preferences2.preferenceManager2
import app.thelauncher.ui.preferences.LocalIsExpandedScreen
import app.thelauncher.ui.preferences.components.AppItem
import app.thelauncher.ui.preferences.components.AppItemPlaceholder
import app.thelauncher.ui.preferences.components.layout.PreferenceLazyColumn
import app.thelauncher.ui.preferences.components.layout.PreferenceScaffold
import app.thelauncher.ui.preferences.components.layout.preferenceGroupItems
import app.thelauncher.util.App
import app.thelauncher.util.appComparator
import app.thelauncher.util.appsState
import com.android.launcher3.R
import java.util.Comparator.comparing

@Composable
fun LockedAppsPreferences(
    modifier: Modifier = Modifier,
) {
    val adapter = preferenceManager2().lockedApps.getAdapter()
    val lockedApps by adapter.state
    val comparator = remember {
        comparing<App, Int> { if (lockedApps.contains(it.key.toString())) 0 else 1 }.then(appComparator)
    }
    val apps by appsState(comparator = comparator)
    val state = rememberLazyListState()
    PreferenceScaffold(
        label = if (lockedApps.isEmpty()) {
            stringResource(id = R.string.app_lock_label)
        } else {
            stringResource(id = R.string.app_lock_label_with_count, lockedApps.size)
        },
        modifier = modifier,
        isExpandedScreen = LocalIsExpandedScreen.current,
    ) {
        Crossfade(targetState = apps.isNotEmpty(), label = "") { present ->
            if (present) {
                PreferenceLazyColumn(it, state = state) {
                    preferenceGroupItems(
                        items = apps,
                        isFirstChild = true,
                    ) { _, app ->
                        val key = app.key.toString()
                        AppItem(
                            app = app,
                            onClick = {
                                val newSet = lockedApps.toMutableSet()
                                if (!newSet.add(key)) newSet.remove(key)
                                adapter.onChange(newSet)
                            },
                        ) {
                            Checkbox(
                                checked = lockedApps.contains(key),
                                onCheckedChange = null,
                            )
                        }
                    }
                }
            } else {
                PreferenceLazyColumn(it, enabled = false) {
                    preferenceGroupItems(
                        count = 20,
                        isFirstChild = true,
                    ) {
                        AppItemPlaceholder {
                            Spacer(Modifier.width(24.dp))
                        }
                    }
                }
            }
        }
    }
}
