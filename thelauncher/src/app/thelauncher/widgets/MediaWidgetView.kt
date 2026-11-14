package app.thelauncher.widgets

import android.app.Notification
import android.content.Context
import android.content.Intent
import android.content.res.ColorStateList
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.Outline
import android.graphics.drawable.GradientDrawable
import android.media.session.MediaController
import android.media.session.MediaSession
import android.provider.Settings
import android.text.TextUtils
import android.view.Gravity
import android.view.View
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import android.view.ViewGroup.LayoutParams.WRAP_CONTENT
import android.view.ViewOutlineProvider
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.app.NotificationManagerCompat
import androidx.core.graphics.ColorUtils
import androidx.core.view.isVisible
import androidx.palette.graphics.Palette
import app.thelauncher.smartspace.provider.MediaListener
import com.android.launcher3.R

/**
 * What is playing: the album art, the title and artist, and previous, play or pause, and next.
 * The card takes its colour from the album art.
 */
class MediaWidgetView(context: Context) : WidgetCardView(context) {

    private val listener = MediaListener(context) { render() }

    private val row = LinearLayout(context)
    private val art = ImageView(context)
    private val title = TextView(context)
    private val artist = TextView(context)
    private val buttons = LinearLayout(context)
    private val previous = ImageView(context)
    private val playPause = ImageView(context)
    private val next = ImageView(context)
    private val playPauseBackground = GradientDrawable().apply { shape = GradientDrawable.OVAL }

    private var tintedFor: Bitmap? = null
    private var artCard: Int? = null

