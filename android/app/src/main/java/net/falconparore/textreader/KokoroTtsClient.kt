package net.falconparore.textreader

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.io.File
import java.io.IOException
import java.util.concurrent.TimeUnit

/**
 * Mirrors the contract used by background.js:32-61 in the Chrome extension:
 * POST {baseUrl}/v1/audio/speech with an OpenAI-compatible JSON body,
 * receive mp3 bytes.
 */
class KokoroTtsClient(private val settings: Settings) {

    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .build()

    sealed class Result {
        data class Success(val mp3File: File) : Result()
        data class Failure(val message: String, val cause: Throwable? = null) : Result()
    }

    /** Synthesise [text] and write the mp3 into [cacheDir]. Blocking — call from a coroutine. */
    suspend fun synthesise(text: String, cacheDir: File): Result = withContext(Dispatchers.IO) {
        val url = settings.baseUrl.trimEnd('/') + "/v1/audio/speech"
        val voice = settings.voice.ifBlank { Voices.DEFAULT_ID }
        val payload = JSONObject().apply {
            put("model", settings.model.ifBlank { Settings.DEFAULT_MODEL })
            put("input", text)
            put("voice", voice)
            put("response_format", "mp3")
        }.toString()

        val request = Request.Builder()
            .url(url)
            .post(payload.toRequestBody(JSON))
            .build()

        try {
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    return@withContext Result.Failure(
                        "HTTP ${response.code}: ${response.message}"
                    )
                }
                val bytes = response.body?.bytes()
                    ?: return@withContext Result.Failure("Empty response body")
                val file = File(cacheDir, "tts_${System.currentTimeMillis()}.mp3")
                file.writeBytes(bytes)
                Result.Success(file)
            }
        } catch (e: IOException) {
            Result.Failure(e.message ?: "Network error", e)
        }
    }

    companion object {
        private val JSON = "application/json; charset=utf-8".toMediaType()
    }
}
