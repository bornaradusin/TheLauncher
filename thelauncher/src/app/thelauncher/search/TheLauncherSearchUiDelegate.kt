package app.thelauncher.search

import com.android.launcher3.allapps.ActivityAllAppsContainerView
import com.android.launcher3.allapps.search.AllAppsSearchUiDelegate
import com.android.launcher3.allapps.search.SearchAdapterProvider
import com.android.launcher3.views.ActivityContext

class TheLauncherSearchUiDelegate(private val appsView: ActivityAllAppsContainerView<*>) : AllAppsSearchUiDelegate(appsView) {

    override fun createMainAdapterProvider(): SearchAdapterProvider<*> {
        return TheLauncherSearchAdapterProvider(ActivityContext.lookupContext(appsView.context), appsView)
    }
}
