package app.thelauncher.widgets

import android.Manifest
import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import android.text.TextUtils
import android.text.format.DateFormat
import android.view.View
import app.thelauncher.smartspace.WeatherApp
import com.android.launcher3.R
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

/**
 * The weather where the phone is: now, the next hours, and the coming days when the widget is tall
 * enough for them.
 */
class WeatherWidgetView(context: Context) : WidgetCardView(context) {

    private val textPaint = TextPaint(Paint.ANTI_ALIAS_FLAG)
    private val iconPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val iconPath = Path()
    private val iconRect = RectF()
    private val onWeatherChanged = Runnable { invalidate() }

    init {
        setOnClickListener {
            val store = WeatherStore
            when {
                !store.hasPermission(context) -> requestPermission(Manifest.permission.ACCESS_COARSE_LOCATION)
                store.weather == null -> store.refresh(context, force = true)
                else -> {
                    store.refresh(context)
                    WeatherApp.intent(context)?.let { startSafely(it) }
                }
            }
        }
    }

    override fun applyPalette() {
        invalidate()
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        WeatherStore.addListener(onWeatherChanged)
        WeatherStore.refresh(context)
    }

    override fun onDetachedFromWindow() {
        WeatherStore.removeListener(onWeatherChanged)
        super.onDetachedFromWindow()
    }

    override fun onWindowVisibilityChanged(visibility: Int) {
        super.onWindowVisibilityChanged(visibility)
        if (visibility == View.VISIBLE) WeatherStore.refresh(context)
    }

    override fun onWindowFocusChanged(hasWindowFocus: Boolean) {
        super.onWindowFocusChanged(hasWindowFocus)
        // Back from the permission dialog.
        if (hasWindowFocus) WeatherStore.refresh(context)
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        textPaint.typeface = headingTypeface
        val weather = WeatherStore.weather
        if (weather == null) {
            drawMessage(canvas)
            return
        }

        val pad = dp(16).toFloat()
        val left = pad
        val right = width - pad
        var y = pad

        // Now: the icon, the temperature, and the conditions and place beside them.
        val headerHeight = min(dp(52).toFloat(), height - 2 * pad)
        if (headerHeight < dp(20)) return
        drawIcon(canvas, left + headerHeight / 2f, y + headerHeight / 2f, headerHeight, weather.code, weather.day)
        textPaint.textAlign = Paint.Align.LEFT
        textPaint.color = palette.primary
        textPaint.textSize = headerHeight * 0.62f
        val temp = degrees(weather.temp)
        val tempX = left + headerHeight + dp(8)
        canvas.drawText(temp, tempX, y + headerHeight / 2f - (textPaint.ascent() + textPaint.descent()) / 2f, textPaint)
        val afterTemp = tempX + textPaint.measureText(temp) + dp(12)
        if (right - afterTemp > dp(60)) {
            textPaint.textAlign = Paint.Align.RIGHT
            textPaint.textSize = sp(14f)
            val room = right - afterTemp
            val condition = TextUtils.ellipsize(conditionName(weather.code), textPaint, room, TextUtils.TruncateAt.END)
            canvas.drawText(condition, 0, condition.length, right, y + headerHeight * 0.40f, textPaint)
            if (weather.place.isNotEmpty()) {
                textPaint.textSize = sp(12f)
                textPaint.color = palette.secondary
                val place = TextUtils.ellipsize(weather.place, textPaint, room, TextUtils.TruncateAt.END)
                canvas.drawText(place, 0, place.length, right, y + headerHeight * 0.82f, textPaint)
            }
        }
        y += headerHeight + dp(10)

        // The next hours.
        val hourHeight = dp(70).toFloat()
        val now = System.currentTimeMillis()
        val hours = weather.hours.filter { it.time > now - 30 * 60 * 1000L }
        if (hours.isNotEmpty() && y + hourHeight <= height - pad + 1f) {
            val columns = ((right - left) / dp(48)).toInt().coerceIn(3, 6)
            val shown = hours.take(columns)
            val cell = (right - left) / shown.size
            val hourFormat = SimpleDateFormat(if (DateFormat.is24HourFormat(context)) "HH" else "h a", Locale.getDefault())
            textPaint.textAlign = Paint.Align.CENTER
            shown.forEachIndexed { index, hour ->
                val cx = left + cell * (index + 0.5f)
                textPaint.textSize = sp(11f)
                textPaint.color = palette.secondary
                canvas.drawText(hourFormat.format(Date(hour.time)), cx, y - textPaint.ascent(), textPaint)
                drawIcon(canvas, cx, y + hourHeight * 0.50f, dp(24).toFloat(), hour.code, hour.day)
                textPaint.textSize = sp(13f)
                textPaint.color = palette.primary
                canvas.drawText(degrees(hour.temp), cx, y + hourHeight - textPaint.descent(), textPaint)
            }
            y += hourHeight + dp(10)
        }

        // The coming days, as many as fit.
        val rowHeight = dp(32).toFloat()
        val rows = min(((height - pad - y) / rowHeight).toInt(), min(5, weather.days.size - 1))
        if (rows <= 0) return
        val dayFormat = SimpleDateFormat(if (width >= dp(240)) "EEEE" else "EEE", Locale.getDefault())
        val calendar = Calendar.getInstance()
        for (i in 1..rows) {
            val day = weather.days[i]
            calendar.timeInMillis = now
            calendar.add(Calendar.DAY_OF_YEAR, i)
            val centre = y + rowHeight / 2f
            textPaint.textSize = sp(13f)
            val baseline = centre - (textPaint.ascent() + textPaint.descent()) / 2f
            textPaint.textAlign = Paint.Align.LEFT
            textPaint.color = palette.primary
            canvas.drawText(dayFormat.format(calendar.time), left, baseline, textPaint)
            textPaint.textAlign = Paint.Align.RIGHT
            canvas.drawText(degrees(day.max), right, baseline, textPaint)
            textPaint.color = palette.secondary
            canvas.drawText(degrees(day.min), right - dp(40), baseline, textPaint)
            drawIcon(canvas, right - dp(96), centre, dp(22).toFloat(), day.code, true)
            y += rowHeight
        }
    }

