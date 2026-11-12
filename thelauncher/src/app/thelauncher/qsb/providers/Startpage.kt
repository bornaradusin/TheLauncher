package app.thelauncher.qsb.providers

import app.thelauncher.qsb.ThemingMethod
import com.android.launcher3.R

data object Startpage : QsbSearchProvider(
    id = "startpage",
    name = R.string.search_provider_startpage,
    icon = R.drawable.ic_startpage,
    themingMethod = ThemingMethod.TINT,
    packageName = "com.startpage.app",
    className = "org.chromium.chrome.browser.searchwidget.SearchActivity",
    website = "https://startpage.com/",
    sponsored = false,
)