    init {
        row.orientation = LinearLayout.HORIZONTAL
        row.gravity = Gravity.CENTER_VERTICAL
        row.setPadding(dp(12), dp(12), dp(12), dp(12))

        art.scaleType = ImageView.ScaleType.CENTER_CROP
        art.clipToOutline = true
        art.outlineProvider = object : ViewOutlineProvider() {
            override fun getOutline(view: View, outline: Outline) {
                outline.setRoundRect(0, 0, view.width, view.height, 18 * density)
            }
        }
        row.addView(art, LinearLayout.LayoutParams(dp(72), dp(72)))

        val column = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL }
        title.maxLines = 1
        title.ellipsize = TextUtils.TruncateAt.END
        title.includeFontPadding = false
        title.textSize = 16f
        fonts.setCustomFont(title, R.id.font_heading)
        artist.maxLines = 1
        artist.ellipsize = TextUtils.TruncateAt.END
        artist.includeFontPadding = false
        artist.textSize = 13f
        fonts.setCustomFont(artist, R.id.font_body)
        column.addView(title, LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT))
        column.addView(
            artist,
            LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT).apply { topMargin = dp(3) },
        )

        buttons.orientation = LinearLayout.HORIZONTAL
        buttons.gravity = Gravity.CENTER_VERTICAL
        previous.setImageResource(R.drawable.ic_glance_skip_previous)
        next.setImageResource(R.drawable.ic_glance_skip_next)
        playPause.background = playPauseBackground
        listOf(previous, playPause, next).forEach { button ->
            button.scaleType = ImageView.ScaleType.CENTER_INSIDE
            button.setPadding(dp(8), dp(8), dp(8), dp(8))
            buttons.addView(
                button,
                LinearLayout.LayoutParams(dp(40), dp(40)).apply { marginEnd = dp(8) },
            )
        }
        previous.setOnClickListener { controller()?.transportControls?.skipToPrevious() }
        next.setOnClickListener { controller()?.transportControls?.skipToNext() }
        playPause.setOnClickListener {
            val controls = controller()?.transportControls ?: return@setOnClickListener
            if (listener.tracking?.playing == true) controls.pause() else controls.play()
        }
        column.addView(
            buttons,
            LinearLayout.LayoutParams(WRAP_CONTENT, WRAP_CONTENT).apply { topMargin = dp(8) },
        )
        row.addView(
            column,
            LinearLayout.LayoutParams(0, WRAP_CONTENT, 1f).apply { marginStart = dp(12) },
        )
        addView(row, LayoutParams(MATCH_PARENT, MATCH_PARENT))

        setOnClickListener { openPlayer() }
        render()
    }

    override fun applyPalette() {
        render()
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        listener.onResume()
    }

    override fun onDetachedFromWindow() {
        listener.onPause()
        super.onDetachedFromWindow()
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        post {
            // The art fills the height of the card, and gives way first when the card is narrow.
            val side = (h - dp(24)).coerceIn(dp(40), dp(140))
            art.layoutParams = art.layoutParams.apply {
                width = side
                height = side
            }
            art.isVisible = art.drawable != null && w >= dp(200)
            buttons.isVisible = h >= dp(88)
        }
    }

    private fun hasNotificationAccess(): Boolean =
        NotificationManagerCompat.getEnabledListenerPackages(context).contains(context.packageName)

    private fun controller(): MediaController? = runCatching {
        val token = listener.tracking?.sbn?.notification?.extras
            ?.getParcelable<MediaSession.Token>(Notification.EXTRA_MEDIA_SESSION)
        token?.let { MediaController(context, it) }
    }.getOrNull()

    private fun openPlayer() {
        if (!hasNotificationAccess()) {
            startSafely(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
            return
        }
        val tracking = listener.tracking ?: return
        context.packageManager.getLaunchIntentForPackage(tracking.packageName)?.let { startSafely(it) }
    }

    private fun render() {
        val tracking = listener.tracking
        val info = tracking?.info
        val playing = info?.title != null

        if (!playing) {
            tintedFor = null
            artCard = null
            art.setImageDrawable(null)
            art.isVisible = false
            buttons.isVisible = false
            title.text = context.getString(
                if (hasNotificationAccess()) R.string.widget_media_nothing else R.string.widget_media_access,
            )
            artist.text = ""
            artist.isVisible = false
            applyColors()
            return
        }

        title.text = info?.title
        artist.text = info?.artist ?: info?.album ?: ""
        artist.isVisible = artist.text.isNotEmpty()
        buttons.isVisible = height == 0 || height >= dp(88)
        playPause.setImageResource(
            if (tracking?.playing == true) R.drawable.ic_glance_pause else R.drawable.ic_glance_play,
        )

        val bitmap = info?.art
        art.setImageBitmap(bitmap)
        art.isVisible = bitmap != null && (width == 0 || width >= dp(200))
        if (bitmap == null) {
            tintedFor = null
            artCard = null
        } else if (bitmap !== tintedFor) {
            tintedFor = bitmap
            readColors(bitmap)
        }
        applyColors()
    }

    /** Picks the card colour out of the album art, off the main thread. */
    private fun readColors(bitmap: Bitmap) {
        runCatching {
            val source = if (bitmap.config == Bitmap.Config.HARDWARE) {
                bitmap.copy(Bitmap.Config.ARGB_8888, false)
            } else {
                bitmap
            }
            Palette.from(source).generate { swatches ->
                if (tintedFor !== bitmap) return@generate
                val fallback = palette.card
                artCard = swatches?.let { it.getDarkMutedColor(it.getDominantColor(fallback)) } ?: fallback
                applyColors()
            }
        }
    }

    private fun applyColors() {
        val card = artCard ?: palette.card
        setCardColor(card)
        val onDark = ColorUtils.calculateLuminance(card) < 0.5
        val primary = if (artCard == null) palette.primary else if (onDark) Color.WHITE else 0xFF1B1B1B.toInt()
        val secondary = if (artCard == null) palette.secondary else ColorUtils.setAlphaComponent(primary, 0xB3)
        title.setTextColor(primary)
        artist.setTextColor(secondary)
        val tint = ColorStateList.valueOf(primary)
        previous.imageTintList = tint
        next.imageTintList = tint
        playPauseBackground.setColor(primary)
        playPause.imageTintList = ColorStateList.valueOf(card)
    }
}
