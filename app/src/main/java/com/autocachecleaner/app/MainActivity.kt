package com.autocachecleaner.app

import android.Manifest
import android.content.ActivityNotFoundException
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.text.format.Formatter
import android.view.View
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.Spinner
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat

class MainActivity : AppCompatActivity() {

    private lateinit var cacheSizeText: TextView
    private lateinit var resultText: TextView
    private lateinit var updateStatusText: TextView
    private lateinit var checkUpdateButton: Button

    private val intervalLabels =
        arrayOf("Every 3 days", "Every 7 days", "Every 14 days", "Every 30 days", "Off")
    private val intervalValues = longArrayOf(3, 7, 14, 30, 0)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        cacheSizeText = findViewById(R.id.cacheSizeText)
        resultText = findViewById(R.id.resultText)
        updateStatusText = findViewById(R.id.updateStatusText)
        checkUpdateButton = findViewById(R.id.checkUpdateButton)

        findViewById<TextView>(R.id.versionText).text =
            getString(R.string.version_format, BuildConfig.VERSION_NAME, BuildConfig.VERSION_CODE)

        findViewById<Button>(R.id.clearCacheButton).setOnClickListener { clearCache() }
        findViewById<Button>(R.id.scanAppsButton).setOnClickListener {
            startActivity(Intent(this, AppCachesActivity::class.java))
        }
        findViewById<Button>(R.id.openStorageButton).setOnClickListener { openStorageSettings() }
        checkUpdateButton.setOnClickListener { checkForUpdate() }

        requestNotificationPermission()
        setupReminderSpinner()
        refreshCacheSize()

        // Opened from the "update available" notification.
        if (intent.getBooleanExtra(EXTRA_CHECK_UPDATE, false)) checkForUpdate()
    }

    override fun onResume() {
        super.onResume()
        refreshCacheSize()
    }

    // ---- Cache ----------------------------------------------------------------------

    private fun refreshCacheSize() {
        val size = Formatter.formatFileSize(this, CacheCleaner.cacheSize(this))
        cacheSizeText.text = getString(R.string.cache_size_format, size)
    }

    private fun clearCache() {
        val freed = CacheCleaner.clear(this)
        resultText.text = getString(
            R.string.cache_cleared_format,
            Formatter.formatFileSize(this, freed)
        )
        refreshCacheSize()
    }

    private fun openStorageSettings() {
        try {
            startActivity(Intent(Settings.ACTION_INTERNAL_STORAGE_SETTINGS))
        } catch (e: ActivityNotFoundException) {
            startActivity(Intent(Settings.ACTION_SETTINGS))
        }
    }

    // ---- Reminders ------------------------------------------------------------------

    private fun setupReminderSpinner() {
        // First launch: default to a reminder every 7 days.
        if (!ReminderScheduler.hasSavedInterval(this)) {
            ReminderScheduler.setInterval(this, ReminderScheduler.DEFAULT_DAYS)
        }

        val spinner = findViewById<Spinner>(R.id.reminderSpinner)
        spinner.adapter = ArrayAdapter(
            this,
            android.R.layout.simple_spinner_dropdown_item,
            intervalLabels
        )
        val selected = intervalValues.indexOf(ReminderScheduler.currentInterval(this))
        spinner.setSelection(if (selected >= 0) selected else 1)

        var firstCallback = true
        spinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                if (firstCallback) {
                    firstCallback = false
                    return
                }
                ReminderScheduler.setInterval(this@MainActivity, intervalValues[position])
            }

            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }
    }

    private fun requestNotificationPermission() {
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
            != PackageManager.PERMISSION_GRANTED
        ) {
            requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 100)
        }
    }

    // ---- Updates --------------------------------------------------------------------

    private fun checkForUpdate() {
        checkUpdateButton.isEnabled = false
        updateStatusText.text = getString(R.string.checking_for_update)

        Thread {
            val info = UpdateChecker.fetchLatest()
            runOnUiThread {
                if (isFinishing || isDestroyed) return@runOnUiThread
                checkUpdateButton.isEnabled = true
                when {
                    info == null ->
                        updateStatusText.text = getString(R.string.update_check_failed)
                    UpdateChecker.isNewer(info) -> {
                        updateStatusText.text =
                            getString(R.string.update_available_format, info.versionName)
                        showUpdateDialog(info)
                    }
                    else -> updateStatusText.text = getString(R.string.up_to_date)
                }
            }
        }.start()
    }

    private fun showUpdateDialog(info: UpdateInfo) {
        val message = buildString {
            append("Version ${info.versionName} is available (you have ${BuildConfig.VERSION_NAME}).")
            if (info.notes.isNotBlank()) append("\n\n${info.notes}")
        }
        val builder = AlertDialog.Builder(this)
            .setTitle(R.string.update_available_title)
            .setMessage(message)
            .setNegativeButton(R.string.later, null)

        // Only ever open https links from the update file.
        if (info.apkUrl.startsWith("https://")) {
            builder.setPositiveButton(R.string.download) { _, _ ->
                startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(info.apkUrl)))
            }
        }
        builder.show()
    }

    companion object {
        const val EXTRA_CHECK_UPDATE = "check_update"
    }
}
