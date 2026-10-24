package com.betterappmafia.apophenia.audio

/** Kotlin side of the C++/Oboe audio engine. */
object AudioEngine {
    init {
        System.loadLibrary("apophenia_audio")
    }

    /** Version of the Oboe library actually loaded at runtime. */
    fun oboeVersion(): String = nativeOboeVersion()

    private external fun nativeOboeVersion(): String
}
