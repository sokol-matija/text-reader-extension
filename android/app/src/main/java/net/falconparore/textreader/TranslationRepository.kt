package net.falconparore.textreader

import com.google.mlkit.nl.languageid.LanguageIdentification
import com.google.mlkit.nl.translate.TranslateLanguage
import com.google.mlkit.nl.translate.Translation
import com.google.mlkit.nl.translate.TranslatorOptions
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

/**
 * On-device language detection + translation via ML Kit.
 * Used after OCR to translate non-English text into English before TTS —
 * Kokoro only speaks English voices, so this lets the app read French
 * (and any other supported source language) aloud.
 */
class TranslationRepository {
    private val identifier = LanguageIdentification.getClient()

    /** Returns a BCP-47 tag ("fr", "es", …) or null if undetermined. */
    suspend fun detect(text: String): String? =
        suspendCancellableCoroutine { cont ->
            identifier.identifyLanguage(text)
                .addOnSuccessListener { lang ->
                    cont.resume(if (lang.isBlank() || lang == "und") null else lang)
                }
                .addOnFailureListener { cont.resume(null) }
        }

    /**
     * Translate [text] (in [sourceLangTag]) to English on-device.
     * Returns null if the language isn't supported by ML Kit's Translation API,
     * the model download fails, or translation itself fails.  The caller is
     * expected to fall back to the original text in those cases.
     */
    suspend fun translateToEnglish(text: String, sourceLangTag: String): String? {
        val sourceLang = TranslateLanguage.fromLanguageTag(sourceLangTag) ?: return null
        if (sourceLang == TranslateLanguage.ENGLISH) return text

        val translator = Translation.getClient(
            TranslatorOptions.Builder()
                .setSourceLanguage(sourceLang)
                .setTargetLanguage(TranslateLanguage.ENGLISH)
                .build()
        )

        return try {
            suspendCancellableCoroutine { cont ->
                translator.downloadModelIfNeeded()
                    .addOnSuccessListener {
                        translator.translate(text)
                            .addOnSuccessListener { cont.resume(it) }
                            .addOnFailureListener { cont.resume(null) }
                    }
                    .addOnFailureListener { cont.resume(null) }
            }
        } finally {
            translator.close()
        }
    }
}
