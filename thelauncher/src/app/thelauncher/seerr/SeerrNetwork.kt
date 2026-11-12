package app.thelauncher.seerr

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build

/**
 * Android 17 blocks apps from reaching addresses on the home network unless they are allowed to.
 * Seerr often lives there, or its address resolves to one on the home Wi-Fi.
 */
object SeerrNetwork {
    const val PERMISSION = "android.permission.ACCESS_LOCAL_NETWORK"

    private const val ANDROID_17 = 37

    val needsPermission: Boolean get() = Build.VERSION.SDK_INT >= ANDROID_17

    fun isAllowed(context: Context): Boolean =
        !needsPermission || context.checkSelfPermission(PERMISSION) == PackageManager.PERMISSION_GRANTED
}
