package com.autocachecleaner.app

import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.text.format.Formatter
import android.view.View
import android.widget.Button
import android.widget.CheckBox
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView

/**
 * Lists every app's cache size. The user can tick apps (or Select all) and step through them
 * one by one: each app's system settings page opens, the user taps Clear cache and goes back,
 * and the next app opens automatically.
 */
class AppCachesActivity : AppCompatActivity() {

    private lateinit var totalText: TextView
    private lateinit var statusText: TextView
    private lateinit var grantButton: Button
    private lateinit var recycler: RecyclerView
    private lateinit var selectionBar: View
    private lateinit var selectAllCheck: CheckBox
    private lateinit var clearSelectedButton: Button
    private lateinit var stopButton: Button

    private val adapter = AppCacheAdapter(
        onOpen = { openCacheScreen(it.packageName) },
        onSelectionChanged = { updateSelectionUi(it) }
    )

    // Queue of apps still to open.
    private val queue = ArrayDeque<String>()
    private var queueActive = false
    private var queueTotal = 0
    private var awaitingReturn = false   // we opened a settings page and are waiting to come back
    private var pendingNext = false      // a delayed "open next app" is scheduled
    private val handler = Handler(Looper.getMainLooper())
    private val nextRunnable = Runnable {
        pendingNext = false
        if (queueActive) openNext()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_app_caches)

        totalText = findViewById(R.id.totalText)
        statusText = findViewById(R.id.statusText)
        grantButton = findViewById(R.id.grantButton)
        recycler = findViewById(R.id.appList)
        selectionBar = findViewById(R.id.selectionBar)
        selectAllCheck = findViewById(R.id.selectAllCheck)
        clearSelectedButton = findViewById(R.id.clearSelectedButton)
        stopButton = findViewById(R.id.stopButton)

        recycler.layoutManager = LinearLayoutManager(this)
        recycler.adapter = adapter

        grantButton.setOnClickListener {
            try {
                startActivity(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS))
            } catch (e: ActivityNotFoundException) {
                startActivity(Intent(Settings.ACTION_SETTINGS))
            }
        }

        selectAllCheck.setOnClickListener { adapter.selectAll(selectAllCheck.isChecked) }
        clearSelectedButton.setOnClickListener { startQueue() }
        stopButton.setOnClickListener { stopQueue() }

        updateSelectionUi(0)
    }

    // Re-scan every time the screen is shown (also right after returning from settings).
    override fun onResume() {
        super.onResume()
        refresh()
        if (queueActive && awaitingReturn) {
            awaitingReturn = false
            scheduleNext()
        }
    }

    override fun onPause() {
        super.onPause()
        // If the user leaves during the short delay, don't launch settings from the background.
        if (pendingNext) {
            handler.removeCallbacks(nextRunnable)
            pendingNext = false
            awaitingReturn = true
        }
    }

    override fun onDestroy() {
        handler.removeCallbacks(nextRunnable)
        super.onDestroy()
    }

    private fun refresh() {
        if (!AppCacheScanner.hasUsageAccess(this)) {
            adapter.submit(emptyList())
            totalText.text = ""
            selectionBar.visibility = View.GONE
            statusText.text = getString(R.string.grant_usage_access_message)
            grantButton.visibility = View.VISIBLE
            return
        }

        grantButton.visibility = View.GONE
        if (!queueActive) statusText.text = getString(R.string.scanning)

        Thread {
            val apps = AppCacheScanner.scan(this)
            runOnUiThread {
                if (isFinishing || isDestroyed) return@runOnUiThread
                adapter.submit(apps)
                selectionBar.visibility = if (apps.isEmpty()) View.GONE else View.VISIBLE
                val total = apps.sumOf { it.cacheBytes }
                totalText.text = getString(
                    R.string.app_caches_total_format,
                    Formatter.formatFileSize(this, total),
                    apps.size
                )
                if (!queueActive) {
                    statusText.text = getString(
                        if (apps.isEmpty()) R.string.no_cache_found else R.string.tap_to_clear_hint
                    )
                }
            }
        }.start()
    }

    // ---- Selection ------------------------------------------------------------------

    private fun updateSelectionUi(count: Int) {
        clearSelectedButton.text = getString(R.string.clear_selected_format, count)
        clearSelectedButton.isEnabled = count > 0 && !queueActive
        selectAllCheck.isChecked = count > 0 && count == adapter.itemCount
    }

    // ---- One-by-one queue -----------------------------------------------------------

    private fun startQueue() {
        val packages = adapter.selectedPackages()
        if (packages.isEmpty()) return
        queue.clear()
        queue.addAll(packages)
        queueTotal = packages.size
        queueActive = true
        stopButton.visibility = View.VISIBLE
        updateSelectionUi(adapter.selectedCount())
        openNext()
    }

    private fun openNext() {
        val packageName = queue.removeFirstOrNull()
        if (packageName == null) {
            finishQueue()
            return
        }
        statusText.text = getString(
            R.string.queue_progress_format, queueTotal - queue.size, queueTotal
        )
        openCacheScreen(packageName)
    }

    /** Short pause after returning so the user can hit Stop before the next app opens. */
    private fun scheduleNext() {
        pendingNext = true
        handler.postDelayed(nextRunnable, NEXT_DELAY_MS)
    }

    private fun stopQueue() {
        handler.removeCallbacks(nextRunnable)
        pendingNext = false
        awaitingReturn = false
        queue.clear()
        queueActive = false
        stopButton.visibility = View.GONE
        updateSelectionUi(adapter.selectedCount())
        statusText.text = getString(R.string.tap_to_clear_hint)
    }

    private fun finishQueue() {
        queueActive = false
        awaitingReturn = false
        stopButton.visibility = View.GONE
        adapter.selectAll(false)
        statusText.text = getString(R.string.queue_done)
    }

    // ---- Opening system settings ----------------------------------------------------

    /**
     * Tries to jump straight to the app's "Storage & cache" page. Android has no public way to
     * do this, so on most phones the extras below are ignored and the app's App info page opens
     * instead (one extra tap on Storage & cache). Either way the user presses Clear cache.
     */
    private fun openCacheScreen(packageName: String) {
        awaitingReturn = queueActive
        val uri = Uri.parse("package:$packageName")
        val direct = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, uri).apply {
            putExtra(":settings:show_fragment", "com.android.settings.applications.AppStorageSettings")
            putExtra(
                ":settings:show_fragment_args",
                Bundle().apply { putString("package", packageName) }
            )
        }
        try {
            startActivity(direct)
        } catch (e: Exception) {
            openAppInfo(packageName)
        }
    }

    private fun openAppInfo(packageName: String) {
        try {
            startActivity(
                Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:$packageName"))
            )
        } catch (e: Exception) {
            try {
                startActivity(Intent(Settings.ACTION_SETTINGS))
            } catch (e2: Exception) {
                awaitingReturn = false
            }
        }
    }

    companion object {
        private const val NEXT_DELAY_MS = 1200L
    }
}
