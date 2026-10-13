package com.autocachecleaner.app

import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.text.format.Formatter
import android.view.View
import android.widget.Button
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView

/** Lists every app's cache size so the user can see it before clearing anything. */
class AppCachesActivity : AppCompatActivity() {

    private lateinit var totalText: TextView
    private lateinit var statusText: TextView
    private lateinit var grantButton: Button
    private lateinit var recycler: RecyclerView
    private val adapter = AppCacheAdapter { openAppSettings(it.packageName) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_app_caches)

        totalText = findViewById(R.id.totalText)
        statusText = findViewById(R.id.statusText)
        grantButton = findViewById(R.id.grantButton)
        recycler = findViewById(R.id.appList)

        recycler.layoutManager = LinearLayoutManager(this)
        recycler.adapter = adapter

        grantButton.setOnClickListener {
            try {
                startActivity(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS))
            } catch (e: ActivityNotFoundException) {
                startActivity(Intent(Settings.ACTION_SETTINGS))
            }
        }
    }

    // Re-scan every time the screen is shown (also right after returning from settings).
    override fun onResume() {
        super.onResume()
        refresh()
    }

    private fun refresh() {
        if (!AppCacheScanner.hasUsageAccess(this)) {
            adapter.submit(emptyList())
            totalText.text = ""
            statusText.text = getString(R.string.grant_usage_access_message)
            grantButton.visibility = View.VISIBLE
            return
        }

        grantButton.visibility = View.GONE
        statusText.text = getString(R.string.scanning)

        Thread {
            val apps = AppCacheScanner.scan(this)
            runOnUiThread {
                if (isFinishing || isDestroyed) return@runOnUiThread
                adapter.submit(apps)
                val total = apps.sumOf { it.cacheBytes }
                totalText.text = getString(
                    R.string.app_caches_total_format,
                    Formatter.formatFileSize(this, total),
                    apps.size
                )
                statusText.text = getString(
                    if (apps.isEmpty()) R.string.no_cache_found else R.string.tap_to_clear_hint
                )
            }
        }.start()
    }

    private fun openAppSettings(packageName: String) {
        try {
            startActivity(
                Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:$packageName"))
            )
        } catch (e: ActivityNotFoundException) {
            startActivity(Intent(Settings.ACTION_SETTINGS))
        }
    }
}
