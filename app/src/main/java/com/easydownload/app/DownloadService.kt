package com.easydownload.app

import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.SystemClock
import android.util.Log
import android.widget.Toast
import androidx.core.app.ServiceCompat
import com.yausername.youtubedl_android.YoutubeDL
import com.yausername.youtubedl_android.YoutubeDLRequest
import java.io.File
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicInteger

/**
 * Runs downloads one at a time in the background, so they keep going after the popup closes
 * and even if the person leaves the app.
 */
class DownloadService : Service() {

    private val worker: ExecutorService = Executors.newSingleThreadExecutor()
    private val pending = AtomicInteger(0)
    private val main = Handler(Looper.getMainLooper())
    private val processCounter = AtomicInteger(0)

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val url = intent?.getStringExtra(EXTRA_URL)
        val mode = intent?.getStringExtra(EXTRA_MODE)?.let { runCatching { Mode.valueOf(it) }.getOrNull() }

        // Count the job before going foreground, so finishJob() of an earlier job never tears this one down.
        pending.incrementAndGet()
        ServiceCompat.startForeground(
            this,
            Notifications.ID_PROGRESS,
            Notifications.progress(this, getString(R.string.notif_getting_ready), "", -1),
            ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
        )

        if (url == null || mode == null) {
            finishJob(startId)
            return START_NOT_STICKY
        }

        worker.execute {
            try {
                process(url, mode)
            } finally {
                finishJob(startId)
            }
        }
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        worker.shutdown()
        super.onDestroy()
    }

    private fun finishJob(startId: Int) {
        pending.decrementAndGet()
        main.post {
            if (pending.get() == 0) stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf(startId) // no-op if a newer job has been started meanwhile
        }
    }

    private fun process(url: String, mode: Mode) {
        val title = getString(
            if (mode == Mode.AUDIO) R.string.notif_downloading_audio else R.string.notif_downloading_video
        )
        Notifications.show(
            this, Notifications.ID_PROGRESS,
            Notifications.progress(this, title, getString(R.string.notif_starting), -1)
        )

        val base = getExternalFilesDir(null) ?: filesDir
        val tmp = File(base, "tmp/${System.currentTimeMillis()}").apply { mkdirs() }

        try {
            Engine.ensureReady(this)

            try {
                runYtDlp(url, mode, tmp, title)
            } catch (e: Exception) {
                // Sites change constantly. If yt-dlp is out of date, refresh it once and try again.
                if (Engine.updateIfStale(this, Engine.HOUR)) {
                    tmp.listFiles()?.forEach { it.deleteRecursively() }
                    runYtDlp(url, mode, tmp, title)
                } else {
                    throw e
                }
            }

            val file = pickOutput(tmp) ?: throw IllegalStateException("The download finished but no file was produced")
            val saved = MediaSaver.save(this, file)

            Notifications.done(
                this,
                getString(R.string.notif_done),
                saved.displayName,
                saved.uri,
                saved.mime
            )
            toast(getString(if (saved.isAudio) R.string.toast_done_audio else R.string.toast_done_video))
        } catch (e: Throwable) {
            Log.e(TAG, "Download failed for $url", e)
            Errors.record(this, url, e)
            val message = Errors.friendly(e)
            Notifications.failed(this, message)
            toast(message)
        } finally {
            tmp.deleteRecursively()
        }
    }

    private fun runYtDlp(url: String, mode: Mode, tmp: File, title: String) {
        val request = YoutubeDLRequest(url)
        request.addOption("--no-playlist") // a shared link often carries "&list=..."; only get the one video
        request.addOption("--no-mtime")
        request.addOption("-o", File(tmp, "%(title).80s.%(ext)s").absolutePath)

        when (mode) {
            Mode.VIDEO_HD -> videoOptions(request, 720)
            Mode.VIDEO_SMALL -> videoOptions(request, 360)
            Mode.AUDIO -> {
                request.addOption("-f", "ba/b")
                request.addOption("-x")
                request.addOption("--audio-format", "mp3")
                request.addOption("--audio-quality", "0")
            }
        }

        var lastUpdate = 0L
        val processId = "job-${processCounter.incrementAndGet()}"
        YoutubeDL.getInstance().execute(request, processId) { progress, etaSeconds, _ ->
            val now = SystemClock.elapsedRealtime()
            if (now - lastUpdate >= 800) {
                lastUpdate = now
                val percent = if (progress >= 0f) progress.toInt().coerceIn(0, 100) else -1
                val text = buildString {
                    if (percent >= 0) append("$percent%")
                    val eta = formatEta(etaSeconds)
                    if (eta.isNotEmpty()) {
                        if (isNotEmpty()) append(" · ")
                        append(eta)
                    }
                    if (isEmpty()) append(getString(R.string.notif_starting))
                }
                Notifications.show(
                    this, Notifications.ID_PROGRESS,
                    Notifications.progress(this, title, text, percent)
                )
            }
        }
    }

    /** Best video up to [maxHeight], preferring H.264/AAC so it plays on any phone, merged into one MP4. */
    private fun videoOptions(request: YoutubeDLRequest, maxHeight: Int) {
        request.addOption("-f", "bv*+ba/b")
        request.addOption("-S", "res:$maxHeight,vcodec:h264,acodec:aac")
        request.addOption("--merge-output-format", "mp4")
    }

    private fun pickOutput(dir: File): File? {
        val files = dir.listFiles()
            ?.filter { it.isFile && !it.name.endsWith(".part") && !it.name.endsWith(".ytdl") && !it.name.endsWith(".temp") }
            ?: return null
        val media = files.filter { it.extension.lowercase() in MEDIA_EXT }
        return (media.ifEmpty { files }).maxByOrNull { it.length() }
    }

    private fun formatEta(seconds: Long): String = when {
        seconds <= 0 -> ""
        seconds < 60 -> "$seconds sec left"
        else -> "${seconds / 60} min left"
    }

    private fun toast(message: String) {
        main.post { Toast.makeText(applicationContext, message, Toast.LENGTH_LONG).show() }
    }

    companion object {
        const val EXTRA_URL = "url"
        const val EXTRA_MODE = "mode"
        private const val TAG = "EasyDownload"
        private val MEDIA_EXT = setOf("mp4", "mp3", "m4a", "mkv", "webm", "mov", "opus", "ogg", "aac")
    }
}
