package app.thelauncher.allapps

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager

const val HIDDEN_DASHBOARDS_NONE = "none"
const val HIDDEN_DASHBOARDS_ICON_PACKS = "icon_packs"
const val HIDDEN_DASHBOARDS_THEMES = "themes"
const val HIDDEN_DASHBOARDS_ALL = "all"

/** Icon packs announce themselves to launchers with these actions. */
private val iconPackActions = listOf(
    "org.adw.launcher.THEMES",
    "com.gau.go.launcherex.theme",
    "com.novalauncher.THEME",
    "com.teslacoilsw.launcher.THEME",
)

/**
 * The packages whose own app ("dashboard") should be hidden from the drawer: icon packs, Substratum
 * themes, or both.
 */
fun dashboardPackages(context: Context, mode: String): Set<String> {
    if (mode == HIDDEN_DASHBOARDS_NONE) return emptySet()
    val pm = context.packageManager
    val packages = mutableSetOf<String>()
    if (mode == HIDDEN_DASHBOARDS_ICON_PACKS || mode == HIDDEN_DASHBOARDS_ALL) {
        iconPackActions.forEach { action ->
            runCatching {
                pm.queryIntentActivities(Intent(action), PackageManager.GET_META_DATA)
                    .mapTo(packages) { it.activityInfo.packageName }
            }
        }
    }
    if (mode == HIDDEN_DASHBOARDS_THEMES || mode == HIDDEN_DASHBOARDS_ALL) {
        runCatching {
            pm.getInstalledApplications(PackageManager.GET_META_DATA)
                .filter { it.metaData?.containsKey("Substratum_Name") == true }
                .mapTo(packages) { it.packageName }
        }
    }
    packages.remove(context.packageName)
    return packages
}
