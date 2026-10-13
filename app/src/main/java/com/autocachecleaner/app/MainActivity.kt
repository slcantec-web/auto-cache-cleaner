package com.autocachecleaner.app

import android.Manifest
import android.content.ActivityNotFoundException
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.text.format.Formatter
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.widget.ArrayAdapter
import android.widget.AutoCompleteTextView
import android.widget.Button
import android.widget.CheckBox
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView

class MainActivity : AppCompatActivity() {

    private lateinit var cacheSizeText: TextView
    private lateinit var resultText: TextView
    private lateinit var updateStatusText: TextView
    private lateinit var checkUpdateButton: Button
    private lateinit var scanAppsButton: Button
    private lateinit var scanProgress: ProgressBar
    private lateinit var scanStatusText: TextView
    private lateinit var resultsSection: View
    private lateinit var chartCard: View
    private lateinit var pieChart: PieChartView
    private lateinit var legendContainer: LinearLayout
    private lateinit var totalText: TextView
    private lateinit var selectionBar: View
    private lateinit var selectAllCheck: CheckBox
    private lateinit var clearSelectedButton: Button
    private lateinit var stopButton: Button
    private lateinit var grantButton: Button
    private lateinit var recycler: RecyclerView

    private val adapter = AppCacheAdapter(
        onOpen = { openCacheScreen(it.packageName) },
        onSelectionChanged = { updateSelectionUi(it) }
    )

    private val queue = ArrayDeque<String>()
    private var queueActive = false
    private var queueTotal = 0
    private var awaitingReturn = false
    private var pendingNext = false
    private var scanning = false
    private val handler = Handler(Looper.getMainLooper())
    private val nextRunnable = Runnable {
        pendingNext = false
        if (queueActive) openNext()
    }

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
        scanAppsButton = findViewById(R.id.scanAppsButton)
        scanProgress = findViewById(R.id.scanProgress)
        scanStatusText = findViewById(R.id.scanStatusText)
        resultsSection = findViewById(R.id.resultsSection)
        chartCard = findViewById(R.id.chartCard)
        pieChart = findViewById(R.id.pieChart)
        legendContainer = findViewById(R.id.legendContainer)
        totalText = findViewById(R.id.totalText)
        selectionBar = findViewById(R.id.selectionBar)
        selectAllCheck = findViewById(R.id.selectAllCheck)
        clearSelectedButton = findViewById(R.id.clearSelectedButton)
        stopButton = findViewById(R.id.stopButton)
        grantButton = findViewById(R.id.grantButton)
        recycler = findViewById(R.id.appList)

        pieChart.setHoleColor(ContextCompat.getColor(this, R.color.card_bg))

        recycler.layoutManager = LinearLayoutManager(this)
        recycler.adapter = adapter
        recycler.isNestedScrollingEnabled = false

        findViewById<TextView>(R.id.versionText).text =
            getString(R.string.version_format, BuildConfig.VERSION_NAME, BuildConfig.VERSION_CODE)

        findViewById<Button>(R.id.clearCacheButton).setOnClickListener { clearCache() }
        scanAppsButton.setOnClickListener { startScan() }
        checkUpdateButton.setOnClickListener { checkForUpdate() }
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

        requestNotificationPermission()
        setupReminderSpinner()
        refreshCacheSize()
        updateSelectionUi(0)

