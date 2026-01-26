package com.shadowmaster.crash

import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import java.io.File
import java.io.PrintWriter
import java.io.StringWriter
import java.text.SimpleDateFormat
import java.util.*

/**
 * Simple crash reporter that saves crash logs to a file for debugging.
 */
object CrashReporter {
    private const val TAG = "CrashReporter"
    private const val CRASH_FILE_NAME = "crash_log.txt"
    private const val MAX_CRASH_FILES = 5

    private var applicationContext: Context? = null
    private var defaultHandler: Thread.UncaughtExceptionHandler? = null

    fun initialize(context: Context) {
        applicationContext = context.applicationContext
        defaultHandler = Thread.getDefaultUncaughtExceptionHandler()

        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            try {
                saveCrashReport(throwable)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to save crash report", e)
            }

            // Call the default handler to let the app crash normally
            defaultHandler?.uncaughtException(thread, throwable)
        }

        Log.i(TAG, "Crash reporter initialized")
    }

    private fun saveCrashReport(throwable: Throwable) {
        val context = applicationContext ?: return

        val crashDir = File(context.filesDir, "crash_reports")
        crashDir.mkdirs()

        // Clean up old crash files
        cleanupOldCrashFiles(crashDir)

        val timestamp = SimpleDateFormat("yyyy-MM-dd_HH-mm-ss", Locale.US).format(Date())
        val crashFile = File(crashDir, "crash_$timestamp.txt")

        val report = buildString {
            appendLine("=== Shadow Master Crash Report ===")
            appendLine("Timestamp: ${SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(Date())}")
            appendLine()
            appendLine("=== Device Info ===")
            appendLine("Device: ${Build.MANUFACTURER} ${Build.MODEL}")
            appendLine("Android: ${Build.VERSION.RELEASE} (SDK ${Build.VERSION.SDK_INT})")
            appendLine("App Version: ${getAppVersion(context)}")
            appendLine()
            appendLine("=== Stack Trace ===")
            val sw = StringWriter()
            throwable.printStackTrace(PrintWriter(sw))
            appendLine(sw.toString())
            appendLine()
            appendLine("=== Cause Chain ===")
            var cause = throwable.cause
            var depth = 0
            while (cause != null && depth < 10) {
                appendLine("Caused by: ${cause.javaClass.name}: ${cause.message}")
                cause = cause.cause
                depth++
            }
        }

        crashFile.writeText(report)

        // Also save to a "latest" file for easy access
        File(crashDir, CRASH_FILE_NAME).writeText(report)

        Log.i(TAG, "Crash report saved to ${crashFile.absolutePath}")
    }

    private fun cleanupOldCrashFiles(crashDir: File) {
        val files = crashDir.listFiles { file -> file.name.startsWith("crash_") && file.name.endsWith(".txt") }
            ?.sortedByDescending { it.lastModified() }
            ?: return

        // Keep only the latest MAX_CRASH_FILES
        files.drop(MAX_CRASH_FILES).forEach { it.delete() }
    }

    private fun getAppVersion(context: Context): String {
        return try {
            val pInfo = context.packageManager.getPackageInfo(context.packageName, 0)
            "${pInfo.versionName} (${pInfo.longVersionCode})"
        } catch (e: Exception) {
            "Unknown"
        }
    }

    /**
     * Check if there's a crash report from a previous session.
     */
    fun hasCrashReport(context: Context): Boolean {
        val crashFile = File(context.filesDir, "crash_reports/$CRASH_FILE_NAME")
        return crashFile.exists() && crashFile.length() > 0
    }

    /**
     * Get the latest crash report.
     */
    fun getCrashReport(context: Context): String? {
        val crashFile = File(context.filesDir, "crash_reports/$CRASH_FILE_NAME")
        return if (crashFile.exists()) {
            crashFile.readText()
        } else {
            null
        }
    }

    /**
     * Get all crash reports.
     */
    fun getAllCrashReports(context: Context): List<Pair<String, String>> {
        val crashDir = File(context.filesDir, "crash_reports")
        if (!crashDir.exists()) return emptyList()

        return crashDir.listFiles { file -> file.name.startsWith("crash_") && file.name.endsWith(".txt") }
            ?.sortedByDescending { it.lastModified() }
            ?.map { it.name to it.readText() }
            ?: emptyList()
    }

    /**
     * Clear the latest crash report (call after user has seen it).
     */
    fun clearCrashReport(context: Context) {
        val crashFile = File(context.filesDir, "crash_reports/$CRASH_FILE_NAME")
        crashFile.delete()
    }

    /**
     * Clear all crash reports.
     */
    fun clearAllCrashReports(context: Context) {
        val crashDir = File(context.filesDir, "crash_reports")
        crashDir.deleteRecursively()
    }

    /**
     * Create a share intent for the crash report.
     */
    fun createShareIntent(context: Context): Intent? {
        val report = getCrashReport(context) ?: return null

        return Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_SUBJECT, "Shadow Master Crash Report")
            putExtra(Intent.EXTRA_TEXT, report)
        }
    }
}
