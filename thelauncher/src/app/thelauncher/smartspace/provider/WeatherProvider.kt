package app.thelauncher.smartspace.provider

import android.Manifest
import android.annotation.SuppressLint
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.graphics.drawable.Icon
import android.location.Location
import android.location.LocationManager
import android.net.Uri
import android.util.Log
import androidx.core.content.getSystemService
import app.thelauncher.extras.PermissionRequestActivity
import app.thelauncher.preferences2.PreferenceManager2
import app.thelauncher.smartspace.model.GlanceExtras
import app.thelauncher.smartspace.model.SmartspaceAction
import app.thelauncher.smartspace.model.SmartspaceScores
import app.thelauncher.smartspace.model.SmartspaceTarget
import com.android.launcher3.R
import java.net.HttpURLConnection
import java.net.URL
import java.util.Locale
import kotlin.math.roundToInt
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext
import org.json.JSONObject

class WeatherProvider(context: Context) :
    SmartspaceDataSource(
        context,
        R.string.smartspace_weather_open_meteo,
        { smartspaceWeather },
    ) {

    private val prefs = PreferenceManager2.getInstance(context)

    @OptIn(ExperimentalCoroutinesApi::class)
    override val internalTargets = combine(
        prefs.smartspaceWeatherFahrenheit.get(),
        prefs.smartspaceWeatherKelvin.get(),
        prefs.smartspaceWeatherLocation.get(),
    ) { fahrenheit, kelvin, place ->
        // Kelvin wins over Fahrenheit; both off is Celsius.
        Pair(if (kelvin) UNIT_KELVIN else if (fahrenheit) UNIT_FAHRENHEIT else UNIT_CELSIUS, place.trim())
    }.flatMapLatest { (unit, place) ->
        flow {
            var last: SmartspaceTarget? = null
            while (true) {
                val target = runCatching { fetchTarget(unit, place) }
                    .onFailure { Log.w(TAG, "weather update failed", it) }
                    .getOrNull()
                if (target != null) last = target
                emit(listOfNotNull(last))
                delay(if (target != null) REFRESH_MS else RETRY_MS)
            }
        }.flowOn(Dispatchers.IO)
    }

    private suspend fun fetchTarget(displayUnit: String, place: String): SmartspaceTarget? {
        val location = if (place.isNotEmpty()) geocode(place) else lastKnownLocation()
        if (location == null) return null
        // Open-Meteo has no Kelvin, so Kelvin is fetched in Celsius and converted.
        val unit = if (displayUnit == UNIT_FAHRENHEIT) "fahrenheit" else "celsius"
        fun shown(value: Double) = if (displayUnit == UNIT_KELVIN) value + 273.15 else value
        val url = String.format(
            Locale.US,
            "https://api.open-meteo.com/v1/forecast?latitude=%.3f&longitude=%.3f" +
                "&current=temperature_2m,weather_code,is_day&daily=temperature_2m_max,temperature_2m_min" +
                "&forecast_days=1&timezone=auto&temperature_unit=%s",
            location.latitude,
            location.longitude,
            unit,
        )
        val body = withContext(Dispatchers.IO) { download(url) }
        val json = JSONObject(body)
        val current = json.getJSONObject("current")
        val temp = shown(current.getDouble("temperature_2m")).roundToInt()
        val code = current.getInt("weather_code")
        val isDay = current.optInt("is_day", 1) == 1
        val daily = json.optJSONObject("daily")
        val high = daily?.optJSONArray("temperature_2m_max")?.optDouble(0)?.let(::shown)
        val low = daily?.optJSONArray("temperature_2m_min")?.optDouble(0)?.let(::shown)
        val condition = WeatherCondition.fromWmoCode(code, isDay)
        val symbol = when (displayUnit) {
            UNIT_FAHRENHEIT -> "°F"
            UNIT_KELVIN -> "K"
            else -> "°C"
        }
        val subtitle = buildString {
            append(context.getString(condition.label))
            if (high != null && low != null && !high.isNaN() && !low.isNaN()) {
                append(" · ")
                append(context.getString(R.string.smartspace_weather_high_low, high.roundToInt(), low.roundToInt()))
            }
        }
        return SmartspaceTarget(
            id = "weather",
            headerAction = SmartspaceAction(
                id = "weatherAction",
                icon = Icon.createWithResource(context, condition.icon),
                title = "",
                subtitle = "$temp $symbol",
                contentDescription = "$temp $symbol, $subtitle",
                intent = weatherIntent(location),
                extras = android.os.Bundle().apply { putString(GlanceExtras.DETAIL, subtitle) },
            ),
            score = WEATHER_FIRST_SCORE,
            featureType = SmartspaceTarget.FeatureType.FEATURE_WEATHER,
        )
    }

    private fun weatherIntent(location: Location): Intent {
        val query = String.format(Locale.US, "weather %.3f,%.3f", location.latitude, location.longitude)
        return Intent(Intent.ACTION_VIEW, Uri.parse("https://www.google.com/search?q=" + Uri.encode(query)))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }

    private var geocodeCache: Pair<String, Location>? = null

    private fun geocode(place: String): Location? {
        geocodeCache?.let { (name, location) -> if (name == place) return location }
        val url = "https://geocoding-api.open-meteo.com/v1/search?count=1&name=" + Uri.encode(place)
        val results = JSONObject(download(url)).optJSONArray("results") ?: return null
        val first = results.optJSONObject(0) ?: return null
        val location = Location("geocoding").apply {
            latitude = first.getDouble("latitude")
            longitude = first.getDouble("longitude")
        }
        geocodeCache = place to location
        return location
    }

    @SuppressLint("MissingPermission")
    private fun lastKnownLocation(): Location? {
        if (!PermissionRequestActivity.hasPermissions(context, Manifest.permission.ACCESS_COARSE_LOCATION)) return null
        val lm = context.getSystemService<LocationManager>() ?: return null
        return lm.getProviders(true)
            .mapNotNull { runCatching { lm.getLastKnownLocation(it) }.getOrNull() }
            .maxByOrNull { it.time }
    }

    private fun download(url: String): String {
        val connection = URL(url).openConnection() as HttpURLConnection
        return try {
            connection.connectTimeout = 10_000
            connection.readTimeout = 10_000
            connection.inputStream.bufferedReader().use { it.readText() }
        } finally {
            connection.disconnect()
        }
    }

    override suspend fun requiresSetup(): Boolean = prefs.smartspaceWeatherLocation.get().first().isBlank() &&
        !PermissionRequestActivity.hasPermissions(context, Manifest.permission.ACCESS_COARSE_LOCATION)

    override suspend fun startSetup(activity: Activity) {
        PermissionRequestActivity.request(activity, Manifest.permission.ACCESS_COARSE_LOCATION)
    }

    enum class WeatherCondition(val label: Int, val icon: Int) {
        CLEAR(R.string.weather_clear, R.drawable.ic_weather_clear),
        PARTLY_CLOUDY(R.string.weather_partly_cloudy, R.drawable.ic_weather_partly_cloudy),
        CLOUDY(R.string.weather_cloudy, R.drawable.ic_weather_cloudy),
        FOG(R.string.weather_fog, R.drawable.ic_weather_fog),
        DRIZZLE(R.string.weather_drizzle, R.drawable.ic_weather_rain),
        RAIN(R.string.weather_rain, R.drawable.ic_weather_rain),
        SNOW(R.string.weather_snow, R.drawable.ic_weather_snow),
        STORM(R.string.weather_storm, R.drawable.ic_weather_storm),
        ;

        companion object {
            fun fromWmoCode(code: Int, @Suppress("UNUSED_PARAMETER") isDay: Boolean): WeatherCondition = when (code) {
                0 -> CLEAR
                1, 2 -> PARTLY_CLOUDY
                3 -> CLOUDY
                45, 48 -> FOG
                51, 53, 55, 56, 57 -> DRIZZLE
                61, 63, 65, 66, 67, 80, 81, 82 -> RAIN
                71, 73, 75, 77, 85, 86 -> SNOW
                95, 96, 99 -> STORM
                else -> CLOUDY
            }
        }
    }

    companion object {
        private const val UNIT_CELSIUS = "c"
        private const val UNIT_FAHRENHEIT = "f"
        private const val UNIT_KELVIN = "k"
        private const val TAG = "WeatherProvider"
        private const val REFRESH_MS = 30 * 60 * 1000L
        private const val RETRY_MS = 5 * 60 * 1000L
        private const val WEATHER_FIRST_SCORE = 500f
    }
}
