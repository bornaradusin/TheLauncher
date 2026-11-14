package app.thelauncher.widgets

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.os.BatteryManager
import android.os.Build
import android.text.TextPaint
import android.text.TextUtils
import androidx.core.content.ContextCompat
import com.android.launcher3.R
import kotlin.math.min

/** A ring for the phone's battery and one for each connected Bluetooth device that reports its own. */
class BatteryWidgetView(context: Context) : WidgetCardView(context) {

    private data class Entry(val name: String, val level: Int, val charging: Boolean)

    private var entries: List<Entry> = emptyList()
    private var phone = Entry(context.getString(R.string.widget_battery_phone), 0, false)

    private val ringPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
    }
    private val levelPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply { textAlign = Paint.Align.CENTER }
    private val namePaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply { textAlign = Paint.Align.CENTER }
    private val ringBounds = RectF()

    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (intent.action == Intent.ACTION_BATTERY_CHANGED) readPhone(intent)
            reload()
        }
    }

    init {
        setOnClickListener {
            if (needsBluetoothPermission()) {
                requestPermission(Manifest.permission.BLUETOOTH_CONNECT)
            } else {
                startSafely(Intent(Intent.ACTION_POWER_USAGE_SUMMARY))
            }
        }
    }

    override fun applyPalette() {
        invalidate()
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_BATTERY_CHANGED)
            addAction(BluetoothDevice.ACTION_ACL_CONNECTED)
            addAction(BluetoothDevice.ACTION_ACL_DISCONNECTED)
            addAction(ACTION_BLUETOOTH_BATTERY)
        }
        val sticky = ContextCompat.registerReceiver(
            context.applicationContext,
            receiver,
            filter,
            ContextCompat.RECEIVER_EXPORTED,
        )
        if (sticky != null && sticky.action == Intent.ACTION_BATTERY_CHANGED) readPhone(sticky)
        reload()
    }

    override fun onDetachedFromWindow() {
        runCatching { context.applicationContext.unregisterReceiver(receiver) }
        super.onDetachedFromWindow()
    }

    override fun onWindowFocusChanged(hasWindowFocus: Boolean) {
        super.onWindowFocusChanged(hasWindowFocus)
        // Back from the permission dialog, or a device connected while away.
        if (hasWindowFocus) reload()
    }

    private fun readPhone(intent: Intent) {
        val scale = intent.getIntExtra(BatteryManager.EXTRA_SCALE, 100).coerceAtLeast(1)
        val level = 100 * intent.getIntExtra(BatteryManager.EXTRA_LEVEL, 0) / scale
        val status = intent.getIntExtra(BatteryManager.EXTRA_STATUS, -1)
        val charging = status == BatteryManager.BATTERY_STATUS_CHARGING || status == BatteryManager.BATTERY_STATUS_FULL
        phone = phone.copy(level = level.coerceIn(0, 100), charging = charging)
    }

    private fun reload() {
        entries = listOf(phone) + bluetoothEntries()
        invalidate()
    }

    private fun needsBluetoothPermission(): Boolean =
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_CONNECT) !=
            PackageManager.PERMISSION_GRANTED

    /** Android keeps a connected device's battery level out of the public API, so it is read by name. */
    @SuppressLint("MissingPermission")
    private fun bluetoothEntries(): List<Entry> {
        if (needsBluetoothPermission()) return emptyList()
        return runCatching {
            val adapter = context.getSystemService(BluetoothManager::class.java)?.adapter
            if (adapter == null || !adapter.isEnabled) {
                emptyList()
            } else {
                adapter.bondedDevices.orEmpty().mapNotNull { device -> bluetoothEntry(device) }
            }
        }.getOrDefault(emptyList())
    }

    @SuppressLint("MissingPermission")
    private fun bluetoothEntry(device: BluetoothDevice): Entry? {
        val connected = runCatching {
            device.javaClass.getMethod("isConnected").invoke(device) as? Boolean
        }.getOrNull() ?: false
        if (!connected) return null
        val level = runCatching {
            device.javaClass.getMethod("getBatteryLevel").invoke(device) as? Int
        }.getOrNull() ?: -1
        if (level !in 0..100) return null
        val alias = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) device.alias else null
        val name = alias ?: device.name ?: return null
        return Entry(name, level, false)
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val shown = entries.take(MAX_RINGS)
        if (shown.isEmpty()) return

        val hint = if (needsBluetoothPermission()) context.getString(R.string.widget_battery_bluetooth_hint) else null
        namePaint.typeface = headingTypeface
        levelPaint.typeface = headingTypeface
        namePaint.textSize = sp(12f)
        namePaint.color = palette.secondary

        val pad = dp(14).toFloat()
        val nameHeight = namePaint.fontSpacing
        val hintHeight = if (hint != null && height >= dp(120)) nameHeight + dp(4) else 0f
        val cellWidth = (width - 2 * pad) / shown.size
        val showNames = height >= dp(88)
        val ringSpace = height - 2 * pad - hintHeight - if (showNames) nameHeight + dp(6) else 0f
        val diameter = min(cellWidth - dp(10), ringSpace)
        if (diameter <= dp(12)) return
        val stroke = (diameter * 0.11f).coerceIn(dp(3).toFloat(), dp(9).toFloat())
        ringPaint.strokeWidth = stroke
        levelPaint.textSize = diameter * 0.28f
        levelPaint.color = palette.primary

        val blockHeight = diameter + if (showNames) nameHeight + dp(6) else 0f
        val top = pad + (height - 2 * pad - hintHeight - blockHeight) / 2f
        shown.forEachIndexed { index, entry ->
            val cx = pad + cellWidth * (index + 0.5f)
            val radius = (diameter - stroke) / 2f
            val cy = top + diameter / 2f
            ringBounds.set(cx - radius, cy - radius, cx + radius, cy + radius)
            ringPaint.color = palette.track
            canvas.drawArc(ringBounds, 0f, 360f, false, ringPaint)
            ringPaint.color = when {
                entry.charging -> COLOR_CHARGING
                entry.level <= 15 -> COLOR_LOW
                else -> palette.accent
            }
            canvas.drawArc(ringBounds, -90f, 360f * entry.level / 100f, false, ringPaint)
            val label = context.getString(R.string.n_percent, entry.level)
            canvas.drawText(label, cx, cy - (levelPaint.descent() + levelPaint.ascent()) / 2f, levelPaint)
            if (showNames) {
                val name = TextUtils.ellipsize(entry.name, namePaint, cellWidth - dp(6), TextUtils.TruncateAt.END)
                canvas.drawText(name, 0, name.length, cx, top + diameter + dp(6) - namePaint.ascent(), namePaint)
            }
        }
        if (hintHeight > 0f && hint != null) {
            val text = TextUtils.ellipsize(hint, namePaint, width - 2 * pad, TextUtils.TruncateAt.END)
            canvas.drawText(text, 0, text.length, width / 2f, height - pad - namePaint.descent(), namePaint)
        }
    }

    private companion object {
        const val MAX_RINGS = 4
        const val ACTION_BLUETOOTH_BATTERY = "android.bluetooth.device.action.BATTERY_LEVEL_CHANGED"
        const val COLOR_CHARGING = 0xFF4CAF50.toInt()
        const val COLOR_LOW = 0xFFE53935.toInt()
    }
}
