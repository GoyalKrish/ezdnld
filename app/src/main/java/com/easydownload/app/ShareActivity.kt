package com.easydownload.app

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.view.View
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialog

/**
 * The popup. Shows up over whatever app the link was shared from (YouTube, Instagram, ...),
 * asks "Video or Audio?", starts the download, and gets out of the way.
 */
class ShareActivity : AppCompatActivity() {

    private var sheet: BottomSheetDialog? = null

    private val askNotifications =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { /* nothing to do either way */ }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val url = extractUrl(intent)
        if (url == null) {
            Toast.makeText(this, R.string.no_link, Toast.LENGTH_LONG).show()
            finish()
            return
        }

        showSheet(url)
        askForNotificationsOnce()
    }

    private fun showSheet(url: String) {
        val view = layoutInflater.inflate(R.layout.sheet_share, null)

        val host = Uri.parse(url).host
            ?.removePrefix("www.")
            ?.removePrefix("m.")
            ?: url
        view.findViewById<TextView>(R.id.source).text = getString(R.string.from_site, host)

        view.findViewById<View>(R.id.btn_video).setOnClickListener { start(url, Mode.VIDEO_HD) }
        view.findViewById<View>(R.id.btn_audio).setOnClickListener { start(url, Mode.AUDIO) }
        view.findViewById<View>(R.id.btn_small).setOnClickListener { start(url, Mode.VIDEO_SMALL) }

        val dialog = BottomSheetDialog(this)
        dialog.setContentView(view)
        dialog.behavior.state = BottomSheetBehavior.STATE_EXPANDED
        dialog.behavior.skipCollapsed = true
        dialog.setOnDismissListener { if (!isFinishing) finish() }
        dialog.show()
        sheet = dialog
    }

    private fun start(url: String, mode: Mode) {
        val intent = Intent(this, DownloadService::class.java)
            .putExtra(DownloadService.EXTRA_URL, url)
            .putExtra(DownloadService.EXTRA_MODE, mode.name)
        ContextCompat.startForegroundService(this, intent)
        Toast.makeText(this, R.string.toast_started, Toast.LENGTH_LONG).show()
        sheet?.dismiss()
    }

    /** Apps put the link inside a sentence ("Watch this! https://youtu.be/..."), so pull out just the URL. */
    private fun extractUrl(intent: Intent): String? {
        val text = intent.getCharSequenceExtra(Intent.EXTRA_TEXT)?.toString() ?: return null
        return URL_REGEX.find(text)?.value?.trimEnd('.', ',', ')', '"', '\'')
    }

    private fun askForNotificationsOnce() {
        if (Build.VERSION.SDK_INT < 33) return
        val granted = ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) ==
            PackageManager.PERMISSION_GRANTED
        val prefs = getSharedPreferences("ui", MODE_PRIVATE)
        if (granted || prefs.getBoolean("asked_notifications", false)) return
        prefs.edit().putBoolean("asked_notifications", true).apply()
        askNotifications.launch(Manifest.permission.POST_NOTIFICATIONS)
    }

    override fun onDestroy() {
        sheet?.setOnDismissListener(null)
        sheet?.dismiss()
        super.onDestroy()
    }

    private companion object {
        val URL_REGEX = Regex("https?://\\S+")
    }
}
