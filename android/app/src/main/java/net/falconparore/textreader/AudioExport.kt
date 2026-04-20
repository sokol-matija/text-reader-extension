package net.falconparore.textreader

import android.content.ContentResolver
import android.net.Uri
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Helpers for writing a cached mp3 out via the Storage Access Framework. */
object AudioExport {

    /** Filename suggested in the system Save-As picker. */
    fun suggestedFilename(): String {
        val stamp = SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US).format(Date())
        return "text-reader-$stamp.mp3"
    }

    /** Copy [source] into the document URI returned by ACTION_CREATE_DOCUMENT. */
    fun copyToUri(resolver: ContentResolver, source: File, destination: Uri): Boolean {
        return try {
            resolver.openOutputStream(destination)?.use { out ->
                source.inputStream().use { it.copyTo(out) }
                true
            } ?: false
        } catch (_: Exception) {
            false
        }
    }
}
