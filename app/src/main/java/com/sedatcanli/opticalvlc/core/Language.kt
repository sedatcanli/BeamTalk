package com.sedatcanli.opticalvlc.core

/**
 * Languages that can travel inside a frame. The numeric [id] is what goes on
 * the wire (LANG_ID field) and must never be reordered.
 *
 * [sttAssetDir] points at a bundled Vosk model inside `assets/`. Languages
 * without a bundled model can still be used as a TTS target on the receiver.
 */
enum class Language(
    val id: Int,
    val tag: String,
    val displayName: String,
    val sttAssetDir: String?
) {
    TURKISH(1, "tr-TR", "Türkçe", "models/tr"),
    ENGLISH(2, "en-US", "English", "models/en"),
    GERMAN(3, "de-DE", "Deutsch", null),
    FRENCH(4, "fr-FR", "Français", null),
    SPANISH(5, "es-ES", "Español", null);

    val supportsStt: Boolean get() = sttAssetDir != null

    companion object {
        private val byId = entries.associateBy { it.id }

        fun fromId(id: Int): Language? = byId[id and 0xFF]

        val sttLanguages: List<Language> get() = entries.filter { it.supportsStt }
        val all: List<Language> get() = entries.toList()
    }
}
