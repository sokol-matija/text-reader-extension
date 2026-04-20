package net.falconparore.textreader

/**
 * Voice catalogue — mirrors the <optgroup>s in popup.html of the Chrome
 * extension so the Android dropdown lists the same voices grouped the same way.
 * af_sky remains the default (matches background.js:40).
 */
sealed class VoiceEntry {
    data class Header(val label: String) : VoiceEntry()
    data class Item(val id: String, val display: String) : VoiceEntry()
}

data class Voice(val id: String, val display: String)

object Voices {
    const val DEFAULT_ID: String = "af_sky"

    private val americanFemale = listOf(
        Voice("af_sky", "af_sky (Default)"),
        Voice("af_alloy", "af_alloy"),
        Voice("af_aoede", "af_aoede"),
        Voice("af_bella", "af_bella"),
        Voice("af_heart", "af_heart"),
        Voice("af_jadzia", "af_jadzia"),
        Voice("af_jessica", "af_jessica"),
        Voice("af_kore", "af_kore"),
        Voice("af_nicole", "af_nicole"),
        Voice("af_nova", "af_nova"),
        Voice("af_river", "af_river"),
        Voice("af_sarah", "af_sarah")
    )

    private val americanMale = listOf(
        Voice("am_adam", "am_adam"),
        Voice("am_echo", "am_echo"),
        Voice("am_eric", "am_eric"),
        Voice("am_fenrir", "am_fenrir"),
        Voice("am_liam", "am_liam"),
        Voice("am_michael", "am_michael"),
        Voice("am_onyx", "am_onyx"),
        Voice("am_puck", "am_puck"),
        Voice("am_santa", "am_santa")
    )

    private val britishFemale = listOf(
        Voice("bf_alice", "bf_alice"),
        Voice("bf_emma", "bf_emma"),
        Voice("bf_lily", "bf_lily")
    )

    private val britishMale = listOf(
        Voice("bm_daniel", "bm_daniel"),
        Voice("bm_fable", "bm_fable"),
        Voice("bm_george", "bm_george"),
        Voice("bm_lewis", "bm_lewis")
    )

    /** Flat list of every selectable voice — used when we don't need headers. */
    val all: List<Voice> = americanFemale + americanMale + britishFemale + britishMale

    /** Same list but with group headers interleaved, for the custom spinner adapter. */
    val grouped: List<VoiceEntry> = buildList {
        add(VoiceEntry.Header("American Female"))
        addAll(americanFemale.map { VoiceEntry.Item(it.id, it.display) })
        add(VoiceEntry.Header("American Male"))
        addAll(americanMale.map { VoiceEntry.Item(it.id, it.display) })
        add(VoiceEntry.Header("British Female"))
        addAll(britishFemale.map { VoiceEntry.Item(it.id, it.display) })
        add(VoiceEntry.Header("British Male"))
        addAll(britishMale.map { VoiceEntry.Item(it.id, it.display) })
    }

    /** Index of a voice in [grouped], or the first Item if not found. */
    fun groupedIndexOf(id: String): Int {
        val idx = grouped.indexOfFirst { it is VoiceEntry.Item && it.id == id }
        if (idx >= 0) return idx
        return grouped.indexOfFirst { it is VoiceEntry.Item }.coerceAtLeast(0)
    }

    /** Back-compat for screens still using the flat list. */
    fun indexOf(id: String): Int = all.indexOfFirst { it.id == id }.coerceAtLeast(0)
}