    private fun degrees(value: Int) = "$value°"

    private fun drawMessage(canvas: Canvas) {
        val text = context.getString(
            when (WeatherStore.state) {
                WeatherStore.State.NEEDS_PERMISSION -> R.string.widget_weather_permission
                WeatherStore.State.NO_LOCATION -> R.string.widget_weather_no_location
                WeatherStore.State.FAILED -> R.string.widget_weather_failed
                else -> R.string.widget_weather_loading
            },
        )
        textPaint.textAlign = Paint.Align.LEFT
        textPaint.textSize = sp(14f)
        textPaint.color = palette.secondary
        val room = width - dp(32)
        if (room <= 0) return
        val layout = StaticLayout.Builder.obtain(text, 0, text.length, textPaint, room)
            .setAlignment(Layout.Alignment.ALIGN_CENTER)
            .build()
        canvas.save()
        canvas.translate(dp(16).toFloat(), (height - layout.height) / 2f)
        layout.draw(canvas)
        canvas.restore()
    }

    private fun conditionName(code: Int): String = context.getString(
        when (kind(code)) {
            Kind.CLEAR -> R.string.widget_weather_clear
            Kind.PARTLY -> R.string.widget_weather_partly_cloudy
            Kind.CLOUDY -> R.string.widget_weather_cloudy
            Kind.FOG -> R.string.widget_weather_fog
            Kind.RAIN -> R.string.widget_weather_rain
            Kind.SNOW -> R.string.widget_weather_snow
            Kind.THUNDER -> R.string.widget_weather_thunder
        },
    )

    private enum class Kind { CLEAR, PARTLY, CLOUDY, FOG, RAIN, SNOW, THUNDER }

    /** Open-Meteo reports the weather as a WMO code. */
    private fun kind(code: Int): Kind = when (code) {
        0 -> Kind.CLEAR
        1, 2 -> Kind.PARTLY
        3 -> Kind.CLOUDY
        45, 48 -> Kind.FOG
        in 51..67, in 80..82 -> Kind.RAIN
        in 71..77, 85, 86 -> Kind.SNOW
        in 95..99 -> Kind.THUNDER
        else -> Kind.CLOUDY
    }

