package app.thelauncher.ui.preferences.components.search

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.thelauncher.preferences.PreferenceManager
import app.thelauncher.preferences.getAdapter
import app.thelauncher.preferences.not
import app.thelauncher.preferences.preferenceManager
import app.thelauncher.preferences2.PreferenceManager2
import app.thelauncher.preferences2.preferenceManager2
import app.thelauncher.search.algorithms.TheLauncherSearchAlgorithm
import app.thelauncher.search.algorithms.engine.provider.web.CustomWebSearchProvider
import app.thelauncher.ui.preferences.LocalNavController
import app.thelauncher.ui.preferences.components.HiddenAppsInSearchPreference
import app.thelauncher.ui.preferences.components.controls.ListPreference
import app.thelauncher.ui.preferences.components.controls.ListPreferenceEntry
import app.thelauncher.ui.preferences.components.controls.MainSwitchPreference
import app.thelauncher.ui.preferences.components.controls.SwitchPreference
import app.thelauncher.ui.preferences.components.controls.TwoTargetSwitchPreference
import app.thelauncher.ui.preferences.components.layout.PreferenceGroup
import app.thelauncher.ui.preferences.navigation.SearchProviderPreference
import app.thelauncher.util.FileAccessManager
import com.android.launcher3.R
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.isGranted
import com.google.accompanist.permissions.rememberPermissionState

@Composable
fun DrawerSearchPreference(
    modifier: Modifier = Modifier,
) {
    val prefs = preferenceManager()
    val prefs2 = preferenceManager2()
    val context = LocalContext.current

    val showDrawerSearchBar = !prefs2.hideAppDrawerSearchBar.getAdapter()
    val hiddenApps = prefs2.hiddenApps.getAdapter().state.value

    MainSwitchPreference(
        adapter = showDrawerSearchBar,
        label = stringResource(id = R.string.show_app_search_bar),
        modifier = modifier,
    ) {
        // The search finds apps, Seerr and phone settings only, so there are no result types to pick.
        PreferenceGroup(
            heading = stringResource(R.string.general_label),
            description = stringResource(id = R.string.drawer_search_finds),
        ) {
            if (hiddenApps.isNotEmpty()) {
                HiddenAppsInSearchPreference()
            }
            SwitchPreference(
                adapter = prefs2.autoShowKeyboardInDrawer.getAdapter(),
                label = stringResource(id = R.string.pref_search_auto_show_keyboard),
            )
            SwitchPreference(
                label = stringResource(R.string.allapps_match_qsb_style_label),
                description = stringResource(R.string.allapps_match_qsb_style_description),
                adapter = prefs2.matchHotseatQsbStyle.getAdapter(),
            )
            SwitchPreference(
                label = stringResource(R.string.drawer_qsb_provider_icons),
                description = stringResource(R.string.drawer_qsb_provider_icons_desc),
                adapter = prefs2.drawerQsbProviderIcons.getAdapter(),
            )
            QsbEndButtonPreference()
            app.thelauncher.ui.preferences.components.colorpreference.ColorPreference(preference = prefs2.drawerQsbColor)
        }

    }
}

@Composable
private fun ASISearchSettings(prefs: PreferenceManager) {
    SwitchPreference(
        adapter = prefs.searchResultShortcuts.getAdapter(),
        label = stringResource(id = R.string.search_pref_result_shortcuts_title),
    )
    SwitchPreference(
        adapter = prefs.searchResultPeople.getAdapter(),
        label = stringResource(id = R.string.search_pref_result_people_title),
    )
    SwitchPreference(
        adapter = prefs.searchResultPixelTips.getAdapter(),
        label = stringResource(id = R.string.search_pref_result_tips_title),
    )
    SwitchPreference(
        adapter = prefs.searchResultSettings.getAdapter(),
        label = stringResource(id = R.string.search_pref_result_settings_title),
    )
}

