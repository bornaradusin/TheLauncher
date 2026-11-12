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

import app.thelauncher.ui.preferences.components.controls.SliderPreference
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.thelauncher.preferences.PreferenceAdapter
import app.thelauncher.preferences.getAdapter
import app.thelauncher.preferences.preferenceManager
import app.thelauncher.preferences2.asState
import app.thelauncher.preferences2.preferenceManager2
import app.thelauncher.theme.color.ColorOption
import app.thelauncher.theme.color.ColorStyle
import app.thelauncher.ui.preferences.LocalIsExpandedScreen
import app.thelauncher.ui.preferences.LocalPreferenceInteractor
import app.thelauncher.ui.preferences.components.FONT_EVERYTHING_ELSE
import app.thelauncher.ui.preferences.components.FontPreference
import app.thelauncher.ui.preferences.components.NavigationActionPreference
import app.thelauncher.ui.preferences.components.NotificationDotsPreference
import app.thelauncher.ui.preferences.components.ThemePreference
import app.thelauncher.ui.preferences.components.colorpreference.ColorContrastWarning
import app.thelauncher.ui.preferences.components.colorpreference.ColorPreference
import app.thelauncher.ui.preferences.components.controls.ListPreference
import app.thelauncher.ui.preferences.components.controls.ListPreferenceEntry
import app.thelauncher.ui.preferences.components.controls.SwitchPreference
import app.thelauncher.ui.preferences.components.controls.WarningPreference
import app.thelauncher.ui.preferences.components.layout.ExpandAndShrink
import app.thelauncher.ui.preferences.components.layout.PreferenceGroup
import app.thelauncher.ui.preferences.components.layout.PreferenceLayout
import app.thelauncher.ui.preferences.components.notificationDotsEnabled
import app.thelauncher.ui.preferences.components.notificationServiceEnabled
import app.thelauncher.ui.preferences.data.liveinfo.liveInformationManager
import app.thelauncher.ui.preferences.destinations.icons.ThemedIconsState
import app.thelauncher.ui.preferences.navigation.GeneralAdvancedIcons
import app.thelauncher.ui.preferences.navigation.GeneralIconPack
import app.thelauncher.ui.preferences.navigation.GeneralIconShape
import app.thelauncher.ui.preferences.navigation.GeneralThemedIcons
import com.android.launcher3.BuildConfig
import app.thelauncher.util.DOT_PLACEMENT_TOP_RIGHT
import app.thelauncher.util.DOT_PLACEMENT_TOP_LEFT
import app.thelauncher.util.DOT_PLACEMENT_BOTTOM_LEFT
import app.thelauncher.util.DOT_PLACEMENT_BOTTOM_RIGHT
import com.android.launcher3.R
import com.android.launcher3.Utilities