    /** A small picture of the weather, centred on ([cx], [cy]) in a square of [size]. */
    private fun drawIcon(canvas: Canvas, cx: Float, cy: Float, size: Float, code: Int, day: Boolean) {
        when (kind(code)) {
            Kind.CLEAR -> drawSunOrMoon(canvas, cx, cy, size * 0.30f, day)
            Kind.PARTLY -> {
                drawSunOrMoon(canvas, cx - size * 0.16f, cy - size * 0.16f, size * 0.20f, day)
                drawCloud(canvas, cx + size * 0.06f, cy + size * 0.10f, size * 0.74f)
            }
            Kind.CLOUDY -> drawCloud(canvas, cx, cy, size * 0.90f)
            Kind.FOG -> {
                iconPaint.style = Paint.Style.STROKE
                iconPaint.strokeCap = Paint.Cap.ROUND
                iconPaint.strokeWidth = size * 0.09f
                iconPaint.color = cloudColor()
                for (i in -1..1) {
                    val lineY = cy + i * size * 0.22f
                    canvas.drawLine(cx - size * 0.34f, lineY, cx + size * 0.34f, lineY, iconPaint)
                }
            }
            Kind.RAIN -> {
                drawCloud(canvas, cx, cy - size * 0.12f, size * 0.84f)
                iconPaint.style = Paint.Style.STROKE
                iconPaint.strokeCap = Paint.Cap.ROUND
                iconPaint.strokeWidth = size * 0.07f
                iconPaint.color = COLOR_RAIN
                for (i in -1..1) {
                    val x = cx + i * size * 0.20f
                    canvas.drawLine(x + size * 0.04f, cy + size * 0.22f, x - size * 0.04f, cy + size * 0.40f, iconPaint)
                }
            }
            Kind.SNOW -> {
                drawCloud(canvas, cx, cy - size * 0.12f, size * 0.84f)
                iconPaint.style = Paint.Style.FILL
                iconPaint.color = if (palette.dark) COLOR_SNOW_ON_DARK else COLOR_RAIN
                for (i in -1..1) {
                    canvas.drawCircle(cx + i * size * 0.20f, cy + size * 0.32f, size * 0.05f, iconPaint)
                }
            }
            Kind.THUNDER -> {
                drawCloud(canvas, cx, cy - size * 0.12f, size * 0.84f)
                iconPaint.style = Paint.Style.FILL
                iconPaint.color = COLOR_SUN
                iconPath.reset()
                iconPath.moveTo(cx + size * 0.04f, cy + size * 0.10f)
                iconPath.lineTo(cx - size * 0.12f, cy + size * 0.30f)
                iconPath.lineTo(cx - size * 0.01f, cy + size * 0.30f)
                iconPath.lineTo(cx - size * 0.06f, cy + size * 0.48f)
                iconPath.lineTo(cx + size * 0.12f, cy + size * 0.24f)
                iconPath.lineTo(cx + size * 0.01f, cy + size * 0.24f)
                iconPath.close()
                canvas.drawPath(iconPath, iconPaint)
            }
        }
    }

    private fun cloudColor() = if (palette.dark) COLOR_CLOUD_ON_DARK else COLOR_CLOUD_ON_LIGHT

    private fun drawSunOrMoon(canvas: Canvas, cx: Float, cy: Float, radius: Float, day: Boolean) {
        iconPaint.style = Paint.Style.FILL
        if (!day) {
            // A crescent: a disc with a second disc cut out of it.
            iconPaint.color = if (palette.dark) COLOR_MOON_ON_DARK else COLOR_MOON_ON_LIGHT
            iconPath.reset()
            iconPath.addCircle(cx, cy, radius, Path.Direction.CW)
            val cut = Path()
            cut.addCircle(cx + radius * 0.45f, cy - radius * 0.30f, radius * 0.85f, Path.Direction.CW)
            iconPath.op(cut, Path.Op.DIFFERENCE)
            canvas.drawPath(iconPath, iconPaint)
            return
        }
        iconPaint.color = COLOR_SUN
        canvas.drawCircle(cx, cy, radius * 0.62f, iconPaint)
        iconPaint.style = Paint.Style.STROKE
        iconPaint.strokeCap = Paint.Cap.ROUND
        iconPaint.strokeWidth = radius * 0.16f
        for (i in 0 until 8) {
            val angle = Math.toRadians(i * 45.0)
            val dx = cos(angle).toFloat()
            val dy = sin(angle).toFloat()
            canvas.drawLine(
                cx + dx * radius * 0.82f,
                cy + dy * radius * 0.82f,
                cx + dx * radius * 1.05f,
                cy + dy * radius * 1.05f,
                iconPaint,
            )
        }
    }

    private fun drawCloud(canvas: Canvas, cx: Float, cy: Float, width: Float) {
        iconPaint.style = Paint.Style.FILL
        iconPaint.color = cloudColor()
        val base = cy + width * 0.20f
        canvas.drawCircle(cx - width * 0.22f, base - width * 0.14f, width * 0.16f, iconPaint)
        canvas.drawCircle(cx + width * 0.02f, base - width * 0.26f, width * 0.22f, iconPaint)
        canvas.drawCircle(cx + width * 0.26f, base - width * 0.13f, width * 0.15f, iconPaint)
        iconRect.set(cx - width * 0.38f, base - width * 0.26f, cx + width * 0.41f, base)
        canvas.drawRoundRect(iconRect, width * 0.13f, width * 0.13f, iconPaint)
    }

    private companion object {
        const val COLOR_SUN = 0xFFFFB300.toInt()
        const val COLOR_RAIN = 0xFF42A5F5.toInt()
        const val COLOR_SNOW_ON_DARK = 0xFFE3F2FD.toInt()
        const val COLOR_CLOUD_ON_DARK = 0xFFCFD8DC.toInt()
        const val COLOR_CLOUD_ON_LIGHT = 0xFF90A4AE.toInt()
        const val COLOR_MOON_ON_DARK = 0xFFFFE082.toInt()
        const val COLOR_MOON_ON_LIGHT = 0xFF7986CB.toInt()
    }
}
