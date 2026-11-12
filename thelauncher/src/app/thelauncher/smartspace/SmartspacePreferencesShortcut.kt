package app.thelauncher.smartspace

import android.os.Bundle
import androidx.activity.ComponentActivity
import app.thelauncher.ui.preferences.PreferenceActivity
import app.thelauncher.ui.preferences.navigation.SmartspaceWidget

class SmartspacePreferencesShortcut : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        startActivity(PreferenceActivity.createIntent(this, SmartspaceWidget))
        finish()
    }
}
