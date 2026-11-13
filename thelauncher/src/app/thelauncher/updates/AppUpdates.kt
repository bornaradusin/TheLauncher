package app.thelauncher.updates

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.core.content.FileProvider
import com.android.launcher3.BuildConfig
import java.io.File
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest
import kotlin.coroutines.coroutineContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import org.json.JSONObject

/** A newer TheLauncher, published on GitHub. */
data class AppUpdate(
    val version: String,
    val notes: String,
    val apkUrl: String,
    val apkName: String,
    val apkSize: Long,
    val sumsUrl: String?,
    val page: String,
)

/**
 * Updates from GitHub: the newest published release (drafts and pre-releases don't count), its APK
 * downloaded into the app's cache and checked against the release's SHA256SUMS.txt, then handed to
 * Android's installer. The APK is deleted once the update is in (see [cleanLeftovers]).
 */
class AppUpdates(private val context: Context) {

    private fun open(url: String, accept: String? = null): HttpURLConnection =
        (URL(url).openConnection() as HttpURLConnection).apply {
            connectTimeout = 15_000
            readTimeout = 60_000
            instanceFollowRedirects = true
            setRequestProperty("User-Agent", USER_AGENT)
            accept?.let { setRequestProperty("Accept", it) }
        }

    private fun HttpURLConnection.text(): String {
        if (responseCode !in 200..299) throw IOException("GitHub answered $responseCode")
        return inputStream.bufferedReader().use { it.readText() }
    }

    /** The newest release if it's newer than this app, or null. Throws if GitHub can't be reached. */
    suspend fun check(): AppUpdate? = withContext(Dispatchers.IO) {
        val connection = open(LATEST, "application/vnd.github+json")
        val release = try {
            JSONObject(connection.text())
        } finally {
            connection.disconnect()
        }
        if (release.optBoolean("draft") || release.optBoolean("prerelease")) return@withContext null
        val version = release.optString("tag_name").trim().removePrefix("v")
        if (version.isEmpty() || !isNewer(version, BuildConfig.VERSION_NAME)) return@withContext null
        val assets = release.optJSONArray("assets")
        var apk: JSONObject? = null
        var sums: String? = null
        if (assets != null) {
            for (i in 0 until assets.length()) {
                val asset = assets.optJSONObject(i) ?: continue
                val name = asset.optString("name")
                // The launcher itself (TheLauncher-v1.2.3.apk), never the feed companion beside it.
                if (apk == null && name.startsWith("TheLauncher-v") && name.endsWith(".apk")) apk = asset
                if (name == SUMS_FILE) sums = asset.optString("browser_download_url")
            }
        }
        if (apk == null) return@withContext null
        AppUpdate(
            version = version,
            notes = readable(release.optString("body")),
            apkUrl = apk.optString("browser_download_url"),
            apkName = apk.optString("name"),
            apkSize = apk.optLong("size"),
            sumsUrl = sums,
            page = release.optString("html_url"),
        )
    }

