package app.thelauncher.smartspace.model

import android.app.PendingIntent
import android.content.Intent
import android.graphics.drawable.Icon
import android.os.Bundle

data class SmartspaceAction(
    val id: String,
    val icon: Icon? = null,
    val title: CharSequence,
    val subtitle: CharSequence? = null,
    val contentDescription: CharSequence? = null,
    val pendingIntent: PendingIntent? = null,
    val intent: Intent? = null,
    val onClick: Runnable? = null,
    val extras: Bundle? = null,
    /** Buttons the At a Glance widget shows on this item's row (media controls, Turn off). */
    val controls: List<SmartspaceAction> = emptyList(),
)

val SmartspaceAction?.hasIntent get() = this != null && (intent != null || pendingIntent != null || onClick != null)
