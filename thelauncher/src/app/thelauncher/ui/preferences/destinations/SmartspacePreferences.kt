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

import android.app.Activity
import android.view.ContextThemeWrapper
import android.view.ViewGroup
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import androidx.compose.animation.Crossfade
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import app.thelauncher.preferences.PreferenceAdapter
import app.thelauncher.preferences.getAdapter
import app.thelauncher.preferences2.preferenceManager2
import app.thelauncher.smartspace.SmartspaceViewContainer
import app.thelauncher.smartspace.model.TheLauncherSmartspace
import app.thelauncher.smartspace.model.SMARTSPACE_DATE_FULL
import app.thelauncher.smartspace.model.SMARTSPACE_DATE_LONG
import app.thelauncher.smartspace.model.SMARTSPACE_DATE_SHORT
import app.thelauncher.smartspace.model.SMARTSPACE_LAYOUT_ONE_CARD
import app.thelauncher.smartspace.model.SMARTSPACE_LAYOUT_PAGES
import app.thelauncher.ui.preferences.components.colorpreference.ColorPreference
import app.thelauncher.smartspace.model.SmartspaceCalendar
import app.thelauncher.smartspace.model.SmartspaceMode
import app.thelauncher.smartspace.model.SmartspaceTimeFormat
import app.thelauncher.smartspace.model.Smartspacer
import app.thelauncher.smartspace.provider.CalendarEventsProvider
import app.thelauncher.smartspace.provider.SmartspaceProvider
import app.thelauncher.ui.preferences.LocalIsExpandedScreen
import app.thelauncher.ui.preferences.components.controls.ClickablePreference
import app.thelauncher.ui.preferences.components.controls.ListPreference
import app.thelauncher.ui.preferences.components.controls.ListPreferenceEntry
import app.thelauncher.ui.preferences.components.controls.MainSwitchPreference
import app.thelauncher.ui.preferences.components.controls.SliderPreference
import app.thelauncher.ui.preferences.components.controls.SwitchPreference
import app.thelauncher.ui.preferences.components.controls.TextPreference
import app.thelauncher.ui.preferences.components.layout.AdvancedSection
import app.thelauncher.ui.preferences.components.layout.ExpandAndShrink
import app.thelauncher.ui.preferences.components.layout.PreferenceGroup
import app.thelauncher.ui.preferences.components.layout.PreferenceLayout
import app.thelauncher.ui.theme.isSelectedThemeDark
import app.thelauncher.ui.theme.preferenceGroupColor
import android.content.Context
import android.widget.Toast
import androidx.compose.runtime.rememberCoroutineScope
import app.thelauncher.smartspace.model.GLANCE_SIZE_AUTO
import app.thelauncher.smartspace.model.GLANCE_SIZE_COMPACT
import app.thelauncher.smartspace.model.GLANCE_SIZE_LARGE
import app.thelauncher.smartspace.model.GLANCE_STYLE_MATERIAL
import app.thelauncher.smartspace.model.GLANCE_STYLE_TEXT
import com.android.launcher3.LauncherAppState
import com.android.launcher3.LauncherSettings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import com.android.launcher3.R
import com.kieronquinn.app.smartspacer.sdk.SmartspacerConstants

