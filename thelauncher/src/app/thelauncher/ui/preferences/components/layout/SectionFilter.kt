package app.thelauncher.ui.preferences.components.layout

import androidx.compose.runtime.staticCompositionLocalOf

/**
 * Limits a settings screen to some of its sections, so one screen can be shown in parts under
 * different categories. [headings] null shows every section; [title] replaces the screen title.
 */
data class SectionFilter(
    val title: String? = null,
    val headings: Set<String>? = null,
    val showUntitled: Boolean = false,
) {
    fun shows(heading: String?): Boolean = when {
        headings == null -> true
        heading == null -> showUntitled
        else -> heading in headings
    }
}

val LocalSectionFilter = staticCompositionLocalOf { SectionFilter() }
