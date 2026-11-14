package app.thelauncher

import android.appwidget.AppWidgetProviderInfo
import android.content.ComponentName
import android.content.Context
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import android.widget.RemoteViews
import app.thelauncher.preferences2.PreferenceManager2
import app.thelauncher.preferences2.firstCached
import app.thelauncher.smartspace.GlanceWidgetView
import app.thelauncher.smartspace.SmartspaceAppWidgetProvider
import app.thelauncher.smartspace.model.GLANCE_STYLE_MATERIAL
import app.thelauncher.widgets.BatteryWidgetProvider
import app.thelauncher.widgets.BatteryWidgetView
import app.thelauncher.widgets.ClockWidgetProvider
import app.thelauncher.widgets.ClockWidgetView
import app.thelauncher.widgets.MediaWidgetProvider
import app.thelauncher.widgets.MediaWidgetView
import app.thelauncher.widgets.WeatherWidgetProvider
import app.thelauncher.widgets.WeatherWidgetView
import com.android.launcher3.R
import com.android.launcher3.util.Themes
import com.android.launcher3.widget.LauncherAppWidgetHostView

class TheLauncherAppWidgetHostView @JvmOverloads constructor(
    context: Context,
    private var previewMode: Boolean = false,
) : LauncherAppWidgetHostView(context) {

    private var customView: ViewGroup? = null

    override fun setAppWidget(appWidgetId: Int, info: AppWidgetProviderInfo) {
        inflateCustomView(info)
        super.setAppWidget(appWidgetId, info)
    }

    fun disablePreviewMode() {
        previewMode = false
        inflateCustomView(appWidgetInfo)
    }

    private fun inflateCustomView(info: AppWidgetProviderInfo) {
        customView = inflateCustomView(context, info, previewMode)
        if (customView == null) {
            return
        }
        customView!!.setOnLongClickListener(this)
        removeAllViews()
        addView(customView, MATCH_PARENT, MATCH_PARENT)
    }

    override fun updateAppWidget(remoteViews: RemoteViews?) {
        if (customView != null) return
        super.updateAppWidget(remoteViews)
    }

    override fun getDefaultView(): View {
        if (customView != null) return getEmptyView()
        return super.getDefaultView()
    }

    override fun getErrorView(): View {
        if (customView != null) return getEmptyView()
        return super.getErrorView()
    }

    private fun getEmptyView(): View {
        return View(context)
    }

    companion object {

        private val customLayouts = mapOf(
            SmartspaceAppWidgetProvider.componentName to R.layout.smartspace_widget,
        )

        /** TheLauncher's own widgets that are built in code instead of from a layout. */
        private fun createOwnWidget(context: Context, provider: ComponentName?): ViewGroup? = when (provider) {
            ClockWidgetProvider.componentName -> ClockWidgetView(context)
            WeatherWidgetProvider.componentName -> WeatherWidgetView(context)
            BatteryWidgetProvider.componentName -> BatteryWidgetView(context)
            MediaWidgetProvider.componentName -> MediaWidgetView(context)
            else -> null
        }

        private val ownWidgets = setOf(
            ClockWidgetProvider.componentName,
            WeatherWidgetProvider.componentName,
            BatteryWidgetProvider.componentName,
            MediaWidgetProvider.componentName,
        )

        @JvmStatic
        fun inflateCustomView(context: Context, info: AppWidgetProviderInfo, previewMode: Boolean): ViewGroup? {
            val ownWidget = info.provider in ownWidgets
            val layoutId = customLayouts[info.provider]
            if (!ownWidget && layoutId == null) return null

            val inflationContext = if (previewMode) Themes.createWidgetPreviewContext(context) else context
            if (ownWidget || layoutId == null) {
                // A widget that can't be built leaves the plain card rather than stopping the launcher.
                return runCatching { createOwnWidget(inflationContext, info.provider) }
                    .onFailure { Log.e("TheLauncherWidgets", "Couldn't build ${info.provider}", it) }
                    .getOrNull()
            }
            // At a Glance as the Material card, sized like any other widget.
            if (info.provider == SmartspaceAppWidgetProvider.componentName &&
                PreferenceManager2.getInstance(context).smartspaceWidgetStyle.firstCached() == GLANCE_STYLE_MATERIAL
            ) {
                return GlanceWidgetView(inflationContext, appWidget = true).also { it.previewMode = previewMode }
            }
            return LayoutInflater.from(inflationContext)
                .inflate(layoutId, null, false) as ViewGroup
        }
    }
}