@Composable
fun GeneralPreferences(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val prefs = preferenceManager()
    val prefs2 = preferenceManager2()
    val liveInfoManager = liveInformationManager()
    val iconPacks by LocalPreferenceInteractor.current.iconPacks.collectAsStateWithLifecycle()
    val themedIconsAdapter = prefs.themedIcons.getAdapter()
    val drawerThemedIconsAdapter = prefs.drawerThemedIcons.getAdapter()
    val iconShapeAdapter = prefs2.iconShape.getAdapter()

    val currentIconPackName = iconPacks
        .find { it.packageName == preferenceManager().iconPackPackage.get() }
        ?.name
    val themedIconsState = ThemedIconsState.getForSettings(
        themedIcons = themedIconsAdapter.state.value,
        drawerThemedIcons = drawerThemedIconsAdapter.state.value,
    )
    val themedIconsSubtitle = stringResource(id = themedIconsState.labelResourceId)
    val iconShapeSubtitle = iconShapeEntries(context)
        .firstOrNull { it.value == iconShapeAdapter.state.value }
        ?.label?.invoke()
        ?: stringResource(id = R.string.custom)

    PreferenceLayout(
        backArrowVisible = !LocalIsExpandedScreen.current,
        label = stringResource(id = R.string.general_label),
        modifier = modifier,
    ) {
        PreferenceGroup {
            SwitchPreference(
                adapter = prefs.allowRotation.getAdapter(),
                label = stringResource(id = R.string.home_screen_rotation_label),
                description = stringResource(id = R.string.home_screen_rotation_description),
            )
            SwitchPreference(
                adapter = prefs2.highRefreshRate.getAdapter(),
                label = stringResource(id = R.string.high_refresh_rate),
                description = stringResource(id = R.string.high_refresh_rate_desc),
            )
        }
        PreferenceGroup(heading = stringResource(id = R.string.updater)) {
            if (BuildConfig.APPLICATION_ID.contains("nightly")) {
                SwitchPreference(
                    adapter = prefs2.autoUpdaterNightly.getAdapter(),
                    label = stringResource(id = R.string.auto_updater_label),
                    description = stringResource(id = R.string.auto_updater_description),
                )
            }
            SwitchPreference(
                adapter = liveInfoManager.enabled.getAdapter(),
                label = stringResource(id = R.string.live_information_label),
                description = stringResource(id = R.string.live_information_description),
            )
        }
        // Two fonts, as in Hyperion: app names, and everything else (headings and body text).
        PreferenceGroup(heading = stringResource(id = R.string.font_label)) {
            FontPreference(
                fontPref = prefs.fontWorkspace,
                label = stringResource(R.string.font_app_names),
            )
            FontPreference(
                fontPref = prefs.fontHeading,
                label = stringResource(R.string.font_everything_else),
                routeKey = FONT_EVERYTHING_ELSE,
            )
        }

        PreferenceGroup(
            modifier = Modifier,
            heading = stringResource(id = R.string.icons),
        ) {
            NavigationActionPreference(
                label = stringResource(id = R.string.icon_pack),
                destination = GeneralIconPack,
                subtitle = currentIconPackName,
            )
            NavigationActionPreference(
                label = stringResource(id = R.string.themed_icon_title),
                destination = GeneralThemedIcons,
                subtitle = themedIconsSubtitle,
            )
            NavigationActionPreference(
                label = stringResource(id = R.string.icon_shape_label),
                destination = GeneralIconShape(ShapeRoute.APP_SHAPE),
                subtitle = iconShapeSubtitle,
                endWidget = {
                    IconShapePreview(iconShape = iconShapeAdapter.state.value)
                },
            )
            NavigationActionPreference(
                label = stringResource(id = R.string.advanced_icon_settings_title),
                destination = GeneralAdvancedIcons,
            )
        }

        val accentColorAdapter = prefs2.accentColor.getAdapter()
        val showColorStyle = !(Utilities.ATLEAST_S && accentColorAdapter.state.value == ColorOption.SystemAccent) ||
            !Utilities.ATLEAST_S

        PreferenceGroup(heading = stringResource(id = R.string.colors)) {
            ThemePreference()
            ColorPreference(preference = prefs2.accentColor)
            SwitchPreference(
                adapter = prefs2.blackoutDarkMode.getAdapter(),
                label = stringResource(id = R.string.blackout_dark_mode),
                description = stringResource(id = R.string.blackout_dark_mode_desc),
            )
            ExpandAndShrink(visible = showColorStyle) {
                ColorStylePreference(prefs2.colorStyle.getAdapter())
            }
        }

        val notificationEnabled by remember { notificationDotsEnabled(context) }.collectAsStateWithLifecycle(initialValue = false)
        val serviceEnabled = notificationServiceEnabled()
        val showNotificationCountAdapter = prefs2.showNotificationCount.getAdapter()
        val showNotificationCount = showNotificationCountAdapter.state.value
        val dotColor = prefs2.notificationDotColor.asState().value
        val dotTextColor = prefs2.notificationDotTextColor.asState().value

        PreferenceGroup(heading = stringResource(id = R.string.notification_dots)) {
            NotificationDotsPreference(enabled = notificationEnabled, serviceEnabled = serviceEnabled)
            val canDisplayNotificationDot = notificationEnabled && serviceEnabled
            ExpandAndShrink(visible = canDisplayNotificationDot) {
                SwitchPreference(
                    adapter = prefs2.hideNotificationDots.getAdapter(),
                    label = stringResource(id = R.string.hide_notification_dots),
                    description = stringResource(id = R.string.hide_notification_dots_desc),
                )
            }
            ExpandAndShrink(visible = canDisplayNotificationDot) {
                SliderPreference(
                    label = stringResource(id = R.string.notification_dot_size),
                    adapter = prefs2.notificationDotSizeFactor.getAdapter(),
                    step = 0.1f,
                    valueRange = 0.5F..1.5F,
                    showAsPercentage = true,
                )
            }
            val dotFromIconAdapter = prefs2.notificationDotFromIcon.getAdapter()
            ExpandAndShrink(visible = canDisplayNotificationDot) {
                SwitchPreference(
                    adapter = dotFromIconAdapter,
                    label = stringResource(id = R.string.notification_dot_from_icon),
                    description = stringResource(id = R.string.notification_dot_from_icon_desc),
                )
            }
            ExpandAndShrink(visible = canDisplayNotificationDot && !dotFromIconAdapter.state.value) {
                ColorPreference(preference = prefs2.notificationDotColor)
            }
            ExpandAndShrink(visible = canDisplayNotificationDot) {
                ListPreference(
                    adapter = prefs2.notificationDotPlacement.getAdapter(),
                    entries = listOf(
                        ListPreferenceEntry(DOT_PLACEMENT_TOP_RIGHT) { stringResource(id = R.string.dot_placement_top_right) },
                        ListPreferenceEntry(DOT_PLACEMENT_TOP_LEFT) { stringResource(id = R.string.dot_placement_top_left) },
                        ListPreferenceEntry(DOT_PLACEMENT_BOTTOM_LEFT) { stringResource(id = R.string.dot_placement_bottom_left) },
                        ListPreferenceEntry(DOT_PLACEMENT_BOTTOM_RIGHT) { stringResource(id = R.string.dot_placement_bottom_right) },
                    ),
                    label = stringResource(id = R.string.dot_placement),
                )
            }
            ExpandAndShrink(visible = canDisplayNotificationDot) {
                SwitchPreference(
                    adapter = showNotificationCountAdapter,
                    label = stringResource(id = R.string.show_notification_count),
                )
            }
            ExpandAndShrink(visible = canDisplayNotificationDot && showNotificationCount) {
                SwitchPreference(
                    adapter = prefs2.notificationCountShort.getAdapter(),
                    label = stringResource(id = R.string.notification_count_short),
                    description = stringResource(id = R.string.notification_count_short_desc),
                )
            }
            ExpandAndShrink(visible = canDisplayNotificationDot && showNotificationCount) {
                ColorPreference(preference = prefs2.notificationDotTextColor)
            }
            ExpandAndShrink(visible = canDisplayNotificationDot && showNotificationCount) {
                NotificationDotColorContrastWarnings(
                    dotColor = dotColor,
                    dotTextColor = dotTextColor,
                )
            }
        }
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
            PreferenceGroup(heading = stringResource(id = R.string.language_label)) {
                ForceEnglishPreference()
            }
        }
    }
}

