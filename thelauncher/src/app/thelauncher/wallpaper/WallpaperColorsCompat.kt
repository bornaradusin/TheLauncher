package app.thelauncher.wallpaper

import androidx.annotation.IntDef

data class WallpaperColorsCompat(
    val primaryColor: Int,
    @ColorsHints val colorHints: Int,
    /**
     * The colour Material You would build the palette from: the most colourful of the wallpaper's
     * main colours, not simply the most common one (often a near black or grey).
     */
    val seedColor: Int = primaryColor,
) {

    companion object {

        /**
         * Picks the seed the way the system does: colourful colours win, and among those the more
         * common one. Grey wallpapers fall back to the most common colour.
         */
        fun pickSeed(colors: List<Int>): Int {
            if (colors.isEmpty()) return 0
            val shares = listOf(0.5, 0.3, 0.2)
            return colors.withIndex().maxByOrNull { (i, color) ->
                val chroma = com.androidinternal.graphics.cam.Cam.fromInt(color).chroma.toDouble()
                val chromaScore = if (chroma < 48.0) 0.1 * (chroma - 48.0) else 0.3 * (chroma - 48.0)
                val share = 70.0 * shares.getOrElse(i) { 0.1 }
                if (chroma < 5.0) Double.NEGATIVE_INFINITY else chromaScore + share
            }?.takeIf { (_, color) -> com.androidinternal.graphics.cam.Cam.fromInt(color).chroma >= 5f }
                ?.value ?: colors.first()
        }

        const val HINT_SUPPORTS_DARK_TEXT = 1 shl 0
        const val HINT_SUPPORTS_DARK_THEME = 1 shl 1

        @IntDef(
            value = [HINT_SUPPORTS_DARK_TEXT, HINT_SUPPORTS_DARK_THEME],
            flag = true,
        )
        @Retention(
            AnnotationRetention.SOURCE,
        )
        annotation class ColorsHints
    }
}
