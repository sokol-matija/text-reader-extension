package net.falconparore.textreader

import android.app.Application
import java.io.File
import java.io.PrintWriter
import java.io.StringWriter

/**
 * Writes the stack trace of any uncaught exception to files/last_crash.txt
 * so MainActivity can surface it in a dialog on the next launch.  Without
 * adb logcat access this is the easiest way to get a crash report out of a
 * user-installed debug build.
 */
class TextReaderApp : Application() {

    override fun onCreate() {
        super.onCreate()
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            runCatching { dumpCrash(throwable) }
            previous?.uncaughtException(thread, throwable)
        }
    }

    private fun dumpCrash(t: Throwable) {
        val sw = StringWriter()
        PrintWriter(sw).use { t.printStackTrace(it) }
        File(filesDir, LAST_CRASH).writeText(
            "${System.currentTimeMillis()}\n${t.javaClass.simpleName}: ${t.message}\n$sw"
        )
    }

    companion object {
        const val LAST_CRASH = "last_crash.txt"

        fun readAndClear(context: android.content.Context): String? {
            val f = File(context.filesDir, LAST_CRASH)
            if (!f.exists()) return null
            val text = runCatching { f.readText() }.getOrNull()
            f.delete()
            return text
        }
    }
}
