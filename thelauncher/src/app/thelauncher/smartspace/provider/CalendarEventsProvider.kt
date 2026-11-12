package app.thelauncher.smartspace.provider

import android.Manifest
import android.app.Activity
import android.content.ContentUris
import android.content.Context
import android.content.Intent
import android.database.ContentObserver
import android.graphics.drawable.Icon
import android.os.Handler
import android.os.Looper
import android.provider.CalendarContract
import android.text.format.DateFormat
import android.text.format.DateUtils
import app.thelauncher.extras.PermissionRequestActivity
import app.thelauncher.preferences2.PreferenceManager2
import app.thelauncher.smartspace.model.GlanceExtras
import app.thelauncher.smartspace.model.SmartspaceAction
import app.thelauncher.smartspace.model.SmartspaceScores
import app.thelauncher.smartspace.model.SmartspaceTarget
import com.android.launcher3.R
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.launch

class CalendarEventsProvider(context: Context) :
    SmartspaceDataSource(
        context,
        R.string.smartspace_calendar_events,
        { smartspaceCalendarEvents },
    ) {

    private val prefs = PreferenceManager2.getInstance(context)

    @OptIn(ExperimentalCoroutinesApi::class)
    override val internalTargets = prefs.smartspaceCalendarIds.get().flatMapLatest { calendarIds ->
        callbackFlow {
            fun refresh() {
                trySend(listOfNotNull(queryNextEvent(calendarIds)))
            }
            val observer = object : ContentObserver(Handler(Looper.getMainLooper())) {
                override fun onChange(selfChange: Boolean) {
                    launch(Dispatchers.IO) { refresh() }
                }
            }
            runCatching {
                context.contentResolver.registerContentObserver(CalendarContract.CONTENT_URI, true, observer)
            }
            val ticker = launch(Dispatchers.IO) {
                while (true) {
                    refresh()
                    delay(60_000L)
                }
            }
            awaitClose {
                ticker.cancel()
                context.contentResolver.unregisterContentObserver(observer)
            }
        }.flowOn(Dispatchers.IO)
    }

    private fun queryNextEvent(calendarIds: Set<String>): SmartspaceTarget? {
        if (!PermissionRequestActivity.hasPermissions(context, Manifest.permission.READ_CALENDAR)) return null
        val now = System.currentTimeMillis()
        val end = now + DateUtils.DAY_IN_MILLIS
        val uri = CalendarContract.Instances.CONTENT_URI.buildUpon().also {
            ContentUris.appendId(it, now)
            ContentUris.appendId(it, end)
        }.build()
        val projection = arrayOf(
            CalendarContract.Instances.EVENT_ID,
            CalendarContract.Instances.TITLE,
            CalendarContract.Instances.BEGIN,
            CalendarContract.Instances.END,
            CalendarContract.Instances.ALL_DAY,
            CalendarContract.Instances.CALENDAR_ID,
            CalendarContract.Instances.EVENT_LOCATION,
        )
        val selection = "${CalendarContract.Instances.VISIBLE} = 1 AND ${CalendarContract.Instances.END} > ?"
        val cursor = runCatching {
            context.contentResolver.query(
                uri,
                projection,
                selection,
                arrayOf(now.toString()),
                "${CalendarContract.Instances.BEGIN} ASC",
            )
        }.getOrNull() ?: return null
        cursor.use {
            while (it.moveToNext()) {
                val allDay = it.getInt(4) == 1
                if (allDay) continue
                val calendarId = it.getLong(5).toString()
                if (calendarIds.isNotEmpty() && calendarId !in calendarIds) continue
                val eventId = it.getLong(0)
                val title = it.getString(1)?.takeIf { t -> t.isNotBlank() }
                    ?: context.getString(R.string.smartspace_calendar_untitled)
                val begin = it.getLong(2)
                val finish = it.getLong(3)
                val location = it.getString(6)
                return buildTarget(eventId, title, begin, finish, location, now)
            }
        }
        return null
    }

    private fun buildTarget(
        eventId: Long,
        title: String,
        begin: Long,
        end: Long,
        location: String?,
        now: Long,
    ): SmartspaceTarget {
        val timeFormat = DateFormat.getTimeFormat(context)
        val range = "${timeFormat.format(begin)} – ${timeFormat.format(end)}"
        val whenText = if (begin <= now) {
            context.getString(R.string.smartspace_calendar_now)
        } else {
            DateUtils.getRelativeTimeSpanString(begin, now, DateUtils.MINUTE_IN_MILLIS).toString()
        }
        val subtitle = listOfNotNull(whenText, range, location?.takeIf { it.isNotBlank() })
            .joinToString(" · ")
        val intent = Intent(Intent.ACTION_VIEW)
            .setData(ContentUris.withAppendedId(CalendarContract.Events.CONTENT_URI, eventId))
            .putExtra(CalendarContract.EXTRA_EVENT_BEGIN_TIME, begin)
            .putExtra(CalendarContract.EXTRA_EVENT_END_TIME, end)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        return SmartspaceTarget(
            id = "calendarEvent-$eventId-$begin",
            headerAction = SmartspaceAction(
                id = "calendarEventAction-$eventId",
                icon = Icon.createWithResource(context, R.drawable.ic_calendar_event),
                title = title,
                subtitle = subtitle,
                intent = intent,
                extras = android.os.Bundle().apply { putLong(GlanceExtras.BEGIN, begin) },
            ),
            score = SmartspaceScores.SCORE_CALENDAR,
            featureType = SmartspaceTarget.FeatureType.FEATURE_CALENDAR,
        )
    }

    override suspend fun requiresSetup(): Boolean = !PermissionRequestActivity.hasPermissions(context, Manifest.permission.READ_CALENDAR)

    override suspend fun startSetup(activity: Activity) {
        PermissionRequestActivity.request(activity, Manifest.permission.READ_CALENDAR)
    }

    data class CalendarInfo(val id: String, val name: String, val account: String, val color: Int)

    companion object {
        fun listCalendars(context: Context): List<CalendarInfo> {
            if (!PermissionRequestActivity.hasPermissions(context, Manifest.permission.READ_CALENDAR)) return emptyList()
            val projection = arrayOf(
                CalendarContract.Calendars._ID,
                CalendarContract.Calendars.CALENDAR_DISPLAY_NAME,
                CalendarContract.Calendars.ACCOUNT_NAME,
                CalendarContract.Calendars.CALENDAR_COLOR,
            )
            val cursor = runCatching {
                context.contentResolver.query(CalendarContract.Calendars.CONTENT_URI, projection, null, null, null)
            }.getOrNull() ?: return emptyList()
            return cursor.use {
                buildList {
                    while (it.moveToNext()) {
                        add(
                            CalendarInfo(
                                id = it.getLong(0).toString(),
                                name = it.getString(1).orEmpty(),
                                account = it.getString(2).orEmpty(),
                                color = it.getInt(3),
                            ),
                        )
                    }
                }
            }
        }
    }
}
