package com.easydownload.app

import android.app.Application
import android.util.Log

class DadApp : Application() {

    override fun onCreate() {
        super.onCreate()
        Notifications.createChannels(this)

        // Warm up the download engine in the background so the first download isn't slow,
        // and quietly refresh yt-dlp if it hasn't been checked for a while.
        Thread {
            try {
                Engine.ensureReady(this)
                Engine.updateIfStale(this, 12 * Engine.HOUR)
            } catch (e: Throwable) {
                Log.e(TAG, "Engine warm-up failed", e)
            }
        }.start()
    }

    private companion object {
        const val TAG = "EasyDownload"
    }
}
