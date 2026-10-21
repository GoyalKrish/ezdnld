package com.easydownload.app

import android.content.Context
import android.util.Log
import com.yausername.ffmpeg.FFmpeg
import com.yausername.youtubedl_android.YoutubeDL

/**
 * Thin wrapper around the yt-dlp Android library: one-time init plus updating.
 * YouTube changes things often, and a stale yt-dlp is the #1 reason downloads break,
 * so updating is built in rather than left to the user.
 */
object Engine {

    const val HOUR = 60L * 60L * 1000L

    private const val TAG = "EasyDownload"
    private const val PREFS = "engine"
    private const val KEY_LAST_CHECK = "last_update_check"

    @Volatile
    private var ready = false

    @Synchronized
    fun ensureReady(ctx: Context) {
        if (ready) return
        val app = ctx.applicationContext
        YoutubeDL.getInstance().init(app)
        FFmpeg.getInstance().init(app)
        ready = true
    }

    /** Checks for a newer yt-dlp and installs it. Returns true only if a newer version was installed. */
    @Synchronized
    fun update(ctx: Context): Boolean {
        ensureReady(ctx)
        val app = ctx.applicationContext
        val status = YoutubeDL.getInstance().updateYoutubeDL(app)
        app.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().putLong(KEY_LAST_CHECK, System.currentTimeMillis()).apply()
        return status?.name == "DONE"
    }

    /** Like [update], but skipped if we already checked within [maxAgeMs]. Never throws. */
    fun updateIfStale(ctx: Context, maxAgeMs: Long): Boolean {
        val app = ctx.applicationContext
        val last = app.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getLong(KEY_LAST_CHECK, 0L)
        if (System.currentTimeMillis() - last < maxAgeMs) return false
        return try {
            update(app)
        } catch (e: Exception) {
            Log.w(TAG, "yt-dlp update check failed", e)
            false
        }
    }

    fun versionName(ctx: Context): String? = try {
        ensureReady(ctx)
        YoutubeDL.getInstance().versionName(ctx.applicationContext)
    } catch (e: Exception) {
        null
    }
}
