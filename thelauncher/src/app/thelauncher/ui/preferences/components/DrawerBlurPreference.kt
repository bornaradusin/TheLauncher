package app.thelauncher.ui.preferences.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.thelauncher.preferences.PreferenceAdapter
import app.thelauncher.preferences.getAdapter
import app.thelauncher.preferences.preferenceManager
import app.thelauncher.ui.preferences.components.controls.SliderPreference
import app.thelauncher.util.FileAccessManager
import app.thelauncher.util.FileAccessState
import com.android.launcher3.R

/**
 * The app drawer's background blur as one slider: 0 is off, anything above turns it on at
 * that strength. Blurring needs access to the wallpaper, which is asked for the first time
 * the slider is moved up.
 */
@Composable
fun DrawerBlurPreference() {
    val prefs = preferenceManager()
    val enabled = prefs.enableWallpaperBlur.getAdapter()
    val strength = prefs.wallpaperBlur.getAdapter()
    val context = LocalContext.current
    val fileAccessManager = remember { FileAccessManager.getInstance(context) }
    val allFilesAccessState by fileAccessManager.allFilesAccessState.collectAsStateWithLifecycle()
    val wallpaperAccessState by fileAccessManager.wallpaperAccessState.collectAsStateWithLifecycle()
    val hasPermission = wallpaperAccessState != FileAccessState.Denied
    var showPermissionDialog by remember { mutableStateOf(false) }

    val adapter = remember(enabled, strength, hasPermission) {
        object : PreferenceAdapter<Int> {
            override val state: State<Int> = derivedStateOf {
                if (hasPermission && enabled.state.value) strength.state.value.coerceAtLeast(5) else 0
            }
            override fun onChange(newValue: Int) {
                when {
                    newValue <= 0 -> enabled.onChange(false)
                    !hasPermission -> showPermissionDialog = true
                    else -> {
                        strength.onChange(newValue)
                        enabled.onChange(true)
                    }
                }
            }
        }
    }
    SliderPreference(
        label = stringResource(id = R.string.drawer_background_blur),
        adapter = adapter,
        step = 5,
        valueRange = 0..100,
        showUnit = "%",
    )
    if (showPermissionDialog) {
        WallpaperAccessPermissionDialog(
            managedFilesChecked = allFilesAccessState != FileAccessState.Denied,
            onDismiss = { showPermissionDialog = false },
            onPermissionRequest = { fileAccessManager.refresh() },
        )
    }
    LifecycleResumeEffect(Unit) {
        showPermissionDialog = false
        fileAccessManager.refresh()
        onPauseOrDispose { }
    }
}
