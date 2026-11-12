package app.thelauncher.smartspace.provider

import android.app.Activity
import android.content.Context
import android.graphics.drawable.Icon
import app.thelauncher.BlankActivity
import app.thelauncher.getAppName
import androidx.core.graphics.drawable.toBitmap
import app.thelauncher.smartspace.model.GlanceExtras
import app.thelauncher.smartspace.model.SmartspaceAction
import app.thelauncher.smartspace.model.SmartspaceScores
import app.thelauncher.smartspace.model.SmartspaceTarget
import app.thelauncher.ui.preferences.PreferenceActivity
import app.thelauncher.ui.preferences.components.isNotificationServiceEnabled
import app.thelauncher.ui.preferences.components.notificationDotsEnabled
import app.thelauncher.ui.preferences.navigation.General
import com.android.launcher3.R
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.first

class NowPlayingProvider(context: Context) :
    SmartspaceDataSource(
        context,
        R.string.smartspace_now_playing,
        { smartspaceNowPlaying },
    ) {

    private val defaultIcon = Icon.createWithResource(context, R.drawable.ic_music_note)

    override val internalTargets = callbackFlow {
        val mediaListener = MediaListener(context) {
            trySend(listOfNotNull(getSmartspaceTarget(it)))
        }
        mediaListener.onResume()
        awaitClose { mediaListener.onPause() }
    }

    private fun getSmartspaceTarget(media: MediaListener): SmartspaceTarget? {
        val tracking = media.tracking ?: return null
        val title = tracking.info.title ?: return null

        val sbn = tracking.sbn
        val icon = sbn.notification.smallIcon ?: defaultIcon

        val mediaInfo = tracking.info
        val subtitle = mediaInfo.artist?.takeIf { it.isNotEmpty() }
            ?: sbn?.getAppName(context)
            ?: context.getAppName(tracking.packageName)
        val intent = sbn?.notification?.contentIntent
        val playing = tracking.playing
        // Album art, otherwise the playing app's icon, for the widget's row.
        val art = mediaInfo.art ?: runCatching {
            context.packageManager.getApplicationIcon(tracking.packageName).toBitmap()
        }.getOrNull()
        val appName = sbn?.getAppName(context) ?: context.getAppName(tracking.packageName)
        val controls = listOf(
            SmartspaceAction(
                id = "mediaPrevious",
                icon = Icon.createWithResource(context, R.drawable.ic_glance_skip_previous),
                title = context.getString(R.string.glance_media_previous),
                onClick = Runnable { media.previous(true) },
            ),
            SmartspaceAction(
                id = "mediaToggle",
                icon = Icon.createWithResource(context, if (playing) R.drawable.ic_glance_pause else R.drawable.ic_glance_play),
                title = context.getString(if (playing) R.string.glance_media_pause else R.string.glance_media_play),
                onClick = Runnable { media.toggle(false) },
            ),
            SmartspaceAction(
                id = "mediaNext",
                icon = Icon.createWithResource(context, R.drawable.ic_glance_skip_next),
                title = context.getString(R.string.glance_media_next),
                onClick = Runnable { media.next(true) },
            ),
        )
        return SmartspaceTarget(
            id = "nowPlaying-${mediaInfo.hashCode()}-$playing",
            headerAction = SmartspaceAction(
                id = "nowPlayingAction-${mediaInfo.hashCode()}",
                icon = icon,
                title = title,
                subtitle = subtitle,
                contentDescription = listOfNotNull(mediaInfo.artist?.takeIf { it.isNotEmpty() }, appName).joinToString(" · "),
                pendingIntent = intent,
                onClick = if (intent == null) Runnable { media.toggle(false) } else null,
                extras = art?.let { bitmap -> android.os.Bundle().apply { putParcelable(GlanceExtras.ART, bitmap) } },
                controls = controls,
            ),
            score = SmartspaceScores.SCORE_MEDIA,
            featureType = SmartspaceTarget.FeatureType.FEATURE_MEDIA,
        )
    }

    override suspend fun requiresSetup(): Boolean = isNotificationServiceEnabled(context = context).not() ||
        notificationDotsEnabled(context = context).first().not()

    override suspend fun startSetup(activity: Activity) {
        val intent = PreferenceActivity.createIntent(activity, General)
        val message = activity.getString(
            R.string.event_provider_missing_notification_dots,
            activity.getString(providerName),
        )
        BlankActivity.startBlankActivityDialog(
            activity,
            intent,
            activity.getString(R.string.title_missing_notification_access),
            message,
            context.getString(R.string.title_change_settings),
        )
    }
}
