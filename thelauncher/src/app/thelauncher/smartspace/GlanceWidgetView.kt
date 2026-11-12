package app.thelauncher.smartspace

import android.content.Context
import android.content.Intent
import android.content.res.ColorStateList
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Outline
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.Icon
import android.graphics.drawable.RippleDrawable
import android.text.TextUtils
import android.text.format.DateUtils
import android.util.AttributeSet
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.ViewOutlineProvider
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.graphics.ColorUtils
import androidx.core.view.isVisible
import app.thelauncher.font.FontManager
import app.thelauncher.preferences2.PreferenceManager2
import app.thelauncher.smartspace.model.GLANCE_SIZE_LARGE
import app.thelauncher.smartspace.model.GlanceExtras
import app.thelauncher.smartspace.model.GlanceRanking
import app.thelauncher.smartspace.model.SmartspaceAction
import app.thelauncher.smartspace.model.SmartspaceTarget
import app.thelauncher.smartspace.model.SmartspaceTarget.FeatureType
import app.thelauncher.smartspace.model.hasIntent
import app.thelauncher.smartspace.provider.SmartspaceProvider
import app.thelauncher.theme.color.ColorOption
import app.thelauncher.theme.color.tokens.ColorTokens
import app.thelauncher.util.repeatOnAttached
import com.android.launcher3.R
import com.android.launcher3.Utilities
import kotlin.math.roundToInt
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach

/**
 * At a Glance as a Material You card: the date and weather on top, and under them up to two rows
 * for what matters right now (see [GlanceRanking]), each with its own buttons.
 */