@Composable
fun SmartspacePreferences(
    fromWidget: Boolean,
    modifier: Modifier = Modifier,
) {
    val preferenceManager2 = preferenceManager2()
    val smartspaceProvider = SmartspaceProvider.INSTANCE.get(LocalContext.current)
    val smartspaceAdapter = preferenceManager2.enableSmartspace.getAdapter()
    val smartspaceModeAdapter = preferenceManager2.smartspaceMode.getAdapter()
    val selectedMode = smartspaceModeAdapter.state.value
    val modeIsTheLauncher = selectedMode == TheLauncherSmartspace

    PreferenceLayout(
        label = stringResource(id = R.string.smartspace_widget),
        backArrowVisible = !LocalIsExpandedScreen.current && !fromWidget,
        modifier = modifier,
    ) {
        if (fromWidget) {
            SmartspacePreview()
            TheLauncherSmartspaceSettings(smartspaceProvider)
        } else {
            MainSwitchPreference(
                adapter = smartspaceAdapter,
                label = stringResource(R.string.smartspace_widget_toggle_label),
                description = stringResource(id = R.string.smartspace_widget_toggle_description).takeIf { modeIsTheLauncher },
            ) {
                if (modeIsTheLauncher) {
                    SmartspacePreview()
                }
                if (modeIsTheLauncher) {
                    TheLauncherSmartspaceSettings(smartspaceProvider)
                }
                // Where the glance comes from is rarely changed, so it sits under Advanced, open
                // when another provider is in use.
                PreferenceGroup(modifier = Modifier.padding(top = 8.dp)) {
                    AdvancedSection(initiallyOpen = !modeIsTheLauncher) {
                        SmartspaceProviderPreference(
                            adapter = smartspaceModeAdapter,
                        )
                        if (selectedMode == Smartspacer) {
                            SmartspacerSettings()
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun TheLauncherSmartspaceSettings(
    smartspaceProvider: SmartspaceProvider,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier,
    ) {
        PreferenceGroup(
            heading = stringResource(id = R.string.what_to_show),
            modifier = Modifier.padding(top = 8.dp),
        ) {
            smartspaceProvider.dataSources
                .asSequence()
                .filter { it.isAvailable }
                .forEach {
                    key(it.providerName) {
                        SwitchPreference(
                            adapter = it.enabledPref.getAdapter(),
                            label = stringResource(id = it.providerName),
                        )
                    }
                }
        }
        val textStyle = preferenceManager2().smartspaceWidgetStyle.getAdapter().state.value == GLANCE_STYLE_TEXT
        ExpandAndShrink(visible = textStyle) {
            PreferenceGroup(
                heading = stringResource(id = R.string.smartspace_layout),
                modifier = Modifier.padding(top = 8.dp),
            ) {
                SwitchPreference(
                    adapter = preferenceManager2().smartspaceCenteredDate.getAdapter(),
                    label = stringResource(id = R.string.smartspace_centered_date),
                    description = stringResource(id = R.string.smartspace_centered_date_description),
                )
            }
        }
        SmartspaceStylePreferences()
        ExtraSmartspacePreferences()
        SmartspaceDateAndTimePreferences()
    }
}

/** How At a Glance looks: the Material card or text, its size and colours, the date and text. */
@Composable
private fun SmartspaceStylePreferences() {
    val prefs2 = preferenceManager2()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val styleAdapter = prefs2.smartspaceWidgetStyle.getAdapter()
    val material = styleAdapter.state.value == GLANCE_STYLE_MATERIAL
    val layoutAdapter = prefs2.smartspaceLayout.getAdapter()
    val swipeable = layoutAdapter.state.value == SMARTSPACE_LAYOUT_PAGES
    val sizeAdapter = prefs2.smartspaceWidgetSize.getAdapter()

    PreferenceGroup(
        heading = stringResource(id = R.string.smartspace_style),
        description = (stringResource(id = R.string.glance_widget_desc) + "\n\n" + stringResource(id = R.string.glance_pinned_tip))
            .takeIf { material },
        showDescription = material,
        modifier = Modifier.padding(top = 8.dp),
    ) {
        ListPreference(
            adapter = styleAdapter,
            entries = remember {
                listOf(
                    ListPreferenceEntry(GLANCE_STYLE_MATERIAL) { stringResource(id = R.string.glance_style_material) },
                    ListPreferenceEntry(GLANCE_STYLE_TEXT) { stringResource(id = R.string.glance_style_text) },
                )
            },
            label = stringResource(id = R.string.glance_style),
        )
        ExpandAndShrink(visible = material) {
            Column {
                ListPreference(
                    entries = remember {
                        listOf(
                            ListPreferenceEntry(GLANCE_SIZE_COMPACT) { stringResource(id = R.string.glance_size_compact) },
                            ListPreferenceEntry(GLANCE_SIZE_AUTO) { stringResource(id = R.string.glance_size_auto) },
                            ListPreferenceEntry(GLANCE_SIZE_LARGE) { stringResource(id = R.string.glance_size_large) },
                        )
                    },
                    value = sizeAdapter.state.value,
                    onValueChange = { size ->
                        if (size != GLANCE_SIZE_LARGE && size != GLANCE_SIZE_AUTO) {
                            sizeAdapter.onChange(size)
                        } else {
                            // The large and auto-sized widgets take the second row of the first
                            // home screen, so that row has to be empty.
                            scope.launch {
                                val free = withContext(Dispatchers.IO) { secondRowIsFree(context) }
                                if (free) {
                                    sizeAdapter.onChange(size)
                                } else {
                                    Toast.makeText(context, R.string.glance_size_large_blocked, Toast.LENGTH_LONG).show()
                                }
                            }
                        }
                    },
                    label = stringResource(id = R.string.glance_size),
                )
                ColorPreference(preference = prefs2.smartspaceCardColor)
                SliderPreference(
                    label = stringResource(id = R.string.glance_card_opacity),
                    adapter = prefs2.smartspaceCardOpacity.getAdapter(),
                    valueRange = 0.2f..1f,
                    step = 0.05f,
                    showAsPercentage = true,
                )
                SliderPreference(
                    label = stringResource(id = R.string.glance_card_corners),
                    adapter = prefs2.smartspaceCardRadius.getAdapter(),
                    valueRange = 0f..40f,
                    step = 2f,
                    showUnit = "dp",
                )
            }
        }
        ExpandAndShrink(visible = !material) {
            Column {
                ListPreference(
                    adapter = layoutAdapter,
                    entries = remember {
                        listOf(
                            ListPreferenceEntry(SMARTSPACE_LAYOUT_ONE_CARD) { stringResource(id = R.string.smartspace_layout_one_card) },
                            ListPreferenceEntry(SMARTSPACE_LAYOUT_PAGES) { stringResource(id = R.string.smartspace_layout_pages) },
                        )
                    },
                    label = stringResource(id = R.string.smartspace_card_layout),
                )
                ExpandAndShrink(visible = swipeable) {
                    SwitchPreference(
                        adapter = prefs2.smartspacePageDots.getAdapter(),
                        label = stringResource(id = R.string.smartspace_page_dots),
                        description = stringResource(id = R.string.smartspace_page_dots_description),
                    )
                }
            }
        }
        ListPreference(
            adapter = prefs2.smartspaceDateStyle.getAdapter(),
            entries = remember {
                listOf(
                    ListPreferenceEntry(SMARTSPACE_DATE_SHORT) { stringResource(id = R.string.smartspace_date_short) },
                    ListPreferenceEntry(SMARTSPACE_DATE_LONG) { stringResource(id = R.string.smartspace_date_long) },
                    ListPreferenceEntry(SMARTSPACE_DATE_FULL) { stringResource(id = R.string.smartspace_date_full) },
                )
            },
            label = stringResource(id = R.string.smartspace_date_style),
        )
        SliderPreference(
            label = stringResource(id = R.string.smartspace_text_size),
            adapter = prefs2.smartspaceTextScale.getAdapter(),
            valueRange = 0.7f..1.5f,
            step = 0.05f,
            showAsPercentage = true,
        )
        ColorPreference(preference = prefs2.smartspaceTextColor)
    }
}

/** Whether nothing sits in the second row of the first home screen. */
private fun secondRowIsFree(context: Context): Boolean = runCatching {
    val db = LauncherAppState.INSTANCE.get(context).model.modelDbController
    db.query(
        arrayOf(LauncherSettings.Favorites._ID),
        "${LauncherSettings.Favorites.CONTAINER} = ${LauncherSettings.Favorites.CONTAINER_DESKTOP} AND " +
            "${LauncherSettings.Favorites.SCREEN} = 0 AND ${LauncherSettings.Favorites.CELLY} <= 1 AND " +
            "${LauncherSettings.Favorites.CELLY} + ${LauncherSettings.Favorites.SPANY} > 1",
        null,
        null,
    ).use { it.count == 0 }
}.getOrDefault(false)

@Composable
private fun ExtraSmartspacePreferences() {
    val preferenceManager2 = preferenceManager2()
    val context = LocalContext.current
    val weatherEnabled = preferenceManager2.smartspaceWeather.getAdapter().state.value
    val calendarEnabled = preferenceManager2.smartspaceCalendarEvents.getAdapter().state.value
    ExpandAndShrink(visible = weatherEnabled) {
        PreferenceGroup(
            heading = stringResource(id = R.string.smartspace_weather),
            modifier = Modifier.padding(top = 8.dp),
        ) {
            val fahrenheit = preferenceManager2.smartspaceWeatherFahrenheit.getAdapter()
            val kelvin = preferenceManager2.smartspaceWeatherKelvin.getAdapter()
            // One choice instead of two switches that could both be on.
            val unitAdapter = remember(fahrenheit, kelvin) {
                object : PreferenceAdapter<String> {
                    override val state = derivedStateOf {
                        when {
                            kelvin.state.value -> "k"
                            fahrenheit.state.value -> "f"
                            else -> "c"
                        }
                    }
                    override fun onChange(newValue: String) {
                        fahrenheit.onChange(newValue == "f")
                        kelvin.onChange(newValue == "k")
                    }
                }
            }
            ListPreference(
                adapter = unitAdapter,
                entries = remember {
                    listOf(
                        ListPreferenceEntry("c") { stringResource(id = R.string.weather_unit_celsius) },
                        ListPreferenceEntry("f") { stringResource(id = R.string.weather_unit_fahrenheit) },
                        ListPreferenceEntry("k") { stringResource(id = R.string.weather_unit_kelvin) },
                    )
                },
                label = stringResource(id = R.string.weather_unit),
            )
            if (preferenceManager2.smartspaceWidgetStyle.getAdapter().state.value == GLANCE_STYLE_MATERIAL) {
                SwitchPreference(
                    adapter = preferenceManager2.smartspaceWeatherDetails.getAdapter(),
                    label = stringResource(id = R.string.glance_weather_details),
                    description = stringResource(id = R.string.glance_weather_details_desc),
                )
                SmartspaceAppPreference(
                    adapter = preferenceManager2.smartspaceWeatherApp.getAdapter(),
                    label = stringResource(id = R.string.glance_weather_app),
                    defaultLabel = stringResource(id = R.string.glance_weather_app_default),
                )
            }
            val automaticLocation = stringResource(id = R.string.smartspace_weather_location_auto)
            TextPreference(
                adapter = preferenceManager2.smartspaceWeatherLocation.getAdapter(),
                label = stringResource(id = R.string.smartspace_weather_location),
                description = { it.ifBlank { automaticLocation } },
            )
        }
    }
    ExpandAndShrink(visible = calendarEnabled) {
        val idsAdapter = preferenceManager2.smartspaceCalendarIds.getAdapter()
        val selected = idsAdapter.state.value
        val calendars = remember(calendarEnabled) {
            CalendarEventsProvider.listCalendars(context)
        }
        if (preferenceManager2.smartspaceWidgetStyle.getAdapter().state.value == GLANCE_STYLE_MATERIAL) {
            PreferenceGroup(modifier = Modifier.padding(top = 8.dp)) {
                ListPreference(
                    adapter = preferenceManager2.smartspaceEventLookahead.getAdapter(),
                    entries = remember {
                        listOf(
                            ListPreferenceEntry(1) { stringResource(id = R.string.glance_event_lookahead_1) },
                            ListPreferenceEntry(3) { stringResource(id = R.string.glance_event_lookahead_3) },
                            ListPreferenceEntry(12) { stringResource(id = R.string.glance_event_lookahead_12) },
                            ListPreferenceEntry(24) { stringResource(id = R.string.glance_event_lookahead_24) },
                        )
                    },
                    label = stringResource(id = R.string.glance_event_lookahead),
                )
            }
        }
        if (calendars.isNotEmpty()) {
            PreferenceGroup(
                heading = stringResource(id = R.string.smartspace_calendars_to_show),
                description = stringResource(id = R.string.smartspace_calendars_to_show_description),
                showDescription = true,
                modifier = Modifier.padding(top = 8.dp),
            ) {
                calendars.forEach { calendar ->
                    key(calendar.id) {
                        SwitchPreference(
                            checked = selected.isEmpty() || calendar.id in selected,
                            onCheckedChange = { checked ->
                                val base = if (selected.isEmpty()) calendars.map { it.id }.toSet() else selected
                                val updated = if (checked) base + calendar.id else base - calendar.id
                                // An empty set means "all calendars"; keep at least one selected.
                                val all = calendars.map { it.id }.toSet()
                                idsAdapter.onChange(if (updated == all) emptySet() else updated.ifEmpty { base })
                            },
                            label = calendar.name,
                            description = calendar.account,
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun SmartspaceProviderPreference(
    adapter: PreferenceAdapter<SmartspaceMode>,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current

    val entries = remember {
        SmartspaceMode.values().map { mode ->
            ListPreferenceEntry(
                value = mode,
                label = { stringResource(id = mode.nameResourceId) },
                enabled = mode.isAvailable(context = context),
            )
        }.toList()
    }

    ListPreference(
        adapter = adapter,
        entries = entries,
        label = stringResource(id = R.string.smartspace_mode_label),
        modifier = modifier,
    )
}

@Composable
fun SmartspacePreview(
    modifier: Modifier = Modifier,
) {
    val themeRes = if (isSelectedThemeDark) R.style.AppTheme_Dark else R.style.AppTheme_DarkText
    val context = LocalContext.current
    val themedContext = remember(themeRes) { ContextThemeWrapper(context, themeRes) }

    PreferenceGroup(
        heading = stringResource(id = R.string.preview_label),
        modifier = modifier,
    ) {
        Surface(
            color = preferenceGroupColor(),
            modifier = Modifier.fillMaxWidth(),
        ) {
            val prefs2 = preferenceManager2()
            val widgetStyle = prefs2.smartspaceWidgetStyle.getAdapter().state.value
            val widgetSize = prefs2.smartspaceWidgetSize.getAdapter().state.value
            CompositionLocalProvider(LocalContext provides themedContext) {
                key(widgetStyle, widgetSize) {
                AndroidView(
                    factory = {
                        val view = SmartspaceViewContainer(it, previewMode = true)
                        val height = if (widgetStyle == GLANCE_STYLE_MATERIAL) {
                            ViewGroup.LayoutParams.WRAP_CONTENT
                        } else {
                            it.resources.getDimensionPixelSize(R.dimen.enhanced_smartspace_height)
                        }
                        view.layoutParams = ViewGroup.LayoutParams(MATCH_PARENT, height)
                        view
                    },
                    modifier = Modifier.padding(
                        start = 8.dp,
                        end = 8.dp,
                        top = 8.dp,
                        bottom = 8.dp,
                    ),
                )
                }
            }
        }
        LaunchedEffect(key1 = null) {
            SmartspaceProvider.INSTANCE.get(context).startSetup(context as Activity)
        }
    }
}

@Composable
fun SmartspaceDateAndTimePreferences(
    modifier: Modifier = Modifier,
) {
    val preferenceManager2 = preferenceManager2()

    val calendarAdapter = preferenceManager2.smartspaceCalendar.getAdapter()
    val showDateAdapter = preferenceManager2.smartspaceShowDate.getAdapter()
    val showTimeAdapter = preferenceManager2.smartspaceShowTime.getAdapter()

    val calendarHasMinimumContent = !showDateAdapter.state.value || !showTimeAdapter.state.value
    val calendar = calendarAdapter.state.value

    PreferenceGroup(
        heading = stringResource(id = R.string.smartspace_date_and_time),
        modifier = modifier.padding(top = 8.dp),
    ) {
        val supportCustomizationFormat = calendar.formatCustomizationSupport
        ExpandAndShrink(visible = supportCustomizationFormat) {
            SwitchPreference(
                adapter = showDateAdapter,
                label = stringResource(id = R.string.smartspace_date),
                enabled = if (showDateAdapter.state.value) !calendarHasMinimumContent else true,
            )
        }
        ExpandAndShrink(visible = supportCustomizationFormat && showDateAdapter.state.value) {
            SmartspaceCalendarPreference()
        }
        ExpandAndShrink(visible = supportCustomizationFormat) {
            SwitchPreference(
                adapter = showTimeAdapter,
                label = stringResource(id = R.string.smartspace_time),
                enabled = if (showTimeAdapter.state.value) !calendarHasMinimumContent else true,
            )
        }
        ExpandAndShrink(visible = supportCustomizationFormat && showTimeAdapter.state.value) {
            SmartspaceTimeFormatPreference()
        }
        SmartspaceCalendarAppPreference()
    }
}

@Composable
fun SmartspaceTimeFormatPreference(
    modifier: Modifier = Modifier,
) {
    val entries = remember {
        SmartspaceTimeFormat.values().map { format ->
            ListPreferenceEntry(format) { stringResource(id = format.nameResourceId) }
        }
    }

    val adapter = preferenceManager2().smartspaceTimeFormat.getAdapter()

    ListPreference(
        adapter = adapter,
        entries = entries,
        label = stringResource(id = R.string.smartspace_time_format),
        modifier = modifier,
    )
}

@Composable
fun SmartspaceCalendarPreference(
    modifier: Modifier = Modifier,
) {
    val entries = remember {
        SmartspaceCalendar.values().map { calendar ->
            ListPreferenceEntry(calendar) { stringResource(id = calendar.nameResourceId) }
        }
    }

    val adapter = preferenceManager2().smartspaceCalendar.getAdapter()

    ListPreference(
        adapter = adapter,
        entries = entries,
        label = stringResource(id = R.string.smartspace_calendar),
        modifier = modifier,
    )
}

@Composable
fun SmartspacerSettings(
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val prefs2 = preferenceManager2()

    SliderPreference(
        label = stringResource(R.string.maximum_number_of_targets),
        adapter = prefs2.smartspacerMaxCount.getAdapter(),
        valueRange = 5..15,
        step = 1,
    )
    ClickablePreference(label = stringResource(R.string.open_smartspacer_settings), modifier = modifier) {
        val intent = context.packageManager.getLaunchIntentForPackage(
            SmartspacerConstants.SMARTSPACER_PACKAGE_NAME,
        ) ?: return@ClickablePreference
        context.startActivity(intent)
    }
}

/** Which app tapping the date opens: the default calendar or any app. */
@Composable
private fun SmartspaceCalendarAppPreference(modifier: Modifier = Modifier) {
    SmartspaceAppPreference(
        adapter = preferenceManager2().smartspaceCalendarApp.getAdapter(),
        label = stringResource(id = R.string.smartspace_calendar_app),
        defaultLabel = stringResource(id = R.string.smartspace_calendar_app_default),
        modifier = modifier,
    )
}

/** Picks an app to open, or the default ([defaultLabel]) when empty. */
@Composable
private fun SmartspaceAppPreference(
    adapter: PreferenceAdapter<String>,
    label: String,
    defaultLabel: String,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val entries = remember(defaultLabel) {
        val launcherApps = context.getSystemService(android.content.pm.LauncherApps::class.java)
        val apps = runCatching {
            launcherApps.getActivityList(null, android.os.Process.myUserHandle())
                .distinctBy { it.componentName.packageName }
                .map { it.componentName.packageName to it.label.toString() }
                .sortedBy { it.second.lowercase() }
        }.getOrDefault(emptyList())
        listOf(ListPreferenceEntry("") { defaultLabel }) +
            apps.map { (pkg, appLabel) -> ListPreferenceEntry(pkg) { appLabel } }
    }
    ListPreference(
        adapter = adapter,
        entries = entries,
        label = label,
        modifier = modifier,
    )
}
