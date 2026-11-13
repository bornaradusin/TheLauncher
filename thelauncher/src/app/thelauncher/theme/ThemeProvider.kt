package app.thelauncher.theme

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Handler
import android.os.Looper
import android.os.PatternMatcher
import androidx.core.graphics.ColorUtils
import app.thelauncher.preferences2.PreferenceManager2
import app.thelauncher.preferences2.firstCached
import app.thelauncher.theme.color.AndroidColor
import app.thelauncher.theme.color.ColorOption
import app.thelauncher.theme.color.ColorStyle
import app.thelauncher.theme.color.MonetColorSchemeCompat
import app.thelauncher.theme.color.SystemColorScheme
import app.thelauncher.ui.theme.getSystemAccent
import app.thelauncher.wallpaper.WallpaperManagerCompat
import com.android.launcher3.Utilities
import com.android.launcher3.dagger.ApplicationContext
import com.android.launcher3.dagger.LauncherAppComponent
import com.android.launcher3.dagger.LauncherAppSingleton
import com.android.launcher3.util.DaggerSingletonObject
import com.android.launcher3.util.DaggerSingletonTracker
import com.android.launcher3.util.SafeCloseable
import com.android.systemui.monet.Style
import com.patrykmichalik.opto.core.onEach
import dev.kdrag0n.colorkt.Color
import dev.kdrag0n.colorkt.conversion.ConversionGraph.convert
import dev.kdrag0n.colorkt.rgb.Srgb
import dev.kdrag0n.monet.theme.ColorScheme
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArrayList
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel

@LauncherAppSingleton
class ThemeProvider @Inject constructor(
    @ApplicationContext private val context: Context,
    tracker: DaggerSingletonTracker,
) : SafeCloseable {
    private val preferenceManager2 = PreferenceManager2.getInstance(context)
    private val wallpaperManager = WallpaperManagerCompat.INSTANCE.get(context)
    private val coroutineScope = CoroutineScope(Dispatchers.Default)

    @Volatile
    private var accentColor: ColorOption = preferenceManager2.accentColor.firstCached()

    @Volatile
    private var colorStyle: ColorStyle = preferenceManager2.colorStyle.firstCached()

    private val colorSchemeMap = ConcurrentHashMap<Pair<Int, Style>, ColorScheme>()
    private val listeners = CopyOnWriteArrayList<ColorSchemeChangeListener>()
    private var overlayChangedReceiver: BroadcastReceiver? = null
    private val wallpaperColorsChangedListener =
        object : WallpaperManagerCompat.OnColorsChangedListener {
            override fun onColorsChanged() {
                if (accentColor is ColorOption.WallpaperPrimary) {
                    notifyColorSchemeChanged()
                }
            }
        }

    @Volatile
    private var isClosed = false

    init {
        if (Utilities.ATLEAST_S) {
            colorSchemeMap[SYSTEM_SCHEME_KEY] = SystemColorScheme(context)
            registerOverlayChangedListener()
        }
        wallpaperManager.addOnChangeListener(wallpaperColorsChangedListener)
        preferenceManager2.accentColor.onEach(launchIn = coroutineScope) {
            accentColor = it
            notifyColorSchemeChanged()
        }
        preferenceManager2.colorStyle.onEach(launchIn = coroutineScope) {
            colorStyle = it
            notifyColorSchemeChanged()
        }
        tracker.addCloseable(this)
    }

    private fun registerOverlayChangedListener() {
        val packageFilter = IntentFilter("android.intent.action.OVERLAY_CHANGED")
        packageFilter.addDataScheme("package")
        packageFilter.addDataSchemeSpecificPart("android", PatternMatcher.PATTERN_LITERAL)
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                colorSchemeMap[SYSTEM_SCHEME_KEY] = SystemColorScheme(context)
                if (accentColor is ColorOption.SystemAccent || accentColor is ColorOption.Default) {
                    notifyColorSchemeChanged()
                }
            }
        }
        context.registerReceiver(receiver, packageFilter, null, Handler(Looper.getMainLooper()))
        overlayChangedReceiver = receiver
    }

    val colorScheme get() = when (val accentColor = this.accentColor) {
        is ColorOption.SystemAccent -> systemColorScheme

        is ColorOption.WallpaperPrimary -> {
            val wallpaperPrimary = wallpaperManager.wallpaperColors?.seedColor
            getColorScheme(wallpaperPrimary ?: ColorOption.TheLauncherBlue.color, colorStyle.style)
        }

        is ColorOption.CustomColor -> getColorScheme(accentColor.color, colorStyle.style)

        // No colour picked: follow the system's wallpaper colours, like the rest of the phone.
        else -> if (Utilities.ATLEAST_S) systemColorScheme else getColorScheme(ColorOption.TheLauncherBlue.color, colorStyle.style)
    }

    /**
     * The system's own Material You palette (Settings → Wallpaper & style). It already reflects the
     * style picked there, so it is used as it is rather than rebuilt from a seed colour.
     */
    private val systemColorScheme get() = when {
        Utilities.ATLEAST_S -> colorSchemeMap.getOrPut(SYSTEM_SCHEME_KEY) { SystemColorScheme(context) }
        else -> getColorScheme(context.getSystemAccent(darkTheme = false), colorStyle.style)
    }

    private fun getColorScheme(
        colorInt: Int,
        colorStyle: Style,
    ): ColorScheme {
        val key = Pair(colorInt, colorStyle)
        return colorSchemeMap.computeIfAbsent(key) {
            MonetColorSchemeCompat(colorInt, colorStyle)
        }
    }

    fun addListener(listener: ColorSchemeChangeListener) {
        listeners.add(listener)
    }

    fun removeListener(listener: ColorSchemeChangeListener) {
        listeners.remove(listener)
    }

    private fun notifyColorSchemeChanged() {
        if (isClosed) return
        listeners.forEach(ColorSchemeChangeListener::onColorSchemeChanged)
    }

    override fun close() {
        isClosed = true
        coroutineScope.cancel()
        wallpaperManager.removeOnChangeListener(wallpaperColorsChangedListener)
        overlayChangedReceiver?.let {
            context.unregisterReceiver(it)
            overlayChangedReceiver = null
        }
        listeners.clear()
    }

    companion object {
        @JvmField
        val INSTANCE = DaggerSingletonObject(LauncherAppComponent::getThemeProvider)

        /** Where the system's palette is kept among the schemes built from seed colours. */
        private val SYSTEM_SCHEME_KEY = Pair(0, Style.TONAL_SPOT)
    }

    interface ColorSchemeChangeListener {
        fun onColorSchemeChanged()
    }
}

fun Color.toAndroidColor(): Int {
    return when (this) {
        is AndroidColor -> color
        is Srgb -> ColorUtils.setAlphaComponent(toRgb8(), 255)
        else -> convert<Srgb>().toAndroidColor()
    }
}
