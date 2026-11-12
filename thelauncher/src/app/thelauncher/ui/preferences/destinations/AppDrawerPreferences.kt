/*
 * Copyright 2021, Lawnchair
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package app.thelauncher.ui.preferences.destinations

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import app.thelauncher.preferences.PreferenceAdapter
import app.thelauncher.preferences.getAdapter
import app.thelauncher.preferences.preferenceManager
import app.thelauncher.preferences2.preferenceManager2
import app.thelauncher.ui.preferences.LocalIsExpandedScreen
import app.thelauncher.ui.preferences.components.AppDrawerHapticFeedbackPreference
import app.thelauncher.ui.preferences.components.NavigationActionPreference
import app.thelauncher.ui.preferences.components.colorpreference.ColorPreference
import app.thelauncher.ui.preferences.components.controls.SliderPreference
import app.thelauncher.ui.preferences.components.controls.SwitchPreference
import app.thelauncher.ui.preferences.components.controls.SwitchPreferenceWithPreview
import app.thelauncher.ui.preferences.components.controls.WarningPreference
import app.thelauncher.ui.preferences.components.layout.ExpandAndShrink
import app.thelauncher.ui.preferences.components.layout.PreferenceGroup
import app.thelauncher.ui.preferences.components.layout.PreferenceLayout
import app.thelauncher.ui.preferences.navigation.AppDrawerHiddenApps
import app.thelauncher.ui.preferences.navigation.AppDrawerLockedApps
import app.thelauncher.ui.preferences.navigation.AppDrawerTabs
import app.thelauncher.ui.preferences.navigation.Predictions
import com.android.launcher3.InvariantDeviceProfile
import com.android.launcher3.R

object AppDrawerRoutes {
    const val HIDDEN_APPS = "hiddenApps"
}

@Composable
fun AppDrawerPreferences(
    modifier: Modifier = Modifier,
) {
    val prefs = preferenceManager()
    val prefs2 = preferenceManager2()
    val context = LocalContext.current
    val resources = context.resources
    val isFoldable = InvariantDeviceProfile.deviceType == InvariantDeviceProfile.TYPE_MULTI_DISPLAY

    PreferenceLayout(
        label = stringResource(id = R.string.app_drawer_label),
        backArrowVisible = !LocalIsExpandedScreen.current,
        modifier = modifier,
    ) {
        val drawerListAdapter = prefs.drawerList.getAdapter()
        Column {
            DrawerLayoutPreference(drawerListAdapter)
            ExpandAndShrink(visible = drawerListAdapter.state.value) {
                Column {
                    AppDrawerFolderPreferenceItem()
                    PreferenceGroup {
                        SwitchPreference(
                            adapter = prefs2.drawerFoldersFirst.getAdapter(),
                            label = stringResource(id = R.string.drawer_folders_first),
                            description = stringResource(id = R.string.drawer_folders_first_desc),
                        )
                    }
                }
            }
        }
        val hiddenApps = prefs2.hiddenApps.getAdapter().state.value
        PreferenceGroup(heading = stringResource(id = R.string.general_label)) {
            SwitchPreference(
                adapter = prefs2.drawerOnSheet.getAdapter(),
                label = stringResource(id = R.string.drawer_on_sheet),
                description = stringResource(id = R.string.drawer_on_sheet_desc),
            )
            SwitchPreference(
                adapter = prefs2.closeDrawerOnLaunch.getAdapter(),
                label = stringResource(id = R.string.close_drawer_on_launch),
                description = stringResource(id = R.string.close_drawer_on_launch_desc),
            )
            SwitchPreference(
                adapter = prefs2.drawerOverscroll.getAdapter(),
                label = stringResource(id = R.string.drawer_overscroll),
                description = stringResource(id = R.string.drawer_overscroll_desc),
            )
            NavigationActionPreference(
                label = stringResource(id = R.string.hidden_apps_label),
                destination = AppDrawerHiddenApps,
                subtitle = resources.getQuantityString(R.plurals.apps_count, hiddenApps.size, hiddenApps.size),
            )
            val drawerTabs = prefs2.drawerTabs.getAdapter().state.value
            NavigationActionPreference(
                label = stringResource(id = R.string.drawer_tabs_label),
                destination = AppDrawerTabs,
                subtitle = resources.getQuantityString(R.plurals.drawer_tabs_count, drawerTabs.size, drawerTabs.size),
            )
            val lockedApps = prefs2.lockedApps.getAdapter().state.value
            NavigationActionPreference(
                label = stringResource(id = R.string.app_lock_label),
                destination = AppDrawerLockedApps,
                subtitle = resources.getQuantityString(R.plurals.apps_count, lockedApps.size, lockedApps.size),
            )
            ExpandAndShrink(visible = lockedApps.isNotEmpty()) {
                SliderPreference(
                    label = stringResource(id = R.string.app_lock_timeout),
                    adapter = prefs2.appLockTimeoutSeconds.getAdapter(),
                    step = 30,
                    valueRange = 0..1800,
                    showUnit = "s",
                )
            }
            SwitchPreference(
                label = stringResource(id = R.string.hide_duplicate_apps),
                description = stringResource(id = R.string.hide_duplicate_apps_description),
                adapter = prefs2.hideDuplicateApps.getAdapter(),
            )
            SearchBarPreference(SearchRoute.DRAWER_SEARCH, showLabel = false)
            NavigationActionPreference(
                label = stringResource(R.string.suggestion_pref_screen_title),
                destination = Predictions,
            )
            app.thelauncher.ui.preferences.components.controls.ListPreference(
                adapter = prefs2.drawerSuggestionsDivider.getAdapter(),
                entries = listOf(
                    app.thelauncher.ui.preferences.components.controls.ListPreferenceEntry(app.thelauncher.util.SUGGESTIONS_DIVIDER_AUTO) { stringResource(id = R.string.suggestions_divider_auto) },
                    app.thelauncher.ui.preferences.components.controls.ListPreferenceEntry(app.thelauncher.util.SUGGESTIONS_DIVIDER_LABEL) { stringResource(id = R.string.suggestions_divider_label) },
                    app.thelauncher.ui.preferences.components.controls.ListPreferenceEntry(app.thelauncher.util.SUGGESTIONS_DIVIDER_NONE) { stringResource(id = R.string.suggestions_divider_none) },
                ),
                label = stringResource(id = R.string.suggestions_divider),
            )
            SwitchPreference(
                adapter = prefs2.showWorkTab.getAdapter(),
                label = stringResource(id = R.string.show_work_tab),
                description = stringResource(id = R.string.show_work_tab_desc),
            )
            AppDrawerHapticFeedbackPreference()
        }
        PreferenceGroup(heading = stringResource(R.string.style)) {
            ColorPreference(preference = prefs2.appDrawerBackgroundColor)
            app.thelauncher.ui.preferences.components.controls.ListPreference(
                adapter = prefs2.drawerStatusBarIcons.getAdapter(),
                entries = listOf(
                    app.thelauncher.ui.preferences.components.controls.ListPreferenceEntry("auto") { stringResource(id = R.string.status_bar_icons_auto) },
                    app.thelauncher.ui.preferences.components.controls.ListPreferenceEntry("dark") { stringResource(id = R.string.status_bar_icons_dark) },
                    app.thelauncher.ui.preferences.components.controls.ListPreferenceEntry("light") { stringResource(id = R.string.status_bar_icons_light) },
                ),
                label = stringResource(id = R.string.drawer_status_bar_icons),
            )
            SwitchPreference(
                adapter = prefs2.drawerNavBarTint.getAdapter(),
                label = stringResource(id = R.string.drawer_navbar_tint),
                description = stringResource(id = R.string.drawer_navbar_tint_desc),
            )
            SliderPreference(
                label = stringResource(id = R.string.background_opacity),
                adapter = prefs.drawerOpacity.getAdapter(),
                step = 0.1f,
                valueRange = 0F..1F,
                showAsPercentage = true,
            )
            ColorPreference(preference = prefs2.workProfileTabBackgroundColor)
            SwitchPreference(
                label = stringResource(id = R.string.work_profile_tab_container_background_label),
                adapter = prefs2.workProfileTabContainerBackground.getAdapter(),
            )
            SwitchPreference(
                label = stringResource(id = R.string.pref_all_apps_search_bar_background),
                adapter = prefs2.appDrawerSearchBarBackground.getAdapter(),
            )
        }
        PreferenceGroup(heading = stringResource(id = R.string.grid)) {
            val drawerColumnsAdapter = prefs2.drawerColumns.getAdapter()
            val drawerColumnsUnfoldedAdapter = prefs2.drawerColumnsUnfolded.getAdapter()
            if (isFoldable) {
                SliderPreference(
                    label = stringResource(id = R.string.state_folded, stringResource(id = R.string.app_drawer_columns)),
                    adapter = drawerColumnsAdapter,
                    step = 1,
                    valueRange = 3..10,
                )
                SliderPreference(
                    label = stringResource(id = R.string.state_unfolded, stringResource(id = R.string.app_drawer_columns)),
                    adapter = drawerColumnsUnfoldedAdapter,
                    step = 1,
                    valueRange = 3..10,
                )
                ExpandAndShrink(
                    visible = drawerColumnsAdapter.state.value > drawerColumnsUnfoldedAdapter.state.value,
                ) {
                    WarningPreference(
                        text = stringResource(id = R.string.foldable_columns_error),
                    )
                }
            } else {
                SliderPreference(
                    label = stringResource(id = R.string.app_drawer_columns),
                    adapter = drawerColumnsAdapter,
                    step = 1,
                    valueRange = 3..10,
                )
            }
            SliderPreference(
                adapter = prefs2.drawerCellHeightFactor.getAdapter(),
                label = stringResource(id = R.string.row_height_label),
                valueRange = 0.3F..1.5F,
                step = 0.1F,
                showAsPercentage = true,
            )
            SliderPreference(
                adapter = prefs2.drawerLeftRightMarginFactor.getAdapter(),
                label = stringResource(id = R.string.app_drawer_indent_label),
                valueRange = 0.0F..1.5F,
                step = 0.05F,
                showAsPercentage = true,
            )
            SliderPreference(
                adapter = prefs2.drawerPaddingTopFactor.getAdapter(),
                label = stringResource(id = R.string.top_padding_label),
                valueRange = 1.0F..2.0F,
                step = 0.05F,
                showAsPercentage = true,
            )
        }
        val showDrawerLabels = prefs2.showIconLabelsInDrawer.getAdapter()
        PreferenceGroup(heading = stringResource(id = R.string.icons)) {
            ColorPreference(preference = prefs2.drawerLabelColor)
            SliderPreference(
                label = stringResource(id = R.string.icon_sizes),
                adapter = prefs2.drawerIconSizeFactor.getAdapter(),
                step = 0.1f,
                valueRange = 0.5F..1.5F,
                showAsPercentage = true,
            )
            SwitchPreference(
                adapter = showDrawerLabels,
                label = stringResource(id = R.string.show_labels),
            )
            ExpandAndShrink(
                visible = showDrawerLabels.state.value,
            ) {
                SliderPreference(
                    label = stringResource(id = R.string.label_size),
                    adapter = prefs2.drawerIconLabelSizeFactor.getAdapter(),
                    step = 0.1F,
                    valueRange = 0.5F..1.5F,
                    showAsPercentage = true,
                )
            }
            ExpandAndShrink(
                visible = showDrawerLabels.state.value,
            ) {
                SwitchPreference(
                    adapter = prefs2.twoLineAllApps.getAdapter(),
                    label = stringResource(R.string.twoline_label),
                )
            }
        }
        PreferenceGroup(heading = stringResource(id = R.string.advanced)) {
            SwitchPreference(
                label = stringResource(id = R.string.pref_all_apps_remember_position_title),
                description = stringResource(id = R.string.pref_all_apps_remember_position_description),
                adapter = prefs2.rememberPosition.getAdapter(),
            )
            val showScrollbar = prefs2.showScrollbar.getAdapter()
            SwitchPreference(
                label = stringResource(id = R.string.pref_all_apps_show_scrollbar_title),
                adapter = showScrollbar,
            )
            ExpandAndShrink(visible = showScrollbar.state.value) {
                ColorPreference(preference = prefs2.drawerScrollbarColor)
            }
            HiddenDashboardsPreference()
        }
    }
}

/** Hide the launcher icons of icon packs and themes, which only open their own dashboards. */
@Composable
private fun HiddenDashboardsPreference() {
    val entries = listOf(
        app.thelauncher.ui.preferences.components.controls.ListPreferenceEntry(app.thelauncher.allapps.HIDDEN_DASHBOARDS_NONE) { stringResource(id = R.string.hidden_dashboards_none) },
        app.thelauncher.ui.preferences.components.controls.ListPreferenceEntry(app.thelauncher.allapps.HIDDEN_DASHBOARDS_ICON_PACKS) { stringResource(id = R.string.hidden_dashboards_icon_packs) },
        app.thelauncher.ui.preferences.components.controls.ListPreferenceEntry(app.thelauncher.allapps.HIDDEN_DASHBOARDS_THEMES) { stringResource(id = R.string.hidden_dashboards_themes) },
        app.thelauncher.ui.preferences.components.controls.ListPreferenceEntry(app.thelauncher.allapps.HIDDEN_DASHBOARDS_ALL) { stringResource(id = R.string.hidden_dashboards_all) },
    )
    app.thelauncher.ui.preferences.components.controls.ListPreference(
        adapter = preferenceManager2().hiddenDashboards.getAdapter(),
        entries = entries,
        label = stringResource(id = R.string.hidden_dashboards),
    )
}

