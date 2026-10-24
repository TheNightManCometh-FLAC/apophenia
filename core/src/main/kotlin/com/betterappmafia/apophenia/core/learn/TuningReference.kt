package com.betterappmafia.apophenia.core.learn

import com.betterappmafia.apophenia.core.instrument.Tuning
import com.betterappmafia.apophenia.core.instrument.Tunings
import com.betterappmafia.apophenia.core.interval.Interval

/** One guitar tuning in the Reference: its strings (low to high), the family it's listed under, and a note on it. */
data class TuningEntry(val tuning: Tuning, val family: String, val note: String? = null)

/** One string of a tuning page: number (1 = highest-positioned), open pitch, Hz, change from standard, step up to the next string. */
data class TuningString(val number: Int, val pitch: String, val midi: Int, val hz: String, val change: Int?, val toNext: String?)

data class TuningPage(val entry: TuningEntry, val strings: List<TuningString>, val chords: List<NamedChord>, val changed: Int)

/*
 * The guitar tunings in the Reference (Christopher, 24 Oct 2026: "a list of all guitar tunings and open tunings").
 * [Claude] The common tunings in each family of Wikipedia's "List of guitar tunings", every string written with its
 * octave; the tunings Apophenia already had keep their ids. One-off variants on that page are left out.
 */
object TuningReference {
    private fun t(id: String, name: String, notes: String) = Tuning.of(id, name, notes)
    private fun e(t: Tuning, family: String, note: String? = null) = TuningEntry(t, family, note)

    private const val STD = "standard and lowered"
    private const val DROP = "drop"
    private const val OPEN = "open major"
    private const val MINOR = "open minor (cross-note)"
    private const val MODAL = "modal and suspended"
    private const val CHORD = "chord tunings"
    private const val REGULAR = "regular"
    private const val OTHER = "other"
    private const val EXTENDED = "seven and eight strings"

    val all: List<TuningEntry> = listOf(
        e(Tunings.GUITAR_STANDARD, STD, "Fourths, with a major third between G and B."),
        e(t("guitar-eb-standard", "E♭ standard", "Eb2 Ab2 Db3 Gb3 Bb3 Eb4"), STD, "Every string a half step down: the same shapes, slacker strings."),
        e(Tunings.GUITAR_D_STANDARD, STD, "Every string a whole step down."),
        e(t("guitar-db-standard", "C♯ standard", "C#2 F#2 B2 E3 G#3 C#4"), STD, "Every string a step and a half down."),
        e(t("guitar-c-standard", "C standard", "C2 F2 Bb2 Eb3 G3 C4"), STD, "Every string two whole steps down."),
        e(t("guitar-b-standard", "B standard", "B1 E2 A2 D3 F#3 B3"), STD, "A fourth below standard: the baritone guitar's usual tuning, and a seven-string's lowest six."),
        e(Tunings.GUITAR_DROP_D, DROP, "The 6th string a whole step down: a one-finger power chord on the bottom three strings."),
        e(t("guitar-drop-db", "Drop D♭ (drop C♯)", "Db2 Ab2 Db3 Gb3 Bb3 Eb4"), DROP, "Drop D a half step down: E♭ standard with the 6th string dropped."),
        e(t("guitar-drop-c", "Drop C", "C2 G2 C3 F3 A3 D4"), DROP, "D standard with the 6th string dropped."),
        e(t("guitar-drop-b", "Drop B", "B1 F#2 B2 E3 G#3 C#4"), DROP, "C♯ standard with the 6th string dropped."),
        e(t("guitar-drop-a", "Drop A", "A1 E2 A2 D3 F#3 B3"), DROP, "B standard with the 6th string dropped."),
        e(t("guitar-double-drop-d", "Double drop D", "D2 A2 D3 G3 B3 D4"), DROP, "Both E strings a whole step down."),
        e(Tunings.GUITAR_OPEN_D, OPEN, "The open strings ring D major; a barre at any fret plays a major chord."),
        e(t("guitar-open-e", "Open E", "E2 B2 E3 G#3 B3 E4"), OPEN, "Open D's shape a whole step higher, three strings raised."),
        e(Tunings.GUITAR_OPEN_G, OPEN, "The open strings ring G major, root on the 5th string."),
        e(t("guitar-open-a", "Open A", "E2 A2 E3 A3 C#4 E4"), OPEN, "Open G's shape a whole step higher."),
        e(t("guitar-open-c", "Open C", "C2 G2 C3 G3 C4 E4"), OPEN, "A wide, low C major."),
        e(t("guitar-open-b", "Open B", "B1 F#2 B2 F#3 B3 D#4"), OPEN),
        e(t("guitar-open-f", "Open F", "F2 A2 C3 F3 C4 F4"), OPEN),
        e(t("guitar-dobro-g", "Open G (high bass, Dobro)", "G2 B2 D3 G3 B3 D4"), OPEN, "The resonator guitar's tuning: G at the bottom instead of D."),
        e(t("guitar-open-dm", "Open D minor", "D2 A2 D3 F3 A3 D4"), MINOR, "Open D with the F♯ lowered: cross-note tunings put a minor chord on the open strings."),
        e(t("guitar-open-em", "Open E minor", "E2 B2 E3 G3 B3 E4"), MINOR),
        e(t("guitar-open-gm", "Open G minor", "D2 G2 D3 G3 Bb3 D4"), MINOR),
        e(t("guitar-open-am", "Open A minor", "E2 A2 E3 A3 C4 E4"), MINOR),
        e(t("guitar-open-cm", "Open C minor", "C2 G2 C3 G3 C4 Eb4"), MINOR),
        e(Tunings.GUITAR_DADGAD, MODAL, "D sus4: no third, so the open strings sit between major and minor."),
        e(t("guitar-dadead", "Open Dsus2 (DADEAD)", "D2 A2 D3 E3 A3 D4"), MODAL),
        e(t("guitar-csus2", "Open Csus2 (CGCGCD)", "C2 G2 C3 G3 C4 D4"), MODAL),
        e(t("guitar-gsus4", "Open Gsus4 (DGDGCD)", "D2 G2 D3 G3 C4 D4"), MODAL),
        e(t("guitar-orkney", "Orkney (CGDGCD)", "C2 G2 D3 G3 C4 D4"), MODAL),
        e(t("guitar-c6", "C6 (CACGCE)", "C2 A2 C3 G3 C4 E4"), CHORD),
        e(t("guitar-d6", "D6 (DADF♯BD)", "D2 A2 D3 F#3 B3 D4"), CHORD),
        e(t("guitar-g6", "G6 (DGDGBE)", "D2 G2 D3 G3 B3 E4"), CHORD, "Open G with the top string left at E."),
        e(t("guitar-dmaj7", "Dmaj7 (DADF♯AC♯)", "D2 A2 D3 F#3 A3 C#4"), CHORD),
        e(t("guitar-dadd9", "Dadd9 (DADF♯AE)", "D2 A2 D3 F#3 A3 E4"), CHORD),
        e(t("guitar-dm7", "Dm7 (DADFAC)", "D2 A2 D3 F3 A3 C4"), CHORD),
        e(t("guitar-all-fourths", "All fourths", "E2 A2 D3 G3 C4 F4"), REGULAR, "Standard with the top two strings up a half step: every string a fourth apart, so every shape moves across strings unchanged."),
        e(t("guitar-major-thirds", "Major thirds", "E2 G#2 C3 E3 G#3 C4"), REGULAR, "Every string a major third apart: the notes repeat every three strings."),
        e(t("guitar-all-fifths", "All fifths", "C2 G2 D3 A3 E4 B4"), REGULAR, "The violin family's tuning carried to six strings; the top B needs a light string."),
        e(t("guitar-new-standard", "New standard tuning", "C2 G2 D3 A3 E4 G4"), REGULAR, "Robert Fripp's tuning: fifths, with a minor third at the top."),
        e(t("guitar-nashville", "Nashville (high-strung)", "E3 A3 D4 G4 B3 E4"), OTHER, "Standard notes with the lower four strings an octave up, from the thin strings of a twelve-string set."),
        e(Tunings.GUITAR_7_STRING, EXTENDED, "Standard with a low B added."),
        e(t("guitar-7-drop-a", "7-string drop A", "A1 E2 A2 D3 G3 B3 E4"), EXTENDED),
        e(t("guitar-8-standard", "8-string standard", "F#1 B1 E2 A2 D3 G3 B3 E4"), EXTENDED, "Standard with a low B and a low F♯ added."),
        e(t("guitar-8-drop-e", "8-string drop E", "E1 B1 E2 A2 D3 G3 B3 E4"), EXTENDED),
    )

