/*
 * Copyright 2022, Lawnchair
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

import android.content.res.Configuration
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import app.thelauncher.hotseat.DOCK_SHAPE_CUSTOM
import app.thelauncher.hotseat.DOCK_SHAPE_FLAT
import app.thelauncher.hotseat.DOCK_SHAPE_ROUND
import app.thelauncher.hotseat.DockShape
import app.thelauncher.hotseat.dockShapePresets
import app.thelauncher.hotseat.presetName
import app.thelauncher.ui.preferences.components.controls.ListPreference
import app.thelauncher.ui.preferences.components.controls.ListPreferenceEntry
import app.thelauncher.preferences.PreferenceManager
import app.thelauncher.preferences.getAdapter
import app.thelauncher.preferences.preferenceManager
import app.thelauncher.preferences2.PreferenceManager2
import app.thelauncher.preferences2.preferenceManager2
import app.thelauncher.ui.preferences.LocalIsExpandedScreen
import app.thelauncher.ui.preferences.components.DummyLauncherBox
import app.thelauncher.ui.preferences.components.DummyLauncherLayout
import app.thelauncher.ui.preferences.components.WallpaperPreview
import app.thelauncher.ui.preferences.components.WithWallpaper
import app.thelauncher.ui.preferences.components.clipToBottomPercentage
import app.thelauncher.ui.preferences.components.colorpreference.ColorPreference
import app.thelauncher.ui.preferences.components.controls.MainSwitchPreference
import app.thelauncher.ui.preferences.components.controls.SliderPreference
import app.thelauncher.ui.preferences.components.controls.SwitchPreference
import app.thelauncher.ui.preferences.components.controls.WarningPreference
import app.thelauncher.ui.preferences.components.createPreviewIdp
import app.thelauncher.ui.preferences.components.layout.DividerColumn
import app.thelauncher.ui.preferences.components.layout.ExpandAndShrink
import app.thelauncher.ui.preferences.components.layout.PreferenceGroup
import app.thelauncher.ui.preferences.components.layout.PreferenceGroupHeading
import app.thelauncher.ui.preferences.components.layout.PreferenceLayout
import com.android.launcher3.InvariantDeviceProfile
import com.android.launcher3.R

@Composable
fun DockPreferences(modifier: Modifier = Modifier) {
    val prefs = preferenceManager()
    val prefs2 = preferenceManager2()

    PreferenceLayout(
        label = stringResource(id = R.string.dock_label),
        backArrowVisible = !LocalIsExpandedScreen.current,
        modifier = modifier,
    ) {
        val hotseatBgAdapter = prefs.hotseatBG.getAdapter()

        MainSwitchPreference(adapter = prefs2.isHotseatEnabled.getAdapter(), label = stringResource(id = R.string.show_hotseat_title)) {
            DockPreferencesPreview()
            PreferenceGroup(heading = stringResource(id = R.string.layout)) {
                SliderPreference(
                    label = stringResource(id = R.string.dock_height),
                    adapter = prefs2.dockHeightFactor.getAdapter(),
                    step = 0.05f,
                    valueRange = 0.5F..2F,
                    showAsPercentage = true,
                )
                SwitchPreference(
                    adapter = prefs2.dockRowsMatchHome.getAdapter(),
                    label = stringResource(id = R.string.dock_rows_match_home),
                    description = stringResource(id = R.string.dock_rows_match_home_desc),
                )
                SwitchPreference(
                    adapter = prefs2.dockShadow.getAdapter(),
                    label = stringResource(id = R.string.dock_shadow_label),
                    description = stringResource(id = R.string.dock_shadow_description),
                )
            }
            PreferenceGroup(heading = stringResource(id = R.string.style)) {
                SwitchPreference(
                    adapter = hotseatBgAdapter,
                    label = stringResource(id = R.string.hotseat_background),
                )
                ExpandAndShrink(
                    visible = hotseatBgAdapter.state.value,
                ) {
                    HotseatBackgroundSettings(prefs, prefs2)
                }
                SliderPreference(
                    label = stringResource(id = R.string.dock_icon_size),
                    adapter = prefs2.dockIconSizeFactor.getAdapter(),
                    step = 0.05f,
                    valueRange = 0.5F..1.5F,
                    showAsPercentage = true,
                )
                val drawerIndicator = prefs2.drawerIndicator.getAdapter()
                ListPreference(
                    adapter = drawerIndicator,
                    entries = listOf(
                        ListPreferenceEntry(app.thelauncher.util.DRAWER_INDICATOR_NONE) { stringResource(id = R.string.drawer_indicator_none) },
                        ListPreferenceEntry(app.thelauncher.util.DRAWER_INDICATOR_ARROW) { stringResource(id = R.string.drawer_indicator_arrow) },
                        ListPreferenceEntry(app.thelauncher.util.DRAWER_INDICATOR_LINE) { stringResource(id = R.string.drawer_indicator_line) },
                    ),
                    label = stringResource(id = R.string.drawer_indicator),
                    description = stringResource(id = R.string.drawer_indicator_desc),
                )
                ExpandAndShrink(visible = drawerIndicator.state.value != app.thelauncher.util.DRAWER_INDICATOR_NONE) {
                    ColorPreference(preference = prefs2.drawerIndicatorColor)
                }
            }
            SearchBarPreference(SearchRoute.DOCK_SEARCH)
            GridSettings(prefs, prefs2)
            PreferenceGroup(heading = stringResource(id = R.string.icons)) {
                ColorPreference(preference = prefs2.dockLabelColor)
                val dockLabels = prefs2.enableLabelInDock.getAdapter()
                SwitchPreference(
                    adapter = dockLabels,
                    label = stringResource(id = R.string.show_labels),
                )
                ExpandAndShrink(visible = dockLabels.state.value) {
                    Column {
                        SliderPreference(
                            label = stringResource(id = R.string.label_size),
                            adapter = prefs2.dockIconLabelSizeFactor.getAdapter(),
                            step = 0.1f,
                            valueRange = 0.5F..1.5F,
                            showAsPercentage = true,
                        )
                        SwitchPreference(
                            adapter = prefs2.dockTextShadow.getAdapter(),
                            label = stringResource(id = R.string.desktop_text_shadow_label),
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun HotseatBackgroundSettings(prefs: PreferenceManager, prefs2: PreferenceManager2) {
    DividerColumn {
        DockShapePreference(prefs, prefs2)
        ColorPreference(preference = prefs2.hotseatBackgroundColor)
        SliderPreference(
            label = stringResource(id = R.string.hotseat_bg_corner_radius),
            adapter = prefs2.hotseatBackgroundCornerRadius.getAdapter(),
            step = 1f,
            valueRange = 0f..100f,
            showUnit = "dp",
        )
        SliderPreference(
            label = stringResource(id = R.string.hotseat_bg_alpha),
            adapter = prefs.hotseatBGAlpha.getAdapter(),
            step = 5,
            valueRange = 5..100,
            showUnit = "%",
        )
        SliderPreference(
            label = stringResource(id = R.string.hotseat_bg_horizontal_inset_left),
            adapter = prefs.hotseatBGHorizontalInsetLeft.getAdapter(),
            step = 5,
            valueRange = 0..100,
            showUnit = "px",
        )
        SliderPreference(
            label = stringResource(id = R.string.hotseat_bg_horizontal_inset_right),
            adapter = prefs.hotseatBGHorizontalInsetRight.getAdapter(),
            step = 5,
            valueRange = 0..100,
            showUnit = "px",
        )
        SliderPreference(
            label = stringResource(id = R.string.hotseat_bg_vertical_inset_top),
            adapter = prefs.hotseatBGVerticalInsetTop.getAdapter(),
            step = 5,
            valueRange = 0..100,
            showUnit = "px",
        )
        SliderPreference(
            label = stringResource(id = R.string.hotseat_bg_vertical_inset_bottom),
            adapter = prefs.hotseatBGVerticalInsetBottom.getAdapter(),
            step = 5,
            valueRange = 0..100,
            showUnit = "px",
        )
    }
}

@Composable
fun GridSettings(prefs: PreferenceManager, prefs2: PreferenceManager2) {
    val isFoldable = InvariantDeviceProfile.deviceType == InvariantDeviceProfile.TYPE_MULTI_DISPLAY
    val hotseatColumnsAdapter = prefs.hotseatColumns.getAdapter()
    val hotseatColumnsUnfoldedAdapter = prefs.hotseatColumnsUnfolded.getAdapter()
    val hotseatRowsAdapter = prefs.hotseatRows.getAdapter()
    val dockPagesAdapter = prefs.dockPages.getAdapter()

    PreferenceGroup(heading = stringResource(id = R.string.grid)) {
        if (isFoldable) {
            SliderPreference(
                label = stringResource(id = R.string.state_folded, stringResource(id = R.string.dock_icons)),
                adapter = hotseatColumnsAdapter,
                step = 1,
                valueRange = 3..10,
            )
            SliderPreference(
                label = stringResource(id = R.string.state_unfolded, stringResource(id = R.string.dock_icons)),
                adapter = hotseatColumnsUnfoldedAdapter,
                step = 1,
                valueRange = 3..10,
            )
            ExpandAndShrink(
                visible = hotseatColumnsAdapter.state.value > hotseatColumnsUnfoldedAdapter.state.value,
            ) {
                WarningPreference(
                    text = stringResource(id = R.string.foldable_columns_error),
                )
            }
        } else {
            SliderPreference(
                label = stringResource(id = R.string.dock_icons),
                adapter = hotseatColumnsAdapter,
                step = 1,
                valueRange = 3..10,
            )
        }
        SliderPreference(
            label = stringResource(id = R.string.dock_rows),
            adapter = hotseatRowsAdapter,
            step = 1,
            valueRange = 1..2,
        )
        SliderPreference(
            label = stringResource(id = R.string.dock_pages),
            adapter = dockPagesAdapter,
            step = 1,
            valueRange = 1..5,
        )
        SliderPreference(
            adapter = prefs2.hotseatBottomFactor.getAdapter(),
            label = stringResource(id = R.string.hotseat_bottom_space_label),
            valueRange = 0.0F..1.7F,
            step = 0.1F,
            showAsPercentage = true,
        )
        SliderPreference(
            adapter = prefs2.pageIndicatorHeightFactor.getAdapter(),
            label = stringResource(id = R.string.page_indicator_height),
            valueRange = 0.0F..1.0F,
            step = 0.1F,
            showAsPercentage = true,
        )
    }
}

@Composable
fun ColumnScope.DockPreferencesPreview(modifier: Modifier = Modifier) {
    if (LocalConfiguration.current.orientation == Configuration.ORIENTATION_PORTRAIT) {
        val prefs = preferenceManager()
        val prefs2 = preferenceManager2()

        val hotseatRows = prefs.hotseatRows
        val dockPages = prefs.dockPages

        val adapters = listOf(
            prefs2.hotseatMode.getAdapter(),
            prefs.hotseatColumns.getAdapter(),
            prefs.hotseatColumnsUnfolded.getAdapter(),
            hotseatRows.getAdapter(),
            dockPages.getAdapter(),
            prefs2.themedHotseatQsb.getAdapter(),
            prefs.hotseatQsbCornerRadius.getAdapter(),
            prefs.hotseatQsbAlpha.getAdapter(),
            prefs.hotseatQsbStrokeWidth.getAdapter(),
            prefs2.hotseatBottomFactor.getAdapter(),
            prefs2.strokeColorStyle.getAdapter(),
            prefs2.enableLabelInDock.getAdapter(),
            prefs.hotseatBG.getAdapter(),
            prefs.hotseatBGHorizontalInsetLeft.getAdapter(),
            prefs.hotseatBGVerticalInsetTop.getAdapter(),
            prefs.hotseatBGHorizontalInsetRight.getAdapter(),
            prefs.hotseatBGVerticalInsetBottom.getAdapter(),
            prefs2.pageIndicatorHeightFactor.getAdapter(),
            prefs2.hotseatBackgroundColor.getAdapter(),
            prefs2.hotseatBackgroundCornerRadius.getAdapter(),
            prefs.hotseatBGAlpha.getAdapter(),
            prefs2.dockHeightFactor.getAdapter(),
            prefs2.dockRowsMatchHome.getAdapter(),
        )

        PreferenceGroupHeading(
            heading = stringResource(id = R.string.preview_label),
        )
        DividerColumn(
            modifier = modifier.padding(horizontal = 16.dp),
        ) {
            WithWallpaper { wallpaper ->
                DummyLauncherBox(
                    modifier = Modifier
                        .weight(1f)
                        .align(Alignment.CenterHorizontally)
                        .clip(MaterialTheme.shapes.large)
                        .clipToBottomPercentage(if (hotseatRows.getAdapter().state.value >= 2) .4f else .3f),
                ) {
                    WallpaperPreview(
                        wallpaper = wallpaper,
                        modifier = Modifier.fillMaxSize(),
                    )
                    key(adapters.map { it.state.value }.toTypedArray()) {
                        DummyLauncherLayout(
                            idp = createPreviewIdp { copy(numHotseatColumns = prefs.hotseatColumns.get()) },
                            modifier = Modifier.fillMaxSize(),
                        )
                    }
                }
            }
        }
    }
}

/** Hyperion's Flat / Round dock styles, applied to the radius and inset sliders below. */
@Composable
private fun DockShapePreference(prefs: PreferenceManager, prefs2: PreferenceManager2) {
    val radius = prefs2.hotseatBackgroundCornerRadius.getAdapter()
    val left = prefs.hotseatBGHorizontalInsetLeft.getAdapter()
    val top = prefs.hotseatBGVerticalInsetTop.getAdapter()
    val right = prefs.hotseatBGHorizontalInsetRight.getAdapter()
    val bottom = prefs.hotseatBGVerticalInsetBottom.getAdapter()
    val current = DockShape(radius.state.value, left.state.value, top.state.value, right.state.value, bottom.state.value).presetName()
    ListPreference(
        entries = listOf(
            ListPreferenceEntry(DOCK_SHAPE_FLAT) { stringResource(id = R.string.dock_shape_flat) },
            ListPreferenceEntry(DOCK_SHAPE_ROUND) { stringResource(id = R.string.dock_shape_round) },
            ListPreferenceEntry(DOCK_SHAPE_CUSTOM, enabled = false) { stringResource(id = R.string.dock_shape_custom) },
        ),
        value = current,
        onValueChange = { name ->
            dockShapePresets[name]?.let { shape ->
                radius.onChange(shape.cornerRadiusDp)
                left.onChange(shape.insetLeft)
                top.onChange(shape.insetTop)
                right.onChange(shape.insetRight)
                bottom.onChange(shape.insetBottom)
            }
        },
        label = stringResource(id = R.string.dock_shape),
    )
}