@Composable
private fun DrawerLayoutPreference(drawerListAdapter: PreferenceAdapter<Boolean>) {
    SwitchPreferenceWithPreview(
        label = stringResource(id = R.string.layout),
        checked = !drawerListAdapter.state.value,
        onCheckedChange = { drawerListAdapter.onChange(!it) },
        disabledLabel = stringResource(id = R.string.feed_default),
        disabledContent = {
            Box(
                modifier = Modifier
                    .height(24.dp)
                    .fillMaxWidth(0.8f)
                    .background(
                        MaterialTheme.colorScheme.surfaceVariant,
                        RoundedCornerShape(16.dp),
                    ),
            )

            Column(modifier = Modifier, horizontalAlignment = Alignment.CenterHorizontally) {
                Row(
                    modifier = Modifier,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    repeat(4) {
                        Box(
                            modifier = Modifier
                                .size(20.dp)
                                .background(
                                    MaterialTheme.colorScheme.surfaceVariant,
                                    CircleShape,
                                ),
                        )
                    }
                }
            }
        },
        enabledLabel = stringResource(id = R.string.caddy_beta),
        enabledContent = {
            Box(
                modifier = Modifier
                    .height(24.dp)
                    .fillMaxWidth(0.8f)
                    .background(
                        MaterialTheme.colorScheme.surfaceVariant,
                        RoundedCornerShape(16.dp),
                    ),
            )
            Row(modifier = Modifier, horizontalArrangement = Arrangement.SpaceBetween) {
                repeat(2) {
                    Spacer(modifier = Modifier.width(10.dp))
                    Column(modifier = Modifier) {
                        repeat(2) {
                            Row(
                                modifier = Modifier,
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                repeat(2) {
                                    Box(
                                        modifier = Modifier
                                            .size(16.dp)
                                            .background(
                                                MaterialTheme.colorScheme.surfaceVariant,
                                                CircleShape,
                                            ),
                                    )
                                }
                            }
                            Spacer(modifier = Modifier.height(8.dp))
                        }
                    }
                    Spacer(modifier = Modifier.width(10.dp))
                }
            }
        },
    )
}
