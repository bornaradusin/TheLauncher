package app.thelauncher.search.algorithms.engine.provider.web

import android.content.Context
import app.thelauncher.preferences.PreferenceManager
import app.thelauncher.search.algorithms.engine.SearchProvider
import app.thelauncher.search.algorithms.engine.SearchResult
import com.android.launcher3.R
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow

/**
 * Google in the drawer search: "Search Google for …" at once, and Google's own suggestions as
 * they come in. Off when Google search is turned off in the search settings.
 */
object GoogleSearchProvider : SearchProvider {
    override val id = "google"

    private const val MAX_SUGGESTIONS = 4
    private const val TIMEOUT_MS = 1_500L

    override fun search(context: Context, query: String): Flow<List<SearchResult>> {
        val text = query.trim()
        if (text.isEmpty() || !PreferenceManager.getInstance(context).searchGoogle.get()) return flowOf(emptyList())

        val action = SearchResult.Action.WebSearch(
            query = text,
            providerName = context.getString(R.string.search_provider_google),
            searchUrl = GoogleWebSearchProvider.getSearchUrl(text),
            providerIconRes = GoogleWebSearchProvider.iconRes,
        )
        return flow {
            emit(listOf<SearchResult>(action))
            val suggestions = withTimeoutOrNull(TIMEOUT_MS) { GoogleWebSearchProvider.getSuggestions(text).first() }.orEmpty()
            emit(
                listOf<SearchResult>(action) + suggestions
                    .filterNot { it.equals(text, ignoreCase = true) }
                    .take(MAX_SUGGESTIONS)
                    .map { SearchResult.WebSuggestion(suggestion = it, provider = GoogleWebSearchProvider.id) },
            )
        }.catch { emit(listOf(action)) }
    }
}
