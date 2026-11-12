package app.thelauncher.gestures.handlers

import android.content.Context
import app.thelauncher.MainLauncher
import app.thelauncher.animateToAllApps

class OpenAppDrawerGestureHandler(context: Context) : GestureHandler(context) {

    override suspend fun onTrigger(launcher: MainLauncher) {
        launcher.animateToAllApps()
    }
}
