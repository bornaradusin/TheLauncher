package app.thelauncher.allapps

import android.content.Context
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.lifecycleScope
import app.thelauncher.data.folder.FolderEntry
import app.thelauncher.data.folder.model.FolderViewModel
import app.thelauncher.extras.DrawerTab
import app.thelauncher.launcher
import app.thelauncher.preferences.PreferenceManager
import app.thelauncher.preferences2.PreferenceManager2
import app.thelauncher.preferences2.firstCached
import app.thelauncher.util.categorizeAppsWithSystemAndGoogle
import app.thelauncher.util.observeOnce
import com.android.launcher3.InvariantDeviceProfile.OnIDPChangeListener
import com.android.launcher3.allapps.AllAppsStore
import com.android.launcher3.allapps.AlphabeticalAppsList
import com.android.launcher3.allapps.BaseAllAppsAdapter.AdapterItem
import com.android.launcher3.allapps.PrivateProfileManager
import com.android.launcher3.allapps.WorkProfileManager
import com.android.launcher3.model.data.AppInfo
import com.android.launcher3.model.data.FolderInfo
import com.android.launcher3.allapps.AlphabeticalAppsList.FastScrollSectionInfo
import com.android.launcher3.model.data.ItemInfo
import com.android.launcher3.util.ComponentKey
import com.android.launcher3.views.ActivityContext
import com.patrykmichalik.opto.core.onEach
import java.util.function.Predicate

