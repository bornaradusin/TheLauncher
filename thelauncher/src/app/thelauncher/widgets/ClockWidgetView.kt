package app.thelauncher.widgets

import android.app.AlarmManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.res.ColorStateList
import android.provider.AlarmClock
import android.text.TextUtils
import android.text.format.DateFormat
import android.util.TypedValue
import android.view.Gravity
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import android.view.ViewGroup.LayoutParams.WRAP_CONTENT
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextClock
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.core.view.isVisible
import com.android.launcher3.R
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.max
import kotlin.math.min

/** The time in large digits, the date under it and the next alarm when one is set. */
class ClockWidgetView(context: Context) : WidgetCardView(context) {

    private val column = LinearLayout(context)
    private val time = TextClock(context)
    private val date = TextClock(context)
    private val alarmRow = LinearLayout(context)
    private val alarmIcon = ImageView(context)
    private val alarmText = TextView(context)

    private val alarmReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            updateAlarm()
        }
    }

    init {
        column.orientation = LinearLayout.VERTICAL
        column.gravity = Gravity.CENTER
        column.setPadding(dp(12), dp(8), dp(12), dp(8))

        time.format12Hour = "h:mm"
        time.format24Hour = "HH:mm"
        time.includeFontPadding = false
        time.maxLines = 1
        time.gravity = Gravity.CENTER
        fonts.setCustomFont(time, R.id.font_heading)

        val datePattern = DateFormat.getBestDateTimePattern(Locale.getDefault(), "EEEdMMM")
        date.format12Hour = datePattern
        date.format24Hour = datePattern
        date.includeFontPadding = false
        date.maxLines = 1
        date.ellipsize = TextUtils.TruncateAt.END
        date.gravity = Gravity.CENTER
        fonts.setCustomFont(date, R.id.font_body)

        alarmRow.orientation = LinearLayout.HORIZONTAL
        alarmRow.gravity = Gravity.CENTER
        alarmIcon.setImageResource(R.drawable.ic_smartspace_alarm)
        alarmText.includeFontPadding = false
        alarmText.maxLines = 1
        fonts.setCustomFont(alarmText, R.id.font_body)
        alarmRow.addView(alarmIcon, LinearLayout.LayoutParams(dp(14), dp(14)))
        alarmRow.addView(
            alarmText,
            LinearLayout.LayoutParams(WRAP_CONTENT, WRAP_CONTENT).apply { marginStart = dp(4) },
        )

        column.addView(time, LinearLayout.LayoutParams(WRAP_CONTENT, WRAP_CONTENT))
        column.addView(
            date,
            LinearLayout.LayoutParams(WRAP_CONTENT, WRAP_CONTENT).apply { topMargin = dp(4) },
        )
        column.addView(
            alarmRow,
            LinearLayout.LayoutParams(WRAP_CONTENT, WRAP_CONTENT).apply { topMargin = dp(4) },
        )
        addView(column, LayoutParams(MATCH_PARENT, MATCH_PARENT))

        setOnClickListener {
            if (!startSafely(Intent(AlarmClock.ACTION_SHOW_ALARMS))) {
                startSafely(Intent(android.provider.Settings.ACTION_DATE_SETTINGS))
            }
        }
        applyPalette()
    }

    override fun applyPalette() {
        time.setTextColor(palette.primary)
        date.setTextColor(palette.secondary)
        alarmText.setTextColor(palette.secondary)
        alarmIcon.imageTintList = ColorStateList.valueOf(palette.accent)
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        ContextCompat.registerReceiver(
            context.applicationContext,
            alarmReceiver,
            IntentFilter(AlarmManager.ACTION_NEXT_ALARM_CLOCK_CHANGED),
            ContextCompat.RECEIVER_EXPORTED,
        )
        updateAlarm()
    }

    override fun onDetachedFromWindow() {
        runCatching { context.applicationContext.unregisterReceiver(alarmReceiver) }
        super.onDetachedFromWindow()
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        post { fitText(w, h) }
    }

    /** The digits grow with the widget; the date and alarm follow at a readable size. */
    private fun fitText(w: Int, h: Int) {
        if (w <= 0 || h <= 0) return
        val timePx = max(min(h * 0.40f, w * 0.26f), sp(20f))
        val smallPx = max(min(timePx * 0.26f, sp(18f)), sp(11f))
        time.setTextSize(TypedValue.COMPLEX_UNIT_PX, timePx)
        date.setTextSize(TypedValue.COMPLEX_UNIT_PX, smallPx)
        alarmText.setTextSize(TypedValue.COMPLEX_UNIT_PX, smallPx)
        // A one-row widget has room for the time and date only.
        alarmRow.isVisible = alarmText.text.isNotEmpty() && h >= dp(96)
        date.isVisible = h >= dp(56)
    }

    private fun updateAlarm() {
        val next = context.getSystemService(AlarmManager::class.java)?.nextAlarmClock
        if (next == null) {
            alarmText.text = ""
        } else {
            val skeleton = if (DateFormat.is24HourFormat(context)) "EEEHm" else "EEEhma"
            val pattern = DateFormat.getBestDateTimePattern(Locale.getDefault(), skeleton)
            alarmText.text = SimpleDateFormat(pattern, Locale.getDefault()).format(Date(next.triggerTime))
        }
        fitText(width, height)
    }
}
