package app.thelauncher.widgets

import android.content.Context
import android.content.Intent
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.widget.FrameLayout
import android.widget.TextView
import androidx.core.graphics.ColorUtils
import app.thelauncher.font.FontManager
import app.thelauncher.theme.color.tokens.ColorToken
import app.thelauncher.theme.color.tokens.ColorTokens
import com.android.launcher3.R
import com.android.launcher3.Utilities
import kotlin.math.roundToInt

/** Colours for a widget card, taken from the wallpaper like the rest of the launcher. */
data class WidgetPalette(
    val dark: Boolean,
    val card: Int,
    val tile: Int,
    val primary: Int,
    val secondary: Int,
    val accent: Int,
    val track: Int,
) {
    companion object {
        fun resolve(context: Context): WidgetPalette {
            val dark = Utilities.isDarkTheme(context)
            fun c(token: ColorToken) = token.resolveColor(context)
            val primary = if (dark) c(ColorTokens.Neutral1_50) else c(ColorTokens.Neutral1_900)
            return WidgetPalette(
                dark = dark,
                card = if (dark) c(ColorTokens.Neutral1_800) else c(ColorTokens.Neutral1_50),
                tile = if (dark) c(ColorTokens.Accent2_800) else c(ColorTokens.Accent2_100),
                primary = primary,
                secondary = if (dark) c(ColorTokens.Neutral2_200) else c(ColorTokens.Neutral2_700),
                accent = if (dark) c(ColorTokens.Accent1_200) else c(ColorTokens.Accent1_600),
                track = ColorUtils.setAlphaComponent(primary, 0x30),
            )
        }
    }
}

/** A rounded card that every TheLauncher widget is drawn on. */
open class WidgetCardView(context: Context) : FrameLayout(context) {

    protected val density: Float = resources.displayMetrics.density
    protected val fonts: FontManager = FontManager.INSTANCE.get(context)
    protected var palette: WidgetPalette = WidgetPalette.resolve(context)
        private set

    private val cardBackground = GradientDrawable()

    // Text drawn straight on the card uses the launcher's heading font, which loads in the
    // background into this unseen text view.
    private val fontProbe = TextView(context)
    protected val headingTypeface: Typeface?
        get() = fontProbe.typeface

    init {
        cardBackground.cornerRadius = 28 * density
        cardBackground.setColor(palette.card)
        background = cardBackground
        clipToOutline = true
        setWillNotDraw(false)
    }

    protected fun dp(value: Int): Int = (value * density).roundToInt()

    protected fun sp(value: Float): Float = value * resources.displayMetrics.scaledDensity

    protected fun setCardColor(color: Int) {
        cardBackground.setColor(color)
    }

    /** Called when the colours may have changed; set them on the views here. */
    protected open fun applyPalette() {}

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        palette = WidgetPalette.resolve(context)
        cardBackground.setColor(palette.card)
        applyPalette()
        runCatching { fonts.setCustomFont(fontProbe, R.id.font_heading) }
        postDelayed({ invalidate() }, 700)
    }

    protected fun startSafely(intent: Intent): Boolean = runCatching {
        context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }.isSuccess

    /** Asks for [permission] with Android's own dialog. */
    protected fun requestPermission(permission: String) {
        startSafely(
            Intent(context, app.thelauncher.extras.PermissionRequestActivity::class.java)
                .putExtra("permissions", arrayOf(permission)),
        )
    }
}
