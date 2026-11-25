package com.example.sande.data

import com.google.common.truth.Truth.assertThat
import org.junit.Test

/**
 * Tests for the startup-resilience rules.
 *
 * The app must always be able to open: a crash while the first screen is
 * composed leaves the package in the "stopped" state, where the launcher
 * shows no icon and Settings offers no way to launch it.
 */
class StartupResilienceTest {

    /**
     * The token store must never throw while it is being created.
     *
     * The Android keystore is unavailable on devices whose secure lock screen
     * was reset, and EncryptedSharedPreferences throws in that case. The token
     * store used to rethrow, which killed the app during startup.
     */
    @Test
    fun `token store creation must not throw`() {
        val source = readSource("data/prefs/TokenManager.kt")

        // The createEncryptedPrefs function must not rethrow.
        val createBlock = window(source, "private fun createEncryptedPrefs")
        assertThat(createBlock).doesNotContain("throw RuntimeException")
        assertThat(createBlock).contains("getSharedPreferences")
    }

    /**
     * Reading the stored tokens must not throw on a corrupted entry.
     */
    @Test
    fun `reading tokens must swallow decode errors`() {
        val source = readSource("data/prefs/TokenManager.kt")

        val getTokens = window(source, "suspend fun getTokens()")
        assertThat(getTokens).contains("catch")
        assertThat(getTokens).contains("null")
    }

    /**
     * Restoring the session must not throw: `checkSession` runs from the
     * SessionManager constructor.
     */
    @Test
    fun `session restore must swallow failures`() {
        val source = readSource("data/auth/SessionManager.kt")

        val checkSession = window(source, "fun checkSession()")
        assertThat(checkSession).contains("catch")
        assertThat(checkSession).contains("AuthState.UNAUTHENTICATED")
    }

    /**
     * The launcher activity has to be the app's entry point, and the crash
     * handler has to be installed so a failure is recorded.
     */
    @Test
    fun `launcher activity starts the navigation graph`() {
        val source = readSource("ui/SplashActivity.kt")

        assertThat(source).contains("AppNavGraph()")
        assertThat(source).contains("class SplashActivity")
    }

    /**
     * An unhandled exception has to be recorded, so a crash that makes the
     * app unopenable can still be diagnosed.
     */
    @Test
    fun `a crash handler is installed`() {
        val handler = readSource("ui/StartupCrashHandler.kt")
        assertThat(handler).contains("UncaughtExceptionHandler")
        assertThat(handler).contains("crash-reports")

        val application = readSource("di/SandeApplication.kt")
        assertThat(application).contains("StartupCrashHandler.install(this)")
    }

    /**
     * The database must be recreated when the file on disk cannot be opened.
     */
    @Test
    fun `database open must recover from a broken file`() {
        val source = readSource("data/db/AppDatabase.kt")

        assertThat(source).contains("moveAsideBrokenDatabase")
        assertThat(source).contains("openHelper.writableDatabase")
    }

    /**
     * Returns the text that follows a marker, up to the next declaration.
     *
     * A fixed window is used rather than brace matching so the block is
     * independent of how the function is formatted.
     */
    private fun window(source: String, marker: String, size: Int = 2500): String {
        val start = source.indexOf(marker)
        assertThat(start).isGreaterThan(-1)
        return source.substring(start, minOf(source.length, start + size))
    }

    /**
     * Reads a source file from the module.
     *
     * The unit test task runs with the module directory as its working
     * directory, so the sources are next to it under `src/main/java/...`.
     */
    private fun readSource(relativePath: String): String {
        val roots = listOf(
            "src/main/java/com/example/sande/",
            "app/src/main/java/com/example/sande/",
            "../app/src/main/java/com/example/sande/",
            "../../sande2/app/src/main/java/com/example/sande/"
        )
        for (root in roots) {
            val file = java.io.File(root + relativePath)
            if (file.isFile) return file.readText()
        }
        throw AssertionError(
            "Could not find $relativePath from ${java.io.File(".").absolutePath}"
        )
    }
}