@Suppress("SYNTHETIC_PROPERTY_WITHOUT_JAVA_ORIGIN")
class TheLauncherAlphabeticalAppsList<T>(
    private val context: T,
    private val appsStore: AllAppsStore<T>,
    workProfileManager: WorkProfileManager?,
    privateProfileManager: PrivateProfileManager?,
) : AlphabeticalAppsList<T>(context, appsStore, workProfileManager, privateProfileManager),
    OnIDPChangeListener,
    DefaultLifecycleObserver
    where T : Context, T : ActivityContext {

    private var hiddenApps: Set<String> = setOf()
    private var hiddenDashboardPackages: Set<String> = setOf()

    private var drawerTabs: List<DrawerTab> = emptyList()
    private var removeTabAppsFromAll = false
    private var baseItemFilter: Predicate<ItemInfo>? = null
    var selectedTabIndex: Int = -1
        private set
    private val prefs2 = PreferenceManager2.getInstance(context)
    private val prefs = PreferenceManager.getInstance(context)

    private val viewModel = FolderViewModel(
        (context as? ComponentActivity)?.application ?: context.launcher.application,
    )
    private val folderList = mutableListOf<FolderEntry>()
    private val filteredList = mutableListOf<AppInfo>()

    init {
        context.launcher.deviceProfile.inv.addOnChangeListener(this)
        (context as? LifecycleOwner)?.lifecycle?.addObserver(this)
        try {
            prefs2.hiddenApps.onEach(launchIn = context.launcher.lifecycleScope) {
                hiddenApps = it
                onAppsUpdated()
            }
        } catch (t: Throwable) {
            Log.w(TAG, "Failed to initialize hidden apps", t)
        }
        try {
            prefs2.hiddenDashboards.onEach(launchIn = context.launcher.lifecycleScope) { mode ->
                hiddenDashboardPackages = dashboardPackages(context, mode)
                updateItemFilter(baseItemFilter)
            }
        } catch (t: Throwable) {
            Log.w(TAG, "Failed to initialize hidden dashboards", t)
        }
        try {
            prefs2.drawerTabs.onEach(launchIn = context.launcher.lifecycleScope) {
                drawerTabs = it
                if (selectedTabIndex >= it.size) selectedTabIndex = -1
                updateItemFilter(baseItemFilter)
            }
            prefs2.drawerTabsRemoveFromAll.onEach(launchIn = context.launcher.lifecycleScope) {
                removeTabAppsFromAll = it
                updateItemFilter(baseItemFilter)
            }
        } catch (t: Throwable) {
            Log.w(TAG, "Failed to initialize drawer tabs", t)
        }
        observeFolders()
    }

    override fun onDestroy(owner: LifecycleOwner) {
        context.launcher.deviceProfile.inv.removeOnChangeListener(this)
    }

    private fun observeFolders() {
        viewModel.folders.observeOnce(context as LifecycleOwner) { folders ->
            if (folders != null) {
                folderList.clear()
                folderList.addAll(folders)
                updateAdapterItems()
            }
        }
    }

    override fun updateItemFilter(itemFilter: Predicate<ItemInfo>?) {
        baseItemFilter = itemFilter
        mItemFilter = Predicate { info ->
            require(info is AppInfo) { "`info` must be an instance of `AppInfo`." }
            val componentKey = info.toComponentKey().toString()
            (itemFilter?.test(info) != false) &&
                !hiddenApps.contains(componentKey) &&
                info.componentName?.packageName !in hiddenDashboardPackages &&
                passesTabFilter(componentKey)
        }
        onAppsUpdated()
    }

    private fun passesTabFilter(componentKey: String): Boolean {
        val tab = drawerTabs.getOrNull(selectedTabIndex)
        if (tab != null) return componentKey in tab.apps
        if (removeTabAppsFromAll) return drawerTabs.none { componentKey in it.apps }
        return true
    }

    fun selectTab(index: Int) {
        val newIndex = if (index in drawerTabs.indices) index else -1
        if (newIndex == selectedTabIndex) return
        selectedTabIndex = newIndex
        updateItemFilter(baseItemFilter)
    }

    override fun addAppsWithSections(rawAppList: List<AppInfo?>?, startPosition: Int): Int {
        if (rawAppList.isNullOrEmpty()) return startPosition
        val appList: List<AppInfo?> = if (prefs2.hideDuplicateApps.firstCached()) {
            rawAppList.filterNotNull().distinctBy {
                "${it.targetComponent?.packageName}|${it.title}|${it.user.hashCode()}"
            }
        } else {
            rawAppList
        }
        val drawerListDefault = prefs.drawerList.get()
        filteredList.clear()
        var position = startPosition

        // Show app drawer folders only on main profile, to prevent state complexity
        if (isWorkOrPrivateSpace(appList)) return super.addAppsWithSections(appList, position)
        if (selectedTabIndex >= 0) return super.addAppsWithSections(appList, position)

        if (!drawerListDefault) {
            val validApps = appList.mapNotNull { it }
            val finalCategorizedApps = categorizeAppsWithSystemAndGoogle(validApps, context)

            finalCategorizedApps.forEach { (category, apps) ->
                if (apps.size == 1) {
                    mAdapterItems.add(AdapterItem.asApp(apps.first()))
                } else {
                    val folderInfo = FolderInfo().apply {
                        title = category
                        apps.forEach { add(it) }
                    }
                    mAdapterItems.add(AdapterItem.asFolder(folderInfo))
                }
                position++
            }
        } else {
            val drawerFolders = mutableListOf<FolderInfo>()
            folderList.forEach { folderEntry ->
                val resolvedApps = folderEntry.itemComponentKeys.mapNotNull { keyString ->
                    val componentKey = ComponentKey.fromString(keyString) ?: return@mapNotNull null
                    // Apps rename their launch activity in updates; fall back to the same app's
                    // current entry so it stays in its folder.
                    (appsStore.getApp(componentKey) as? AppInfo)
                        ?: appList.firstOrNull { app ->
                            app != null &&
                                app.componentName?.packageName == componentKey.componentName.packageName &&
                                app.user == componentKey.user
                        }
                }.distinct()

                if (resolvedApps.size > 1) {
                    val folderInfo = FolderInfo().apply {
                        id = folderEntry.id
                        title = folderEntry.title
                        resolvedApps.forEach { add(it) }
                    }
                    drawerFolders.add(folderInfo)

                    if (prefs.folderApps.get()) {
                        filteredList.addAll(resolvedApps)
                    }
                }
            }
            val remainingApps = appList.filterNot { app -> filteredList.contains(app) && prefs.folderApps.get() }
            if (prefs2.drawerFoldersFirst.firstCached()) {
                drawerFolders.forEach {
                    mAdapterItems.add(AdapterItem.asFolder(it))
                    position++
                }
                position = super.addAppsWithSections(remainingApps, position)
            } else {
                position = addAppsAndFoldersByName(remainingApps.filterNotNull(), drawerFolders, position)
            }
        }

        return position
    }

    /** Lists drawer folders among the apps by name, as Hyperion does unless "Show first" is on. */
    private fun addAppsAndFoldersByName(apps: List<AppInfo>, folders: List<FolderInfo>, startPosition: Int): Int {
        val collator = java.text.Collator.getInstance()
        val sortedFolders = folders.sortedWith { a, b -> collator.compare(a.title?.toString().orEmpty(), b.title?.toString().orEmpty()) }
        var position = startPosition
        var nextFolder = 0
        var lastSectionName: String? = null
        fun addSection(name: String?) {
            if (name != null && name != lastSectionName) {
                lastSectionName = name
                fastScrollerSections.add(FastScrollSectionInfo(name, position))
            }
        }
        apps.forEach { app ->
            val appTitle = app.title?.toString().orEmpty()
            while (nextFolder < sortedFolders.size &&
                collator.compare(sortedFolders[nextFolder].title?.toString().orEmpty(), appTitle) <= 0
            ) {
                addSection(app.sectionName)
                mAdapterItems.add(AdapterItem.asFolder(sortedFolders[nextFolder++]))
                position++
            }
            addSection(app.sectionName)
            mAdapterItems.add(AdapterItem.asApp(app))
            position++
        }
        while (nextFolder < sortedFolders.size) {
            mAdapterItems.add(AdapterItem.asFolder(sortedFolders[nextFolder++]))
            position++
        }
        return position
    }

    override fun onIdpChanged(modelPropertiesChanged: Boolean) {
        onAppsUpdated()
    }
}
