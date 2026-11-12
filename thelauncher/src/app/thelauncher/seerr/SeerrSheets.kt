package app.thelauncher.seerr

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.DeleteSweep
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.thelauncher.MainLauncher
import app.thelauncher.views.ComposeBottomSheet
import coil.compose.AsyncImage
import com.android.launcher3.R
import kotlinx.coroutines.launch

/** Seerr's sheets over search: a title's info, picking seasons to add, and what to delete. */
object SeerrSheets {

    fun showInfo(launcher: MainLauncher, item: SeerrItem, mode: SeerrMode, onStatus: (Int) -> Unit) =
        show(launcher, item, mode, Screen.INFO, onStatus)

    fun showSeasons(launcher: MainLauncher, item: SeerrItem, onStatus: (Int) -> Unit) =
        show(launcher, item, SeerrMode.ADD, Screen.SEASONS, onStatus)

    fun showDelete(launcher: MainLauncher, item: SeerrItem, onStatus: (Int) -> Unit) =
        show(launcher, item, SeerrMode.DELETE, Screen.DELETE, onStatus)

    private fun show(launcher: MainLauncher, item: SeerrItem, mode: SeerrMode, start: Screen, onStatus: (Int) -> Unit) {
        ComposeBottomSheet.show(context = launcher, contentPaddings = PaddingValues(bottom = 16.dp)) {
            SeerrSheet(item, mode, start, onStatus) { close(true) }
        }
    }

    private enum class Screen { INFO, SEASONS, DELETE }

