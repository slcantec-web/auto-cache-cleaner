package com.autocachecleaner.app

import android.content.ActivityNotFoundException
import android.content.Intent
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.text.format.Formatter
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.widget.Button
import android.widget.CheckBox
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView

/**
 * Lists every app's cache size with a live pie chart. The user can tick apps (or Select all)
 * and step through them one by one: each app's system settings page opens, the user taps
 * Clear cache and goes back, and the next app opens automatically. Chart and list refresh
 * after each return from settings.
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
    private lateinit var pieChart: PieChartView
    private lateinit var chartCard: View
    private lateinit var legendContainer: LinearLayout

    private val adapter = AppCacheAdapter(
        onOpen = { openCacheScreen(it.packageName) },
        onSelectionChanged = { updateSelectionUi(it) }
    )

    private val queue = ArrayDeque<String>()
    private var queueActive = false
    private var queueTotal = 0
    private var awaitingReturn = false
    private var pendingNext = false
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
        pieChart = findViewById(R.id.pieChart)
        chartCard = findViewById(R.id.chartCard)
        legendContainer = findViewById(R.id.legendContainer)

        val cardBg = ContextCompat.getColor(this, R.color.card_bg)
        pieChart.setHoleColor(cardBg)

        recycler.layoutManager = LinearLayoutManager(this)
        recycler.adapter = adapter
        recycler.isNestedScrollingEnabled = false

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
            chartCard.visibility = View.GONE
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
                updateChart(apps)
                if (!queueActive) {
                    statusText.text = getString(
                        if (apps.isEmpty()) R.string.no_cache_found else R.string.tap_to_clear_hint
                    )
                }
            }
        }.start()
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
                val d = GradientDrawable().apply {
                    shape = GradientDrawable.RECTANGLE
                    cornerRadius = dp4.toFloat()
                    setColor(s.color)
                }
                background = d
                layoutParams = LinearLayout.LayoutParams(dp12, dp12).apply {
                    marginEnd = dp4 * 2
                }
            }
            val pct = (s.value * 100.0 / total).toInt()
            val label = TextView(this).apply {
                text = getString(
                    R.string.legend_item_format,
                    s.label,
                    Formatter.formatFileSize(this@AppCachesActivity, s.value),
                    pct
                )
                textSize = 12f
                setTextColor(ContextCompat.getColor(this@AppCachesActivity, R.color.on_surface_variant))
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
            refresh()
            return
        }
        statusText.text = getString(
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
        statusText.text = getString(R.string.tap_to_clear_hint)
        refresh()
    }

    private fun finishQueue() {
        queueActive = false
        awaitingReturn = false
        stopButton.visibility = View.GONE
        adapter.selectAll(false)
        statusText.text = getString(R.string.queue_done)
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
