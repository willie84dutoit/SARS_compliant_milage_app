package com.mileagetracker.app.data.logging

import android.util.Log
import timber.log.Timber
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Append-only plain-text [Timber.Tree] (T-018 field-debuggability). Writes one line per log call
 * to `<filesDir>/logs/mileage_tracker_log.txt`, rotating to `.1` once the active file exceeds
 * [MAXIMUM_LOG_FILE_SIZE_BYTES] so a field tester's device never fills up from a long-running
 * foreground service.
 *
 * Line format (deliberately grep-friendly — see [formatLogLine]):
 * `<ISO-8601 timestamp> <LEVEL> <TAG> <message>`
 * with any exception's message and stack trace appended as indented continuation lines, so a
 * `grep` on the first line of any event (timestamp + level + tag) isolates exactly one event,
 * never a mid-stack-trace fragment.
 *
 * This class does its own file I/O defensively: a logging failure must never crash the app or
 * propagate — if the log write itself throws, it is swallowed after one attempt to report it to
 * the platform logcat via [Log.e], since there is no lower-level sink left to report to.
 */
class FileLoggingTree(private val logsDirectory: File) : Timber.Tree() {

    private val isoTimestampFormat =
        SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSXXX", Locale.US)

    private val activeLogFile: File
        get() = File(logsDirectory, ACTIVE_LOG_FILE_NAME)

    private val rotatedLogFile: File
        get() = File(logsDirectory, ROTATED_LOG_FILE_NAME)

    override fun log(priority: Int, tag: String?, message: String, throwable: Throwable?) {
        val logLine = formatLogLine(priority, tag, message, throwable)
        writeLogLineSafely(logLine)
    }

    private fun formatLogLine(priority: Int, tag: String?, message: String, throwable: Throwable?): String {
        val timestamp = isoTimestampFormat.format(Date())
        val levelLabel = priorityToLevelLabel(priority)
        val resolvedTag = tag ?: UNTAGGED_LABEL
        val firstLine = "$timestamp $levelLabel $resolvedTag $message"

        if (throwable == null) return firstLine

        val stackTraceContinuationLines = Log.getStackTraceString(throwable)
            .lineSequence()
            .filter { line -> line.isNotBlank() }
            .joinToString(separator = "\n") { line -> "$STACK_TRACE_INDENT$line" }

        return "$firstLine\n$stackTraceContinuationLines"
    }

    private fun priorityToLevelLabel(priority: Int): String = when (priority) {
        Log.VERBOSE -> "VERBOSE"
        Log.DEBUG -> "DEBUG"
        Log.INFO -> "INFO"
        Log.WARN -> "WARN"
        Log.ERROR -> "ERROR"
        Log.ASSERT -> "ASSERT"
        else -> "UNKNOWN"
    }

    private fun writeLogLineSafely(logLine: String) {
        try {
            if (!logsDirectory.exists()) {
                logsDirectory.mkdirs()
            }
            rotateIfActiveFileTooLarge()
            activeLogFile.appendText(logLine + "\n", Charsets.UTF_8)
        } catch (fileWriteFailure: Exception) {
            // Last-resort sink: the file-based logger itself failed. There is nowhere lower to
            // report this, so it goes to logcat only — it must never rethrow into the caller's
            // original Timber.log() call, which could be on a hot path (e.g. inside the
            // foreground service's coroutine scope).
            Log.e("MT-FileLogging", "Failed to write log line to $activeLogFile", fileWriteFailure)
        }
    }

    private fun rotateIfActiveFileTooLarge() {
        if (activeLogFile.exists() && activeLogFile.length() >= MAXIMUM_LOG_FILE_SIZE_BYTES) {
            if (rotatedLogFile.exists()) {
                rotatedLogFile.delete()
            }
            activeLogFile.renameTo(rotatedLogFile)
        }
    }

    companion object {
        private const val ACTIVE_LOG_FILE_NAME = "mileage_tracker_log.txt"
        private const val ROTATED_LOG_FILE_NAME = "mileage_tracker_log.txt.1"
        private const val UNTAGGED_LABEL = "MT-Untagged"
        private const val STACK_TRACE_INDENT = "    "

        /** Rotation threshold: ~2 MB, per T-018 spec. */
        private const val MAXIMUM_LOG_FILE_SIZE_BYTES = 2L * 1024 * 1024
    }
}
