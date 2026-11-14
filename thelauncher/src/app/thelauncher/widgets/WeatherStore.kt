package app.thelauncher.widgets

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.Geocoder
import android.location.Location
import android.location.LocationManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.util.Log
import androidx.core.content.ContextCompat
import java.net.HttpURLConnection
import java.net.URL
import java.util.Locale
import java.util.concurrent.CopyOnWriteArraySet
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.math.roundToInt
import org.json.JSONObject

/**
 * The forecast for where the phone is, from Open-Meteo (free, no account). Only the rough location
 * is sent, rounded to about a kilometre. The last forecast is kept so the widget is never empty
 * after a restart.
 */
object WeatherStore {

    enum class State { EMPTY, LOADING, READY, NEEDS_PERMISSION, NO_LOCATION, FAILED }

    data class Hour(val time: Long, val temp: Int, val code: Int, val day: Boolean)

    data class Day(val min: Int, val max: Int, val code: Int)

    data class Weather(
        val fetched: Long,
        val place: String,
        val temp: Int,
        val code: Int,
        val day: Boolean,
        val hours: List<Hour>,
        val days: List<Day>,
    )

    private const val TAG = "WeatherStore"
    private const val PREFS = "widget_weather"
    private const val KEY_JSON = "json"
    private const val KEY_PLACE = "place"
    private const val KEY_FETCHED = "fetched"
    private const val KEY_LAT = "lat"
    private const val KEY_LON = "lon"
    private const val MAX_AGE = 30 * 60 * 1000L

    private val main = Handler(Looper.getMainLooper())
    private val listeners = CopyOnWriteArraySet<Runnable>()
    private val loading = AtomicBoolean(false)
    private var restored = false

    @Volatile
    var weather: Weather? = null
        private set

    @Volatile
    var state: State = State.EMPTY
        private set

    fun addListener(listener: Runnable) {
        listeners.add(listener)
    }

    fun removeListener(listener: Runnable) {
        listeners.remove(listener)
    }

