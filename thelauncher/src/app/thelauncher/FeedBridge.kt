/*
 * Copyright 2021, Lawnchair
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package app.thelauncher

import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo.FLAG_DEBUGGABLE
import android.content.pm.ApplicationInfo.FLAG_SYSTEM
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Process
import android.util.Log
import app.thelauncher.preferences.PreferenceManager
import app.thelauncher.util.SingletonHolder
import app.thelauncher.util.ensureOnMainThread
import app.thelauncher.util.getSignatureHash
import app.thelauncher.util.useApplicationContext
import com.android.launcher3.BuildConfig
import com.android.launcher3.R
import com.android.launcher3.Utilities
import com.kieronquinn.app.smartspacer.sdk.SmartspacerConstants

class FeedBridge(private val context: Context) {

    private val shouldUseFeed = context.applicationInfo.flags and (FLAG_DEBUGGABLE or FLAG_SYSTEM) == 0
    private val prefs by lazy { PreferenceManager.getInstance(context) }
    /** TheLauncher Feed, the companion app released with TheLauncher and signed with its key. */
    private val ownBridge by lazy { OwnBridgeInfo(OWN_BRIDGE_PACKAGE) }

    private val bridgePackages by lazy {
        listOf(
            ownBridge,
            PixelBridgeInfo("com.google.android.apps.nexuslauncher", R.integer.bridge_signature_hash),
            BridgeInfo("app.lawnchair.lawnfeed", R.integer.lawnfeed_signature_hash),
        )
    }

    @JvmOverloads
    fun resolveBridge(customPackage: String = prefs.feedProvider.get()): BridgeInfo? {
        val customBridge = customBridgeOrNull(customPackage)
        val feedProvider = customPackage.toBoolean()
        return when {
            // Picking the Google app itself can't work from a release build; the companion can.
            customBridge != null && customBridge.packageName == GOOGLE_APP && shouldUseFeed && ownBridge.isAvailable() -> ownBridge
            customBridge != null -> customBridge
            !shouldUseFeed && !feedProvider -> null
            else -> bridgePackages.firstOrNull { it.isAvailable() }
        }
    }

    /**
     * True when the feed is on its way to failing: no companion app installed to reach the Google
     * app, or one too old to work.
     */
    fun needsCompanionApp(): Boolean = shouldUseFeed && resolveBridge().let {
        it == null || it.packageName == GOOGLE_APP || (it.packageName == OWN_BRIDGE_PACKAGE && companionOutdated())
    }

    /**
     * TheLauncher Feed is installed but older than the one inside this build. Before 1.1 it handed
     * the Google app's connection straight to TheLauncher, and the Google app ignores TheLauncher.
     */
    fun companionOutdated(): Boolean = runCatching {
        val info = context.packageManager.getPackageInfo(OWN_BRIDGE_PACKAGE, 0)
        androidx.core.content.pm.PackageInfoCompat.getLongVersionCode(info) < OWN_BRIDGE_VERSION
    }.getOrDefault(false)

    private fun customBridgeOrNull(customPackage: String = prefs.feedProvider.get()): CustomBridgeInfo? {
        return if (customPackage.isNotBlank()) {
            val bridge = CustomBridgeInfo(customPackage)
            if (bridge.isAvailable()) bridge else null
        } else {
            null
        }
    }

    private fun customBridgeAvailable() = customBridgeOrNull()?.isAvailable() == true

    fun isInstalled(): Boolean {
        return customBridgeAvailable() || !shouldUseFeed || bridgePackages.any { it.isAvailable() }
    }

    fun resolveSmartspace(): String {
        return bridgePackages.firstOrNull { it.supportsSmartspace }?.packageName
            ?: "com.google.android.googlequicksearchbox"
    }

    open inner class BridgeInfo(val packageName: String, signatureHashRes: Int) {
        protected open val signatureHash =
            if (signatureHashRes > 0) context.resources.getInteger(signatureHashRes) else 0

        open val supportsSmartspace = false

        fun isAvailable(): Boolean {
            val info = context.packageManager.resolveService(
                Intent(OVERLAY_ACTION)
                    .setPackage(packageName)
                    .setData(
                        Uri.parse(
                            StringBuilder(packageName.length + 18)
                                .append("app://")
                                .append(packageName)
                                .append(":")
                                .append(Process.myUid())
                                .toString(),
                        )
                            .buildUpon()
                            .appendQueryParameter("v", 7.toString())
                            .appendQueryParameter("cv", 9.toString())
                            .build(),
                    ),
                0,
            )
            return info != null && isSigned()
        }

        open fun isSigned(): Boolean {
            when {
                BuildConfig.DEBUG -> return true

                Utilities.ATLEAST_P -> {
                    val info =
                        context.packageManager.getPackageInfo(packageName, PackageManager.GET_SIGNING_CERTIFICATES)
                    val signingInfo = info.signingInfo
                    if (signingInfo!!.hasMultipleSigners()) return false
                    return signingInfo.signingCertificateHistory.any { it.hashCode() == signatureHash }
                }

                else -> {
                    val info = context.packageManager.getPackageInfo(packageName, PackageManager.GET_SIGNATURES)
                    return if (info.signatures!!.any { it.hashCode() != signatureHash }) false else info.signatures!!.isNotEmpty()
                }
            }
        }
    }

    private inner class CustomBridgeInfo(packageName: String) : BridgeInfo(packageName, 0) {
        override val signatureHash = whitelist[packageName]?.toInt() ?: -1
        val ignoreWhitelist = prefs.ignoreFeedWhitelist.get()
        override fun isSigned(): Boolean {
            if (signatureHash == -1 && Utilities.ATLEAST_P) {
                val info = context.packageManager
                    .getPackageInfo(packageName, PackageManager.GET_SIGNING_CERTIFICATES)
                val signingInfo = info.signingInfo
                if (signingInfo!!.hasMultipleSigners()) return false
                signingInfo.signingCertificateHistory.forEach {
                    val hash = Integer.toHexString(it.hashCode())
                    Log.d(TAG, "Feed provider $packageName(0x$hash) isn't whitelisted")
                }
            }
            return ignoreWhitelist || signatureHash != -1 && super.isSigned()
        }
    }

    /** Trusted when it is signed with the same key as this launcher. */
    private inner class OwnBridgeInfo(packageName: String) : BridgeInfo(packageName, 0) {
        override fun isSigned(): Boolean = runCatching {
            context.packageManager.checkSignatures(context.packageName, packageName) == PackageManager.SIGNATURE_MATCH
        }.getOrDefault(false)
    }

    private inner class PixelBridgeInfo(packageName: String, signatureHashRes: Int) : BridgeInfo(packageName, signatureHashRes) {
        override val supportsSmartspace get() = isAvailable()
    }

    companion object : SingletonHolder<FeedBridge, Context>(
        ensureOnMainThread(
            useApplicationContext(::FeedBridge),
        ),
    ) {
        private const val TAG = "FeedBridge"
        private const val OVERLAY_ACTION = "com.android.launcher3.WINDOW_OVERLAY"
        private const val GOOGLE_APP = "com.google.android.googlequicksearchbox"
        const val OWN_BRIDGE_PACKAGE = "app.thelauncher.feed"
        /** The version of TheLauncher Feed inside this build (feedbridge/build.gradle). */
        private const val OWN_BRIDGE_VERSION = 2L

        private val whitelist = mutableMapOf<String, Long?>()

        fun initializeWhitelist(context: Context) {
            whitelist["com.saulhdev.neofeed"] = getSignatureHash(context, "com.saulhdev.neofeed")
            whitelist["com.nyancatlabs.whisper"] = getSignatureHash(context, "com.nyancatlabs.whisper")
            whitelist["ua.itaysonlab.homefeeder"] = 0x887456ed
            whitelist["launcher.libre.dev"] = 0x2e9dbab5
            whitelist[SmartspacerConstants.SMARTSPACER_PACKAGE_NAME] = 0x15c6e36f
            whitelist["amirz.aidlbridge"] = 0xb662cc2f
            // TheLauncher Feed carries this launcher's own signature.
            whitelist[OWN_BRIDGE_PACKAGE] = getSignatureHash(context, context.packageName)
            whitelist["com.google.android.googlequicksearchbox"] = 0xe3ca78d8
            whitelist["com.google.android.apps.nexuslauncher"] = 0xb662cc2f
        }

        fun getAvailableProviders(context: Context) = context.packageManager
            .queryIntentServices(
                Intent(OVERLAY_ACTION).setData(Uri.parse("app://${context.packageName}")),
                PackageManager.GET_META_DATA,
            )
            .asSequence()
            .map { it.serviceInfo.applicationInfo }
            .distinct()
            .filter { getInstance(context).CustomBridgeInfo(it.packageName).isSigned() }

        @JvmStatic
        fun useBridge(context: Context) = getInstance(context).let { it.shouldUseFeed || it.customBridgeAvailable() }
    }

    init {
        initializeWhitelist(context)
    }
}
