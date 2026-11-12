package app.thelauncher.gestures.handlers

import android.content.Context
import android.content.Intent
import app.thelauncher.MainLauncher
import app.thelauncher.ui.preferences.PreferenceActivity
import app.thelauncher.ui.preferences.navigation.GeneralIconPack

/** Opens the launcher's own settings. */
class OpenLauncherSettingsHandler(context: Context) : GestureHandler(context) {
    override suspend fun onTrigger(launcher: MainLauncher) {
        launcher.startActivity(Intent(launcher, PreferenceActivity::class.java))
    }
}

/** Scrolls back to the home screen's default page. */
class GoToDefaultPageHandler(context: Context) : GestureHandler(context) {
    override suspend fun onTrigger(launcher: MainLauncher) {
        launcher.workspace.moveToDefaultScreen()
    }
}

/** Opens the icon pack picker. */
class OpenIconPackPickerHandler(context: Context) : GestureHandler(context) {
    override suspend fun onTrigger(launcher: MainLauncher) {
        launcher.startActivity(PreferenceActivity.createIntent(launcher, GeneralIconPack))
    }
}

/** Hides the home screen's icons and widgets, or shows them again, leaving just the wallpaper. */
class ToggleHomeContentHandler(context: Context) : GestureHandler(context) {
    override suspend fun onTrigger(launcher: MainLauncher) {
        val target = if (launcher.workspace.alpha < 0.5f) 1f else 0f
        launcher.workspace.animate().alpha(target).setDuration(FADE_MS).start()
        launcher.hotseat.animate().alpha(target).setDuration(FADE_MS).start()
    }

    private companion object {
        const val FADE_MS = 200L
    }
}
