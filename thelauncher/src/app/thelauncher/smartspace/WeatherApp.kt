package app.thelauncher.smartspace

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager

/**
 * The app At a Glance opens when the weather is tapped. It is always an app, never a web page:
 * the one picked in settings, else the phone's weather app, else the Google app's weather.
 */
object WeatherApp {
    private const val PIXEL_WEATHER = "com.google.android.apps.weather"
    private const val GSA_PACKAGE = "com.google.android.googlequicksearchbox"
    private const val GSA_WEATHER = "com.google.android.apps.search.weather.WeatherExportedActivity"
    private const val CATEGORY_APP_WEATHER = "android.intent.category.APP_WEATHER"

    /** Null when the phone has no weather app; the Google card's own tap is used then. */
    fun intent(context: Context, picked: String = ""): Intent? {
        val pm = context.packageManager
        picked.takeIf { it.isNotBlank() }?.let { pm.getLaunchIntentForPackage(it) }?.let { return it.newTask() }
        pm.getLaunchIntentForPackage(PIXEL_WEATHER)?.let { return it.newTask() }
        Intent.makeMainSelectorActivity(Intent.ACTION_MAIN, CATEGORY_APP_WEATHER)
            .takeIf { canStart(pm, it) }
            ?.let { return it.newTask() }
        Intent().setComponent(ComponentName(GSA_PACKAGE, GSA_WEATHER))
            .takeIf { canStart(pm, it) }
            ?.let { return it.newTask() }
        return null
    }

    private fun canStart(pm: PackageManager, intent: Intent): Boolean =
        runCatching { pm.resolveActivity(intent, 0)?.activityInfo?.exported == true }.getOrDefault(false)

    private fun Intent.newTask() = addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
}
