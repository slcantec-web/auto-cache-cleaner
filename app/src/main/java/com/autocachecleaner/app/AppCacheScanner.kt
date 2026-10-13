package com.autocachecleaner.app

import android.app.AppOpsManager
import android.app.usage.StorageStatsManager
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Process
import android.os.storage.StorageManager

data class AppCacheInfo(
    val packageName: String,
    val label: String,
    val cacheBytes: Long
)

/**
 * Reads each launchable app's cache size using StorageStatsManager.
 *
 * Needs "Usage access" (PACKAGE_USAGE_STATS), which the user grants once in system settings.
 * Android does not let normal apps CLEAR other apps' caches, so this is read-only.
 */
object AppCacheScanner {

    fun hasUsageAccess(context: Context): Boolean {
        val appOps = context.getSystemService(Context.APP_OPS_SERVICE) as AppOpsManager
        val mode = if (Build.VERSION.SDK_INT >= 29) {
            appOps.unsafeCheckOpNoThrow(
                AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), context.packageName
            )
        } else {
            @Suppress("DEPRECATION")
            appOps.checkOpNoThrow(
                AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), context.packageName
            )
        }
        return mode == AppOpsManager.MODE_ALLOWED
    }

    /** Call from a background thread. Returns apps with a non-zero cache, largest first. */
    fun scan(context: Context): List<AppCacheInfo> {
        val pm = context.packageManager
        val stats = context.getSystemService(Context.STORAGE_STATS_SERVICE) as StorageStatsManager

        val launcherIntent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        val apps = pm.queryIntentActivities(launcherIntent, 0)
            .distinctBy { it.activityInfo.packageName }

        val result = ArrayList<AppCacheInfo>()
        for (app in apps) {
            val pkg = app.activityInfo.packageName
            try {
                val cache = stats.queryStatsForPackage(
                    StorageManager.UUID_DEFAULT, pkg, Process.myUserHandle()
                ).cacheBytes
                if (cache > 0) {
                    result.add(AppCacheInfo(pkg, app.loadLabel(pm).toString(), cache))
                }
            } catch (e: Exception) {
                // App vanished or its stats are unavailable: skip it.
            }
        }
        return result.sortedByDescending { it.cacheBytes }
    }
}
