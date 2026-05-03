package com.example.milkdrop

import android.content.Context
import java.io.File
import java.io.PrintWriter
import java.io.StringWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Writes uncaught exceptions to a file on internal storage so they can be
 * retrieved without ADB. File location: filesDir/crash_log.txt
 */
object CrashLogger {

    fun install(context: Context) {
        val appContext = context.applicationContext
        val defaultHandler = Thread.getDefaultUncaughtExceptionHandler()

        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            try {
                val sw = StringWriter()
                throwable.printStackTrace(PrintWriter(sw))
                val timestamp = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(Date())
                val log = "=== CRASH at $timestamp on thread ${thread.name} ===\n$sw\n\n"

                val file = File(appContext.filesDir, "crash_log.txt")
                val existing = if (file.exists() && file.length() < 50_000) file.readText() else ""
                file.writeText(existing + log)

                // Also copy to Downloads so LocalSend / file managers can reach it
                exportToDownloads(appContext)
            } catch (e: Exception) {
                // Don't let the logger itself crash
            }
            defaultHandler?.uncaughtException(thread, throwable)
        }
    }

    fun getLog(context: Context): String {
        val file = File(context.filesDir, "crash_log.txt")
        return if (file.exists()) file.readText() else "No crashes logged yet."
    }

    /**
     * Copy crash log to Downloads so LocalSend / ES File Explorer / any file
     * manager can access it. Returns the destination path or null on failure.
     * Path: /sdcard/Download/milkdrop_crash_log.txt
     */
    fun exportToDownloads(context: Context): String? {
        return try {
            val src = File(context.filesDir, "crash_log.txt")
            if (!src.exists()) return null
            val downloads = android.os.Environment.getExternalStoragePublicDirectory(
                android.os.Environment.DIRECTORY_DOWNLOADS
            )
            downloads.mkdirs()
            val dst = File(downloads, "milkdrop_crash_log.txt")
            src.copyTo(dst, overwrite = true)
            dst.absolutePath
        } catch (e: Exception) {
            null
        }
    }

    fun clear(context: Context) {
        File(context.filesDir, "crash_log.txt").delete()
    }
}
