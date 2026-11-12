package app.thelauncher.extras

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import app.thelauncher.BlankActivity

class PermissionRequestActivity : ComponentActivity() {

    private val launcher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) { result ->
        setResult(if (result.values.all { it }) RESULT_OK else RESULT_CANCELED)
        finish()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val permissions: Array<String> = intent.getStringArrayExtra(EXTRA_PERMISSIONS)?.filterNotNull()?.toTypedArray() ?: emptyArray()
        if (permissions.isEmpty() || hasPermissions(this, *permissions)) {
            setResult(RESULT_OK)
            finish()
            return
        }
        if (savedInstanceState == null) {
            launcher.launch(permissions)
        }
    }

    companion object {
        private const val EXTRA_PERMISSIONS = "permissions"

        fun hasPermissions(context: Context, vararg permissions: String): Boolean = permissions.all {
            ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED
        }

        suspend fun request(activity: Activity, vararg permissions: String): Boolean {
            if (hasPermissions(activity, *permissions)) return true
            val intent = Intent(activity, PermissionRequestActivity::class.java)
                .putExtra(EXTRA_PERMISSIONS, permissions)
            BlankActivity.startBlankActivityForResult(activity, intent)
            return hasPermissions(activity, *permissions)
        }
    }
}
