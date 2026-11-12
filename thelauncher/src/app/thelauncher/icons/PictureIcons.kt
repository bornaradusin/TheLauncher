package app.thelauncher.icons

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import java.io.File

/**
 * App icons that are pictures rather than icon pack drawables, such as icons brought over from a
 * Hyperion backup. Each is kept as a PNG file and used through the normal icon overrides, with
 * [PACK] as the pack name and the file name as the drawable name.
 */
object PictureIcons {

    const val PACK = "thelauncher.picture"

    private fun dir(context: Context) = File(context.filesDir, "iconPictures").apply { mkdirs() }

    /** Saves [bytes] (any image format) for [key] and returns the drawable name, or null if it isn't an image. */
    fun save(context: Context, key: String, bytes: ByteArray): String? {
        val bitmap = runCatching { BitmapFactory.decodeByteArray(bytes, 0, bytes.size) }.getOrNull() ?: return null
        val name = key.replace(Regex("[^A-Za-z0-9._-]"), "_") + ".png"
        val saved = runCatching {
            File(dir(context), name).outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        }.getOrDefault(false)
        return if (saved) name else null
    }

    fun load(context: Context, name: String): Drawable? {
        val file = File(dir(context), name)
        if (!file.isFile) return null
        val bitmap = BitmapFactory.decodeFile(file.path) ?: return null
        return BitmapDrawable(context.resources, bitmap)
    }
}
