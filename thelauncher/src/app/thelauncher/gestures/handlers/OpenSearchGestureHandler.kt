package app.thelauncher.gestures.handlers

import android.content.Context
import app.thelauncher.MainLauncher
import app.thelauncher.preferences2.PreferenceManager2
import app.thelauncher.qsb.ThemedQsbLayout

class OpenSearchGestureHandler(context: Context) : GestureHandler(context) {

    override suspend fun onTrigger(launcher: MainLauncher) {
        val prefs = PreferenceManager2.getInstance(launcher)
        val searchProvider = ThemedQsbLayout.getSearchProvider(launcher, prefs)
        searchProvider.launch(launcher)
    }
}
