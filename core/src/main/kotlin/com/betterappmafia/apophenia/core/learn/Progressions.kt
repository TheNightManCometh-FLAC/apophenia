package com.betterappmafia.apophenia.core.learn

import com.betterappmafia.apophenia.core.catalog.ChordDef
import com.betterappmafia.apophenia.core.catalog.Chords
import com.betterappmafia.apophenia.core.catalog.KeyMode
import com.betterappmafia.apophenia.core.chord.ChordDialect
import com.betterappmafia.apophenia.core.chord.ChordParser
import com.betterappmafia.apophenia.core.chord.ChordRoot
import com.betterappmafia.apophenia.core.degree.DegreeToken
import com.betterappmafia.apophenia.core.interval.plus
import com.betterappmafia.apophenia.core.pitch.PitchClass
import com.betterappmafia.apophenia.core.pitch.PitchName

/** One chord of a progression: its root as a degree of the key ("b7"), its catalog chord id, and how many beats. */
data class ProgStep(val degree: String, val chord: String, val beats: Int = 4) {
    val token: DegreeToken get() = DegreeToken.parse(degree)
    val def: ChordDef get() = Chords.byId(chord)
}

/** A named progression (plan §8): in a major or a minor key, with where it's heard, written for Apophenia. */
data class ProgressionDef(
    val id: String,
    val name: String,
    val also: List<String>,
    val family: String,
    val minor: Boolean,
    val steps: List<ProgStep>,
    val where: String,
)

/** One chord on the page: spelled root, symbol (ASCII storage form), and the notes played for it. */
data class ProgChord(val step: ProgStep, val root: PitchName, val symbol: String, val midis: List<Int>)

data class ProgPage(val def: ProgressionDef, val key: PitchName, val respelledFrom: PitchName?, val keyMode: KeyMode, val chords: List<ProgChord>)

object Progressions {
    private fun s(spec: String, beats: Int = 4) = spec.split(' ').map { val (d, c) = it.split(':'); ProgStep(d, c, beats) }
    private fun s(vararg parts: Pair<String, Int>) = parts.flatMap { (spec, beats) -> s(spec, beats) }

    private const val POP = "pop and rock"
    private const val MODAL = "modal and minor"
    private const val JAZZ = "jazz"
    private const val BLUES = "blues"
    private const val CLASSICAL = "classical and cadences"

