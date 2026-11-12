package app.thelauncher.smartspace.model

/** The date line plus one details line, with no pages to swipe. */
const val SMARTSPACE_LAYOUT_ONE_CARD = "one_card"

/** One swipeable card per item, as before. */
const val SMARTSPACE_LAYOUT_PAGES = "pages"

const val SMARTSPACE_BUBBLE_OFF = "off"
const val SMARTSPACE_BUBBLE_PILL = "pill"
const val SMARTSPACE_BUBBLE_CARD = "card"

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
    val bubble: String = SMARTSPACE_BUBBLE_OFF,
    val bubbleColor: Int = 0,
    val bubbleRadiusDp: Float = 28f,
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
                prefs.smartspaceBubble.get(),
                prefs.smartspaceBubbleColor.get(),
                prefs.smartspaceBubbleOpacity.get(),
                prefs.smartspaceBubbleRadius.get(),
                prefs.smartspaceTextScale.get(),
                prefs.smartspaceTextColor.get(),
                prefs.smartspaceCenteredDate.get(),
            ),
        ) { values ->
            val bubbleOption = values[3] as app.thelauncher.theme.color.ColorOption
            val opacity = (values[4] as Float).coerceIn(0f, 1f)
            val bubbleBase = bubbleOption.colorPreferenceEntry.lightColor(context)
            val textOption = values[7] as app.thelauncher.theme.color.ColorOption
            SmartspaceStyle(
                layout = values[0] as String,
                pageDots = values[1] as Boolean,
                bubble = values[2] as String,
                bubbleColor = androidx.core.graphics.ColorUtils.setAlphaComponent(bubbleBase, (opacity * 255).toInt()),
                bubbleRadiusDp = values[5] as Float,
                textScale = (values[6] as Float).coerceIn(0.5f, 2f),
                textColor = if (textOption is app.thelauncher.theme.color.ColorOption.Default) {
                    null
                } else {
                    textOption.colorPreferenceEntry.lightColor(context)
                },
                centered = values[8] as Boolean,
            )
        }
    }
}
