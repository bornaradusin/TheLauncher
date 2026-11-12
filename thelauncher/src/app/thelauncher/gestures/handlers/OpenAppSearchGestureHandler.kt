package app.thelauncher.gestures.handlers

import android.content.Context
import app.thelauncher.MainLauncher
import app.thelauncher.animateToAllApps

class OpenAppSearchGestureHandler(context: Context) : GestureHandler(context) {

    override suspend fun onTrigger(launcher: MainLauncher) {
        val searchUiManager = launcher.appsView.searchUiManager
        searchUiManager.setDirectFocus(true)
        searchUiManager.editText?.showKeyboard()
        launcher.animateToAllApps()
    }
}