    val all: List<ProgressionDef> = listOf(
        ProgressionDef("one-four-five", "I–IV–V–I", listOf("Three-chord"), POP, false, s("1:maj 4:maj 5:maj 1:maj"),
            "The three primary triads, which between them hold every note of the major scale. The frame of countless folk, country, rock and roll and hymn tunes."),
        ProgressionDef("axis", "I–V–vi–IV", listOf("Axis progression", "Four chords"), POP, false, s("1:maj 5:maj 6:m 4:maj"),
            "The loop under a great deal of pop since the 1990s. Starting it on vi (vi–IV–I–V) gives the same four chords a minor-key feel."),
        ProgressionDef("fifties", "I–vi–IV–V", listOf("Fifties progression", "Doo-wop changes"), POP, false, s("1:maj 6:m 4:maj 5:maj"),
            "The doo-wop and early rock and roll loop of the 1950s. With ii in place of IV (I–vi–ii–V) it becomes the jazz turnaround."),
        ProgressionDef("royal-road", "IV–V–iii–vi", listOf("Royal road", "Ōdō shinkō"), POP, false, s("4:maj7 5:7 3:m7 6:m7"),
            "Named \"royal road\" in Japan, where it is common in J-pop and anime songs. It never lands on I, which keeps it moving."),
        ProgressionDef("mixolydian", "I–♭VII–IV–I", listOf("Mixolydian vamp"), MODAL, false, s("1:maj b7:maj 4:maj 1:maj"),
            "The ♭VII is borrowed from Mixolydian. A rock staple, often played as open chords or power chords; the ♭VII–IV–I end is sometimes called the double plagal cadence."),
        ProgressionDef("aeolian", "i–VI–VII–i", listOf("Aeolian vamp", "i–♭VI–♭VII–i"), MODAL, true, s("1:m b6:maj b7:maj 1:m"),
            "Natural minor with no leading tone: VII (♭VII counted from the major scale) goes back to i by a whole step. Heard throughout rock and film music."),
        ProgressionDef("andalusian", "i–VII–VI–V", listOf("Andalusian cadence", "i–♭VII–♭VI–V"), MODAL, true, s("1:m b7:maj b6:maj 5:maj"),
            "Four chords stepping down from the tonic to the dominant, the bass falling a fourth. Central to flamenco, where it's heard as Phrygian on the V."),
        ProgressionDef("line-cliche", "Minor line cliché", listOf("i–i(maj7)–i7–i6"), MODAL, true, s("1:m 1:mMaj7 1:m7 1:m6"),
            "One minor chord held while a single inner voice falls by semitones: root, major seventh, minor seventh, sixth. A film and jazz-ballad sound."),
        ProgressionDef("ii-v-i", "ii–V–I", listOf("Two-five-one"), JAZZ, false, s("2:m7 5:7 1:maj7"),
            "The cadence of jazz: roots falling in fifths, the 7th of each chord resolving down a step to the 3rd of the next. Standards are largely chains of them in different keys."),
        ProgressionDef("ii-v-i-minor", "ii–V–i in minor", listOf("Minor two-five-one"), JAZZ, true, s("2:m7b5 5:7b9 1:m6"),
            "The minor key's version: a half-diminished ii, a dominant with ♭9 (from harmonic minor), and a minor tonic, often m6 or m(maj7)."),
        ProgressionDef("turnaround", "I–vi–ii–V", listOf("Turnaround", "One-six-two-five"), JAZZ, false, s(("1:maj7 6:m7" to 2), ("2:m7 5:7" to 2)),
            "Two bars that lead back to the top of a form. It's the A section of rhythm changes, from Gershwin's \"I Got Rhythm\", and the vi often becomes VI7."),
        ProgressionDef("iii-vi-ii-v", "iii–vi–ii–V–I", listOf("Long turnaround"), JAZZ, false, s("3:m7 6:m7 2:m7 5:7 1:maj7"),
            "Every root a fifth below the last, from iii to I: the diatonic circle of fifths."),
        ProgressionDef("tritone-sub", "ii–♭II7–I", listOf("Tritone substitution"), JAZZ, false, s("2:m7 b2:7 1:maj7"),
            "V7 replaced by the dominant a tritone away, which shares its 3rd and 7th; the bass now falls by semitones to the tonic."),
        ProgressionDef("backdoor", "iv7–♭VII7–I", listOf("Backdoor ii–V"), JAZZ, false, s("4:m7 b7:7 1:maj7"),
            "A ii–V borrowed from the parallel minor that arrives at I from a whole step below instead of from V."),
        ProgressionDef("ragtime", "III7–VI7–II7–V7–I", listOf("Ragtime progression"), JAZZ, false, s("3:7 6:7 2:7 5:7 1:maj"),
            "A chain of secondary dominants round the circle of fifths, each a dominant seventh. Ragtime, early jazz, and the bridge of rhythm changes (III7–VI7–II7–V7)."),
        ProgressionDef("coltrane", "Coltrane changes", listOf("Giant Steps cycle"), JAZZ, false, s(("2:m7 b3:7 b6:maj7 7:7 3:maj7 5:7" to 2), ("1:maj7" to 4)),
            "John Coltrane's substitution over a ii–V–I: three tonics a major third apart (I, ♭VI, III), each reached by its own V7. From \"Giant Steps\" and his reharmonisations of standards around 1959–60."),
        ProgressionDef("twelve-bar", "12-bar blues", listOf("Twelve-bar"), BLUES, false, s("1:7 1:7 1:7 1:7 4:7 4:7 1:7 1:7 5:7 4:7 1:7 5:7"),
            "Three four-bar lines: four bars of I, two of IV and two of I, then V, IV, I and a turnaround V. All three chords are usually dominant sevenths."),
        ProgressionDef("quick-change", "12-bar blues, quick change", listOf("Quick-four"), BLUES, false, s("1:7 4:7 1:7 1:7 4:7 4:7 1:7 1:7 5:7 4:7 1:7 5:7"),
            "The 12-bar with IV in bar 2."),
        ProgressionDef("minor-blues", "Minor blues", listOf("12-bar minor blues"), BLUES, true, s("1:m7 1:m7 1:m7 1:m7 4:m7 4:m7 1:m7 1:m7 b6:7 5:7 1:m7 5:7"),
            "The 12-bar in minor, with VI7 (♭VI7 counted from the major scale) stepping down to V7 in bar 9 where the major blues has V–IV."),
        ProgressionDef("authentic", "V7–I", listOf("Authentic cadence", "Perfect cadence"), CLASSICAL, false, s("5:7 1:maj"),
            "The strongest close in tonal music: the leading tone rises to the tonic, the 7th falls to the 3rd."),
        ProgressionDef("plagal", "IV–I", listOf("Plagal cadence", "Amen cadence"), CLASSICAL, false, s("4:maj 1:maj"),
            "The \"Amen\" at the end of hymns: no leading tone, a softer close."),
        ProgressionDef("minor-plagal", "iv–I", listOf("Minor plagal cadence"), CLASSICAL, false, s("4:m 1:maj"),
            "IV made minor by borrowing from the parallel minor: the ♭6 falls a semitone to the 5th."),
        ProgressionDef("deceptive", "V7–vi", listOf("Deceptive cadence", "Interrupted cadence"), CLASSICAL, false, s("5:7 6:m"),
            "The dominant resolves as expected in every voice but the bass, which steps up to vi instead of falling to I."),
        ProgressionDef("half", "IV–V", listOf("Half cadence"), CLASSICAL, false, s("4:maj 5:maj"),
            "A phrase that stops on V, open, like a question waiting for its answer."),
        ProgressionDef("pachelbel", "Pachelbel's Canon", listOf("I–V–vi–iii–IV–I–IV–V"), CLASSICAL, false, s("1:maj 5:maj 6:m 3:m 4:maj 1:maj 4:maj 5:maj", 2),
            "The repeating bass of Johann Pachelbel's Canon in D, harmonised. In D the bass runs D A B F♯ G D G A: down a fourth, up a step, over and over. Pop has reused it many times."),
        ProgressionDef("circle-major", "Diatonic circle in major", listOf("I–IV–vii°–iii–vi–ii–V–I"), CLASSICAL, false, s("1:maj 4:maj 7:dim 3:m 6:m 2:m 5:maj 1:maj", 2),
            "Every chord of the key, roots falling in fifths (and one diminished fifth, IV to vii°). Baroque sequences run on it."),
        ProgressionDef("circle-minor", "Diatonic circle in minor", listOf("i–iv–VII–III–VI–ii°–V–i"), CLASSICAL, true, s("1:m 4:m b7:maj b3:maj b6:maj 2:dim 5:maj 1:m", 2),
            "The same chain in minor, with V raised to major (from harmonic minor) to close."),
    )