@Composable
private fun ColorStylePreference(
    adapter: PreferenceAdapter<ColorStyle>,
    modifier: Modifier = Modifier,
) {
    val entries = remember {
        ColorStyle.values().map { mode ->
            ListPreferenceEntry(
                value = mode,
                label = { stringResource(id = mode.nameResourceId) },
            )
        }
    }

    ListPreference(
        adapter = adapter,
        entries = entries,
        label = stringResource(id = R.string.color_style_label),
        modifier = modifier,
    )
}

@Composable
private fun NotificationDotColorContrastWarnings(
    dotColor: ColorOption,
    dotTextColor: ColorOption,
    modifier: Modifier = Modifier,
) {
    val dotColorIsDynamic = when (dotColor) {
        is ColorOption.SystemAccent,
        is ColorOption.WallpaperPrimary,
        is ColorOption.Default,
        -> true

        else -> false
    }

    if (dotColorIsDynamic && dotTextColor !is ColorOption.Default) {
        WarningPreference(
            text = stringResource(id = R.string.notification_dots_color_contrast_warning_sometimes),
            modifier = modifier,
        )
    } else {
        ColorContrastWarning(
            foregroundColor = dotTextColor,
            backgroundColor = dotColor,
            text = stringResource(id = R.string.notification_dots_color_contrast_warning_always),
            modifier = modifier,
        )
    }
}

/** Shows the launcher in English whatever the phone's language, as Hyperion's "Force English language". */
@androidx.annotation.RequiresApi(android.os.Build.VERSION_CODES.TIRAMISU)
@Composable
private fun ForceEnglishPreference() {
    val context = LocalContext.current
    val localeManager = remember { context.getSystemService(android.app.LocaleManager::class.java) }
    var forced by remember {
        androidx.compose.runtime.mutableStateOf(
            localeManager.applicationLocales.toLanguageTags().startsWith("en"),
        )
    }
    SwitchPreference(
        checked = forced,
        onCheckedChange = { on ->
            localeManager.applicationLocales = if (on) {
                android.os.LocaleList.forLanguageTags("en")
            } else {
                android.os.LocaleList.getEmptyLocaleList()
            }
            forced = on
        },
        label = stringResource(id = R.string.force_english),
        description = stringResource(id = R.string.force_english_desc),
    )
}
