package app.thelauncher.smartspace.model

/** The date line plus one details line, with no pages to swipe. */
const val SMARTSPACE_LAYOUT_ONE_CARD = "one_card"

/** One swipeable card per item, as before. */
const val SMARTSPACE_LAYOUT_PAGES = "pages"

/** At a Glance as a Material You card with the date, weather and what matters right now. */
const val GLANCE_STYLE_MATERIAL = "material"

/** At a Glance as text over the wallpaper. */
const val GLANCE_STYLE_TEXT = "text"

/** One row under the date, in one row of the grid. */
const val GLANCE_SIZE_COMPACT = "compact"

/** Two rows under the date, in two rows of the grid. */
const val GLANCE_SIZE_LARGE = "large"

/**
 * Only the date while there's nothing else, growing to a second row when there is (an alarm, the
 * flashlight, an event). It keeps two rows of the grid free to grow into.
 */
const val GLANCE_SIZE_AUTO = "auto"

const val SMARTSPACE_DATE_SHORT = "short"
const val SMARTSPACE_DATE_LONG = "long"
const val SMARTSPACE_DATE_FULL = "full"

/** ICU skeleton for the date on its own, in the chosen style. */
fun smartspaceDateSkeleton(style: String): String = when (style) {
    SMARTSPACE_DATE_SHORT -> "EEEMMMd"
    SMARTSPACE_DATE_FULL -> "EEEEMMMMd"
    else -> "EEEEMMMd"
}

/** How At a Glance looks, read from the settings in one go. */
data class SmartspaceStyle(
    val layout: String = SMARTSPACE_LAYOUT_ONE_CARD,
    val pageDots: Boolean = false,
    val textScale: Float = 1f,
    /** Null follows the home screen's text colour. */
    val textColor: Int? = null,
    val centered: Boolean = false,
) {
    val oneCard: Boolean get() = layout != SMARTSPACE_LAYOUT_PAGES

    companion object {
        fun flow(
            context: android.content.Context,
            prefs: app.thelauncher.preferences2.PreferenceManager2,
        ): kotlinx.coroutines.flow.Flow<SmartspaceStyle> = kotlinx.coroutines.flow.combine(
            listOf(
                prefs.smartspaceLayout.get(),
                prefs.smartspacePageDots.get(),
                prefs.smartspaceTextScale.get(),
                prefs.smartspaceTextColor.get(),
                prefs.smartspaceCenteredDate.get(),
            ),
        ) { values ->
            val textOption = values[3] as app.thelauncher.theme.color.ColorOption
            SmartspaceStyle(
                layout = values[0] as String,
                pageDots = values[1] as Boolean,
                textScale = (values[2] as Float).coerceIn(0.5f, 2f),
                textColor = if (textOption is app.thelauncher.theme.color.ColorOption.Default) {
                    null
                } else {
                    textOption.colorPreferenceEntry.lightColor(context)
                },
                centered = values[4] as Boolean,
            )
        }
    }
}