    val families: List<String> = all.map { it.family }.distinct()
    val familyNotes: Map<String, String> = mapOf(
        POP to "Loops and the three-chord frame", MODAL to "Borrowed and minor-key loops", JAZZ to "ii–V–I and its substitutes",
        BLUES to "The 12-bar and its variants", CLASSICAL to "Cadences and circle-of-fifths chains",
    )

    fun inFamily(family: String) = all.filter { it.family == family }

    /** The chords grouped into 4/4 bars (a chord longer than a bar fills several, written once). */
    fun bars(page: ProgPage): List<List<ProgChord>> {
        val out = ArrayList<MutableList<ProgChord>>()
        var beat = 0
        for (c in page.chords) {
            if (beat % 4 == 0) out += mutableListOf<ProgChord>()
            out.last() += c
            beat += c.step.beats
            repeat((c.step.beats - 1) / 4) { out += mutableListOf<ProgChord>() }
        }
        return out
    }

    /** The progression played at [beat] seconds a beat. */
    fun sound(page: ProgPage, beat: Double = 0.6): List<com.betterappmafia.apophenia.core.sound.PlayNote> {
        var at = 0.0
        return page.chords.flatMap { c -> RefSound.block(c.midis, at = at, length = c.step.beats * beat * 0.95).also { at += c.step.beats * beat } }
    }
    fun byId(id: String) = all.first { it.id == id }

