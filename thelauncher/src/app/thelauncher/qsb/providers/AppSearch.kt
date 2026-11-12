package app.thelauncher.qsb.providers

import app.thelauncher.animateToAllApps
import app.thelauncher.qsb.ThemingMethod
import com.android.launcher3.Launcher
import com.android.launcher3.R

data object AppSearch : QsbSearchProvider(
    id = "app_search",
    name = R.string.search_provider_app_search,
    icon = R.drawable.ic_qsb_search,
    themingMethod = ThemingMethod.TINT,
    packageName = "",
    website = "",
    type = QsbSearchProviderType.LOCAL,
) {
    override suspend fun launch(launcher: Launcher, forceWebsite: Boolean) {
        launcher.animateToAllApps()
        val searchUiManager = launcher.appsView.searchUiManager
        searchUiManager.setDirectFocus(true)
        searchUiManager.editText?.showKeyboard()
    }
}