class GlanceWidgetView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    /** Added from the widget list: as many rows as its size has room for, up to three. */
    private val appWidget: Boolean = false,
) : FrameLayout(context, attrs) {

    var previewMode = false

    private val prefs = PreferenceManager2.getInstance(context)
    private val provider = SmartspaceProvider.INSTANCE.get(context)
    private val fonts = FontManager.INSTANCE.get(context)
    private val density = resources.displayMetrics.density

    private val card = LinearLayout(context)
    private val dateView = IcuDateTextView(context)
    private val detailView = TextView(context)
    private val weatherBox = LinearLayout(context)
    private val weatherIcon = ImageView(context)
    private val tempView = TextView(context)
    private val rows = LinearLayout(context)

    private var style = GlanceWidgetStyle()

    /** One row fits in one grid row, so everything is a little smaller than in the large widget. */
    private val compact get() = !appWidget && style.maxRows == 1

    private val maxRows get() = if (appWidget) APP_WIDGET_MAX_ROWS else style.maxRows

    /** Shrinks the whole card, only when even the date doesn't fit; text never shrinks otherwise. */
    private var fitScale = 1f
    private var targets: List<SmartspaceTarget> = emptyList()
    private var renderedRows: List<String> = emptyList()
    private var wantsDetail = false

    init {
        clipChildren = false
        clipToPadding = false

        card.orientation = LinearLayout.VERTICAL
        card.setPadding(dp(18), dp(14), dp(14), dp(14))
        addView(card, LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.CENTER))

        val header = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        val headerText = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL }
        dateView.apply {
            shadowsEnabled = false
            maxLines = 1
            ellipsize = TextUtils.TruncateAt.END
            includeFontPadding = false
        }
        fonts.setCustomFont(dateView, R.id.font_heading)
        detailView.apply {
            maxLines = 1
            ellipsize = TextUtils.TruncateAt.END
            includeFontPadding = false
        }
        fonts.setCustomFont(detailView, R.id.font_body)
        headerText.addView(dateView)
        headerText.addView(
            detailView,
            LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT)
                .apply { topMargin = dp(4) },
        )
        header.addView(headerText, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))

        weatherBox.orientation = LinearLayout.HORIZONTAL
        weatherBox.gravity = Gravity.CENTER_VERTICAL
        weatherBox.setPadding(dp(8), dp(4), dp(4), dp(4))
        weatherBox.addView(weatherIcon, LinearLayout.LayoutParams(dp(28), dp(28)))
        tempView.includeFontPadding = false
        tempView.maxLines = 1
        fonts.setCustomFont(tempView, R.id.font_heading)
        weatherBox.addView(
            tempView,
            LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT)
                .apply { marginStart = dp(6) },
        )
        header.addView(weatherBox)
        card.addView(header)

        rows.orientation = LinearLayout.VERTICAL
        card.addView(rows, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))

        repeatOnAttached {
            val source = if (previewMode) provider.previewTargets else provider.targets
            combine(source, GlanceWidgetStyle.flow(prefs), minuteTicks()) { items, itemStyle, _ -> items to itemStyle }
                .onEach { (items, itemStyle) ->
                    val restyled = itemStyle != style
                    style = itemStyle
                    targets = items
                    render(restyled)
                }
                .launchIn(this)
        }
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val width = MeasureSpec.getSize(widthMeasureSpec)
        val heightMode = MeasureSpec.getMode(heightMeasureSpec)
        val available = MeasureSpec.getSize(heightMeasureSpec) - paddingTop - paddingBottom
        val innerWidth = width - paddingLeft - paddingRight
        val unbounded = MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED)

        // Work out what fits without toggling views back and forth (which would ask for layout
        // again on every pass): drop rows from the bottom, then the weather details.
        fun measureCard(cardWidth: Int) = card.measure(MeasureSpec.makeMeasureSpec(cardWidth, MeasureSpec.EXACTLY), unbounded)
        val contentWidth = MeasureSpec.makeMeasureSpec(innerWidth - card.paddingLeft - card.paddingRight, MeasureSpec.EXACTLY)
        val header = card.getChildAt(0)
        header.measure(contentWidth, unbounded)
        val detailHeight = if (wantsDetail) {
            detailView.measure(unbounded, unbounded)
            detailView.measuredHeight + (detailView.layoutParams as ViewGroup.MarginLayoutParams).topMargin
        } else {
            0
        }
        // With the details shown in the header, measure what the header would be without them.
        val headerWithDetail = if (wantsDetail && !detailView.isVisible) header.measuredHeight + detailHeight else header.measuredHeight
        val headerWithoutDetail = maxOf(headerWithDetail - detailHeight, weatherBox.measuredHeight)
        val rowHeights = (0 until rows.childCount).map { i ->
            val row = rows.getChildAt(i)
            row.measure(contentWidth, unbounded)
            row.measuredHeight + (row.layoutParams as ViewGroup.MarginLayoutParams).topMargin
        }
        var showDetail = wantsDetail
        var visibleRows = rowHeights.size
        if (heightMode != MeasureSpec.UNSPECIFIED && available > 0) {
            fun total() = card.paddingTop + card.paddingBottom +
                (if (showDetail) headerWithDetail else headerWithoutDetail) + rowHeights.take(visibleRows).sum()
            while (total() > available && visibleRows > 0) visibleRows--
            if (total() > available && showDetail) showDetail = false
        }
        for (i in 0 until rows.childCount) {
            val visibility = if (i < visibleRows) View.VISIBLE else View.GONE
            if (rows.getChildAt(i).visibility != visibility) rows.getChildAt(i).visibility = visibility
        }
        if (detailView.isVisible != showDetail) detailView.isVisible = showDetail
        measureCard(innerWidth)
        // Last resort for a very short space: shrink the card, keeping it the full width.
        fitScale = if (heightMode != MeasureSpec.UNSPECIFIED && available in 1 until card.measuredHeight) {
            available.toFloat() / card.measuredHeight
        } else {
            1f
        }
        if (fitScale < 1f) measureCard((innerWidth / fitScale).toInt())
        val natural = (card.measuredHeight * fitScale).roundToInt()
        val height = when (heightMode) {
            MeasureSpec.EXACTLY -> MeasureSpec.getSize(heightMeasureSpec)
            MeasureSpec.AT_MOST -> minOf(natural + paddingTop + paddingBottom, MeasureSpec.getSize(heightMeasureSpec))
            else -> natural + paddingTop + paddingBottom
        }
        setMeasuredDimension(width, height)
    }

    override fun onLayout(changed: Boolean, left: Int, top: Int, right: Int, bottom: Int) {
        val shown = card.measuredHeight * fitScale
        val inner = bottom - top - paddingTop - paddingBottom
        val y = paddingTop + ((inner - shown) / 2f).roundToInt()
        card.layout(paddingLeft, y, paddingLeft + card.measuredWidth, y + card.measuredHeight)
        card.pivotX = 0f
        card.pivotY = 0f
        card.scaleX = fitScale
        card.scaleY = fitScale
    }

    private fun render(restyled: Boolean) {
        val now = System.currentTimeMillis()
        val palette = GlancePalette.resolve(context, style)
        val scale = style.textScale

        card.background = GradientDrawable().apply {
            setColor(palette.card)
            cornerRadius = style.radiusDp * density
        }
        if (compact) card.setPadding(dp(16), dp(10), dp(12), dp(10)) else card.setPadding(dp(18), dp(14), dp(14), dp(14))

        dateView.setTextColor(palette.primary)
        dateView.setTextSize(TypedValue.COMPLEX_UNIT_SP, (if (compact) 19f else 22f) * scale)
        val calendarIntent = style.calendarApp.takeIf { it.isNotBlank() }
            ?.let { context.packageManager.getLaunchIntentForPackage(it) }
            ?: BcSmartSpaceUtil.getOpenCalendarIntent()
        BcSmartSpaceUtil.setOnClickListener(dateView, SmartspaceAction(id = "glanceDate", title = "", intent = calendarIntent), null, TAG)

        val weather = targets.firstOrNull { it.featureType == FeatureType.FEATURE_WEATHER }?.headerAction
        val temperature = weather?.subtitle?.toString()?.takeIf { it.isNotBlank() }
        weatherBox.isVisible = temperature != null
        tempView.text = temperature
        tempView.setTextColor(palette.primary)
        tempView.setTextSize(TypedValue.COMPLEX_UNIT_SP, (if (compact) 19f else 24f) * scale)
        weatherIcon.layoutParams = (weatherIcon.layoutParams as LinearLayout.LayoutParams).apply {
            width = dp(if (compact) 22 else 28)
            height = dp(if (compact) 22 else 28)
        }
        weatherIcon.setImageDrawable(BcSmartSpaceUtil.getIconDrawable(weather?.icon, context))
        weatherIcon.imageTintList = if (weather?.icon?.type == Icon.TYPE_RESOURCE) ColorStateList.valueOf(palette.primary) else null
        val detail = weather?.extras?.getString(GlanceExtras.DETAIL)
            ?: weather?.contentDescription?.toString()?.substringAfter(", ", "")
        detailView.text = detail
        detailView.setTextColor(palette.secondary)
        detailView.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f * scale)
        val weatherTap = style.weatherApp.takeIf { it.isNotBlank() }
            ?.let { context.packageManager.getLaunchIntentForPackage(it) }
            ?.let { SmartspaceAction(id = "glanceWeatherApp", title = "", intent = it) }
            ?: weather
        BcSmartSpaceUtil.setOnClickListener(weatherBox, weatherTap, null, TAG)
        BcSmartSpaceUtil.setOnClickListener(detailView, weatherTap, null, TAG)

        val items = GlanceRanking.contextItems(targets, now, style.lookaheadHours)
            .filter { (it.headerAction ?: it.baseAction) != null }
            .take(maxRows)
        // The compact widget has room for the weather details or a row, not both.
        wantsDetail = style.weatherDetails && !detail.isNullOrBlank() && (!compact || items.isEmpty())
        detailView.isVisible = wantsDetail
        val rowContent = items.mapNotNull { describe(it, now) }
        val keys = rowContent.map { it.key }
        if (!restyled && keys == renderedRows) return
        renderedRows = keys
        rows.removeAllViews()
        rowContent.forEach { rows.addView(makeRow(it, palette)) }
        rows.isVisible = rowContent.isNotEmpty()
    }

    /** What one row shows, worked out from its item. */
    private class RowContent(
        val target: SmartspaceTarget,
        val action: SmartspaceAction,
        val title: CharSequence,
        val subtitle: CharSequence?,
        val alert: Boolean,
        val art: Bitmap?,
        val progress: Float?,
    ) {
        val key: String get() = listOf(
            target.id,
            title,
            subtitle,
            progress,
            action.controls.joinToString { "${it.id}:${it.title}" },
        ).joinToString("|")
    }

    private fun describe(target: SmartspaceTarget, now: Long): RowContent? {
        val action = target.headerAction ?: target.baseAction ?: return null
        val extras = action.extras
        return when {
            target.featureType == FeatureType.FEATURE_FLASHLIGHT -> {
                val since = extras?.getLong(GlanceExtras.SINCE) ?: 0L
                val subtitle = when {
                    since <= 0L -> null
                    now - since < DateUtils.MINUTE_IN_MILLIS -> context.getString(R.string.glance_turned_on_just_now)
                    else -> context.getString(
                        R.string.glance_turned_on,
                        DateUtils.getRelativeTimeSpanString(since, now, DateUtils.MINUTE_IN_MILLIS),
                    )
                }
                RowContent(target, action, context.getString(R.string.glance_flashlight_on), subtitle, true, null, null)
            }
            target.featureType == FeatureType.FEATURE_MEDIA -> {
                @Suppress("DEPRECATION")
                val art = extras?.getParcelable<Bitmap>(GlanceExtras.ART)
                RowContent(target, action, action.title, action.contentDescription ?: action.subtitle, false, art, null)
            }
            extras?.containsKey(GlanceExtras.LEVEL) == true -> {
                val level = extras.getInt(GlanceExtras.LEVEL)
                val charging = extras.getBoolean(GlanceExtras.CHARGING)
                val title = context.getString(R.string.glance_title_with_percent, action.title, level)
                RowContent(target, action, title, extras.getString(GlanceExtras.DETAIL), !charging, null, level / 100f)
            }
            extras?.containsKey(GlanceExtras.TRIGGER) == true -> {
                val trigger = extras.getLong(GlanceExtras.TRIGGER)
                val subtitle = DateUtils.getRelativeTimeSpanString(trigger, now, DateUtils.MINUTE_IN_MILLIS)
                RowContent(target, action, action.title, subtitle, false, null, null)
            }
            else -> RowContent(target, action, action.title, action.subtitle, false, null, null)
        }
    }

    private fun makeRow(content: RowContent, palette: GlancePalette): View {
        val scale = style.textScale
        val action = content.action
        val row = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            if (compact) setPadding(dp(6), dp(5), dp(8), dp(5)) else setPadding(dp(8), dp(8), dp(10), dp(8))
            background = GradientDrawable().apply {
                setColor(if (content.alert) palette.alertRow else palette.row)
                cornerRadius = dp(if (compact) 16 else 18).toFloat()
            }
            foreground = ripple(palette.primary, dp(18).toFloat())
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
                .apply { topMargin = dp(if (compact) 8 else 10) }
        }

        if (content.art != null) {
            row.addView(
                ImageView(context).apply {
                    setImageBitmap(content.art)
                    scaleType = ImageView.ScaleType.CENTER_CROP
                    outlineProvider = roundedOutline(dp(10).toFloat())
                    clipToOutline = true
                },
                LinearLayout.LayoutParams(dp(if (compact) 32 else 38), dp(if (compact) 32 else 38)),
            )
        } else {
            val circle = FrameLayout(context).apply {
                background = GradientDrawable().apply {
                    shape = GradientDrawable.OVAL
                    setColor(if (content.alert) palette.alertIconBg else palette.iconBg)
                }
            }
            circle.addView(
                ImageView(context).apply {
                    setImageDrawable(BcSmartSpaceUtil.getIconDrawable(action.icon, context))
                    imageTintList = ColorStateList.valueOf(if (content.alert) palette.alertIconFg else palette.iconFg)
                },
                LayoutParams(dp(18), dp(18), Gravity.CENTER),
            )
            val circleSize = dp(if (compact) 30 else 36)
            row.addView(circle, LinearLayout.LayoutParams(circleSize, circleSize))
        }

        val texts = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL }
        val textColor = if (content.alert) palette.alertText else palette.primary
        val title = TextView(context).apply {
            text = content.title
            maxLines = 1
            ellipsize = TextUtils.TruncateAt.END
            setTextColor(textColor)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, (if (compact) 14f else 15f) * scale)
        }
        fonts.setCustomFont(title, R.id.font_body_medium)
        texts.addView(title)
        if (!content.subtitle.isNullOrBlank()) {
            val subtitle = TextView(context).apply {
                text = content.subtitle
                maxLines = 1
                ellipsize = TextUtils.TruncateAt.END
                setTextColor(if (content.alert) ColorUtils.setAlphaComponent(textColor, 0xCC) else palette.secondary)
                setTextSize(TypedValue.COMPLEX_UNIT_SP, (if (compact) 12f else 12.5f) * scale)
            }
            fonts.setCustomFont(subtitle, R.id.font_body)
            texts.addView(
                subtitle,
                LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT)
                    .apply { topMargin = dp(2) },
            )
        }
        content.progress?.let { fraction ->
            texts.addView(
                ProgressLine(context, fraction, palette.track, if (content.alert) palette.alertIconBg else palette.progress),
                LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(4)).apply { topMargin = dp(6) },
            )
        }
        row.addView(
            texts,
            LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply { marginStart = dp(12) },
        )

        action.controls.forEach { control ->
            val view = if (control.icon != null) {
                ImageView(context).apply {
                    setImageDrawable(BcSmartSpaceUtil.getIconDrawable(control.icon, context))
                    imageTintList = ColorStateList.valueOf(textColor)
                    contentDescription = control.title
                    scaleType = ImageView.ScaleType.CENTER_INSIDE
                    setPadding(dp(6), dp(6), dp(6), dp(6))
                    background = ripple(textColor, dp(20).toFloat())
                    layoutParams = LinearLayout.LayoutParams(dp(if (compact) 32 else 36), dp(if (compact) 32 else 36))
                }
            } else {
                TextView(context).apply {
                    text = control.title
                    maxLines = 1
                    setTextColor(if (content.alert) palette.alertChipFg else palette.chipFg)
                    setTextSize(TypedValue.COMPLEX_UNIT_SP, (if (compact) 13f else 13.5f) * scale)
                    if (compact) setPadding(dp(12), dp(5), dp(12), dp(5)) else setPadding(dp(14), dp(7), dp(14), dp(7))
                    background = GradientDrawable().apply {
                        setColor(if (content.alert) palette.alertChipBg else palette.chipBg)
                        cornerRadius = dp(18).toFloat()
                    }
                    foreground = ripple(palette.primary, dp(18).toFloat())
                    layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT)
                        .apply { marginStart = dp(8) }
                }.also { fonts.setCustomFont(it, R.id.font_button) }
            }
            BcSmartSpaceUtil.setOnClickListener(view, control, null, TAG)
            row.addView(view)
        }

        if (action.hasIntent) {
            BcSmartSpaceUtil.setOnClickListener(row, action, null, TAG)
        }
        return row
    }

    private fun ripple(color: Int, radius: Float) = RippleDrawable(
        ColorStateList.valueOf(ColorUtils.setAlphaComponent(color, 0x33)),
        null,
        GradientDrawable().apply {
            setColor(Color.WHITE)
            cornerRadius = radius
        },
    )

    private fun roundedOutline(radius: Float) = object : ViewOutlineProvider() {
        override fun getOutline(view: View, outline: Outline) {
            outline.setRoundRect(0, 0, view.width, view.height, radius)
        }
    }

    private fun minuteTicks(): Flow<Unit> = flow {
        while (true) {
            emit(Unit)
            delay(DateUtils.MINUTE_IN_MILLIS)
        }
    }

    private fun dp(value: Int): Int = (value * density).roundToInt()

    /** A thin rounded bar, for the battery level. */
    private class ProgressLine(context: Context, private val fraction: Float, track: Int, fill: Int) : View(context) {
        private val trackPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = track }
        private val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = fill }
        private val rect = RectF()

        override fun onDraw(canvas: Canvas) {
            val radius = height / 2f
            rect.set(0f, 0f, width.toFloat(), height.toFloat())
            canvas.drawRoundRect(rect, radius, radius, trackPaint)
            rect.right = width * fraction.coerceIn(0f, 1f)
            canvas.drawRoundRect(rect, radius, radius, fillPaint)
        }
    }

    companion object {
        private const val TAG = "GlanceWidgetView"
        private const val APP_WIDGET_MAX_ROWS = 3
    }
}

