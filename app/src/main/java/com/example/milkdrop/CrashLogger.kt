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

    @Volatile
    private var installed = false

    @Synchronized
    fun install(context: Context) {
        if (installed) return

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

            } catch (e: Exception) {
                // Don't let the logger itself crash
            }
            defaultHandler?.uncaughtException(thread, throwable)
        }
        installed = true
    }

    fun getLog(context: Context): String {
        val file = File(context.filesDir, "crash_log.txt")
        return if (file.exists()) file.readText() else "No crashes logged yet."
    }

    fun clear(context: Context) {
        File(context.filesDir, "crash_log.txt").delete()
    }
}
