package app.thelauncher.ui.preferences.destinations

import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Animation
import androidx.compose.material.icons.rounded.Dashboard
import androidx.compose.material.icons.rounded.Extension
import androidx.compose.material.icons.rounded.Gesture
import androidx.compose.material.icons.rounded.Hexagon
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.material.icons.rounded.TextFields
import androidx.compose.material.icons.rounded.ViewAgenda
import androidx.compose.material.icons.rounded.Widgets
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import app.thelauncher.TheLauncherApp
import app.thelauncher.ui.preferences.LocalIsExpandedScreen
import app.thelauncher.ui.preferences.LocalNavController
import app.thelauncher.ui.preferences.components.layout.PreferenceLayout
import app.thelauncher.ui.preferences.navigation.About
import app.thelauncher.ui.preferences.navigation.AppDrawerLockedApps
import app.thelauncher.ui.preferences.navigation.AppDrawerTabs
import app.thelauncher.ui.preferences.navigation.BackupAndRestore
import app.thelauncher.ui.preferences.navigation.CategoryAnimations
import app.thelauncher.ui.preferences.navigation.CategoryColors
import app.thelauncher.ui.preferences.navigation.CategoryGrids
import app.thelauncher.ui.preferences.navigation.CategoryIconography
import app.thelauncher.ui.preferences.navigation.CategoryIntegrations
import app.thelauncher.ui.preferences.navigation.CategoryInterface
import app.thelauncher.ui.preferences.navigation.CategoryTypography
import app.thelauncher.ui.preferences.navigation.CategoryWidgets
import app.thelauncher.ui.preferences.navigation.ExperimentalFeatures
import app.thelauncher.ui.preferences.navigation.Gestures
import app.thelauncher.ui.preferences.navigation.HomeScreenGrid
import app.thelauncher.ui.preferences.navigation.Predictions
import app.thelauncher.ui.preferences.navigation.PreferenceRootRoute
import app.thelauncher.ui.preferences.navigation.PreferenceRoute
import app.thelauncher.ui.preferences.navigation.Quickstep
import app.thelauncher.ui.preferences.navigation.Search
import app.thelauncher.ui.preferences.navigation.SettingsSection
import app.thelauncher.ui.preferences.navigation.Smartspace
import com.android.launcher3.R

