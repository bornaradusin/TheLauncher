package app.thelauncher.updates

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.android.launcher3.BuildConfig
import kotlin.math.roundToInt

/** Looks for an update about once a day and shows the update dialog when there's something to show. */
@Composable
fun AppUpdatePrompt() {
    val updater = Updater.get(LocalContext.current)
    LaunchedEffect(Unit) { updater.checkForUpdate(manual = false) }
    val state by updater.state.collectAsStateWithLifecycle()
    if (state != UpdateState.Idle) UpdateDialog(updater, state)
}

/** A newer TheLauncher from GitHub: what's new, then download, check and install it. */
@Composable
fun UpdateDialog(updater: Updater, state: UpdateState) {
    // Back from the "install unknown apps" switch: carry on with the install
    LifecycleResumeEffect(state) {
        if (state is UpdateState.NeedsPermission) updater.continueUpdate()
        onPauseOrDispose { }
    }

    val title = when (state) {
        is UpdateState.Available -> "TheLauncher ${state.update.version} is ready"
        is UpdateState.Downloading -> "TheLauncher ${state.update.version} is ready"
        is UpdateState.NeedsPermission -> "Allow TheLauncher to update itself"
        is UpdateState.Failed -> state.update?.let { "TheLauncher ${it.version} is ready" } ?: "Updates"
        UpdateState.Checking -> "Looking for updates…"
        UpdateState.UpToDate -> "TheLauncher is up to date"
        UpdateState.Idle -> "Updates"
    }

    AlertDialog(
        onDismissRequest = { updater.close() },
        title = { Text(title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                when (state) {
                    is UpdateState.Available -> Notes(state.update.notes)
                    is UpdateState.Downloading -> {
                        Text("Downloading… ${(state.progress * 100).roundToInt()}%", style = MaterialTheme.typography.bodyMedium)
                        LinearProgressIndicator(progress = { state.progress }, modifier = Modifier.fillMaxWidth())
                        Text(
                            "Android's installer opens when it's done. The download is deleted once the update is in.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    is UpdateState.NeedsPermission -> Text(
                        "The update is downloaded. Android asks you to allow TheLauncher to install apps once: turn on “Allow from this source”, then come back here.",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    is UpdateState.Failed -> Text(
                        state.message,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.error,
                    )
                    UpdateState.Checking -> LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                    UpdateState.UpToDate -> Text(
                        "You have the newest version, ${BuildConfig.VERSION_NAME}.",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    UpdateState.Idle -> Unit
                }
            }
        },
        confirmButton = {
            Row {
                when (state) {
                    is UpdateState.Available -> {
                        TextButton(onClick = { updater.updateLater() }) { Text("Not now") }
                        TextButton(onClick = { updater.startUpdate() }) { Text("Update") }
                    }
                    is UpdateState.Downloading -> TextButton(onClick = { updater.cancelUpdate() }) { Text("Cancel") }
                    is UpdateState.NeedsPermission -> {
                        TextButton(onClick = { updater.updateLater() }) { Text("Not now") }
                        TextButton(onClick = { updater.allowInstalls() }) { Text("Allow") }
                    }
                    is UpdateState.Failed -> {
                        TextButton(onClick = { updater.close() }) { Text("Close") }
                        TextButton(onClick = {
                            if (state.update != null) updater.startUpdate() else updater.checkForUpdate(manual = true)
                        }) { Text("Try again") }
                    }
                    UpdateState.Checking, UpdateState.UpToDate, UpdateState.Idle ->
                        TextButton(onClick = { updater.close() }) { Text("OK") }
                }
            }
        },
    )
}

/** The release notes, scrolling if they're long. */
@Composable
private fun Notes(notes: String) {
    Text(
        "You have ${BuildConfig.VERSION_NAME}.",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    if (notes.isNotBlank()) {
        Column(Modifier.heightIn(max = 280.dp).verticalScroll(rememberScrollState())) {
            Text(notes, style = MaterialTheme.typography.bodyMedium)
        }
    }
}
