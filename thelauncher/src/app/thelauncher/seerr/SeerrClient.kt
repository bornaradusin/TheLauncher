package app.thelauncher.seerr

import android.content.Context
import android.net.Uri
import app.thelauncher.preferences2.PreferenceManager2
import app.thelauncher.preferences2.firstCached
import java.io.IOException
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.put
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody

/** Seerr's media statuses, as its API reports them. */
object SeerrStatus {
    const val UNKNOWN = 1
    const val PENDING = 2
    const val PROCESSING = 3
    const val PARTIALLY_AVAILABLE = 4
    const val AVAILABLE = 5
}

/** A movie or series from Seerr's search. */
data class SeerrItem(
    val tmdbId: Int,
    val mediaType: String,
    val title: String,
    val year: String?,
    val posterPath: String?,
    val overview: String?,
    val rating: Double?,
    /** Seerr's own id for the title once it's in Seerr, otherwise null. */
    val mediaId: Int?,
    val status: Int,
) {
    val isSeries get() = mediaType == MEDIA_TV
    val isInSeerr get() = mediaId != null && status in SeerrStatus.PENDING..SeerrStatus.AVAILABLE
    val isAvailable get() = status == SeerrStatus.AVAILABLE
    val isRequested get() = status == SeerrStatus.PENDING || status == SeerrStatus.PROCESSING
    val posterUrl get() = posterPath?.let { "https://image.tmdb.org/t/p/w185$it" }

    fun toJson(): String = buildJsonObject {
        put("tmdbId", tmdbId)
        put("mediaType", mediaType)
        put("title", title)
        put("year", year)
        put("posterPath", posterPath)
        put("overview", overview)
        put("rating", rating)
        put("mediaId", mediaId)
        put("status", status)
    }.toString()

    companion object {
        const val MEDIA_MOVIE = "movie"
        const val MEDIA_TV = "tv"

        fun fromJson(json: String): SeerrItem? = runCatching {
            val o = Json.parseToJsonElement(json).jsonObject
            SeerrItem(
                tmdbId = o.int("tmdbId") ?: return null,
                mediaType = o.string("mediaType") ?: return null,
                title = o.string("title").orEmpty(),
                year = o.string("year"),
                posterPath = o.string("posterPath"),
                overview = o.string("overview"),
                rating = o.double("rating"),
                mediaId = o.int("mediaId"),
                status = o.int("status") ?: SeerrStatus.UNKNOWN,
            )
        }.getOrNull()
    }
}

/** A season of a series, with whether Seerr already has it. */
data class SeerrSeason(val number: Int, val name: String, val episodes: Int, val status: Int)

/** A request for a title, as Seerr's Manage panel lists them. */
data class SeerrRequest(val id: Int, val is4k: Boolean, val status: Int)

/** What the info and manage sheets show about a title. */
data class SeerrDetails(
    val item: SeerrItem,
    val runtime: Int?,
    val seasonCount: Int?,
    val seriesStatus: String?,
    val seasons: List<SeerrSeason>,
    val requests: List<SeerrRequest>,
    /** Whether Radarr or Sonarr has it, so it can be removed with its files. */
    val inArr: Boolean,
    val inArr4k: Boolean,
)

class SeerrException(message: String) : IOException(message)

/** Talks to the user's Seerr server with its API key, as Seerr's own web app does. */
class SeerrClient(private val baseUrl: String, private val apiKey: String) {

    suspend fun search(query: String): SeerrPage = withContext(Dispatchers.IO) {
        val json = get("/api/v1/search?query=${Uri.encode(query)}&page=1&language=en").jsonObject
        val results = json["results"]?.jsonArray.orEmpty()
            .mapNotNull { it as? JsonObject }
            .filter { it.string("mediaType") == SeerrItem.MEDIA_MOVIE || it.string("mediaType") == SeerrItem.MEDIA_TV }
            .map { parseItem(it) }
        SeerrPage(results, json.int("totalResults") ?: results.size)
    }

    suspend fun details(item: SeerrItem): SeerrDetails = withContext(Dispatchers.IO) {
        val json = get("/api/v1/${item.mediaType}/${item.tmdbId}").jsonObject
        val parsed = parseItem(json, item.mediaType)
        val mediaInfo = json["mediaInfo"] as? JsonObject
        val seasonStatus = mediaInfo?.get("seasons")?.jsonArrayOrNull().orEmpty()
            .mapNotNull { it as? JsonObject }
            .associate { (it.int("seasonNumber") ?: -1) to (it.int("status") ?: SeerrStatus.UNKNOWN) }
        val seasons = json["seasons"]?.jsonArrayOrNull().orEmpty()
            .mapNotNull { it as? JsonObject }
            .mapNotNull { season ->
                val number = season.int("seasonNumber") ?: return@mapNotNull null
                if (number == 0) return@mapNotNull null
                SeerrSeason(
                    number = number,
                    name = season.string("name") ?: "Season $number",
                    episodes = season.int("episodeCount") ?: 0,
                    status = seasonStatus[number] ?: SeerrStatus.UNKNOWN,
                )
            }
        val requests = mediaInfo?.get("requests")?.jsonArrayOrNull().orEmpty()
            .mapNotNull { it as? JsonObject }
            .mapNotNull { r -> r.int("id")?.let { SeerrRequest(it, r.bool("is4k") ?: false, r.int("status") ?: 0) } }
        SeerrDetails(
            item = parsed.copy(year = parsed.year ?: item.year, posterPath = parsed.posterPath ?: item.posterPath),
            runtime = json.int("runtime"),
            seasonCount = json.int("numberOfSeasons"),
            seriesStatus = json.string("status"),
            seasons = seasons,
            requests = requests,
            inArr = mediaInfo?.let { it.int("externalServiceId") != null || it.int("serviceId") != null } ?: false,
            inArr4k = mediaInfo?.let { it.int("externalServiceId4k") != null || it.int("serviceId4k") != null } ?: false,
        )
    }

