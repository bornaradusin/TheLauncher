package app.thelauncher.ui.preferences.navigation

import app.thelauncher.ui.preferences.destinations.ColorsCategory
import app.thelauncher.ui.preferences.destinations.IconographyCategory
import app.thelauncher.ui.preferences.destinations.TypographyCategory
import app.thelauncher.ui.preferences.destinations.InterfaceCategory
import app.thelauncher.ui.preferences.destinations.GridsCategory
import app.thelauncher.ui.preferences.destinations.WidgetsCategory
import app.thelauncher.ui.preferences.destinations.AnimationsCategory
import app.thelauncher.ui.preferences.destinations.IntegrationsCategory
import android.content.Intent
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.LayoutDirection
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.navDeepLink
import androidx.navigation.toRoute
import app.thelauncher.backup.ui.CreateBackupScreen
import app.thelauncher.backup.ui.restoreBackupGraph
import app.thelauncher.backup.ui.restoreHyperionBackupGraph
import app.thelauncher.backup.ui.restoreNovaBackupGraph
import app.thelauncher.preferences.BasePreferenceManager
import app.thelauncher.preferences.preferenceManager
import app.thelauncher.ui.preferences.LocalIsExpandedScreen
import app.thelauncher.ui.preferences.about.About
import app.thelauncher.ui.preferences.about.acknowledgements.Acknowledgements
import app.thelauncher.ui.preferences.components.colorpreference.ColorPreferenceModelList
import app.thelauncher.ui.preferences.components.colorpreference.ColorSelection
import app.thelauncher.ui.preferences.components.search.SearchProviderId
import app.thelauncher.ui.preferences.components.search.SearchProviderPreferenceScreen
import app.thelauncher.ui.preferences.destinations.AppDrawerFoldersPreference
import app.thelauncher.ui.preferences.destinations.AppDrawerPreferences
import app.thelauncher.ui.preferences.destinations.BackupAndRestorePreference
import app.thelauncher.ui.preferences.destinations.CustomIconShapePreference
import app.thelauncher.ui.preferences.destinations.DebugMenuPreferences
import app.thelauncher.ui.preferences.destinations.DismissedPredictionAppsPreferences
import app.thelauncher.ui.preferences.destinations.DockPreferences
import app.thelauncher.ui.preferences.destinations.DummyPreference
import app.thelauncher.ui.preferences.destinations.ExperimentalFeaturesPreferences
import app.thelauncher.ui.preferences.destinations.FeatureFlagsPreference
import app.thelauncher.ui.preferences.destinations.FolderPreferences
import app.thelauncher.ui.preferences.destinations.FontSelection
import app.thelauncher.ui.preferences.destinations.GeneralPreferences
import app.thelauncher.ui.preferences.destinations.GesturePreferences
import app.thelauncher.ui.preferences.destinations.HiddenAppsPreferences
import app.thelauncher.ui.preferences.destinations.LockedAppsPreferences
import app.thelauncher.ui.preferences.destinations.DrawerTabAppsPreferences
import app.thelauncher.ui.preferences.destinations.DrawerTabsPreferences
import app.thelauncher.ui.preferences.destinations.HomeScreenGridPreferences
import app.thelauncher.ui.preferences.destinations.HomeScreenPreferences
import app.thelauncher.ui.preferences.destinations.IconPickerPreference
import app.thelauncher.ui.preferences.destinations.LauncherPopupPreference
import app.thelauncher.ui.preferences.destinations.PickAppForGesture
import app.thelauncher.ui.preferences.destinations.PredictionsPreferences
import app.thelauncher.ui.preferences.destinations.PreferencesDashboard
import app.thelauncher.ui.preferences.destinations.QuickstepPreferences
import app.thelauncher.ui.preferences.destinations.SearchPreferences
import app.thelauncher.ui.preferences.destinations.SearchProviderPreferences
import app.thelauncher.ui.preferences.destinations.SelectAppsForDrawerFolder
import app.thelauncher.ui.preferences.destinations.SelectIconPreference
import app.thelauncher.ui.preferences.destinations.ShapePreference
import app.thelauncher.ui.preferences.destinations.SmartspacePreferences
import app.thelauncher.ui.preferences.destinations.icons.AdvancedIconPreferences
import app.thelauncher.ui.preferences.destinations.icons.IconPackPreferences
import app.thelauncher.ui.preferences.destinations.icons.ThemedIconsPreferences
import com.android.launcher3.util.ComponentKey
import soup.compose.material.motion.animation.materialSharedAxisXIn
import soup.compose.material.motion.animation.materialSharedAxisXOut
import soup.compose.material.motion.animation.rememberSlideDistance

inline fun <reified T> getDeepLink(route: T) where T : PreferenceRoute, T : PreferenceDeepLink = listOf(navDeepLink<T>(basePath = route.deepLink))

