package app.thelauncher.search.algorithms.engine

import android.content.pm.ShortcutInfo
import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import app.thelauncher.search.algorithms.data.ContactInfo
import app.thelauncher.search.algorithms.data.IFileInfo
import app.thelauncher.search.algorithms.data.RecentKeyword
import app.thelauncher.search.algorithms.data.SettingInfo
import com.android.launcher3.model.data.AppInfo

/**
 * A clean, type-safe, internal representation of any possible search result.
 * This is the "domain model" for the search engine. It has no knowledge of the UI.
 */
sealed interface SearchResult {
    data class App(val data: AppInfo) : SearchResult
    data class Contact(val data: ContactInfo) : SearchResult
    data class File(val data: IFileInfo) : SearchResult
    data class Setting(val data: SettingInfo) : SearchResult
    data class Shortcut(val data: ShortcutInfo) : SearchResult
    data class WebSuggestion(val suggestion: String, val provider: String) : SearchResult
    data class History(val data: RecentKeyword) : SearchResult
    data class Calculation(val data: app.thelauncher.search.algorithms.data.Calculation) : SearchResult

    /** A Seerr match to add or delete. [onlyMatch] lets Enter act on it. */
    data class Seerr(
        val item: app.thelauncher.seerr.SeerrItem,
        val mode: app.thelauncher.seerr.SeerrMode,
        val onlyMatch: Boolean,
    ) : SearchResult

    /** "More results in Seerr", opening Seerr's own search. */
    data class SeerrMore(val term: String, val total: Int) : SearchResult

    /** Why there are no Seerr matches to show. */
    data class SeerrMessage(val mode: app.thelauncher.seerr.SeerrMode, val kind: Kind) : SearchResult {
        enum class Kind { SET_UP, UNREACHABLE, NO_LOCAL_NETWORK, NO_MATCHES }
    }
    sealed interface Action : SearchResult {
        data class MarketSearch(val query: String) : Action
        data class WebSearch(
            val query: String,
            val providerName: String,
            val searchUrl: String,
            @DrawableRes val providerIconRes: Int,
            val tintIcon: Boolean = false,
        ) : Action
        data class EmptyState(
            @StringRes val titleRes: Int,
            @StringRes val subtitleRes: Int,
        ) : Action
        data object SearchSettings : Action
    }
}
