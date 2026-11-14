package app.thelauncher.widgets

import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import com.android.launcher3.BuildConfig

/*
 * TheLauncher's own widgets. Each is listed in the widget list like any other widget, and drawn by
 * the launcher itself (see TheLauncherAppWidgetHostView), so it follows the launcher's colours and
 * font.
 */

class ClockWidgetProvider : AppWidgetProvider() {
    companion object {
        @JvmField val componentName = ComponentName(BuildConfig.APPLICATION_ID, ClockWidgetProvider::class.java.name)
    }
}

class WeatherWidgetProvider : AppWidgetProvider() {
    companion object {
        @JvmField val componentName = ComponentName(BuildConfig.APPLICATION_ID, WeatherWidgetProvider::class.java.name)
    }
}

class BatteryWidgetProvider : AppWidgetProvider() {
    companion object {
        @JvmField val componentName = ComponentName(BuildConfig.APPLICATION_ID, BatteryWidgetProvider::class.java.name)
    }
}

class MediaWidgetProvider : AppWidgetProvider() {
    companion object {
        @JvmField val componentName = ComponentName(BuildConfig.APPLICATION_ID, MediaWidgetProvider::class.java.name)
    }
}