/** The widget's settings, read in one go. */
data class GlanceWidgetStyle(
    val maxRows: Int = 1,
    val cardColor: ColorOption = ColorOption.Default,
    val opacity: Float = 1f,
    val radiusDp: Float = 28f,
    val textScale: Float = 1f,
    val textColor: ColorOption = ColorOption.Default,
    val lookaheadHours: Int = 24,
    val weatherDetails: Boolean = true,
    val weatherApp: String = "",
    val calendarApp: String = "",
) {
    companion object {
        fun flow(prefs: PreferenceManager2): Flow<GlanceWidgetStyle> = combine(
            listOf(
                prefs.smartspaceWidgetSize.get(),
                prefs.smartspaceCardColor.get(),
                prefs.smartspaceCardOpacity.get(),
                prefs.smartspaceCardRadius.get(),
                prefs.smartspaceTextScale.get(),
                prefs.smartspaceTextColor.get(),
                prefs.smartspaceEventLookahead.get(),
                prefs.smartspaceWeatherDetails.get(),
                prefs.smartspaceWeatherApp.get(),
                prefs.smartspaceCalendarApp.get(),
            ),
        ) { values ->
            GlanceWidgetStyle(
                maxRows = if (values[0] == GLANCE_SIZE_LARGE) 2 else 1,
                cardColor = values[1] as ColorOption,
                opacity = (values[2] as Float).coerceIn(0.2f, 1f),
                radiusDp = values[3] as Float,
                textScale = (values[4] as Float).coerceIn(0.5f, 2f),
                textColor = values[5] as ColorOption,
                lookaheadHours = values[6] as Int,
                weatherDetails = values[7] as Boolean,
                weatherApp = values[8] as String,
                calendarApp = values[9] as String,
            )
        }
    }
}

