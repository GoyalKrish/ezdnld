package com.easydownload.app

import android.content.Context
import java.text.DateFormat
import java.util.Date

/** Turns scary yt-dlp errors into one friendly sentence, and remembers the last raw error for support. */
object Errors {

    private const val PREFS = "errors"
    private const val KEY_LAST = "last_error"

    fun friendly(e: Throwable): String {
        val m = (e.message ?: "").lowercase()
        return when {
            "confirm your age" in m || "age-restricted" in m || "age restricted" in m ->
                "This video is age-restricted, so it can't be downloaded."
            "private" in m || "login required" in m || "log in" in m || "cookies" in m ->
                "This needs a login (private or restricted), so it can't be downloaded."
            "sign in to confirm" in m || "not a bot" in m ->
                "YouTube is asking for a check. Please try again in a few minutes."
            "unsupported url" in m ->
                "This link isn't supported."
            "no space left" in m ->
                "The phone is full. Free up some space and try again."
            "unable to resolve host" in m || "network is unreachable" in m ||
                "timed out" in m || "urlopen error" in m || "connection" in m ->
                "No internet connection. Please try again."
            "video unavailable" in m || "has been removed" in m || "no longer available" in m ->
                "This video isn't available any more."
            else ->
                "Something went wrong. Open the app and tap \"Send details\" to share the problem."
        }
    }

    fun record(ctx: Context, url: String, e: Throwable) {
        val stamp = DateFormat.getDateTimeInstance().format(Date())
        val body = (e.message ?: e.toString()).take(4000)
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putString(KEY_LAST, "$stamp\n$url\n\n$body")
            .apply()
    }

    fun last(ctx: Context): String? =
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY_LAST, null)
}