    /** Downloads the update's APK and checks it. [progress] goes from 0 to 1. */
    suspend fun download(update: AppUpdate, progress: (Float) -> Unit): File = withContext(Dispatchers.IO) {
        val expected = update.sumsUrl?.let { url ->
            val connection = open(url)
            try {
                connection.text().lineSequence()
                    .map { it.trim().split(Regex("\\s+")) }
                    .firstOrNull { it.size >= 2 && it.last().removePrefix("*") == update.apkName }
                    ?.first()?.lowercase()
            } finally {
                connection.disconnect()
            }
        }
        val dir = File(context.cacheDir, DIR).apply { mkdirs() }
        dir.listFiles()?.forEach { it.delete() }
        val file = File(dir, update.apkName)
        val partial = File(dir, "${update.apkName}.part")
        val digest = MessageDigest.getInstance("SHA-256")
        val connection = open(update.apkUrl)
        try {
            if (connection.responseCode !in 200..299) throw IOException("GitHub answered ${connection.responseCode}")
            val total = connection.contentLengthLong.takeIf { it > 0 } ?: update.apkSize
            connection.inputStream.use { input ->
                partial.outputStream().use { out ->
                    val buffer = ByteArray(64 * 1024)
                    var done = 0L
                    while (true) {
                        coroutineContext.ensureActive()
                        val n = input.read(buffer)
                        if (n < 0) break
                        out.write(buffer, 0, n)
                        digest.update(buffer, 0, n)
                        done += n
                        if (total > 0) progress((done.toFloat() / total).coerceIn(0f, 1f))
                    }
                }
            }
        } catch (t: Throwable) {
            partial.delete()
            throw t
        } finally {
            connection.disconnect()
        }
        val actual = digest.digest().joinToString("") { "%02x".format(it) }
        if (expected != null && expected != actual) {
            partial.delete()
            throw IOException("The download doesn't match the release's checksum")
        }
        if (!partial.renameTo(file)) throw IOException("Couldn't keep the download")
        file
    }

    /** Whether Android lets TheLauncher install apps; if not, [installPermission] opens the switch. */
    fun canInstall(): Boolean = context.packageManager.canRequestPackageInstalls()

    fun installPermission(): Intent =
        Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:${context.packageName}"))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

    /** Opens Android's installer for the downloaded APK. */
    fun install(apk: File) {
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", apk)
        val intent = Intent(Intent.ACTION_VIEW)
            .setDataAndType(uri, "application/vnd.android.package-archive")
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(intent)
    }

    companion object {
        private const val LATEST = "https://api.github.com/repos/bornaradusin/TheLauncher/releases/latest"
        private const val SUMS_FILE = "SHA256SUMS.txt"
        private const val DIR = "updates"
        private const val USER_AGENT = "TheLauncher"

        /**
         * When the app starts: deletes APKs of this version or older (already installed) and any
         * left from an install given up a day ago. Not all of them, as Android may start the app
         * just so its installer can read the one it's installing.
         */
        fun cleanLeftovers(context: Context) {
            val dayAgo = System.currentTimeMillis() - 24 * 3600 * 1000L
            File(context.cacheDir, DIR).listFiles()?.forEach { f ->
                val version = Regex("(\\d+(\\.\\d+)+)").find(f.name)?.value
                if (version == null || !isNewer(version, BuildConfig.VERSION_NAME) || f.lastModified() < dayAgo) f.delete()
            }
        }

        /** Whether version [a] is newer than [b] ("1.0.10" is newer than "1.0.9"). */
        fun isNewer(a: String, b: String): Boolean {
            fun parts(v: String) = v.trim().substringBefore(' ').split('.', '-', '+')
                .map { p -> p.takeWhile { it.isDigit() }.toIntOrNull() ?: 0 }
            val x = parts(a)
            val y = parts(b)
            for (i in 0 until maxOf(x.size, y.size)) {
                val d = x.getOrElse(i) { 0 } - y.getOrElse(i) { 0 }
                if (d != 0) return d > 0
            }
            return false
        }

        /** Release notes as plain text: no Markdown headings or code fences, bullets as dots. */
        fun readable(markdown: String): String = markdown
            .replace("\r", "")
            .lines()
            .filterNot { it.trim().startsWith("```") }
            .joinToString("\n") { line ->
                val t = line.trimEnd()
                when {
                    t.trimStart().startsWith("#") -> t.trimStart().trimStart('#').trim()
                    t.trimStart().startsWith("- ") || t.trimStart().startsWith("* ") ->
                        " ".repeat((t.length - t.trimStart().length) / 2 * 2) + "• " + t.trimStart().drop(2)
                    else -> t
                }
            }
            .replace(Regex("\\*\\*(.+?)\\*\\*"), "$1")
            .replace(Regex("`([^`]+)`"), "$1")
            .replace(Regex("\n{3,}"), "\n\n")
            .trim()
    }
}