/** One row in the settings menus: an icon, a title and a short description. */
@Composable
fun SettingsRow(
    title: String,
    subtitle: String?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    selected: Boolean = false,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .then(if (selected) Modifier.background(MaterialTheme.colorScheme.secondaryContainer) else Modifier)
            .clickable(onClick = onClick)
            .padding(horizontal = 24.dp, vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) {
            Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(26.dp))
            Spacer(Modifier.width(24.dp))
        }
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(title, style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.onSurface)
            if (subtitle != null) {
                Text(subtitle, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

/** The main settings menu, laid out by category. */
data class SettingsCategory(
    @StringRes val title: Int,
    @StringRes val subtitle: Int,
    val icon: ImageVector,
    val route: PreferenceRootRoute,
)

fun settingsCategories(): List<SettingsCategory> = listOf(
    SettingsCategory(R.string.cat_colors, R.string.cat_colors_desc, Icons.Rounded.Palette, CategoryColors),
    SettingsCategory(R.string.cat_iconography, R.string.cat_iconography_desc, Icons.Rounded.Hexagon, CategoryIconography),
    SettingsCategory(R.string.cat_typography, R.string.cat_typography_desc, Icons.Rounded.TextFields, CategoryTypography),
    SettingsCategory(R.string.cat_interface, R.string.cat_interface_desc, Icons.Rounded.ViewAgenda, CategoryInterface),
    SettingsCategory(R.string.cat_grids, R.string.cat_grids_desc, Icons.Rounded.Dashboard, CategoryGrids),
    SettingsCategory(R.string.cat_widgets, R.string.cat_widgets_desc, Icons.Rounded.Widgets, CategoryWidgets),
    SettingsCategory(R.string.gestures_label, R.string.cat_gestures_desc, Icons.Rounded.Gesture, Gestures),
    SettingsCategory(R.string.cat_animations, R.string.cat_animations_desc, Icons.Rounded.Animation, CategoryAnimations),
    SettingsCategory(R.string.backup_and_restore_label, R.string.cat_backup_desc, Icons.Rounded.History, BackupAndRestore),
    SettingsCategory(R.string.cat_integrations, R.string.cat_integrations_desc, Icons.Rounded.Extension, CategoryIntegrations),
    SettingsCategory(R.string.about_label, R.string.cat_about_desc, Icons.Rounded.Info, About),
)

private data class Entry(@StringRes val title: Int, @StringRes val subtitle: Int?, val route: PreferenceRoute)

private fun section(screen: String, @StringRes title: Int, vararg headings: Int, untitled: Boolean = false) =
    SettingsSection(screen = screen, title = title, headings = headings.joinToString(","), untitled = untitled)

@Composable
private fun CategoryScreen(@StringRes label: Int, entries: List<Entry>) {
    val navController = LocalNavController.current
    PreferenceLayout(
        label = stringResource(label),
        backArrowVisible = !LocalIsExpandedScreen.current,
        verticalArrangement = Arrangement.Top,
    ) {
        entries.forEach { entry ->
            SettingsRow(
                title = stringResource(entry.title),
                subtitle = entry.subtitle?.let { stringResource(it) },
                onClick = { navController.navigate(entry.route) },
            )
        }
    }
}

@Composable
fun ColorsCategory() = CategoryScreen(
    R.string.cat_colors,
    listOf(
        Entry(R.string.cat_entry_theme, R.string.cat_entry_theme_desc, section("general", R.string.cat_entry_theme, R.string.colors)),
        Entry(R.string.notification_dots, R.string.cat_entry_dots_desc, section("general", R.string.notification_dots, R.string.notification_dots)),
        Entry(R.string.home_screen_label, R.string.cat_entry_wallpaper_desc, section("home", R.string.home_screen_label, R.string.wallpaper)),
        Entry(R.string.dock_label, R.string.cat_entry_style_desc, section("dock", R.string.dock_label, R.string.style)),
        Entry(R.string.app_drawer_label, R.string.cat_entry_style_desc, section("drawer", R.string.app_drawer_label, R.string.style)),
        Entry(R.string.folders_label, R.string.cat_entry_style_desc, section("folders", R.string.folders_label, R.string.general_label)),
    ),
)

@Composable
fun IconographyCategory() = CategoryScreen(
    R.string.cat_iconography,
    listOf(
        Entry(R.string.cat_entry_icon_pack, R.string.cat_entry_icon_pack_desc, section("general", R.string.cat_entry_icon_pack, R.string.icons)),
        Entry(R.string.home_screen_label, R.string.cat_entry_icons_desc, section("home", R.string.home_screen_label, R.string.icons)),
        Entry(R.string.dock_label, R.string.cat_entry_icons_desc, section("dock", R.string.dock_label, R.string.icons)),
        Entry(R.string.app_drawer_label, R.string.cat_entry_icons_desc, section("drawer", R.string.app_drawer_label, R.string.icons)),
        Entry(R.string.folders_label, R.string.cat_entry_icons_desc, section("folders", R.string.folders_label, R.string.icons)),
    ),
)

/** Typography holds only the fonts, so it opens them straight away. Label size and lines are under Iconography. */
@Composable
fun TypographyCategory() {
    val font = stringResource(R.string.font_label)
    androidx.compose.runtime.CompositionLocalProvider(
        app.thelauncher.ui.preferences.components.layout.LocalSectionFilter provides
            app.thelauncher.ui.preferences.components.layout.SectionFilter(
                title = stringResource(R.string.cat_typography),
                headings = setOf(font),
            ),
    ) {
        GeneralPreferences()
    }
}

@Composable
fun InterfaceCategory() = CategoryScreen(
    R.string.cat_interface,
    buildList {
        // Rotation, live updates and language had no way in from the phone menus.
        add(
            Entry(
                R.string.general_label,
                R.string.cat_entry_general_desc,
                section("general", R.string.general_label, R.string.updater, R.string.language_label, untitled = true),
            ),
        )
        add(
            Entry(
                R.string.home_screen_label,
                R.string.cat_entry_desktop_desc,
                section(
                    "home", R.string.home_screen_label,
                    R.string.general_label, R.string.page_indicator_label, R.string.layout,
                    R.string.home_screen_actions, R.string.popup_menu,
                    R.string.status_bar_label, R.string.widget_button_text,
                ),
            ),
        )
        add(
            Entry(
                R.string.dock_label,
                R.string.cat_entry_dock_desc,
                section("dock", R.string.dock_label, R.string.layout, R.string.search_bar_label),
            ),
        )
        add(
            Entry(
                R.string.app_drawer_label,
                R.string.cat_entry_drawer_desc,
                section("drawer", R.string.app_drawer_label, R.string.general_label, R.string.advanced, untitled = true),
            ),
        )
        add(Entry(R.string.drawer_tabs_label, R.string.cat_entry_tabs_desc, AppDrawerTabs))
        add(Entry(R.string.app_lock_label, R.string.cat_entry_lock_desc, AppDrawerLockedApps))
        add(Entry(R.string.folders_label, R.string.cat_entry_folders_desc, section("folders", R.string.folders_label, R.string.general_label)))
        if (TheLauncherApp.isRecentsEnabled) add(Entry(R.string.quickstep_label, R.string.quickstep_description, Quickstep))
    },
)

@Composable
fun GridsCategory() = CategoryScreen(
    R.string.cat_grids,
    listOf(
        Entry(R.string.home_screen_label, R.string.cat_entry_grid_desc, HomeScreenGrid),
        Entry(R.string.dock_label, R.string.cat_entry_grid_desc, section("dock", R.string.dock_label, R.string.grid)),
        Entry(R.string.app_drawer_label, R.string.cat_entry_grid_desc, section("drawer", R.string.app_drawer_label, R.string.grid)),
        Entry(R.string.folders_label, R.string.cat_entry_grid_desc, section("folders", R.string.folders_label, R.string.grid)),
    ),
)

@Composable
fun WidgetsCategory() = CategoryScreen(
    R.string.cat_widgets,
    listOf(
        Entry(R.string.smartspace_widget, R.string.smartspace_widget_description, Smartspace),
        Entry(R.string.search_bar_label, R.string.drawer_search_description, Search()),
        Entry(R.string.widget_button_text, R.string.cat_entry_widgets_desc, section("home", R.string.widget_button_text, R.string.widget_button_text)),
    ),
)

@Composable
fun AnimationsCategory() = CategoryScreen(
    R.string.cat_animations,
    listOf(
        Entry(R.string.home_screen_label, R.string.cat_entry_animations_desc, section("home", R.string.cat_animations, R.string.style)),
        Entry(R.string.wallpaper, R.string.cat_entry_wallpaper_motion_desc, section("home", R.string.wallpaper, R.string.wallpaper)),
    ),
)

@Composable
fun IntegrationsCategory() = CategoryScreen(
    R.string.cat_integrations,
    listOf(
        Entry(R.string.minus_one, R.string.cat_entry_feed_desc, section("home", R.string.minus_one, R.string.minus_one)),
        Entry(R.string.suggestion_pref_screen_title, R.string.cat_entry_suggestions_desc, Predictions),
        Entry(R.string.seerr_label, R.string.seerr_entry_desc, app.thelauncher.ui.preferences.navigation.SeerrSettings),
        Entry(R.string.experimental_features_label, R.string.cat_entry_debug_desc, ExperimentalFeatures),
    ),
)
