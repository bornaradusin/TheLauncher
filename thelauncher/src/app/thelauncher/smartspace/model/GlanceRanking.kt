package app.thelauncher.smartspace.model

import java.util.concurrent.TimeUnit

/**
 * Picks what the At a Glance widget shows under the date, most urgent first: the flashlight while
 * it's on, music, an event about to start, an alarm or timer due soon, charging, low battery, the
 * next event, then the next alarm.
 */
object GlanceRanking {

    private val SOON = TimeUnit.MINUTES.toMillis(30)
    private val WITHIN_AN_HOUR = TimeUnit.HOURS.toMillis(1)

    fun contextItems(targets: List<SmartspaceTarget>, now: Long, lookaheadHours: Int): List<SmartspaceTarget> = targets
        .mapNotNull { target -> priority(target, now, lookaheadHours)?.let { target to it } }
        .sortedByDescending { it.second }
        .map { it.first }

    /** Null leaves the item off the widget. */
    private fun priority(target: SmartspaceTarget, now: Long, lookaheadHours: Int): Int? {
        val extras = (target.headerAction ?: target.baseAction)?.extras
        return when {
            target.featureType == SmartspaceTarget.FeatureType.FEATURE_WEATHER -> null
            target.featureType == SmartspaceTarget.FeatureType.FEATURE_FLASHLIGHT -> 100
            target.featureType == SmartspaceTarget.FeatureType.FEATURE_MEDIA -> 90
            extras?.containsKey(GlanceExtras.BEGIN) == true -> {
                val untilStart = extras.getLong(GlanceExtras.BEGIN) - now
                when {
                    untilStart <= SOON -> 80
                    untilStart <= TimeUnit.HOURS.toMillis(lookaheadHours.toLong()) -> 40
                    else -> null
                }
            }
            extras?.containsKey(GlanceExtras.TRIGGER) == true ->
                if (extras.getLong(GlanceExtras.TRIGGER) - now <= WITHIN_AN_HOUR) 70 else 30
            target.featureType == SmartspaceTarget.FeatureType.FEATURE_TIMER -> 70
            extras?.containsKey(GlanceExtras.LEVEL) == true ->
                if (extras.getBoolean(GlanceExtras.CHARGING)) 60 else 55
            target.featureType == SmartspaceTarget.FeatureType.FEATURE_TIPS -> 50
            target.featureType == SmartspaceTarget.FeatureType.FEATURE_ONBOARDING -> 10
            else -> 20
        }
    }
}
