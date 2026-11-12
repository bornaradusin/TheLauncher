package app.thelauncher.ui.preferences.destinations

import android.app.Activity
import android.content.Intent
import androidx.compose.animation.Crossfade
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import app.thelauncher.gestures.config.GestureHandlerConfig
import app.thelauncher.gestures.handlers.OpenAppTarget
import app.thelauncher.ui.preferences.LocalIsExpandedScreen
import app.thelauncher.ui.preferences.components.AppItem
import app.thelauncher.ui.preferences.components.AppItemPlaceholder
import app.thelauncher.ui.preferences.components.layout.PreferenceLazyColumn
import app.thelauncher.ui.preferences.components.layout.PreferenceScaffold
import app.thelauncher.ui.preferences.components.layout.preferenceGroupItems
import app.thelauncher.util.App
import app.thelauncher.util.appsState
import app.thelauncher.util.kotlinxJson
import com.android.launcher3.R
import com.android.launcher3.util.MSDLPlayerWrapper
import com.google.android.msdl.data.model.MSDLToken

@Composable
fun PickAppForGesture() {
    val mMSDLPlayerWrapper = MSDLPlayerWrapper.INSTANCE.get(LocalContext.current)
    val apps by appsState()
    val state = rememberLazyListState()

    val activity = LocalContext.current as Activity
    fun onSelectApp(app: App) {
        val config: GestureHandlerConfig = GestureHandlerConfig.OpenApp(
            appName = app.label,
            target = OpenAppTarget.App(app.key),
        )
        val configString = kotlinxJson.encodeToString(config)
        activity.setResult(Activity.RESULT_OK, Intent().putExtra("config", configString))
        activity.finish()
    }

    PreferenceScaffold(
        label = stringResource(id = R.string.pick_app_for_gesture),
        isExpandedScreen = LocalIsExpandedScreen.current,
    ) {
        Crossfade(targetState = apps.isNotEmpty(), label = "") { present ->
            if (present) {
                PreferenceLazyColumn(it, state = state) {
                    preferenceGroupItems(
                        items = apps,
                        isFirstChild = true,
                    ) { _, app ->
                        AppItem(
                            app = app,
                            onClick = {
                                mMSDLPlayerWrapper.playToken(MSDLToken.TAP_MEDIUM_EMPHASIS)
                                onSelectApp(app)
                            },
                        )
                    }
                }
            } else {
                PreferenceLazyColumn(it, enabled = false) {
                    preferenceGroupItems(
                        count = 20,
                        isFirstChild = true,
                    ) {
                        AppItemPlaceholder()
                    }
                }
            }
        }
    }
}
