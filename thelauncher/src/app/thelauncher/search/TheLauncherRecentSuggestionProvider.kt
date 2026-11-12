package app.thelauncher.search

import android.content.SearchRecentSuggestionsProvider
import com.android.launcher3.BuildConfig

class TheLauncherRecentSuggestionProvider : SearchRecentSuggestionsProvider() {
    companion object {
        const val AUTHORITY = BuildConfig.APPLICATION_ID + ".search.TheLauncherRecentSuggestionProvider"
        const val MODE = DATABASE_MODE_QUERIES
    }

    init {
        setupSuggestions(AUTHORITY, MODE)
    }
}
