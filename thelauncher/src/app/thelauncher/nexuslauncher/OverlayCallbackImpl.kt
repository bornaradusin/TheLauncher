package app.thelauncher.nexuslauncher

import android.app.Activity
import android.content.Context
import android.content.pm.ApplicationInfo
import android.os.Bundle
import app.thelauncher.FeedBridge
import app.thelauncher.MainLauncher
import app.thelauncher.preferences2.PreferenceManager2
import app.thelauncher.preferences2.firstCached
import com.android.launcher3.Launcher
import com.android.launcher3.LauncherPrefs
import com.android.systemui.plugins.shared.LauncherOverlayManager
import com.android.systemui.plugins.shared.LauncherOverlayManager.LauncherOverlay
import com.android.systemui.plugins.shared.LauncherOverlayManager.LauncherOverlayCallbacks
import com.google.android.libraries.launcherclient.ISerializableScrollCallback
import com.google.android.libraries.launcherclient.LauncherClient
import com.google.android.libraries.launcherclient.LauncherClientCallbacks
import com.google.android.libraries.launcherclient.LauncherClientService
import com.google.android.libraries.launcherclient.StaticInteger

/**
 * Implements [LauncherOverlay] and passes all the corresponding events to [LauncherClient],
 * see [LauncherClientService.setClient].
 *
 * Implements [LauncherClientCallbacks] and sends all the corresponding callbacks to [Launcher].
 */
class OverlayCallbackImpl(private val mLauncher: MainLauncher) :
    LauncherOverlay,
    LauncherClientCallbacks,
    LauncherOverlayManager,
    ISerializableScrollCallback {
    private val mClient: LauncherClient
    private var mFlagsChanged = false
    private var mLauncherOverlayCallbacks: LauncherOverlayCallbacks? = null
    private var mWasOverlayAttached = false
    private var mFlags = 0

    init {
        val prefs = PreferenceManager2.getInstance(mLauncher)
        val enableFeed = prefs.enableFeed.firstCached()
        mClient = LauncherClient(
            mLauncher,
            this,
            StaticInteger((if (enableFeed) 1 else 0) or 2 or 4 or 8),
        )
    }

    fun reconnect() {
        mClient.reconnect()
    }

    /**
     * Tells the Google feed which theme to use, the way the Pixel launcher does: through the
     * background hints sent with the window. "auto" sends none, so the Google app decides.
     */
    fun setFeedTheme(theme: String) {
        val bundle = when (theme) {
            "dark" -> Bundle().apply {
                putBoolean("is_background_dark", true)
                putInt("background_color_hint", 0xFF202124.toInt())
                putInt("background_secondary_color_hint", 0xFF303134.toInt())
            }
            "light" -> Bundle().apply {
                putBoolean("is_background_dark", false)
                putInt("background_color_hint", 0xFFFFFFFF.toInt())
                putInt("background_secondary_color_hint", 0xFFF1F3F4.toInt())
            }
            else -> null
        }
        mClient.redraw(bundle)
    }

    fun setEnableFeed(enable: Boolean) {
        mClient.setEnableFeed(enable)
        reconnect()
    }

    override fun onDeviceProvideChanged() {
        mClient.redraw()
    }

    override fun onAttachedToWindow() {
        mClient.onAttachedToWindow()
    }

    override fun onDetachedFromWindow() {
        mClient.onDetachedFromWindow()
    }

    override fun openOverlay() {
        mClient.showOverlay(true)
    }

    override fun hideOverlay(animate: Boolean) {
        mClient.hideOverlay(animate)
    }

    override fun hideOverlay(duration: Int) {
        mClient.hideOverlay(duration)
    }

    fun onActivityCreated(activity: Activity, bundle: Bundle?) = Unit

    override fun onActivityStarted() {
        mClient.onStart()
    }

    override fun onActivityResumed() {
        mClient.onResume()
    }

    override fun onActivityPaused() {
        mClient.onPause()
    }

    override fun onActivityStopped() {
        mClient.onStop()
    }

    fun onActivitySaveInstanceState(activity: Activity, bundle: Bundle) = Unit

    override fun onActivityDestroyed() {
        mClient.onDestroy()
    }

    override fun onOverlayScrollChanged(progress: Float) {
        mLauncherOverlayCallbacks?.onOverlayScrollChanged(progress)
    }

    override fun onServiceStateChanged(overlayAttached: Boolean, hotwordActive: Boolean) {
        onServiceStateChanged(overlayAttached)
    }

    override fun onServiceStateChanged(overlayAttached: Boolean) {
        if (overlayAttached != mWasOverlayAttached) {
            mWasOverlayAttached = overlayAttached
            mLauncher.setLauncherOverlay(if (overlayAttached) this else null)
        }
    }

    override fun onScrollInteractionBegin() {
        mClient.startScroll()
    }

    override fun onScrollInteractionEnd() {
        mClient.endScroll()
    }

    override fun onScrollChange(progress: Float, rtl: Boolean) {
        mClient.setScroll(progress)
    }

    override fun setOverlayCallbacks(callbacks: LauncherOverlayCallbacks?) {
        mLauncherOverlayCallbacks = callbacks
    }

    override fun setPersistentFlags(flags: Int) {
        val newFlags = flags and (8 or 16)
        if (newFlags != mFlags) {
            mFlagsChanged = true
            mFlags = newFlags
            LauncherPrefs.getDevicePrefs(mLauncher).edit().putInt(PREF_PERSIST_FLAGS, newFlags).apply()
        }
    }

    companion object {
        private const val PREF_PERSIST_FLAGS = "pref_persistent_flags"

        fun minusOneAvailable(context: Context): Boolean {
            return FeedBridge.useBridge(context) ||
                context.applicationInfo.flags and
                (ApplicationInfo.FLAG_DEBUGGABLE or ApplicationInfo.FLAG_SYSTEM) != 0
        }
    }
}
