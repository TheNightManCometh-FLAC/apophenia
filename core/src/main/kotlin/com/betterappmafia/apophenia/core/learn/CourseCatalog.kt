package com.betterappmafia.apophenia.core.learn

/*
 * The Course's nine categories and their topics (C4 'learn'). Topic lists and levels are [Claude], following plan §8's
 * scope in the order of Beato's design (sound and pitch, then intervals, keys, chords, harmony, alongside rhythm).
 * A topic without pages is listed and marked as still to be written.
 */
internal object CourseCatalog {

    val categories = listOf(
        CourseCategory("sound", "Sound & pitch", CourseSoundPitch.topics),
        CourseCategory("rhythm", "Rhythm & time", CourseRhythm.topics),
        CourseCategory("intervals", "Intervals", CourseIntervals.topics),
        CourseCategory("scales", "Scales & keys", CourseScales.topics),
        CourseCategory("chords", "Chords", CourseChords.topics),
        CourseCategory("harmony", "Harmony", CourseHarmony.topics),
        CourseCategory("melody", "Melody & songs", CourseMelody.topics),
        CourseCategory("reading", "Reading · notation, tab, charts", CourseReading.topics),
        CourseCategory("instruments", "Instruments · fretboard, piano, voice", CourseInstruments.topics),
    )
}
