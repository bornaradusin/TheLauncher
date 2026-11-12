package app.thelauncher.smartspace

import android.content.Context
import app.thelauncher.preferences2.PreferenceManager2
import app.thelauncher.preferences2.firstCached
import app.thelauncher.smartspace.model.GLANCE_SIZE_LARGE
import app.thelauncher.smartspace.model.GLANCE_STYLE_MATERIAL
import app.thelauncher.smartspace.model.TheLauncherSmartspace

/** How many rows of the first home screen At a Glance takes. */
object GlanceGrid {

    /** The large widget takes two rows; everything else one. */
    @JvmStatic
    fun rows(context: Context): Int {
        val prefs = PreferenceManager2.getInstance(context)
        val large = prefs.smartspaceMode.firstCached() == TheLauncherSmartspace &&
            prefs.smartspaceWidgetStyle.firstCached() == GLANCE_STYLE_MATERIAL &&
            prefs.smartspaceWidgetSize.firstCached() == GLANCE_SIZE_LARGE
        return if (large) 2 else 1
    }
}
