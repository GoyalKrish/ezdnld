package com.easydownload.app

import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import java.util.concurrent.atomic.AtomicInteger

object Notifications {

    private const val CH_PROGRESS = "progress"
    private const val CH_DONE = "done"

    /** The foreground-service notification; also used to show download progress. */
    const val ID_PROGRESS = 1

    private val nextId = AtomicInteger(100)

    fun createChannels(ctx: Context) {
        val nm = ctx.getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(
            NotificationChannel(CH_PROGRESS, "Downloading", NotificationManager.IMPORTANCE_LOW)
        )
        nm.createNotificationChannel(
            NotificationChannel(CH_DONE, "Finished downloads", NotificationManager.IMPORTANCE_DEFAULT)
        )
    }

    /** [percent] outside 0..100 shows an indeterminate bar. */
    fun progress(ctx: Context, title: String, text: String, percent: Int): Notification {
        val b = NotificationCompat.Builder(ctx, CH_PROGRESS)
            .setSmallIcon(R.drawable.ic_stat_download)
            .setContentTitle(title)
            .setContentText(text)
            .setOnlyAlertOnce(true)
            .setOngoing(true)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
        if (percent in 0..100) b.setProgress(100, percent, false) else b.setProgress(0, 0, true)
        return b.build()
    }

    @SuppressLint("MissingPermission")
    fun show(ctx: Context, id: Int, notification: Notification) {
        try {
            NotificationManagerCompat.from(ctx).notify(id, notification)
        } catch (_: SecurityException) {
            // Notifications not allowed; the in-app toast still tells the person what happened.
        }
    }

    /** "Done" notification. Tapping it opens the file in Gallery / the music player. */
    fun done(ctx: Context, title: String, text: String, uri: Uri?, mime: String?) {
        val id = nextId.getAndIncrement()
        val b = NotificationCompat.Builder(ctx, CH_DONE)
            .setSmallIcon(R.drawable.ic_stat_download)
            .setContentTitle(title)
            .setContentText(text)
            .setAutoCancel(true)
        if (uri != null) {
            val view = Intent(Intent.ACTION_VIEW)
                .setDataAndType(uri, mime)
                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
            b.setContentIntent(
                PendingIntent.getActivity(ctx, id, view, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
            )
        }
        show(ctx, id, b.build())
    }

    /** "Failed" notification. Tapping it opens the app, where the details can be sent. */
    fun failed(ctx: Context, text: String) {
        val id = nextId.getAndIncrement()
        val open = Intent(ctx, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        val b = NotificationCompat.Builder(ctx, CH_DONE)
            .setSmallIcon(R.drawable.ic_stat_download)
            .setContentTitle(ctx.getString(R.string.notif_failed))
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setAutoCancel(true)
            .setContentIntent(
                PendingIntent.getActivity(ctx, id, open, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
            )
        show(ctx, id, b.build())
    }
}
