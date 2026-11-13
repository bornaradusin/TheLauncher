package app.thelauncher.smartspace.provider

import android.app.Activity
import android.content.Context
import app.thelauncher.preferences2.PreferenceManager2
import app.thelauncher.smartspace.model.SmartspaceAction
import app.thelauncher.smartspace.model.SmartspaceTarget
import app.thelauncher.ui.preferences.PreferenceActivity
import app.thelauncher.ui.preferences.navigation.Smartspace
import app.thelauncher.util.dropWhileBusy
import com.android.launcher3.R
import com.android.launcher3.dagger.ApplicationContext
import com.android.launcher3.dagger.LauncherAppComponent
import com.android.launcher3.dagger.LauncherAppSingleton
import com.android.launcher3.util.DaggerSingletonObject
import com.android.launcher3.util.SafeCloseable
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.shareIn

@LauncherAppSingleton
class SmartspaceProvider @Inject constructor(
    @ApplicationContext private val context: Context,
) : SafeCloseable {

    val dataSources = listOf(
        SmartspaceWidgetReader(context),
        CalendarEventsProvider(context),
        BatteryStatusProvider(context),
        TorchProvider(context),
        NowPlayingProvider(context),
        MusicRecognitionProvider(context),
        NextAlarmProvider(context),
        OnboardingProvider(context),
    )

    init {
        // Weather now only comes from the Google app. Whoever had the other weather on gets Google's.
        MainScope().launch(Dispatchers.IO) {
            val prefs = PreferenceManager2.getInstance(context)
            if (prefs.smartspaceWeather.get().first()) {
                prefs.smartspaceAagWidget.set(true)
                prefs.smartspaceWeather.set(false)
            }
        }
    }

    private val state = dataSources
        .map { it.targets }
        .reduce { acc, flow -> flow.combine(acc) { a, b -> a + b } }
        .shareIn(
            MainScope(),
            SharingStarted.WhileSubscribed(),
            replay = 1,
        )
    val targets = state
        .map {
            if (it.requiresSetup.isNotEmpty()) {
                listOf(setupTarget) + it.targets.withOneDateCard()
            } else {
                it.targets.withOneDateCard()
            }
        }
    val previewTargets = state
        .map { it.targets.withOneDateCard() }

    /** Only one date card is ever shown: the one that has weather on it, otherwise the first. */
    private fun List<SmartspaceTarget>.withOneDateCard(): List<SmartspaceTarget> {
        val dateCards = filter { it.featureType == SmartspaceTarget.FeatureType.FEATURE_WEATHER }
        if (dateCards.size <= 1) return this
        val keep = dateCards.firstOrNull { !it.headerAction?.subtitle.isNullOrEmpty() } ?: dateCards.first()
        return filter { it.featureType != SmartspaceTarget.FeatureType.FEATURE_WEATHER || it === keep }
    }

    private val setupTarget = SmartspaceTarget(
        id = "smartspaceSetup",
        headerAction = SmartspaceAction(
            id = "smartspaceSetupAction",
            title = context.getString(R.string.smartspace_requires_setup),
            intent = PreferenceActivity.createIntent(context, Smartspace),
        ),
        score = 999f,
        featureType = SmartspaceTarget.FeatureType.FEATURE_TIPS,
    )

    suspend fun startSetup(activity: Activity) {
        state
            .map { it.requiresSetup }
            .dropWhileBusy()
            .collect { sources ->
                sources.forEach {
                    it.startSetup(activity)
                    it.onSetupDone()
                }
            }
    }

    override fun close() {
        TODO("Not yet implemented")
    }

    companion object {
        @JvmField val INSTANCE = DaggerSingletonObject(LauncherAppComponent::getSmartspaceProvider)
    }
}
