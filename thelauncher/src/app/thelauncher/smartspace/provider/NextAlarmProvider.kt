package app.thelauncher.smartspace.provider

import android.app.AlarmManager
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.drawable.Icon
import android.provider.AlarmClock
import android.text.format.DateFormat
import androidx.core.content.getSystemService
import app.thelauncher.smartspace.model.SmartspaceAction
import app.thelauncher.smartspace.model.SmartspaceScores
import app.thelauncher.smartspace.model.SmartspaceTarget
import app.thelauncher.util.broadcastReceiverFlow
import com.android.launcher3.R
import java.util.Date
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.flow.onStart

/** Shows the next alarm once it is less than 12 hours away. Tapping it opens the clock app. */
class NextAlarmProvider(context: Context) :
    SmartspaceDataSource(
        context,
        R.string.smartspace_next_alarm,
        { smartspaceNextAlarm },
    ) {
    private val alarmManager = context.getSystemService<AlarmManager>()

    override val internalTargets: Flow<List<SmartspaceTarget>> = merge(
        broadcastReceiverFlow(context, IntentFilter(AlarmManager.ACTION_NEXT_ALARM_CLOCK_CHANGED)).map { },
        recheckEveryFewMinutes(),
    )
        .onStart { emit(Unit) }
        .map { listOfNotNull(nextAlarmTarget()) }

    private fun recheckEveryFewMinutes(): Flow<Unit> = flow {
        while (true) {
            delay(TimeUnit.MINUTES.toMillis(5))
            emit(Unit)
        }
    }

    private fun nextAlarmTarget(): SmartspaceTarget? {
        val alarm = alarmManager?.nextAlarmClock ?: return null
        val untilAlarm = alarm.triggerTime - System.currentTimeMillis()
        if (untilAlarm <= 0 || untilAlarm > TimeUnit.HOURS.toMillis(12)) return null
        val time = DateFormat.getTimeFormat(context).format(Date(alarm.triggerTime))
        return SmartspaceTarget(
            id = "nextAlarm",
            headerAction = SmartspaceAction(
                id = "nextAlarmAction",
                icon = Icon.createWithResource(context, R.drawable.ic_smartspace_alarm),
                title = context.getString(R.string.smartspace_next_alarm_title, time),
                intent = Intent(AlarmClock.ACTION_SHOW_ALARMS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            ),
            score = SmartspaceScores.SCORE_ALARM,
            featureType = SmartspaceTarget.FeatureType.FEATURE_UPCOMING_ALARM,
        )
    }
}