@Composable
private fun SearchProvider(
    context: Context,
) {
    val searchAlgorithmEntries = remember {
        sequenceOf(
            ListPreferenceEntry(TheLauncherSearchAlgorithm.APP_SEARCH) { stringResource(R.string.search_algorithm_app_search) },
            ListPreferenceEntry(TheLauncherSearchAlgorithm.LOCAL_SEARCH) { stringResource(R.string.search_algorithm_global_search_on_device) },
            ListPreferenceEntry(TheLauncherSearchAlgorithm.ASI_SEARCH) { stringResource(R.string.search_algorithm_global_search_via_asi) },
        ).filter {
            when (it.value) {
                TheLauncherSearchAlgorithm.ASI_SEARCH -> TheLauncherSearchAlgorithm.isASISearchEnabled(
                    context,
                )

                else -> true
            }
        }.toList()
    }

    ListPreference(
        adapter = preferenceManager2().searchAlgorithm.getAdapter(),
        entries = searchAlgorithmEntries,
        label = stringResource(R.string.app_search_algorithm),
    )
}

@OptIn(ExperimentalPermissionsApi::class)
@Composable
private fun LocalSearchSettings(
    prefs: PreferenceManager,
    prefs2: PreferenceManager2,
    context: Context,
) {
    val navController = LocalNavController.current
    val webSuggestionProvider =
        stringResource(prefs2.webSuggestionProvider.getAdapter().state.value.label)

    TwoTargetSwitchPreference(
        adapter = prefs.searchResultStartPageSuggestion.getAdapter(),
        label = stringResource(id = R.string.search_pref_result_web_title),
        description = if (webSuggestionProvider == stringResource(CustomWebSearchProvider.label)) {
            webSuggestionProvider
        } else {
            stringResource(
                id = R.string.search_pref_result_web_provider_description,
                webSuggestionProvider,
            )
        },
        onClick = {
            navController.navigate(SearchProviderPreference(SearchProviderId.WEB))
        },
    )
    val peopleAdapter = prefs.searchResultPeople.getAdapter()
    val peopleEnabled = rememberPermissionState(android.Manifest.permission.READ_CONTACTS).status.isGranted
    TwoTargetSwitchPreference(
        checked = peopleEnabled && peopleAdapter.state.value,
        onCheckedChange = peopleAdapter::onChange,
        switchEnabled = peopleEnabled,
        label = stringResource(id = R.string.search_pref_result_people_title),
        description = stringResource(id = R.string.search_pref_result_contacts_description),
        onClick = {
            navController.navigate(SearchProviderPreference(SearchProviderId.CONTACTS))
        },
    )
    val filesAdapter = prefs.searchResultFilesToggle.getAdapter()
    val filesEnabled = remember { FileAccessManager.getInstance(context) }.hasAnyPermission.collectAsStateWithLifecycle().value
    TwoTargetSwitchPreference(
        checked = filesEnabled && filesAdapter.state.value,
        onCheckedChange = filesAdapter::onChange,
        switchEnabled = filesEnabled,
        label = stringResource(R.string.search_pref_result_files_title),
        description = stringResource(R.string.search_pref_result_files_description),
        onClick = {
            navController.navigate(SearchProviderPreference(SearchProviderId.FILES))
        },
    )
    TwoTargetSwitchPreference(
        adapter = prefs.searchResultSettingsEntry.getAdapter(),
        label = stringResource(id = R.string.search_pref_result_settings_title),
        onClick = {
            navController.navigate(SearchProviderPreference(SearchProviderId.SETTINGS))
        },
    )
    TwoTargetSwitchPreference(
        adapter = prefs.searchResulRecentSuggestion.getAdapter(),
        label = stringResource(id = R.string.search_pref_result_history_title),
        onClick = {
            navController.navigate(SearchProviderPreference(SearchProviderId.HISTORY))
        },
    )
    SwitchPreference(
        adapter = prefs.searchResultCalculator.getAdapter(),
        label = stringResource(R.string.all_apps_search_result_calculator),
    )
}
