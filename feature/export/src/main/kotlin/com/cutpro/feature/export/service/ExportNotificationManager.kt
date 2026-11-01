package com.cutpro.feature.export.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import java.util.Locale
import kotlin.math.abs

/**
 * Manages system notifications for background video export, with throttling and cancellation action.
 * Strictly zero emojis.
 */
class ExportNotificationManager(private val context: Context) {
    private val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
    private var lastUpdateTimestampMs = 0L
    private var lastRenderedPercent = -1

    init {
        createChannel()
    }

    private fun createChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Video Export",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Video export ongoing progress notifications"
                setShowBadge(false)
            }
            notificationManager.createNotificationChannel(channel)
        }
    }

    fun buildOngoingNotification(
        projectTitle: String,
        progressPercent: Int,
        elapsedMs: Long,
        etaMs: Long?
    ): Notification {
        val elapsedFormatted = formatDuration(elapsedMs)
        val etaFormatted = etaMs?.let { formatDuration(it) } ?: "calculating..."
        val contentText = "Rendering: $progressPercent% | Elapsed: $elapsedFormatted | ETA: $etaFormatted"

        val cancelIntent = Intent(context, ExportForegroundService::class.java).apply {
            action = ExportForegroundService.ACTION_CANCEL_EXPORT
        }
        val cancelPendingIntent = PendingIntent.getService(
            context,
            0,
            cancelIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(context, CHANNEL_ID)
            .setContentTitle("Exporting: $projectTitle")
            .setContentText(contentText)
            .setSmallIcon(android.R.drawable.stat_sys_upload)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setProgress(100, progressPercent, false)
            .addAction(android.R.drawable.ic_menu_close_clear_cancel, "Cancel", cancelPendingIntent)
            .build()
    }

    fun updateThrottled(
        projectTitle: String,
        progressPercent: Int,
        elapsedMs: Long,
        etaMs: Long?
    ) {
        val now = System.currentTimeMillis()
        val timeDelta = now - lastUpdateTimestampMs
        val percentDelta = abs(progressPercent - lastRenderedPercent)

        // Throttle: update only if percent changed by >= 1% or timeDelta >= 500ms
        if (percentDelta >= 1 || timeDelta >= 500L) {
            lastUpdateTimestampMs = now
            lastRenderedPercent = progressPercent
            val notif = buildOngoingNotification(projectTitle, progressPercent, elapsedMs, etaMs)
            notificationManager.notify(NOTIFICATION_ID, notif)
        }
    }

    fun notifyCompleted(projectTitle: String, sharePendingIntent: PendingIntent?) {
        val builder = NotificationCompat.Builder(context, CHANNEL_ID)
            .setContentTitle("Export Complete")
            .setContentText("Successfully exported $projectTitle")
            .setSmallIcon(android.R.drawable.stat_sys_upload_done)
            .setOngoing(false)
            .setAutoCancel(true)

        if (sharePendingIntent != null) {
            builder.addAction(android.R.drawable.ic_menu_share, "Share", sharePendingIntent)
        }

        notificationManager.notify(NOTIFICATION_ID, builder.build())
    }

    fun notifyFailed(projectTitle: String, errorMessage: String) {
        val notif = NotificationCompat.Builder(context, CHANNEL_ID)
            .setContentTitle("Export Failed")
            .setContentText("$projectTitle: $errorMessage")
            .setSmallIcon(android.R.drawable.stat_notify_error)
            .setOngoing(false)
            .setAutoCancel(true)
            .build()
        notificationManager.notify(NOTIFICATION_ID, notif)
    }

    fun cancelNotification() {
        notificationManager.cancel(NOTIFICATION_ID)
    }

    companion object {
        const val CHANNEL_ID = "cutpro_export_channel"
        const val NOTIFICATION_ID = 4001

        fun formatDuration(ms: Long): String {
            val totalSec = ms / 1000
            val m = totalSec / 60
            val s = totalSec % 60
            return String.format(Locale.US, "%02d:%02d", m, s)
        }
    }
}
