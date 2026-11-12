package app.thelauncher.search.algorithms.engine.provider

import android.content.Context
import app.thelauncher.search.algorithms.engine.SearchProvider
import app.thelauncher.search.algorithms.engine.SearchResult
import app.thelauncher.seerr.SeerrClient
import app.thelauncher.seerr.SeerrMode
import app.thelauncher.seerr.SeerrNetwork
import app.thelauncher.seerr.SeerrQuery
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

/**
 * "add batman" lists matches to request in Seerr; "delete batman" lists the matches already in
 * Seerr. Anything else isn't a Seerr search.
 */
object SeerrSearchProvider : SearchProvider {

    override val id: String = "seerr"

    /** How many matches show before "More results in Seerr". */
    const val MAX_SHOWN = 5

    private const val TYPING_PAUSE_MS = 350L

    override fun search(context: Context, query: String): Flow<List<SearchResult>> = flow {
        val seerrQuery = SeerrQuery.parse(query)
        if (seerrQuery == null) {
            emit(emptyList())
            return@flow
        }
        val client = SeerrClient.fromSettings(context)
        if (client == null) {
            emit(listOf(SearchResult.SeerrMessage(seerrQuery.mode, SearchResult.SeerrMessage.Kind.SET_UP)))
            return@flow
        }
        // Wait for a pause in typing before asking the server.
        delay(TYPING_PAUSE_MS)
        val page = runCatching { client.search(seerrQuery.term) }.getOrElse {
            val kind = if (SeerrNetwork.isAllowed(context)) {
                SearchResult.SeerrMessage.Kind.UNREACHABLE
            } else {
                SearchResult.SeerrMessage.Kind.NO_LOCAL_NETWORK
            }
            emit(listOf(SearchResult.SeerrMessage(seerrQuery.mode, kind)))
            return@flow
        }
        val matches = when (seerrQuery.mode) {
            SeerrMode.ADD -> page.results
            SeerrMode.DELETE -> page.results.filter { it.isInSeerr }
        }
        if (matches.isEmpty()) {
            emit(listOf(SearchResult.SeerrMessage(seerrQuery.mode, SearchResult.SeerrMessage.Kind.NO_MATCHES)))
            return@flow
        }
        val shown = matches.take(MAX_SHOWN)
        val results = shown.map { SearchResult.Seerr(it, seerrQuery.mode, onlyMatch = matches.size == 1) } +
            listOfNotNull(
                if (seerrQuery.mode == SeerrMode.ADD && page.total > shown.size) {
                    SearchResult.SeerrMore(seerrQuery.term, page.total)
                } else {
                    null
                },
            )
        emit(results)
    }
}
