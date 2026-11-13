package app.thelauncher

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import android.widget.Toast
import androidx.core.content.FileProvider
import com.android.launcher3.R
import java.io.File

/**
 * TheLauncher Feed rides inside TheLauncher (the build copies it into the assets), so it installs
 * with one tap: no download. The Google app only shows its feed to debuggable apps, and making
 * TheLauncher itself debuggable would slow it down, so the feed goes through this small app.
 */
object FeedCompanion {
    private const val ASSET = "feed/TheLauncherFeed.apk"
    private const val DOWNLOAD_URL =
        "https://github.com/bornaradusin/TheLauncher/releases/latest/download/TheLauncherFeed.apk"

    /** Opens Android's installer for the companion; first the "install apps" switch if it's off. */
    fun install(context: Context) {
        val packageManager = context.packageManager
        if (!packageManager.canRequestPackageInstalls()) {
            Toast.makeText(context, R.string.feed_companion_allow_install, Toast.LENGTH_LONG).show()
            context.startActivity(
                Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:${context.packageName}"))
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            )
            return
        }
        val apk = runCatching { copyOut(context) }.getOrNull()
        val intent = if (apk != null) {
            val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", apk)
            Intent(Intent.ACTION_VIEW)
                .setDataAndType(uri, "application/vnd.android.package-archive")
                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        } else {
            // A build without it inside (built by hand): fetch it from the latest release.
            Intent(Intent.ACTION_VIEW, Uri.parse(DOWNLOAD_URL)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        runCatching { context.startActivity(intent) }
    }

    private fun copyOut(context: Context): File {
        val dir = File(context.cacheDir, "feed").apply { mkdirs() }
        val target = File(dir, "TheLauncherFeed.apk")
        context.assets.open(ASSET).use { input -> target.outputStream().use { input.copyTo(it) } }
        return target
    }
}
