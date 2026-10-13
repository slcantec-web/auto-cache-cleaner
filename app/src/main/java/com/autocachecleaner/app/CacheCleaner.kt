package com.autocachecleaner.app

import java.io.File

/**
 * Clears this app's own cache directories.
 *
 * Note: since Android 6 a normal app cannot clear OTHER apps' caches. For those, the
 * main screen offers a shortcut to the system storage settings.
 */
object CacheCleaner {

    fun cacheSize(context: android.content.Context): Long {
        var total = dirSize(context.cacheDir)
        context.externalCacheDir?.let { total += dirSize(it) }
        return total
    }

    /** Deletes the cache contents and returns the number of bytes freed. */
    fun clear(context: android.content.Context): Long {
        val before = cacheSize(context)
        clearDir(context.cacheDir)
        context.externalCacheDir?.let { clearDir(it) }
        val after = cacheSize(context)
        return (before - after).coerceAtLeast(0)
    }

    private fun dirSize(dir: File): Long =
        dir.walkTopDown().filter { it.isFile }.sumOf { it.length() }

    private fun clearDir(dir: File) {
        dir.listFiles()?.forEach { it.deleteRecursively() }
    }
}
