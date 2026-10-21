package com.easydownload.app

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat

/**
 * The screen you see when you open the app by its icon. Mostly a how-to, plus the
 * maintenance buttons: update the engine, allow notifications, send details of a problem.
 */
class MainActivity : AppCompatActivity() {

    private lateinit var engineStatus: TextView
    private lateinit var updateButton: Button
    private lateinit var notificationsButton: Button
    private lateinit var problemSection: View
    private lateinit var problemText: TextView
    private lateinit var sendButton: Button

    private val askNotifications =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { refresh() }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        engineStatus = findViewById(R.id.engine_status)
        updateButton = findViewById(R.id.btn_update)
        notificationsButton = findViewById(R.id.btn_notifications)
        problemSection = findViewById(R.id.problem_section)
        problemText = findViewById(R.id.last_problem)
        sendButton = findViewById(R.id.btn_send_details)

        updateButton.setOnClickListener { updateEngine() }
        notificationsButton.setOnClickListener {
            if (Build.VERSION.SDK_INT >= 33) askNotifications.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
        sendButton.setOnClickListener { sendDetails() }
    }

    override fun onResume() {
        super.onResume()
        refresh()
    }

    private fun refresh() {
        // Notifications button: only when it's needed.
        val needsNotifications = Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        notificationsButton.visibility = if (needsNotifications) View.VISIBLE else View.GONE

        // Last problem: only when there is one.
        val last = Errors.last(this)
        problemSection.visibility = if (last == null) View.GONE else View.VISIBLE
        problemText.text = last?.lineSequence()?.take(3)?.joinToString("\n") ?: ""

        // Engine version (first call can take a few seconds while the engine unpacks).
        engineStatus.text = getString(R.string.engine_checking)
        Thread {
            val version = Engine.versionName(this)
            runOnUiThread {
                engineStatus.text = if (version != null) {
                    getString(R.string.engine_version, version)
                } else {
                    getString(R.string.engine_unknown)
                }
            }
        }.start()
    }

    private fun updateEngine() {
        updateButton.isEnabled = false
        updateButton.setText(R.string.updating)
        Thread {
            val message = try {
                if (Engine.update(this)) getString(R.string.update_done) else getString(R.string.update_already)
            } catch (e: Exception) {
                getString(R.string.update_failed, e.message ?: "unknown error")
            }
            runOnUiThread {
                updateButton.isEnabled = true
                updateButton.setText(R.string.btn_update)
                Toast.makeText(this, message, Toast.LENGTH_LONG).show()
                refresh()
            }
        }.start()
    }

    private fun sendDetails() {
        val report = buildString {
            append("Easy Download problem report\n")
            append("Android ").append(Build.VERSION.RELEASE).append(" · ").append(Build.MODEL).append('\n')
            append("Engine: ").append(Engine.versionName(this@MainActivity) ?: "unknown").append("\n\n")
            append(Errors.last(this@MainActivity) ?: "No problem recorded.")
        }
        val send = Intent(Intent.ACTION_SEND)
            .setType("text/plain")
            .putExtra(Intent.EXTRA_TEXT, report)
        startActivity(Intent.createChooser(send, getString(R.string.send_details_title)))
    }
}
