package net.falconparore.textreader

import android.content.Context
import android.content.SharedPreferences

/** SharedPreferences wrapper for the Kokoro connection settings. */
class Settings(context: Context) {
    private val prefs: SharedPreferences =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    var baseUrl: String
        get() = prefs.getString(KEY_URL, DEFAULT_URL) ?: DEFAULT_URL
        set(value) = prefs.edit().putString(KEY_URL, value.trimEnd('/')).apply()

    var voice: String
        get() = prefs.getString(KEY_VOICE, Voices.DEFAULT_ID) ?: Voices.DEFAULT_ID
        set(value) = prefs.edit().putString(KEY_VOICE, value).apply()

    var model: String
        get() = prefs.getString(KEY_MODEL, DEFAULT_MODEL) ?: DEFAULT_MODEL
        set(value) = prefs.edit().putString(KEY_MODEL, value).apply()

    companion object {
        private const val PREFS_NAME = "text_reader_settings"
        private const val KEY_URL = "kokoro_base_url"
        private const val KEY_VOICE = "kokoro_voice"
        private const val KEY_MODEL = "kokoro_model"

        // Matches background.js:1 in the Chrome extension.
        const val DEFAULT_URL: String = "http://sokol.falcon-parore.ts.net:8880"
        const val DEFAULT_MODEL: String = "tts-1"
    }
}
