package app.thelauncher.extras

import android.app.Activity
import android.app.KeyguardManager
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.hardware.biometrics.BiometricManager
import android.hardware.biometrics.BiometricPrompt
import android.os.Build
import android.os.CancellationSignal
import android.os.SystemClock
import android.os.UserHandle
import android.widget.Toast
import androidx.annotation.RequiresApi
import androidx.core.content.getSystemService
import app.thelauncher.BlankActivity
import app.thelauncher.preferences2.PreferenceManager2
import com.android.launcher3.R
import com.android.launcher3.util.ComponentKey
import com.patrykmichalik.opto.core.firstBlocking
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.launch

object AppLockManager {

    private val unlockedAt = mutableMapOf<String, Long>()
    private var receiverRegistered = false

    fun isLockedApp(context: Context, component: ComponentName?, user: UserHandle?): Boolean {
        if (component == null || user == null) return false
        val locked = PreferenceManager2.getInstance(context).lockedApps.firstBlocking()
        if (locked.isEmpty()) return false
        return ComponentKey(component, user).toString() in locked
    }

    fun needsUnlock(context: Context, component: ComponentName?, user: UserHandle?): Boolean {
        if (!isLockedApp(context, component, user)) return false
        ensureScreenOffReceiver(context)
        val key = ComponentKey(component!!, user!!).toString()
        val timeoutMs = PreferenceManager2.getInstance(context).appLockTimeoutSeconds.firstBlocking() * 1000L
        val last = unlockedAt[key] ?: return true
        return timeoutMs <= 0 || SystemClock.elapsedRealtime() - last > timeoutMs
    }

    fun authenticate(
        activity: Activity,
        component: ComponentName,
        user: UserHandle,
        label: CharSequence?,
        onSuccess: () -> Unit,
    ) {
        val key = ComponentKey(component, user).toString()
        val success = {
            unlockedAt[key] = SystemClock.elapsedRealtime()
            onSuccess()
        }
        val keyguard = activity.getSystemService<KeyguardManager>()
        if (keyguard == null || !keyguard.isDeviceSecure) {
            Toast.makeText(activity, R.string.app_lock_needs_screen_lock, Toast.LENGTH_LONG).show()
            return
        }
        val title = activity.getString(R.string.app_lock_prompt_title, label ?: component.packageName)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            showBiometricPrompt(activity, title, success)
        } else {
            confirmDeviceCredential(activity, title, success)
        }
    }

    @RequiresApi(Build.VERSION_CODES.P)
    private fun showBiometricPrompt(activity: Activity, title: String, onSuccess: () -> Unit) {
        val builder = BiometricPrompt.Builder(activity)
            .setTitle(title)
            .setSubtitle(activity.getString(R.string.app_lock_prompt_subtitle))
        val canUseCredential = Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q
        when {
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.R -> builder.setAllowedAuthenticators(
                BiometricManager.Authenticators.BIOMETRIC_WEAK or
                    BiometricManager.Authenticators.DEVICE_CREDENTIAL,
            )
            canUseCredential -> @Suppress("DEPRECATION") builder.setDeviceCredentialAllowed(true)
            else -> builder.setNegativeButton(
                activity.getString(android.R.string.cancel),
                activity.mainExecutor,
            ) { _, _ -> }
        }
        val callback = object : BiometricPrompt.AuthenticationCallback() {
            override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult?) {
                onSuccess()
            }

            override fun onAuthenticationError(errorCode: Int, errString: CharSequence?) {
                val userCancelled = errorCode == BiometricPrompt.BIOMETRIC_ERROR_USER_CANCELED ||
                    errorCode == BiometricPrompt.BIOMETRIC_ERROR_CANCELED
                if (!canUseCredential && !userCancelled) {
                    confirmDeviceCredential(activity, title, onSuccess)
                }
            }
        }
        runCatching {
            builder.build().authenticate(CancellationSignal(), activity.mainExecutor, callback)
        }.onFailure {
            confirmDeviceCredential(activity, title, onSuccess)
        }
    }

    @Suppress("DEPRECATION")
    private fun confirmDeviceCredential(activity: Activity, title: String, onSuccess: () -> Unit) {
        val keyguard = activity.getSystemService<KeyguardManager>() ?: return
        val intent = keyguard.createConfirmDeviceCredentialIntent(title, null) ?: return
        MainScope().launch {
            val result = BlankActivity.startBlankActivityForResult(activity, intent)
            if (result.resultCode == Activity.RESULT_OK) onSuccess()
        }
    }

    private fun ensureScreenOffReceiver(context: Context) {
        if (receiverRegistered) return
        receiverRegistered = true
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                unlockedAt.clear()
            }
        }
        context.applicationContext.registerReceiver(receiver, IntentFilter(Intent.ACTION_SCREEN_OFF))
    }
}