/** Material You colours for the widget, from the wallpaper unless a colour is picked. */
data class GlancePalette(
    val card: Int,
    val row: Int,
    val primary: Int,
    val secondary: Int,
    val iconBg: Int,
    val iconFg: Int,
    val alertRow: Int,
    val alertIconBg: Int,
    val alertIconFg: Int,
    val alertText: Int,
    val chipBg: Int,
    val chipFg: Int,
    val alertChipBg: Int,
    val alertChipFg: Int,
    val track: Int,
    val progress: Int,
) {
    companion object {
        fun resolve(context: Context, style: GlanceWidgetStyle): GlancePalette {
            val custom = style.cardColor.takeIf { it !is ColorOption.Default }
                ?.colorPreferenceEntry?.lightColor?.invoke(context)
            val dark = if (custom != null) ColorUtils.calculateLuminance(custom) < 0.5 else Utilities.isDarkTheme(context)
            fun c(token: app.thelauncher.theme.color.tokens.ColorToken) = token.resolveColor(context)
            val baseCard = custom ?: if (dark) c(ColorTokens.Neutral1_800) else c(ColorTokens.Neutral1_50)
            val card = ColorUtils.setAlphaComponent(baseCard, (style.opacity * 255).roundToInt())
            val row = when {
                custom != null -> ColorUtils.blendARGB(custom, if (dark) Color.WHITE else Color.BLACK, if (dark) 0.10f else 0.06f)
                dark -> c(ColorTokens.Accent2_800)
                else -> c(ColorTokens.Accent2_100)
            }
            val pickedText = style.textColor.takeIf { it !is ColorOption.Default }
                ?.colorPreferenceEntry?.lightColor?.invoke(context)
            val primary = pickedText ?: if (dark) c(ColorTokens.Neutral1_50) else c(ColorTokens.Neutral1_900)
            val secondary = if (pickedText != null) {
                ColorUtils.setAlphaComponent(pickedText, 0xB3)
            } else if (dark) {
                c(ColorTokens.Neutral2_200)
            } else {
                c(ColorTokens.Neutral2_700)
            }
            return if (dark) {
                GlancePalette(
                    card = card,
                    row = row,
                    primary = primary,
                    secondary = secondary,
                    iconBg = c(ColorTokens.Accent1_200),
                    iconFg = c(ColorTokens.Accent1_800),
                    alertRow = c(ColorTokens.Accent3_800),
                    alertIconBg = c(ColorTokens.Accent3_200),
                    alertIconFg = c(ColorTokens.Accent3_800),
                    alertText = c(ColorTokens.Accent3_100),
                    chipBg = c(ColorTokens.Accent1_200),
                    chipFg = c(ColorTokens.Accent1_800),
                    alertChipBg = c(ColorTokens.Accent3_200),
                    alertChipFg = c(ColorTokens.Accent3_800),
                    track = c(ColorTokens.Neutral2_600),
                    progress = c(ColorTokens.Accent1_200),
                )
            } else {
                GlancePalette(
                    card = card,
                    row = row,
                    primary = primary,
                    secondary = secondary,
                    iconBg = c(ColorTokens.Accent1_600),
                    iconFg = c(ColorTokens.Accent1_10),
                    alertRow = c(ColorTokens.Accent3_100),
                    alertIconBg = c(ColorTokens.Accent3_600),
                    alertIconFg = c(ColorTokens.Accent3_10),
                    alertText = c(ColorTokens.Accent3_800),
                    chipBg = c(ColorTokens.Accent1_600),
                    chipFg = c(ColorTokens.Accent1_10),
                    alertChipBg = c(ColorTokens.Accent3_600),
                    alertChipFg = c(ColorTokens.Accent3_10),
                    track = c(ColorTokens.Neutral2_200),
                    progress = c(ColorTokens.Accent1_600),
                )
            }
        }
    }
}
