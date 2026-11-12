package app.thelauncher.ui.preferences.destinations

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import app.thelauncher.preferences.getAdapter
import app.thelauncher.preferences2.preferenceManager2
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import app.thelauncher.seerr.SeerrClient
import app.thelauncher.seerr.SeerrNetwork
import app.thelauncher.ui.preferences.LocalIsExpandedScreen
import app.thelauncher.ui.preferences.components.controls.ClickablePreference
import app.thelauncher.ui.preferences.components.controls.TextPreference
import app.thelauncher.ui.preferences.components.layout.PreferenceGroup
import app.thelauncher.ui.preferences.components.layout.PreferenceLayout
import com.android.launcher3.R
import kotlinx.coroutines.launch

/**
 * Seerr's address and API key, so typing "add …" or "delete …" in search requests or deletes
 * movies and series in Seerr.
 */
@Composable
fun SeerrPreferences(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val prefs2 = preferenceManager2()
    val scope = rememberCoroutineScope()
    var testResult by remember { mutableStateOf<String?>(null) }
    val notSet = stringResource(R.string.seerr_not_set)
    var localAllowed by remember { mutableStateOf(SeerrNetwork.isAllowed(context)) }
    val testing = stringResource(R.string.seerr_testing)

    fun test() {
        val client = SeerrClient.fromSettings(context)
        if (client == null) {
            testResult = context.getString(R.string.seerr_message_set_up)
            return
        }
        testResult = testing
        scope.launch {
            testResult = runCatching { client.test() }.fold(
                onSuccess = { version -> context.getString(R.string.seerr_test_ok, version) },
                onFailure = { context.getString(R.string.seerr_test_failed, it.message ?: "") },
            )
        }
    }

    val requestLocalNetwork = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        localAllowed = granted
        if (granted) test()
    }

    PreferenceLayout(
        label = stringResource(R.string.seerr_label),
        backArrowVisible = !LocalIsExpandedScreen.current,
        modifier = modifier,
    ) {
        PreferenceGroup(
            heading = stringResource(R.string.seerr_server),
            description = stringResource(R.string.seerr_server_desc),
            showDescription = true,
        ) {
            TextPreference(
                adapter = prefs2.seerrUrl.getAdapter(),
                label = stringResource(R.string.seerr_address),
                description = { it.ifBlank { notSet } },
            )
            TextPreference(
                adapter = prefs2.seerrApiKey.getAdapter(),
                label = stringResource(R.string.seerr_api_key),
                description = { if (it.isBlank()) notSet else "•".repeat(minOf(it.length, 16)) },
            )
            if (!localAllowed) {
                ClickablePreference(
                    label = stringResource(R.string.seerr_local_network),
                    subtitle = stringResource(R.string.seerr_local_network_desc),
                    onClick = { requestLocalNetwork.launch(SeerrNetwork.PERMISSION) },
                )
            }
            ClickablePreference(
                label = stringResource(R.string.seerr_test),
                subtitle = testResult,
                onClick = {
                    if (!localAllowed) requestLocalNetwork.launch(SeerrNetwork.PERMISSION) else test()
                },
            )
        }
        PreferenceGroup(
            heading = stringResource(R.string.seerr_how_heading),
            description = stringResource(R.string.seerr_how_desc),
            showDescription = true,
        ) {}
    }
}
