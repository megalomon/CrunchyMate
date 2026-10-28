package de.crunchymate.sync

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.graphics.drawable.Icon
import de.crunchymate.R
import de.crunchymate.core.ffi.SyncKind
import de.crunchymate.core.ffi.SyncPhase

/** Benachrichtigung für laufende Synchronisationen; eine eigene ID je Art, damit beide parallel sichtbar sind. */
object SyncNotifications {
    const val CHANNEL_ID = "sync"
    const val NOTIFICATION_ID_CATALOG = 1
    const val NOTIFICATION_ID_METADATA = 2

    fun notificationId(kind: SyncKind): Int =
        when (kind) {
            SyncKind.CATALOG -> NOTIFICATION_ID_CATALOG
            SyncKind.METADATA -> NOTIFICATION_ID_METADATA
        }

    fun ensureChannel(context: Context) {
        val manager = context.getSystemService(NotificationManager::class.java)
        val channel =
            NotificationChannel(
                CHANNEL_ID,
                context.getString(R.string.notification_channel_sync_name),
                NotificationManager.IMPORTANCE_LOW,
            ).apply { description = context.getString(R.string.notification_channel_sync_description) }
        manager.createNotificationChannel(channel)
    }

    fun build(
        context: Context,
        kind: SyncKind,
        phase: SyncPhase,
        done: Long,
        total: Long,
        cancelIntent: PendingIntent,
    ): Notification {
        val title =
            when (kind) {
                SyncKind.CATALOG -> R.string.notification_sync_catalog_title
                SyncKind.METADATA -> R.string.notification_sync_metadata_title
            }
        val text =
            if (total > 0) {
                context.getString(R.string.notification_sync_phase_progress, context.getString(syncPhaseLabel(kind, phase)), done, total)
            } else {
                context.getString(R.string.notification_sync_preparing)
            }
        val cancelAction =
            Notification.Action
                .Builder(
                    Icon.createWithResource(context, R.drawable.ic_sync_cancel),
                    context.getString(R.string.notification_action_cancel),
                    cancelIntent,
                ).build()
        return Notification
            .Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification_sync)
            .setContentTitle(context.getString(title))
            .setContentText(text)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setCategory(Notification.CATEGORY_PROGRESS)
            .setForegroundServiceBehavior(Notification.FOREGROUND_SERVICE_IMMEDIATE)
            .apply {
                if (total > 0) {
                    setProgress(total.toProgressMax(), done.coerceAtMost(total).toProgressMax(), false)
                } else {
                    setProgress(0, 0, true)
                }
            }.addAction(cancelAction)
            .build()
    }

    // Notification.setProgress erwartet Int; Katalog- und Anreicherungszähler bleiben weit darunter
    private fun Long.toProgressMax(): Int = coerceAtMost(Int.MAX_VALUE.toLong()).toInt()
}
