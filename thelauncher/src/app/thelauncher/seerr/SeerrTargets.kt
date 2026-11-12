package app.thelauncher.seerr

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import app.thelauncher.ui.preferences.PreferenceActivity
import app.thelauncher.ui.preferences.navigation.SeerrSettings
import app.thelauncher.allapps.views.SearchResultView
import app.thelauncher.search.adapter.SearchActionCompat
import app.thelauncher.search.adapter.SearchTargetCompat
import app.thelauncher.search.adapter.SearchTargetFactory
import app.thelauncher.search.algorithms.engine.SearchResult
import com.android.launcher3.R

/** Layout type of Seerr rows in search. */
const val SEERR_LAYOUT = "thelauncher_seerr"

/** Turns Seerr results into search rows for [app.thelauncher.allapps.views.SeerrResultView]. */
object SeerrTargets {
    const val EXTRA_KIND = "seerr_kind"
    const val EXTRA_ITEM = "seerr_item"
    const val EXTRA_MODE = "seerr_mode"
    const val EXTRA_TERM = "seerr_term"
    const val EXTRA_TOTAL = "seerr_total"
    const val EXTRA_ONLY_MATCH = "seerr_only_match"

    const val KIND_ITEM = "item"
    const val KIND_MORE = "more"
    const val KIND_MESSAGE = "message"

    private const val PACKAGE = "seerr"

    fun item(context: Context, result: SearchResult.Seerr): SearchTargetCompat {
        val item = result.item
        val id = "seerr:${result.mode}:${item.mediaType}:${item.tmdbId}"
        val extras = Bundle().apply {
            putString(EXTRA_KIND, KIND_ITEM)
            putString(EXTRA_ITEM, item.toJson())
            putString(EXTRA_MODE, result.mode.name)
            // Enter only acts when there is exactly one match.
            putBoolean(EXTRA_ONLY_MATCH, result.onlyMatch)
            putBoolean(SearchResultView.EXTRA_QUICK_LAUNCH, result.onlyMatch)
        }
        return target(id, item.title, extras, webIntent(context, "/${item.mediaType}/${item.tmdbId}"))
    }

    fun more(context: Context, result: SearchResult.SeerrMore): SearchTargetCompat {
        val extras = Bundle().apply {
            putString(EXTRA_KIND, KIND_MORE)
            putString(EXTRA_TERM, result.term)
            putInt(EXTRA_TOTAL, result.total)
        }
        return target(
            "seerr:more:${result.term}",
            result.term,
            extras,
            searchIntent(context, result.term) ?: settingsIntent(context),
        )
    }

    /** Seerr's search for [term]: in SeerrMobile (or the Seerr app) when installed, else the web app. */
    fun searchIntent(context: Context, term: String): Intent? {
        val query = Uri.encode(term)
        SEERR_APPS.forEach { (pkg, scheme) ->
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse("$scheme://search?query=$query")).setPackage(pkg)
            if (intent.resolveActivity(context.packageManager) != null) return intent
        }
        return SeerrClient.webUrl(context, "/search?query=$query")?.let { Intent(Intent.ACTION_VIEW, Uri.parse(it)) }
    }

    // Apps that open Seerr's search from a link: package and link scheme, first found wins.
    private val SEERR_APPS = listOf(
        "io.github.bornaradusin.seerrmobile" to "seerrmobile",
        "dev.seerr.mobileapp" to "seerr",
    )

    fun message(context: Context, result: SearchResult.SeerrMessage): SearchTargetCompat {
        val title = context.getString(
            when (result.kind) {
                SearchResult.SeerrMessage.Kind.SET_UP -> R.string.seerr_message_set_up
                SearchResult.SeerrMessage.Kind.UNREACHABLE -> R.string.seerr_message_unreachable
                SearchResult.SeerrMessage.Kind.NO_LOCAL_NETWORK -> R.string.seerr_message_local_network
                SearchResult.SeerrMessage.Kind.NO_MATCHES ->
                    if (result.mode == SeerrMode.ADD) R.string.seerr_message_no_matches else R.string.seerr_message_none_in_seerr
            },
        )
        val extras = Bundle().apply {
            putString(EXTRA_KIND, KIND_MESSAGE)
            putString(EXTRA_MODE, result.mode.name)
            putString(EXTRA_TERM, result.kind.name)
        }
        return target("seerr:message:${result.kind}", title, extras, settingsIntent(context))
    }

    /** The title's page in Seerr's web app; rows handle taps themselves, but search needs an intent. */
    private fun webIntent(context: Context, path: String): Intent =
        SeerrClient.webUrl(context, path)?.let { Intent(Intent.ACTION_VIEW, Uri.parse(it)) } ?: settingsIntent(context)

    private fun settingsIntent(context: Context): Intent = PreferenceActivity.createIntent(context, SeerrSettings)

    private fun target(id: String, title: String, extras: Bundle, intent: Intent): SearchTargetCompat = SearchTargetFactory.createSearchTarget(
        id = id,
        action = SearchActionCompat.Builder(id, title).setIntent(intent).build(),
        layoutType = SEERR_LAYOUT,
        targetCompat = SearchTargetCompat.RESULT_TYPE_REDIRECTION,
        pkg = PACKAGE,
        extras = extras,
    )
}