    val families: List<String> = all.map { it.family }.distinct()

    /** What each family has in common, for its row in the list. */
    val familyNotes: Map<String, String> = mapOf(
        STD to "Standard's intervals, every string moved by the same amount",
        DROP to "The lowest string a whole step below the rest",
        OPEN to "The open strings ring a major chord",
        MINOR to "The open strings ring a minor chord",
        MODAL to "Open strings with no third: sus2 and sus4",
        CHORD to "The open strings ring a sixth, seventh or added-note chord",
        REGULAR to "Every string the same interval from the next",
        OTHER to "Octave-strung and unusual stringings",
        EXTENDED to "Standard with lower strings added",
    )

    fun inFamily(family: String) = all.filter { it.family == family }

    fun byId(id: String): TuningEntry? = all.firstOrNull { it.tuning.id == id }

    /** The page for a tuning: each string with its change from standard (same string count) and the step to the next. */
    fun page(entry: TuningEntry, a4: Double = 440.0): TuningPage {
        val t = entry.tuning
        val standard = when (t.stringCount) { 6 -> Tunings.GUITAR_STANDARD; 7 -> Tunings.GUITAR_7_STRING; 8 -> byId("guitar-8-standard")!!.tuning; else -> null }
        val strings = (t.stringCount downTo 1).map { n ->
            val p = t.open(n)
            val next = if (n > 1) t.open(n - 1) else null
            TuningString(
                n, CourseIntervals.shown(p.name) + p.octave, p.midi, Acoustics.fmt(2, Acoustics.hz(p.midi, a4)),
                standard?.let { p.midi - it.open(n).midi },
                next?.let { if (it.midi >= p.midi) "up a ${Interval.between(p, it)}" else "down a ${Interval.between(it, p)}" },
            )
        }
        val lowest = t.strings.minBy { it.midi }
        val chords = ChordNamer.name(t.strings.map { it.name }, lowest.name).take(3)
        return TuningPage(entry, strings, chords, strings.count { (it.change ?: 0) != 0 })
    }
}
