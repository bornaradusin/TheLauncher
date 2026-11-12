package app.thelauncher.seerr

import android.content.Context
import app.thelauncher.theme.color.tokens.ColorTokens
import com.android.launcher3.Utilities

/** Material You colours for Seerr's buttons and badges, light or dark with the launcher. */
data class SeerrColors(
    val buttonBg: Int,
    val buttonText: Int,
    val dangerBg: Int,
    val dangerText: Int,
    val availableBg: Int,
    val availableText: Int,
    val requestedBg: Int,
    val requestedText: Int,
) {
    companion object {
        // Material 3's error and error-container colours.
        private const val ERROR_DARK_BG = 0xFF8C1D18.toInt()
        private const val ERROR_DARK_TEXT = 0xFFF9DEDC.toInt()
        private const val ERROR_LIGHT_BG = 0xFFF9DEDC.toInt()
        private const val ERROR_LIGHT_TEXT = 0xFF8C1D18.toInt()

        fun resolve(context: Context): SeerrColors {
            fun c(token: app.thelauncher.theme.color.tokens.ColorToken) = token.resolveColor(context)
            return if (Utilities.isDarkTheme(context)) {
                SeerrColors(
                    buttonBg = c(ColorTokens.Accent1_200),
                    buttonText = c(ColorTokens.Accent1_800),
                    dangerBg = ERROR_DARK_BG,
                    dangerText = ERROR_DARK_TEXT,
                    availableBg = c(ColorTokens.Accent3_800),
                    availableText = c(ColorTokens.Accent3_100),
                    requestedBg = c(ColorTokens.Accent2_800),
                    requestedText = c(ColorTokens.Accent2_100),
                )
            } else {
                SeerrColors(
                    buttonBg = c(ColorTokens.Accent1_600),
                    buttonText = c(ColorTokens.Accent1_10),
                    dangerBg = ERROR_LIGHT_BG,
                    dangerText = ERROR_LIGHT_TEXT,
                    availableBg = c(ColorTokens.Accent3_100),
                    availableText = c(ColorTokens.Accent3_800),
                    requestedBg = c(ColorTokens.Accent2_100),
                    requestedText = c(ColorTokens.Accent2_800),
                )
            }
        }

        /** Behind a poster while it loads, or when there is none. */
        fun placeholder(context: Context): Int = if (Utilities.isDarkTheme(context)) {
            ColorTokens.Neutral2_700.resolveColor(context)
        } else {
            ColorTokens.Neutral2_100.resolveColor(context)
        }
    }
}
