package app.thelauncher.gestures.ui

import android.util.Log
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import app.thelauncher.gestures.config.GestureHandlerConfig
import app.thelauncher.gestures.config.GestureHandlerOption
import app.thelauncher.gestures.config.buildConfigFrom
import app.thelauncher.gestures.config.filterGestureHandlerOptions
import app.thelauncher.preferences.getAdapter
import app.thelauncher.preferences2.preferenceManager2
import app.thelauncher.ui.preferences.components.controls.ClickablePreference
import app.thelauncher.ui.preferences.components.layout.PreferenceLayoutLazyColumn
import app.thelauncher.ui.preferences.components.layout.preferenceGroupItems
import com.android.launcher3.R
import kotlinx.coroutines.launch

@Composable
fun CreateActionsScreen(
    modifier: Modifier = Modifier,
    onSelect: (GestureHandlerConfig) -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    val prefs2 = preferenceManager2()
    val newOptions =
        filterGestureHandlerOptions(deckLayoutEnabled = prefs2.deckLayout.getAdapter().state.value)

    fun onClick(option: GestureHandlerOption) {
        scope.launch {
            val config = option.buildConfigFrom(context) ?: return@launch
            onSelect(config)
        }
    }

    PreferenceLayoutLazyColumn(
        label = stringResource(id = R.string.thelauncher_actions),
        modifier = modifier,
    ) {
        preferenceGroupItems(items = newOptions, isFirstChild = true) { _, it ->
            ClickablePreference(
                label = it.getLabel(context),
                onClick = { onClick(it) },
            )
        }
    }
}