    fun hasPermission(context: Context): Boolean =
        ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) ==
            PackageManager.PERMISSION_GRANTED

    /** Loads a new forecast when the one held is older than half an hour, or when [force] is set. */
    fun refresh(context: Context, force: Boolean = false) {
        val app = context.applicationContext
        restore(app)
        val current = weather
        if (!force && current != null && System.currentTimeMillis() - current.fetched < MAX_AGE) return
        if (!hasPermission(app)) {
            if (current == null) publish(State.NEEDS_PERMISSION)
            return
        }
        if (!loading.compareAndSet(false, true)) return
        if (current == null) publish(State.LOADING)
        locate(app) { location ->
            Thread {
                try {
                    load(app, location)
                } finally {
                    loading.set(false)
                }
            }.start()
        }
    }

    private fun prefs(context: Context) = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    private fun restore(context: Context) {
        if (restored) return
        restored = true
        val prefs = prefs(context)
        val json = prefs.getString(KEY_JSON, null) ?: return
        weather = runCatching {
            parse(json, prefs.getString(KEY_PLACE, "").orEmpty(), prefs.getLong(KEY_FETCHED, 0L))
        }.getOrNull()
        if (weather != null) state = State.READY
    }

    private fun publish(newState: State) {
        state = newState
        main.post { listeners.forEach { it.run() } }
    }

    /** The last place Android knows, else a fresh fix, else the place of the last forecast. */
    @SuppressLint("MissingPermission")
    private fun locate(context: Context, onResult: (Location?) -> Unit) {
        val manager = context.getSystemService(LocationManager::class.java)
        val providers = runCatching { manager?.getProviders(true) }.getOrNull().orEmpty()
        val last = providers
            .mapNotNull { runCatching { manager?.getLastKnownLocation(it) }.getOrNull() }
            .maxByOrNull { it.time }
        if (last != null || manager == null || Build.VERSION.SDK_INT < Build.VERSION_CODES.R) {
            onResult(last)
            return
        }
        val provider = when {
            LocationManager.NETWORK_PROVIDER in providers -> LocationManager.NETWORK_PROVIDER
            providers.isNotEmpty() -> providers.first()
            else -> null
        }
        if (provider == null) {
            onResult(null)
            return
        }
        val asked = runCatching {
            manager.getCurrentLocation(provider, null, context.mainExecutor) { onResult(it) }
        }
        if (asked.isFailure) onResult(null)
    }

    private fun load(context: Context, location: Location?) {
        val prefs = prefs(context)
        val lat: Double
        val lon: Double
        if (location != null) {
            lat = (location.latitude * 100).roundToInt() / 100.0
            lon = (location.longitude * 100).roundToInt() / 100.0
        } else if (prefs.contains(KEY_LAT)) {
            lat = prefs.getFloat(KEY_LAT, 0f).toDouble()
            lon = prefs.getFloat(KEY_LON, 0f).toDouble()
        } else {
            publish(if (weather == null) State.NO_LOCATION else State.READY)
            return
        }
        try {
            val unit = if (Locale.getDefault().country in FAHRENHEIT_COUNTRIES) "fahrenheit" else "celsius"
            val url = "https://api.open-meteo.com/v1/forecast" +
                "?latitude=" + String.format(Locale.US, "%.2f", lat) +
                "&longitude=" + String.format(Locale.US, "%.2f", lon) +
                "&current=temperature_2m,weather_code,is_day" +
                "&hourly=temperature_2m,weather_code,is_day" +
                "&daily=weather_code,temperature_2m_max,temperature_2m_min" +
                "&timeformat=unixtime&timezone=auto&forecast_days=6&temperature_unit=" + unit
            val connection = URL(url).openConnection() as HttpURLConnection
            connection.connectTimeout = 10_000
            connection.readTimeout = 10_000
            val json = try {
                connection.inputStream.bufferedReader().use { it.readText() }
            } finally {
                connection.disconnect()
            }
            val place = placeName(context, lat, lon) ?: prefs.getString(KEY_PLACE, "").orEmpty()
            val now = System.currentTimeMillis()
            weather = parse(json, place, now)
            prefs.edit()
                .putString(KEY_JSON, json)
                .putString(KEY_PLACE, place)
                .putLong(KEY_FETCHED, now)
                .putFloat(KEY_LAT, lat.toFloat())
                .putFloat(KEY_LON, lon.toFloat())
                .apply()
            publish(State.READY)
        } catch (t: Throwable) {
            Log.w(TAG, "Couldn't load the forecast", t)
            publish(if (weather == null) State.FAILED else State.READY)
        }
    }

    @Suppress("DEPRECATION")
    private fun placeName(context: Context, lat: Double, lon: Double): String? = runCatching {
        val address = Geocoder(context, Locale.getDefault()).getFromLocation(lat, lon, 1)?.firstOrNull()
        address?.locality ?: address?.subAdminArea ?: address?.adminArea
    }.getOrNull()

    private fun parse(json: String, place: String, fetched: Long): Weather {
        val root = JSONObject(json)
        val current = root.getJSONObject("current")
        val hourly = root.getJSONObject("hourly")
        val hourTimes = hourly.getJSONArray("time")
        val hourTemps = hourly.getJSONArray("temperature_2m")
        val hourCodes = hourly.getJSONArray("weather_code")
        val hourDay = hourly.getJSONArray("is_day")
        val hours = ArrayList<Hour>()
        for (i in 0 until hourTimes.length()) {
            if (hourTemps.isNull(i)) continue
            hours.add(
                Hour(
                    time = hourTimes.getLong(i) * 1000L,
                    temp = hourTemps.getDouble(i).roundToInt(),
                    code = hourCodes.optInt(i, 0),
                    day = hourDay.optInt(i, 1) == 1,
                ),
            )
        }
        val daily = root.getJSONObject("daily")
        val dayCodes = daily.getJSONArray("weather_code")
        val dayMax = daily.getJSONArray("temperature_2m_max")
        val dayMin = daily.getJSONArray("temperature_2m_min")
        val days = ArrayList<Day>()
        for (i in 0 until dayCodes.length()) {
            if (dayMax.isNull(i) || dayMin.isNull(i)) continue
            days.add(
                Day(
                    min = dayMin.getDouble(i).roundToInt(),
                    max = dayMax.getDouble(i).roundToInt(),
                    code = dayCodes.optInt(i, 0),
                ),
            )
        }
        return Weather(
            fetched = fetched,
            place = place,
            temp = current.getDouble("temperature_2m").roundToInt(),
            code = current.optInt("weather_code", 0),
            day = current.optInt("is_day", 1) == 1,
            hours = hours,
            days = days,
        )
    }

    private val FAHRENHEIT_COUNTRIES = setOf("US", "LR", "MM", "BS", "BZ", "KY", "PW")
}