    private fun spellAll(def: ProgressionDef, key: PitchName): List<PitchName>? = runCatching { def.steps.map { key + it.token.interval } }.getOrNull()
        ?.takeIf { roots -> roots.all { kotlin.math.abs(it.alter) < 2 } }

    /** A chord's sound: the root low (C2 to B2), the rest close above F♯3, at most four upper notes (dropping the 5th first). */
    fun voice(root: PitchName, def: ChordDef): List<Int> {
        val rpc = root.pitchClass
        val bass = 36 + Math.floorMod(rpc - 36, 12)
        var upper = def.degrees
        while (upper.size > 4) upper = upper - (def.omitOrder.firstOrNull { it in upper } ?: upper.firstOrNull { it.number == 5 || it.number == 1 } ?: upper.last())
        return listOf(bass) + upper.map { 54 + Math.floorMod(rpc + it.interval.semitones - 54, 12) }.sorted()
    }

    /** The page on [key]: respelled to the table spelling when a step would need a double sharp or flat. */
    fun page(def: ProgressionDef, key: PitchName): ProgPage {
        var k = key
        var roots = spellAll(def, k)
        val respelled = if (roots == null) key else null
        if (roots == null) { k = PitchClass(key.pitchClass).defaultSpelling(); roots = spellAll(def, k) ?: def.steps.map { PitchClass(k.pitchClass + it.token.interval.semitones).defaultSpelling() } }
        val chords = def.steps.zip(roots) { st, r -> ProgChord(st, r, Reference.ascii(r) + st.def.symbols.first().removePrefix("C"), voice(r, st.def)) }
        return ProgPage(def, k, respelled, KeyMode(k, if (def.minor) "aeolian" else "major"), chords)
    }

    private fun third(d: ChordDef) = when (d.degrees.firstOrNull { it.number == 3 }?.interval?.semitones) { 4 -> "3"; 3 -> "b3"; else -> "-" }

    /**
     * What a chord sequence is matched on: each root relative to the first, and each chord's third (or, when [exact], its
     * catalog id), consecutive repeats merged.
     */
    internal fun shape(chords: List<Pair<Int, ChordDef>>, exact: Boolean = false): List<Pair<Int, String>> {
        val first = chords.firstOrNull()?.first ?: return emptyList()
        return chords.map { (pc, d) -> Math.floorMod(pc - first, 12) to if (exact) d.id else third(d) }
            .fold(ArrayList()) { acc, x -> if (acc.lastOrNull() != x) acc += x; acc }
    }

    /** True when the progression is matched on its exact chords: its roots and thirds alone don't change (the line cliché). */
    internal fun exact(def: ProgressionDef) = shape(def.steps.map { it.token.interval.semitones to it.def }).size < 2

    /**
     * Where the progression's chords appear in order in your charts, lyrics and scores, in any key: the roots move the same
     * way and each chord's third is the same (sevenths and extensions don't matter), repeats merged.
     */
    fun inYours(def: ProgressionDef, sources: List<YourSource>, dialect: ChordDialect = ChordDialect()): List<YourHit> {
        val ex = exact(def)
        val want = shape(def.steps.map { it.token.interval.semitones to it.def }, ex)
        val n = want.size
        return sources.flatMap { s ->
            val found = InYours.chordsIn(s).mapNotNull { (text, where) ->
                val c = ChordParser.parse(text, dialect).chord ?: return@mapNotNull null
                val root = (c.root as? ChordRoot.Name)?.name ?: return@mapNotNull null
                val q = c.quality?.let { runCatching { Chords.byId(it) }.getOrNull() } ?: return@mapNotNull null
                Triple(root, q, where to text)
            }.fold(ArrayList<Triple<PitchName, ChordDef, Pair<String, String>>>()) { acc, x ->
                val same = acc.lastOrNull()?.let { it.first.pitchClass == x.first.pitchClass && (if (ex) it.second == x.second else third(it.second) == third(x.second)) } == true
                if (!same) acc += x; acc
            }
            (0..found.size - n).mapNotNull { i ->
                val window = found.subList(i, i + n)
                if (shape(window.map { it.first.pitchClass to it.second }, ex) != want) return@mapNotNull null
                val w = window.first()
                YourHit(s.scrapId, s.title, w.third.first, w.first, window.joinToString(" ") { it.third.second }, s.projectId)
            }
        }
    }
}
