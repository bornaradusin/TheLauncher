package app.thelauncher.backup.ui

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.platform.LocalContext
import app.thelauncher.backup.TheLauncherBackup
import app.thelauncher.ui.preferences.LocalNavController
import app.thelauncher.ui.preferences.navigation.RestoreBackup
import app.thelauncher.ui.preferences.navigation.RestoreHyperionBackup
import app.thelauncher.ui.preferences.navigation.RestoreNovaBackup
import com.android.launcher3.R
import java.util.Base64
import java.util.zip.ZipInputStream
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** The launchers whose backups can be restored here, told apart by the files inside. */
enum class BackupKind { THELAUNCHER, HYPERION, NOVA }

object BackupDetector {

    private const val HYPERION_PREFS = "projekt.launcher_preferences.xml"

    fun detect(context: Context, uri: Uri): BackupKind? = runCatching {
        context.contentResolver.openInputStream(uri)?.use { input ->
            ZipInputStream(input).use { zip ->
                var kind: BackupKind? = null
                var entry = zip.nextEntry
                while (entry != null && kind == null) {
                    val name = entry.name.substringAfterLast('/')
                    kind = when {
                        name == HYPERION_PREFS -> BackupKind.HYPERION
                        name == "nova.xml" || name == "nova.db" -> BackupKind.NOVA
                        name == TheLauncherBackup.INFO_FILE_NAME -> BackupKind.THELAUNCHER
                        else -> null
                    }
                    entry = zip.nextEntry
                }
                kind
            }
        }
    }.getOrNull()
}

/** One "Restore backup" for every kind: picks the file, then opens the matching restore screen. */
@Composable
fun restoreAnyBackupOpener(): () -> Unit {
    val navController = LocalNavController.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    val request = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        if (it.resultCode != Activity.RESULT_OK) return@rememberLauncherForActivityResult
        val uri = it.data?.data ?: return@rememberLauncherForActivityResult
        scope.launch {
            val kind = withContext(Dispatchers.IO) { BackupDetector.detect(context, uri) }
            val base64Uri = Base64.getEncoder().encodeToString(uri.toString().toByteArray())
            when (kind) {
                BackupKind.THELAUNCHER -> navController.navigate(RestoreBackup(base64Uri))
                BackupKind.HYPERION -> navController.navigate(RestoreHyperionBackup(base64Uri))
                BackupKind.NOVA -> navController.navigate(RestoreNovaBackup(base64Uri))
                null -> Toast.makeText(context, R.string.restore_unknown_backup, Toast.LENGTH_LONG).show()
            }
        }
    }

    return {
        Intent(Intent.ACTION_OPEN_DOCUMENT)
            .addCategory(Intent.CATEGORY_OPENABLE)
            .setType("*/*")
            .let { request.launch(it) }
    }
}
