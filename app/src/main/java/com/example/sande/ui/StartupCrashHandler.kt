package com.example.sande.ui

import android.app.Application
import android.util.Log

/**
 * Catches the exceptions that would otherwise kill the app silently.
 *
 * An app that dies during startup is indistinguishable from one that never
 * installed: the launcher shows nothing and the package ends up in the
 * "stopped" state, where Settings lists it but offers no way to open it.
 *
 * The handler records the failure and re-throws, so Android still shows its
 * normal crash dialog, but the details are also written to a file the user
 * can share and to the log.
 */
class StartupCrashHandler(
    private val application: Application,
    private val delegate: Thread.UncaughtExceptionHandler?
) : Thread.UncaughtExceptionHandler {

    override fun uncaughtException(thread: Thread, error: Throwable) {
        try {
            val details = buildString {
                append("Thread: ${thread.name}\n")
                append("Android: ${android.os.Build.VERSION.RELEASE} (API ${android.os.Build.VERSION.SDK_INT})\n")
                append("Device: ${android.os.Build.MANUFACTURER} ${android.os.Build.MODEL}\n")
                append("App version: ${com.example.sande.BuildConfig.VERSION_NAME} (${com.example.sande.BuildConfig.VERSION_CODE})\n")
                append("\n")
                append(Log.getStackTraceString(error))
            }
            writeCrashReport(details)
            Log.e(TAG, "Unhandled exception", error)
        } catch (secondary: Throwable) {
            Log.e(TAG, "Could not write the crash report", secondary)
        }
        // Let Android handle the crash normally.
        delegate?.uncaughtException(thread, error)
    }

    private fun writeCrashReport(details: String) {
        val dir = java.io.File(application.filesDir, "crash-reports").apply { mkdirs() }
        val file = java.io.File(dir, "last-crash.txt")
        file.writeText(details)
    }

    companion object {
        private const val TAG = "SandeCrash"

        /**
         * Installs the handler. Call it from `Application.onCreate`.
         */
        fun install(application: Application) {
            val current = Thread.getDefaultUncaughtExceptionHandler()
            if (current is StartupCrashHandler) return
            Thread.setDefaultUncaughtExceptionHandler(
                StartupCrashHandler(application, current)
            )
        }
    }
}