    /** Requests a movie, or a series' seasons (all of them when [seasons] is null). */
    suspend fun request(item: SeerrItem, seasons: List<Int>? = null) = withContext(Dispatchers.IO) {
        val body = buildJsonObject {
            put("mediaType", item.mediaType)
            put("mediaId", item.tmdbId)
            if (item.isSeries) {
                if (seasons == null) {
                    put("seasons", "all")
                } else {
                    put("seasons", JsonArray(seasons.map { JsonPrimitive(it) }))
                }
            }
        }
        send("POST", "/api/v1/request", body.toString())
    }

    suspend fun deleteRequest(requestId: Int) = withContext(Dispatchers.IO) {
        send("DELETE", "/api/v1/request/$requestId", null)
    }

    /** Removes the title from Radarr or Sonarr, deleting its files. */
    suspend fun removeFromArr(mediaId: Int, is4k: Boolean = false) = withContext(Dispatchers.IO) {
        send("DELETE", "/api/v1/media/$mediaId/file?is4k=$is4k", null)
    }

    /** Clears Seerr's data for the title, so it looks never requested. */
    suspend fun clearData(mediaId: Int) = withContext(Dispatchers.IO) {
        send("DELETE", "/api/v1/media/$mediaId", null)
    }

    /** Checks the address and key; returns Seerr's version. */
    suspend fun test(): String = withContext(Dispatchers.IO) {
        get("/api/v1/auth/me")
        get("/api/v1/status").jsonObject.string("version").orEmpty()
    }

    private fun get(path: String): JsonElement = Json.parseToJsonElement(send("GET", path, null))

    private fun send(method: String, path: String, body: String?): String {
        val request = Request.Builder()
            .url(baseUrl.trimEnd('/') + path)
            .header("X-Api-Key", apiKey)
            .header("Accept", "application/json")
            .method(method, body?.toRequestBody("application/json".toMediaType()))
            .build()
        http.newCall(request).execute().use { response ->
            val text = response.body.string()
            if (!response.isSuccessful) {
                val message = runCatching { Json.parseToJsonElement(text).jsonObject.string("message") }.getOrNull()
                throw SeerrException(message ?: "HTTP ${response.code}")
            }
            return text
        }
    }

    private fun parseItem(o: JsonObject, mediaType: String? = null): SeerrItem {
        val type = o.string("mediaType") ?: mediaType ?: SeerrItem.MEDIA_MOVIE
        val mediaInfo = o["mediaInfo"] as? JsonObject
        return SeerrItem(
            tmdbId = o.int("id") ?: 0,
            mediaType = type,
            title = o.string("title") ?: o.string("name") ?: "",
            year = (o.string("releaseDate") ?: o.string("firstAirDate"))?.take(4)?.takeIf { it.length == 4 },
            posterPath = o.string("posterPath"),
            overview = o.string("overview")?.takeIf { it.isNotBlank() },
            rating = o.double("voteAverage")?.takeIf { it > 0 },
            mediaId = mediaInfo?.int("id"),
            status = mediaInfo?.int("status") ?: SeerrStatus.UNKNOWN,
        )
    }

    companion object {
        private val http = OkHttpClient.Builder()
            .connectTimeout(8, TimeUnit.SECONDS)
            .readTimeout(15, TimeUnit.SECONDS)
            .build()

        /** The client for the address and key in settings, or null when Seerr isn't set up. */
        fun fromSettings(context: Context): SeerrClient? {
            val prefs = PreferenceManager2.getInstance(context)
            val url = serverUrl(prefs.seerrUrl.firstCached()) ?: return null
            val key = prefs.seerrApiKey.firstCached().trim()
            if (key.isEmpty()) return null
            return SeerrClient(url, key)
        }

        /** A page of Seerr's web app, such as its search, or null when no address is set. */
        fun webUrl(context: Context, path: String): String? =
            serverUrl(PreferenceManager2.getInstance(context).seerrUrl.firstCached())?.let { it.trimEnd('/') + path }

        private fun serverUrl(setting: String): String? {
            val url = setting.trim()
            if (url.isEmpty()) return null
            return if (url.startsWith("http://") || url.startsWith("https://")) url else "https://$url"
        }
    }
}

data class SeerrPage(val results: List<SeerrItem>, val total: Int)

private fun JsonElement.jsonArrayOrNull(): JsonArray? = this as? JsonArray
private fun JsonObject.prim(key: String): JsonPrimitive? = (this[key] as? JsonPrimitive)?.takeIf { it !is JsonNull }
private fun JsonObject.string(key: String): String? = prim(key)?.contentOrNull
private fun JsonObject.int(key: String): Int? = prim(key)?.intOrNull
private fun JsonObject.double(key: String): Double? = prim(key)?.doubleOrNull
private fun JsonObject.bool(key: String): Boolean? = prim(key)?.booleanOrNull
