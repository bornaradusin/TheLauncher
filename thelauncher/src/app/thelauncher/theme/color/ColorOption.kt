package app.thelauncher.theme.color

import android.content.Context
import android.content.res.Configuration
import android.graphics.Color
import androidx.compose.ui.res.stringResource
import app.thelauncher.ui.preferences.components.colorpreference.ColorPreferenceEntry
import app.thelauncher.ui.theme.getSystemAccent
import app.thelauncher.wallpaper.WallpaperManagerCompat
import com.android.launcher3.R
import com.android.launcher3.Utilities

sealed class ColorOption {

    abstract val isSupported: Boolean
    abstract val colorPreferenceEntry: ColorPreferenceEntry<ColorOption>

    object SystemAccent : ColorOption() {
        override val isSupported = true

        override val colorPreferenceEntry = ColorPreferenceEntry<ColorOption>(
            this,
            { stringResource(id = R.string.system) },
            { context -> context.getSystemAccent(false) },
            { context -> context.getSystemAccent(true) },
        )

        override fun toString() = "system_accent"
    }

    /** The system accent at its deepest tone in a dark theme and its palest in a light one. */
    object SystemAccentDeep : ColorOption() {
        override val isSupported = Utilities.ATLEAST_S

        private fun isNight(context: Context) =
            (context.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES

        override val colorPreferenceEntry = ColorPreferenceEntry<ColorOption>(
            this,
            { stringResource(id = R.string.system_accent_deep) },
            { context -> context.getColor(if (isNight(context)) R.color.system_accent1_900 else R.color.system_accent1_100) },
            { context -> context.getColor(R.color.system_accent1_900) },
        )

        override fun toString() = "system_accent_deep"
    }

    object WallpaperPrimary : ColorOption() {
        override val isSupported = Utilities.ATLEAST_O_MR1

        override val colorPreferenceEntry = ColorPreferenceEntry<ColorOption>(
            this,
            { stringResource(id = R.string.wallpaper) },
            // The accent Material You makes from the wallpaper, not its raw most common colour.
            { context -> wallpaperAccent(context, dark = false) },
            { context -> wallpaperAccent(context, dark = true) },
        )

        override fun toString() = "wallpaper_primary"

        private fun wallpaperAccent(context: Context, dark: Boolean): Int {
            val seed = WallpaperManagerCompat.INSTANCE.get(context).wallpaperColors?.seedColor
                ?: return TheLauncherBlue.color
            val accent = com.android.systemui.monet.ColorScheme(seed).accent1
            // The same tones as the system accent: 600 on light, 100 on dark.
            return if (dark) accent.s100 else accent.s600
        }
    }

    class CustomColor(val color: Int) : ColorOption() {
        override val isSupported = true

        override val colorPreferenceEntry = ColorPreferenceEntry<ColorOption>(
            this,
            { stringResource(id = R.string.custom) },
            { color },
        )

        constructor(color: Long) : this(color.toInt())

        override fun equals(other: Any?) = other is CustomColor && other.color == color

        override fun hashCode() = color

        override fun toString() = "custom|#${String.format("%08x", color)}"
    }

    object Default : ColorOption() {
        override val isSupported = false

        override val colorPreferenceEntry = ColorPreferenceEntry<ColorOption>(
            this,
            { stringResource(id = R.string.managed_by_thelauncher) },
            { 0 },
        )

        override fun toString() = "default"
    }

    companion object {
        val TheLauncherBlue = CustomColor(0xFF007FFF)

        fun fromString(stringValue: String) = when (stringValue) {
            "system_accent" -> SystemAccent
            "system_accent_deep" -> SystemAccentDeep
            "wallpaper_primary" -> WallpaperPrimary
            "default" -> Default
            else -> instantiateCustomColor(stringValue)
        }

        private fun instantiateCustomColor(stringValue: String): ColorOption {
            try {
                if (stringValue.startsWith("custom")) {
                    val color = Color.parseColor(stringValue.substring(7))
                    return CustomColor(color)
                }
            } catch (_: IllegalArgumentException) {
            }
            return when {
                Utilities.ATLEAST_S -> SystemAccent
                Utilities.ATLEAST_O_MR1 -> WallpaperPrimary
                else -> TheLauncherBlue
            }
        }
    }
}
