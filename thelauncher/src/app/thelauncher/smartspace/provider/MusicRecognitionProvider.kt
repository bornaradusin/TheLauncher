package app.thelauncher.smartspace.provider

import android.app.Activity
import android.app.Notification
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.drawable.Icon
import android.os.Build
import android.provider.Settings
import android.service.notification.StatusBarNotification
import app.thelauncher.BlankActivity
import app.thelauncher.NotificationManager
import app.thelauncher.smartspace.model.SmartspaceAction
import app.thelauncher.smartspace.model.SmartspaceScores
import app.thelauncher.smartspace.model.SmartspaceTarget
import app.thelauncher.ui.preferences.components.isNotificationServiceEnabled
import app.thelauncher.ui.preferences.components.notificationDotsEnabled
import com.android.launcher3.R
import com.android.launcher3.notification.NotificationListener
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow

/**
 * The Pixel's Now Playing: songs it recognises playing nearby. Android System Intelligence posts
 * each one as a notification with the song and artist; At a Glance shows the latest for a while.
 */
class MusicRecognitionProvider(context: Context) :
    SmartspaceDataSource(
        context,
        R.string.smartspace_music_recognition,
        { smartspaceMusicRecognition },
    ) {

    override val isAvailable: Boolean = PACKAGES.any { pkg ->
        runCatching { context.packageManager.getApplicationInfo(pkg, 0) }.isSuccess
    }

    private val musicIcon = Icon.createWithResource(context, R.drawable.ic_music_note)

    override val internalTargets: Flow<List<SmartspaceTarget>> = combine(
        NotificationManager.INSTANCE.get(context).notifications,
        everyMinute(),
    ) { notifications, now ->
        notifications
            .filter { isRecognisedSong(it) && now - it.postTime < SHOW_FOR_MS }
            .maxByOrNull { it.postTime }
            ?.let { listOfNotNull(target(it)) }
            ?: emptyList()
    }

    private fun everyMinute() = flow {
        while (true) {
            emit(System.currentTimeMillis())
            delay(TimeUnit.MINUTES.toMillis(1))
        }
    }

    private fun isRecognisedSong(sbn: StatusBarNotification): Boolean {
        val pkg = sbn.packageName
        if (pkg !in PACKAGES && !pkg.contains("nowplaying")) return false
        val channel = sbn.notification.channelId?.lowercase().orEmpty()
        return CHANNEL_WORDS.any { channel.contains(it) }
    }

    private fun target(sbn: StatusBarNotification): SmartspaceTarget? {
        val extras = sbn.notification.extras
        val song = extras.getCharSequence(Notification.EXTRA_TITLE)?.toString()?.takeIf { it.isNotBlank() }
            ?: return null
        val artist = extras.getCharSequence(Notification.EXTRA_TEXT)?.toString()?.takeIf { it.isNotBlank() }
        return SmartspaceTarget(
            id = "musicRecognition-${song.hashCode()}",
            headerAction = SmartspaceAction(
                id = "musicRecognitionAction",
                icon = musicIcon,
                title = song,
                subtitle = artist,
                contentDescription = artist,
                // Opens the song in Now Playing.
                pendingIntent = sbn.notification.contentIntent,
            ),
            score = SmartspaceScores.SCORE_MEDIA,
            featureType = SmartspaceTarget.FeatureType.FEATURE_MEDIA,
        )
    }

    // The notification is read by TheLauncher's notification listener, which Android runs only
    // with notification access and notification dots on.
    override suspend fun requiresSetup(): Boolean = isNotificationServiceEnabled(context = context).not() ||
        notificationDotsEnabled(context = context).first().not()

    override suspend fun startSetup(activity: Activity) {
        val listener = ComponentName(activity, NotificationListener::class.java)
        val intent = if (!isNotificationServiceEnabled(context = activity) && Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            Intent(Settings.ACTION_NOTIFICATION_LISTENER_DETAIL_SETTINGS)
                .putExtra(Settings.EXTRA_NOTIFICATION_LISTENER_COMPONENT_NAME, listener.flattenToString())
        } else {
            app.thelauncher.ui.preferences.PreferenceActivity.createIntent(
                activity,
                app.thelauncher.ui.preferences.navigation.General,
            )
        }
        BlankActivity.startBlankActivityDialog(
            activity,
            intent,
            activity.getString(R.string.title_missing_notification_access),
            activity.getString(R.string.event_provider_missing_notification_dots, activity.getString(providerName)),
            activity.getString(R.string.title_change_settings),
        )
    }

    companion object {
        /** Android System Intelligence, and the older Pixel Ambient Services. */
        private val PACKAGES = listOf("com.google.android.as", "com.google.intelligence.sense")

        /** Words in the id of the recognised-music notification channel. */
        private val CHANNEL_WORDS = listOf("music", "ambient", "song", "recogni", "nowplaying", "now_playing")

        /** How long a recognised song stays on At a Glance after it was heard. */
        private val SHOW_FOR_MS = TimeUnit.MINUTES.toMillis(10)
    }
}
