package app.thelauncher.search.algorithms

import app.thelauncher.search.algorithms.engine.SeerrSectionBuilder
import app.thelauncher.search.algorithms.engine.provider.SeerrSearchProvider
import android.content.Context
import app.thelauncher.search.adapter.SearchTargetCompat
import app.thelauncher.search.adapter.SearchTargetFactory
import app.thelauncher.search.algorithms.engine.AppsAndShortcutsSectionBuilder
import app.thelauncher.search.algorithms.engine.SearchProvider
import app.thelauncher.search.algorithms.engine.SearchResult
import app.thelauncher.search.algorithms.engine.SectionBuilder
import app.thelauncher.search.algorithms.engine.SettingsSectionBuilder
import app.thelauncher.search.algorithms.engine.provider.SettingsSearchProvider
import app.thelauncher.search.algorithms.engine.provider.apps.AppSearchProvider
import com.android.launcher3.LauncherAppState
import com.android.launcher3.allapps.BaseAllAppsAdapter
import com.android.launcher3.search.SearchCallback
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class TheLauncherLocalSearchAlgorithm(context: Context) : TheLauncherSearchAlgorithm(context) {

    private val appState = LauncherAppState.getInstance(context)

    private val coroutineScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var currentJob: Job? = null
    private var modelRetryJob: Job? = null

    private val appSearchProvider = AppSearchProvider

    // Apps, Seerr and phone settings, nothing else: no web, contacts, files, shortcuts,
    // calculator, history or "search on" actions.
    private val searchProviders: List<SearchProvider> = listOf(
        SettingsSearchProvider,
        SeerrSearchProvider,
    )

    override fun doSearch(query: String, callback: SearchCallback<BaseAllAppsAdapter.AdapterItem>) {
        modelRetryJob?.cancel()
        modelRetryJob = appState.model.runWhenModelLoaded(coroutineScope) { apps ->
            val appResults = appSearchProvider.search(context, query, apps)

            currentJob?.cancel()
            currentJob = coroutineScope.launch {
                // Each provider starts with an empty list so app matches show at once and the
                // others join as they come in.
                val otherResultsFlow = combine(
                    searchProviders.map { provider ->
                        provider.search(context, query).onStart { emit(emptyList()) }
                    },
                ) { resultsArray ->
                    resultsArray.toList().flatten()
                }

                otherResultsFlow.collect { otherResults ->
                    val searchTargets = translateToSearchTargets(appResults + otherResults)
                    setFirstItemQuickLaunch(searchTargets)
                    val adapterItems = transformSearchResults(searchTargets)
                    withContext(Dispatchers.Main) {
                        callback.onSearchResult(query, ArrayList(adapterItems))
                    }
                }
            }
        }
    }

    override fun doZeroStateSearch(callback: SearchCallback<BaseAllAppsAdapter.AdapterItem>) {
        modelRetryJob?.cancel()
        currentJob?.cancel()
        callback.clearSearchResult()
    }

    override fun cancel(interruptActiveRequests: Boolean) {
        modelRetryJob?.cancel()
        currentJob?.cancel()
    }

    private val sectionBuilders: List<SectionBuilder> = listOf(
        SeerrSectionBuilder,
        AppsAndShortcutsSectionBuilder,
        SettingsSectionBuilder,
    )

    private fun translateToSearchTargets(
        results: List<SearchResult>,
    ): List<SearchTargetCompat> {
        val factory = SearchTargetFactory(context)

        // The new function is just a flatMap. It's declarative and beautiful.
        return sectionBuilders.flatMap { builder ->
            builder.build(context, factory, results)
        }
    }
}