    @Composable
    private fun SeerrSheet(
        item: SeerrItem,
        mode: SeerrMode,
        start: Screen,
        onStatus: (Int) -> Unit,
        close: () -> Unit,
    ) {
        val context = LocalContext.current
        val client = remember { SeerrClient.fromSettings(context) }
        val scope = rememberCoroutineScope()
        var screen by remember { mutableStateOf(start) }
        var details by remember { mutableStateOf<SeerrDetails?>(null) }
        var error by remember { mutableStateOf<String?>(null) }
        var working by remember { mutableStateOf(false) }

        LaunchedEffect(item) {
            if (client == null) {
                error = context.getString(R.string.seerr_message_set_up)
                return@LaunchedEffect
            }
            runCatching { client.details(item) }
                .onSuccess { details = it }
                .onFailure { error = it.message ?: context.getString(R.string.seerr_message_unreachable) }
        }

        fun perform(done: String, status: Int, block: suspend (SeerrClient) -> Unit) {
            val seerr = client ?: return
            working = true
            scope.launch {
                runCatching { block(seerr) }
                    .onSuccess {
                        Toast.makeText(context, done, Toast.LENGTH_SHORT).show()
                        onStatus(status)
                        close()
                    }
                    .onFailure {
                        working = false
                        Toast.makeText(context, context.getString(R.string.seerr_failed, it.message ?: ""), Toast.LENGTH_LONG).show()
                    }
            }
        }

        val shown = details?.item ?: item
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = 640.dp)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp, vertical = 8.dp),
        ) {
            Header(shown, details)
            Spacer(Modifier.height(16.dp))
            when {
                error != null -> Text(error.orEmpty(), color = MaterialTheme.colorScheme.error)
                screen == Screen.INFO -> InfoBody(
                    item = shown,
                    details = details,
                    mode = mode,
                    working = working,
                    onAdd = {
                        if (shown.isSeries) {
                            screen = Screen.SEASONS
                        } else {
                            perform(context.getString(R.string.seerr_requested_toast, shown.title), SeerrStatus.PENDING) { it.request(shown) }
                        }
                    },
                    onDelete = { screen = Screen.DELETE },
                    onClose = close,
                )
                details == null -> Loading()
                screen == Screen.SEASONS -> SeasonsBody(details!!, working) { seasons ->
                    perform(context.getString(R.string.seerr_requested_toast, shown.title), SeerrStatus.PENDING) {
                        it.request(shown, seasons)
                    }
                }
                else -> DeleteBody(details!!, working, ::perform)
            }
        }
    }

    @Composable
    private fun Header(item: SeerrItem, details: SeerrDetails?) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            AsyncImage(
                model = item.posterUrl,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .size(width = 84.dp, height = 126.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant),
            )
            Spacer(Modifier.width(16.dp))
            Column {
                Text(item.title, style = MaterialTheme.typography.titleLarge, maxLines = 3, overflow = TextOverflow.Ellipsis)
                Spacer(Modifier.height(4.dp))
                Text(
                    meta(item, details),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                statusLabel(item)?.let { label ->
                    Spacer(Modifier.height(8.dp))
                    Surface(shape = RoundedCornerShape(12.dp), color = MaterialTheme.colorScheme.secondaryContainer) {
                        Text(
                            label,
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onSecondaryContainer,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                        )
                    }
                }
            }
        }
    }

    @Composable
    private fun meta(item: SeerrItem, details: SeerrDetails?): String {
        val parts = mutableListOf<String>()
        item.year?.let { parts.add(it) }
        parts.add(stringResource(if (item.isSeries) R.string.seerr_series else R.string.seerr_movie))
        item.rating?.let { parts.add(stringResource(R.string.seerr_rating, it)) }
        details?.runtime?.takeIf { it > 0 }?.let { minutes ->
            parts.add(stringResource(R.string.seerr_runtime, minutes / 60, minutes % 60))
        }
        details?.seasonCount?.takeIf { it > 0 }?.let { count ->
            parts.add(pluralStringResource(R.plurals.seerr_season_count, count, count))
        }
        details?.seriesStatus?.takeIf { item.isSeries && it.isNotBlank() }?.let { parts.add(it) }
        return parts.joinToString(" · ")
    }

    @Composable
    private fun statusLabel(item: SeerrItem): String? = when {
        item.isAvailable -> stringResource(R.string.seerr_available)
        item.status == SeerrStatus.PARTIALLY_AVAILABLE -> stringResource(R.string.seerr_partly_available)
        item.isRequested -> stringResource(R.string.seerr_requested)
        else -> null
    }

    @Composable
    private fun InfoBody(
        item: SeerrItem,
        details: SeerrDetails?,
        mode: SeerrMode,
        working: Boolean,
        onAdd: () -> Unit,
        onDelete: () -> Unit,
        onClose: () -> Unit,
    ) {
        val overview = details?.item?.overview ?: item.overview
        if (!overview.isNullOrBlank()) {
            Text(overview, style = MaterialTheme.typography.bodyMedium, maxLines = 6, overflow = TextOverflow.Ellipsis)
            Spacer(Modifier.height(20.dp))
        }
        Row(horizontalArrangement = Arrangement.End, modifier = Modifier.fillMaxWidth()) {
            TextButton(onClick = onClose) { Text(stringResource(R.string.seerr_close)) }
            Spacer(Modifier.width(8.dp))
            when {
                mode == SeerrMode.DELETE -> Button(
                    onClick = onDelete,
                    enabled = !working,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error,
                        contentColor = MaterialTheme.colorScheme.onError,
                    ),
                ) { Text(stringResource(R.string.seerr_delete)) }
                item.isAvailable || item.isRequested -> Unit
                else -> Button(onClick = onAdd, enabled = !working) {
                    Text(stringResource(if (working) R.string.seerr_adding else R.string.seerr_add))
                }
            }
        }
    }

    @Composable
    private fun SeasonsBody(details: SeerrDetails, working: Boolean, onRequest: (List<Int>) -> Unit) {
        val requestable = details.seasons.filter { it.status < SeerrStatus.PENDING }.map { it.number }
        var selected by remember(details) { mutableStateOf(requestable.toSet()) }
        Text(stringResource(R.string.seerr_pick_seasons), style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(8.dp))
        SeasonRow(
            label = stringResource(R.string.seerr_all_seasons),
            detail = null,
            checked = requestable.isNotEmpty() && selected.size == requestable.size,
            enabled = requestable.isNotEmpty(),
        ) { checked -> selected = if (checked) requestable.toSet() else emptySet() }
        details.seasons.forEach { season ->
            val inSeerr = season.status >= SeerrStatus.PENDING
            SeasonRow(
                label = season.name,
                detail = when {
                    season.status == SeerrStatus.AVAILABLE -> stringResource(R.string.seerr_available)
                    season.status == SeerrStatus.PARTIALLY_AVAILABLE -> stringResource(R.string.seerr_partly_available)
                    inSeerr -> stringResource(R.string.seerr_requested)
                    else -> pluralStringResource(R.plurals.seerr_episode_count, season.episodes, season.episodes)
                },
                checked = inSeerr || season.number in selected,
                enabled = !inSeerr,
            ) { checked -> selected = if (checked) selected + season.number else selected - season.number }
        }
        Spacer(Modifier.height(16.dp))
        Button(
            onClick = { onRequest(selected.sorted()) },
            enabled = selected.isNotEmpty() && !working,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(
                if (working) {
                    stringResource(R.string.seerr_adding)
                } else {
                    pluralStringResource(R.plurals.seerr_request_seasons, selected.size, selected.size)
                },
            )
        }
    }

    @Composable
    private fun SeasonRow(label: String, detail: String?, checked: Boolean, enabled: Boolean, onChange: (Boolean) -> Unit) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            Checkbox(checked = checked, onCheckedChange = onChange, enabled = enabled)
            Column(Modifier.weight(1f)) {
                Text(label, style = MaterialTheme.typography.bodyLarge)
                detail?.let {
                    Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }

    @Composable
    private fun DeleteBody(
        details: SeerrDetails,
        working: Boolean,
        perform: (String, Int, suspend (SeerrClient) -> Unit) -> Unit,
    ) {
        val item = details.item
        val mediaId = item.mediaId
        val title = item.title
        val removedText = stringResource(R.string.seerr_deleted_toast, title)
        Text(stringResource(R.string.seerr_delete_what), style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(12.dp))
        var any = false
        if (mediaId != null && details.inArr && item.status >= SeerrStatus.PARTIALLY_AVAILABLE) {
            any = true
            DeleteOption(
                icon = Icons.Rounded.Delete,
                title = stringResource(if (item.isSeries) R.string.seerr_remove_sonarr else R.string.seerr_remove_radarr),
                subtitle = stringResource(R.string.seerr_remove_files_desc),
                danger = true,
                enabled = !working,
            ) { perform(removedText, SeerrStatus.UNKNOWN) { it.removeFromArr(mediaId, false) } }
        }
        if (details.requests.isNotEmpty()) {
            any = true
            DeleteOption(
                icon = Icons.Rounded.Close,
                title = stringResource(R.string.seerr_delete_request),
                subtitle = stringResource(R.string.seerr_delete_request_desc),
                danger = false,
                enabled = !working,
            ) {
                val after = if (item.status >= SeerrStatus.PARTIALLY_AVAILABLE) item.status else SeerrStatus.UNKNOWN
                perform(removedText, after) { seerr -> details.requests.forEach { seerr.deleteRequest(it.id) } }
            }
        }
        if (mediaId != null) {
            any = true
            DeleteOption(
                icon = Icons.Rounded.DeleteSweep,
                title = stringResource(R.string.seerr_clear_data),
                subtitle = stringResource(R.string.seerr_clear_data_desc),
                danger = false,
                enabled = !working,
            ) { perform(removedText, SeerrStatus.UNKNOWN) { it.clearData(mediaId) } }
        }
        if (!any) {
            Text(stringResource(R.string.seerr_nothing_to_delete), color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        if (working) {
            Spacer(Modifier.height(12.dp))
            Loading()
        }
    }

    @Composable
    private fun DeleteOption(
        icon: ImageVector,
        title: String,
        subtitle: String,
        danger: Boolean,
        enabled: Boolean,
        onClick: () -> Unit,
    ) {
        val container = if (danger) MaterialTheme.colorScheme.errorContainer else MaterialTheme.colorScheme.surfaceContainerHigh
        val content = if (danger) MaterialTheme.colorScheme.onErrorContainer else MaterialTheme.colorScheme.onSurface
        Surface(
            onClick = onClick,
            enabled = enabled,
            shape = RoundedCornerShape(18.dp),
            color = container,
            contentColor = content,
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 8.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(16.dp)) {
                Icon(icon, contentDescription = null)
                Spacer(Modifier.width(16.dp))
                Column {
                    Text(title, style = MaterialTheme.typography.titleSmall)
                    Text(subtitle, style = MaterialTheme.typography.bodySmall, color = content.copy(alpha = 0.8f))
                }
            }
        }
    }

    @Composable
    private fun Loading() {
        Row(horizontalArrangement = Arrangement.Center, modifier = Modifier.fillMaxWidth().padding(16.dp)) {
            CircularProgressIndicator(modifier = Modifier.size(28.dp), color = MaterialTheme.colorScheme.primary, trackColor = Color.Transparent)
        }
    }
}
