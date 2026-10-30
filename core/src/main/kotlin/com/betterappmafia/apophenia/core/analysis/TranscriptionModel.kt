package com.betterappmafia.apophenia.core.analysis

/** Explicit choice for Notes → MIDI. Stored model IDs preserve provenance on existing results. */
enum class TranscriptionModel(val id: String, val label: String, val description: String) {
    BASIC_PITCH("basic-pitch-icassp2022", "Basic Pitch", "Vocals and other instruments"),
    GUITAR(GuitarPitch.MODEL_ID, "Guitar", "GAPS · solo guitar, including chords");

    companion object {
        fun fromId(id: String?): TranscriptionModel? = entries.firstOrNull { it.id == id }
        fun name(id: String?): String = fromId(id)?.let { if (it == GUITAR) "Guitar (GAPS)" else it.label } ?: id ?: "Unknown model"
    }
}
