package net.falconparore.textreader

/**
 * Kokoro voice options. Mirrors the set exposed by popup.html in the extension,
 * with af_sky as the default (matches background.js:40).
 */
data class Voice(val id: String, val label: String)

object Voices {
    const val DEFAULT_ID: String = "af_sky"

    val all: List<Voice> = listOf(
        Voice("af_sky", "Sky (American F)"),
        Voice("af_bella", "Bella (American F)"),
        Voice("af_nicole", "Nicole (American F)"),
        Voice("af_sarah", "Sarah (American F)"),
        Voice("am_adam", "Adam (American M)"),
        Voice("am_michael", "Michael (American M)"),
        Voice("bf_emma", "Emma (British F)"),
        Voice("bf_isabella", "Isabella (British F)"),
        Voice("bm_george", "George (British M)"),
        Voice("bm_lewis", "Lewis (British M)")
    )

    fun indexOf(id: String): Int = all.indexOfFirst { it.id == id }.coerceAtLeast(0)
}
