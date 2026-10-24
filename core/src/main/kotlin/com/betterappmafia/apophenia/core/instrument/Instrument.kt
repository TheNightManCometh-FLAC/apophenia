package com.betterappmafia.apophenia.core.instrument

import com.betterappmafia.apophenia.core.interval.Interval
import com.betterappmafia.apophenia.core.interval.minus
import com.betterappmafia.apophenia.core.interval.plus
import com.betterappmafia.apophenia.core.pitch.PitchClass
import com.betterappmafia.apophenia.core.pitch.PitchName
import com.betterappmafia.apophenia.core.pitch.SpelledPitch

/**
 * Open strings in **string order**, from the lowest-positioned string (guitar's 6th) to string 1,
 * never sorted by pitch: a re-entrant ukulele is G4 C4 E4 A4.
 * String numbers follow players and MusicXML `<technical><string>`: 1 is the last entry.
 */
data class Tuning(val id: String, val name: String, val strings: List<SpelledPitch>) {
    init {
        require(strings.isNotEmpty()) { "a tuning needs at least one string" }
    }

    val stringCount: Int get() = strings.size

    /** Open pitch of string [number] (1 = highest-positioned string). */
    fun open(number: Int): SpelledPitch {
        require(number in 1..stringCount) { "string $number doesn't exist on a $stringCount-string tuning" }
        return strings[stringCount - number]
    }

    /** MusicXML `<staff-tuning line>` counts from the **lowest** string, opposite to string numbers. */
    fun staffTuningLine(stringNumber: Int): Int = stringCount - stringNumber + 1

    companion object {
        fun of(id: String, name: String, pitches: String) =
            Tuning(id, name, pitches.split(' ').map(SpelledPitch::parse))
    }
}

/**
 * An instrument profile: tuning, capo, fret count, and how far written notation sits above
 * sounding pitch (guitar and bass are written an octave higher; notation shows written pitch,
 * audio/MIDI/tuner use sounding pitch).
 */
data class Instrument(
    val tuning: Tuning,
    val capo: Int = 0,
    val frets: Int = 22,
    val writtenAboveSounding: Interval? = Interval(8, 12),
) {
    init {
        require(capo in 0 until frets) { "capo must sit below the last fret" }
    }

    /** Sounding MIDI number of [string] at [fret] (counted from the nut; the capo is added). */
    fun midiAt(string: Int, fret: Int): Int {
        require(fret in 0..frets) { "fret $fret is off a $frets-fret neck" }
        return tuning.open(string).midi + capo + fret
    }

    /**
     * A tab note with its pitch spelled as [name] (which must be the right pitch class), or from
     * the default table when no context is given.
     */
    fun tabNote(string: Int, fret: Int, name: PitchName? = null): TabNote {
        val midi = midiAt(string, fret)
        val n = name ?: PitchClass(midi).defaultSpelling()
        require(n.pitchClass == Math.floorMod(midi, 12)) { "$n is not the pitch at string $string fret $fret" }
        val octave = Math.floorDiv(midi - n.letter.natural - n.alter, 12) - 1
        return TabNote(string, fret, n.at(octave))
    }

    fun written(sounding: SpelledPitch): SpelledPitch = writtenAboveSounding?.let { sounding + it } ?: sounding

    fun sounding(written: SpelledPitch): SpelledPitch = writtenAboveSounding?.let { written - it } ?: written
}

/** Every tab note stores string, fret and sounding pitch together. */
data class TabNote(val string: Int, val fret: Int, val pitch: SpelledPitch)

/** Presets from the official plan section 6.4, with resolved notes. */
object Tunings {
    val GUITAR_STANDARD = Tuning.of("guitar-standard", "Guitar standard", "E2 A2 D3 G3 B3 E4")
    val GUITAR_DROP_D = Tuning.of("guitar-drop-d", "Drop D", "D2 A2 D3 G3 B3 E4")
    val GUITAR_D_STANDARD = Tuning.of("guitar-d-standard", "D standard", "D2 G2 C3 F3 A3 D4")
    val GUITAR_DADGAD = Tuning.of("guitar-dadgad", "DADGAD", "D2 A2 D3 G3 A3 D4")
    val GUITAR_OPEN_G = Tuning.of("guitar-open-g", "Open G", "D2 G2 D3 G3 B3 D4")
    val GUITAR_OPEN_D = Tuning.of("guitar-open-d", "Open D", "D2 A2 D3 F#3 A3 D4")
    val GUITAR_7_STRING = Tuning.of("guitar-7-standard", "7-string standard", "B1 E2 A2 D3 G3 B3 E4")
    val BASS_STANDARD = Tuning.of("bass-standard", "Bass E standard", "E1 A1 D2 G2")
    val BASS_D_STANDARD = Tuning.of("bass-d-standard", "Bass D standard", "D1 G1 C2 F2")
    val BASS_DROP_D = Tuning.of("bass-drop-d", "Bass drop D", "D1 A1 D2 G2")
    val BASS_5_STRING = Tuning.of("bass-5-standard", "5-string bass", "B0 E1 A1 D2 G2")
    val BASS_6_STRING = Tuning.of("bass-6-standard", "6-string bass", "B0 E1 A1 D2 G2 C3")
    val UKULELE = Tuning.of("ukulele-standard", "Ukulele (re-entrant)", "G4 C4 E4 A4")

    // "Bass drop A" is in the plan but its notes are ambiguous (4- vs 5-string); waiting on Christopher.
    val all = listOf(
        GUITAR_STANDARD, GUITAR_DROP_D, GUITAR_D_STANDARD, GUITAR_DADGAD, GUITAR_OPEN_G, GUITAR_OPEN_D,
        GUITAR_7_STRING, BASS_STANDARD, BASS_D_STANDARD, BASS_DROP_D, BASS_5_STRING, BASS_6_STRING, UKULELE,
    )
}