        if (intent.getBooleanExtra(EXTRA_CHECK_UPDATE, false)) checkForUpdate()
    }

    override fun onResume() {
        super.onResume()
        refreshCacheSize()
        if (queueActive && awaitingReturn) {
            awaitingReturn = false
            rescanQuiet()
            scheduleNext()
        }
    }

    override fun onPause() {
        super.onPause()
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

    private fun startScan() {
        if (scanning) return
        if (!AppCacheScanner.hasUsageAccess(this)) {
            resultsSection.visibility = View.VISIBLE
            chartCard.visibility = View.GONE
            selectionBar.visibility = View.GONE
            recycler.visibility = View.GONE
            grantButton.visibility = View.VISIBLE
            scanStatusText.text = getString(R.string.grant_usage_access_message)
            return
        }
        grantButton.visibility = View.GONE
        scanning = true
        scanAppsButton.isEnabled = false
        scanProgress.visibility = View.VISIBLE
        scanStatusText.text = getString(R.string.scanning)
        resultsSection.visibility = View.VISIBLE

        Thread {
            val apps = AppCacheScanner.scan(this)
            runOnUiThread {
                scanning = false
                scanAppsButton.isEnabled = true
                scanProgress.visibility = View.GONE
                if (isFinishing || isDestroyed) return@runOnUiThread
                showScanResults(apps)
            }
        }.start()
    }

    private fun rescanQuiet() {
        if (!AppCacheScanner.hasUsageAccess(this)) return
        Thread {
            val apps = AppCacheScanner.scan(this)
            runOnUiThread {
                if (isFinishing || isDestroyed) return@runOnUiThread
                showScanResults(apps)
            }
        }.start()
    }

    private fun showScanResults(apps: List<AppCacheInfo>) {
        resultsSection.visibility = View.VISIBLE
        recycler.visibility = View.VISIBLE
        adapter.submit(apps)
        selectionBar.visibility = if (apps.isEmpty()) View.GONE else View.VISIBLE
        val total = apps.sumOf { it.cacheBytes }
        totalText.text = getString(
            R.string.app_caches_total_format,
            Formatter.formatFileSize(this, total),
            apps.size
        )
        updateChart(apps)
        if (!queueActive) {
            scanStatusText.text = getString(
                if (apps.isEmpty()) R.string.no_cache_found else R.string.tap_to_clear_hint
            )
        }
    }

    private fun updateChart(apps: List<AppCacheInfo>) {
        if (apps.isEmpty()) {
            chartCard.visibility = View.GONE
            return
        }
        chartCard.visibility = View.VISIBLE
        pieChart.setFromApps(apps)
        legendContainer.removeAllViews()
        val slices = pieChart.currentSlices()
        val total = slices.sumOf { it.value }.coerceAtLeast(1L)
        val dp4 = TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_DIP, 4f, resources.displayMetrics
        ).toInt()
        val dp12 = TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_DIP, 12f, resources.displayMetrics
        ).toInt()
        for (s in slices) {
            val row = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding(0, dp4, 0, dp4)
            }
            val swatch = View(this).apply {
                background = GradientDrawable().apply {
                    shape = GradientDrawable.RECTANGLE
                    cornerRadius = dp4.toFloat()
                    setColor(s.color)
                }
                layoutParams = LinearLayout.LayoutParams(dp12, dp12).apply {
                    marginEnd = dp4 * 2
                }
            }
            val pct = (s.value * 100.0 / total).toInt()
            val label = TextView(this).apply {
                text = getString(
                    R.string.legend_item_format,
                    s.label,
                    Formatter.formatFileSize(this@MainActivity, s.value),
                    pct
                )
                textSize = 12f
                setTextColor(ContextCompat.getColor(this@MainActivity, R.color.on_surface_variant))
                maxLines = 1
                ellipsize = android.text.TextUtils.TruncateAt.END
            }
            row.addView(swatch)
            row.addView(label, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
            legendContainer.addView(row)
        }
    }

    private fun updateSelectionUi(count: Int) {
        clearSelectedButton.isEnabled = count > 0 && !queueActive
        clearSelectedButton.text = getString(R.string.clear_selected_format, count)
        if (!queueActive) {
            selectAllCheck.isChecked = count > 0 && count == adapter.itemCount && adapter.itemCount > 0
        }
    }

    private fun startQueue() {
        val packages = adapter.selectedPackages()
        if (packages.isEmpty()) return
        queue.clear()
        queue.addAll(packages)
        queueTotal = packages.size
        queueActive = true
        stopButton.visibility = View.VISIBLE
        clearSelectedButton.isEnabled = false
        openNext()
    }

    private fun openNext() {
        val packageName = queue.removeFirstOrNull()
        if (packageName == null) {
            finishQueue()
            rescanQuiet()
            return
        }
        scanStatusText.text = getString(
            R.string.queue_progress_format, queueTotal - queue.size, queueTotal
        )
        openCacheScreen(packageName)
    }

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
        scanStatusText.text = getString(R.string.tap_to_clear_hint)
        rescanQuiet()
    }

    private fun finishQueue() {
        queueActive = false
        awaitingReturn = false
        stopButton.visibility = View.GONE
        adapter.selectAll(false)
        scanStatusText.text = getString(R.string.queue_done)
    }

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
            try {
                startActivity(
                    Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:$packageName"))
                )
            } catch (e2: Exception) {
                awaitingReturn = false
            }
        }
    }

    private fun setupReminderSpinner() {
        if (!ReminderScheduler.hasSavedInterval(this)) {
            ReminderScheduler.setInterval(this, ReminderScheduler.DEFAULT_DAYS)
        }

        val dropdown = findViewById<AutoCompleteTextView>(R.id.reminderDropdown)
        val adapter = ArrayAdapter(this, R.layout.spinner_dropdown_item, intervalLabels)
        dropdown.setAdapter(adapter)

        val selected = intervalValues.indexOf(ReminderScheduler.currentInterval(this))
        val index = if (selected >= 0) selected else 1
        dropdown.setText(intervalLabels[index], false)

        // Full, bright text in the closed field.
        dropdown.setTextColor(ContextCompat.getColor(this, R.color.on_surface))
        dropdown.setHintTextColor(ContextCompat.getColor(this, R.color.on_surface_variant))

        dropdown.setOnItemClickListener { _, _, position, _ ->
            ReminderScheduler.setInterval(this@MainActivity, intervalValues[position])
            dropdown.setText(intervalLabels[position], false)
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

        if (info.apkUrl.startsWith("https://")) {
            builder.setPositiveButton(R.string.download) { _, _ ->
                startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(info.apkUrl)))
            }
        }
        builder.show()
    }

    companion object {
        const val EXTRA_CHECK_UPDATE = "check_update"
        private const val NEXT_DELAY_MS = 1200L
    }
}