@Composable
fun PreferenceNavigation(
    navController: NavHostController,
    startDestination: PreferenceRoute,
    intent: Intent? = null,
) {
    val isRtl = LocalLayoutDirection.current == LayoutDirection.Rtl
    val slideDistance = rememberSlideDistance()

    LaunchedEffect(intent) {
        intent?.let { navController.handleDeepLink(it) }
    }

    // TODO: navigate to nav3: https://developer.android.com/guide/navigation/navigation-3
    NavHost(
        navController = navController,
        startDestination = startDestination,
        enterTransition = { materialSharedAxisXIn(!isRtl, slideDistance) },
        exitTransition = { materialSharedAxisXOut(!isRtl, slideDistance) },
        popEnterTransition = { materialSharedAxisXIn(isRtl, slideDistance) },
        popExitTransition = { materialSharedAxisXOut(isRtl, slideDistance) },
        predictivePopEnterTransition = { materialSharedAxisXIn(isRtl, slideDistance) },
        predictivePopExitTransition = { materialSharedAxisXOut(isRtl, slideDistance) },
    ) {
        composable<Root> {
            val isExpandedScreen = LocalIsExpandedScreen.current

            PreferencesDashboard(
                currentRoute = Root,
                onNavigate = {
                    navController.navigate(it)
                },
            )

            LaunchedEffect(isExpandedScreen) {
                if (isExpandedScreen) {
                    navController.navigate(General) {
                        launchSingleTop = true
                        popUpTo(navController.graph.id)
                    }
                }
            }
        }
        composable<Dummy> {
            DummyPreference()
        }

        composable<CategoryColors> { ColorsCategory() }
        composable<CategoryIconography> { IconographyCategory() }
        composable<CategoryTypography> { TypographyCategory() }
        composable<CategoryInterface> { InterfaceCategory() }
        composable<CategoryGrids> { GridsCategory() }
        composable<CategoryWidgets> { WidgetsCategory() }
        composable<CategoryAnimations> { AnimationsCategory() }
        composable<CategoryIntegrations> { IntegrationsCategory() }
        composable<SettingsSection> { backStackEntry ->
            val route: SettingsSection = backStackEntry.toRoute()
            val headings = route.headings.split(',').mapNotNull { it.trim().toIntOrNull() }
                .map { androidx.compose.ui.res.stringResource(it) }.toSet()
            androidx.compose.runtime.CompositionLocalProvider(
                app.thelauncher.ui.preferences.components.layout.LocalSectionFilter provides
                    app.thelauncher.ui.preferences.components.layout.SectionFilter(
                        title = androidx.compose.ui.res.stringResource(route.title),
                        headings = headings.ifEmpty { null },
                        showUntitled = route.untitled,
                    ),
            ) {
                when (route.screen) {
                    "general" -> GeneralPreferences()
                    "home" -> HomeScreenPreferences()
                    "dock" -> DockPreferences()
                    "drawer" -> AppDrawerPreferences()
                    "folders" -> FolderPreferences()
                }
            }
        }
        composable<General>(
            deepLinks = getDeepLink(General),
        ) { GeneralPreferences() }
        composable<GeneralFontSelection> { backStackEntry ->
            val route: GeneralFontSelection = backStackEntry.toRoute()
            if (route.prefKey == app.thelauncher.ui.preferences.components.FONT_EVERYTHING_ELSE) {
                val prefs = preferenceManager()
                FontSelection(prefs.fontHeading, linked = listOf(prefs.fontHeadingMedium, prefs.fontBody, prefs.fontBodyMedium))
                return@composable
            }
            val pref = preferenceManager().prefsMap[route.prefKey]
                as? BasePreferenceManager.FontPref ?: return@composable
            FontSelection(pref)
        }
        composable<GeneralIconPack>(
            deepLinks = getDeepLink(GeneralIconPack),
        ) { IconPackPreferences() }
        composable<GeneralThemedIcons>(
            deepLinks = getDeepLink(GeneralThemedIcons),
        ) { ThemedIconsPreferences() }
        composable<GeneralAdvancedIcons>(
            deepLinks = getDeepLink(GeneralAdvancedIcons),
        ) { AdvancedIconPreferences() }
        composable<GeneralIconShape> { backStackEntry ->
            val route: GeneralIconShape = backStackEntry.toRoute()
            ShapePreference(currentTab = route.selectedId)
        }
        composable<GeneralCustomIconShapeCreator>(
            deepLinks = getDeepLink(GeneralCustomIconShapeCreator()),
        ) { backStackEntry ->
            val route: GeneralCustomIconShapeCreator = backStackEntry.toRoute()
            CustomIconShapePreference(currentTab = route.selectedId)
        }

        composable<HomeScreen>(
            deepLinks = getDeepLink(HomeScreen),
        ) { HomeScreenPreferences() }
        composable<HomeScreenGrid>(
            deepLinks = getDeepLink(HomeScreenGrid),
        ) { HomeScreenGridPreferences() }
        composable<HomeScreenPopupEditor>(
            deepLinks = getDeepLink(HomeScreenPopupEditor),
        ) { LauncherPopupPreference() }

        composable<Dock>(
            deepLinks = getDeepLink(Dock),
        ) { DockPreferences() }
        composable<DockSearchProvider>(
            deepLinks = getDeepLink(DockSearchProvider),
        ) { SearchProviderPreferences() }

        composable<Smartspace>(
            deepLinks = getDeepLink(Smartspace),
        ) { SmartspacePreferences(fromWidget = false) }
        composable<SmartspaceWidget> { SmartspacePreferences(fromWidget = true) }

        composable<AppDrawer>(
            deepLinks = getDeepLink(AppDrawer),
        ) { AppDrawerPreferences() }
        composable<AppDrawerHiddenApps>(
            deepLinks = getDeepLink(AppDrawerHiddenApps),
        ) { HiddenAppsPreferences() }
        composable<AppDrawerLockedApps>(
            deepLinks = getDeepLink(AppDrawerLockedApps),
        ) { LockedAppsPreferences() }
        composable<AppDrawerTabs>(
            deepLinks = getDeepLink(AppDrawerTabs),
        ) { DrawerTabsPreferences() }
        composable<AppDrawerTabApps> { backStackEntry ->
            DrawerTabAppsPreferences(backStackEntry.arguments!!.getInt("index"))
        }
        composable<AppDrawerAppListToFolder> { backStackEntry ->
            val args = backStackEntry.arguments!!
            val folderInfoId = args.getInt("id")
            SelectAppsForDrawerFolder(folderInfoId)
        }
        composable<AppDrawerFolder>(
            deepLinks = getDeepLink(AppDrawerFolder),
        ) { AppDrawerFoldersPreference() }

        composable<Search>(
            deepLinks = getDeepLink(Search()),
        ) { backStackEntry ->
            val route: Search = backStackEntry.toRoute()
            SearchPreferences(currentTab = route.selectedId)
        }
        composable<SearchProviderPreference>(
            deepLinks = getDeepLink(SearchProviderPreference(SearchProviderId.entries.first())),
        ) { backStackEntry ->
            val route: SearchProviderPreference = backStackEntry.toRoute()
            SearchProviderPreferenceScreen(route.id)
        }

        composable<Folders>(
            deepLinks = getDeepLink(Folders),
        ) { FolderPreferences() }

        composable<Gestures>(
            deepLinks = getDeepLink(Gestures),
        ) { GesturePreferences() }
        composable<GesturesPickApp> { PickAppForGesture() }

        composable<Quickstep>(
            deepLinks = getDeepLink(Quickstep),
        ) { QuickstepPreferences() }
        composable<BackupAndRestore>(
            deepLinks = getDeepLink(BackupAndRestore),
        ) { BackupAndRestorePreference() }
        composable<SeerrSettings>(
            deepLinks = getDeepLink(SeerrSettings),
        ) { app.thelauncher.ui.preferences.destinations.SeerrPreferences() }

        composable<About>(
            deepLinks = getDeepLink(About),
        ) { About() }
        composable<AboutLicenses>(
            deepLinks = getDeepLink(AboutLicenses),
        ) { Acknowledgements() }

        composable<DebugMenu> { DebugMenuPreferences() }
        composable<FeatureFlags> { FeatureFlagsPreference() }

        composable<SelectIcon> { backStackEntry ->
            val args: SelectIcon = backStackEntry.toRoute()
            val componentKey = args.componentKey
            val key = ComponentKey.fromString(componentKey)!!
            SelectIconPreference(key)
        }
        composable<IconPicker> { backStackEntry ->
            val args: IconPicker = backStackEntry.toRoute()
            IconPickerPreference(packageName = args.packageName)
        }

        composable<ExperimentalFeatures>(
            deepLinks = getDeepLink(ExperimentalFeatures),
        ) { ExperimentalFeaturesPreferences() }
        composable<Predictions>(
            deepLinks = getDeepLink(Predictions),
        ) { PredictionsPreferences() }
        composable<DismissedPredictionApps> { DismissedPredictionAppsPreferences() }
        composable<ColorSelection> { backStackEntry ->
            val screen: ColorSelection = backStackEntry.toRoute()
            val modelList = ColorPreferenceModelList.INSTANCE.get(LocalContext.current)
            val model = modelList[screen.prefKey]
            ColorSelection(
                label = stringResource(id = model.labelRes),
                preference = model.prefObject,
                dynamicEntries = model.dynamicEntries,
            )
        }

        composable<CreateBackup>(
            deepLinks = getDeepLink(CreateBackup),
        ) { CreateBackupScreen(viewModel()) }

        restoreBackupGraph()
        restoreNovaBackupGraph()
        restoreHyperionBackupGraph()
    }
}
