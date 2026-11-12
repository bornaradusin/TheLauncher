package app.thelauncher.backup

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Layouts kept inside the app, so a setup can be saved and brought back without a file picker. */
object SavedLayouts {
    private const val DIR = "saved_layouts"
    private const val EXTENSION = ".zip"

    private fun dir(context: Context) = File(context.filesDir, DIR).apply { mkdirs() }

    fun list(context: Context): List<File> =
        dir(context).listFiles { f -> f.name.endsWith(EXTENSION) }?.sortedByDescending { it.lastModified() }.orEmpty()

    fun displayName(file: File): String = file.name.removeSuffix(EXTENSION)

    suspend fun save(context: Context): File {
        val name = SimpleDateFormat("yyyy-MM-dd HH.mm.ss", Locale.US).format(Date())
        val file = File(dir(context), name + EXTENSION)
        file.createNewFile()
        try {
            TheLauncherBackup.create(
                context,
                TheLauncherBackup.INCLUDE_LAYOUT_AND_SETTINGS,
                Bitmap.createBitmap(1, 1, Bitmap.Config.ARGB_8888),
                Uri.fromFile(file),
            )
        } catch (t: Throwable) {
            file.delete()
            throw t
        }
        return file
    }

    suspend fun restore(context: Context, file: File) {
        val backup = TheLauncherBackup(context, Uri.fromFile(file))
        backup.readInfoAndPreview()
        backup.restore(TheLauncherBackup.INCLUDE_LAYOUT_AND_SETTINGS)
    }
}
