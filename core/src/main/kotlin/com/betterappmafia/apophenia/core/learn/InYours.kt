package com.betterappmafia.apophenia.core.learn

import com.betterappmafia.apophenia.core.analysis.AnalysisText
import com.betterappmafia.apophenia.core.catalog.ChordDef
import com.betterappmafia.apophenia.core.catalog.Chords
import com.betterappmafia.apophenia.core.catalog.KeyMode
import com.betterappmafia.apophenia.core.catalog.ScaleDef
import com.betterappmafia.apophenia.core.chord.ChordDialect
import com.betterappmafia.apophenia.core.chord.ChordParser
import com.betterappmafia.apophenia.core.chord.ChordRoot
import com.betterappmafia.apophenia.core.interval.Interval
import com.betterappmafia.apophenia.core.pitch.PitchName
import com.betterappmafia.apophenia.core.score.ScoreText
import com.betterappmafia.apophenia.core.sheet.ChordChart
import com.betterappmafia.apophenia.core.sheet.LyricSheet

/**
 * "Where it appears in your own material" (plan §8): the places a Reference page's chord, scale or interval turns up
 * in the user's scraps. It lists them and says where; it never suggests anything.
 */
data class YourSource(
    val scrapId: Long,
    val title: String,
    /** The scrap format token: chord_chart, lyric, tab, sheet, recording… */
    val format: String,
    val body: String? = null,
    /** The scrap's key-and-mode token (KeyMode). */
    val key: String? = null,
    /** A project's own copy of the text: the project's name, so the hit says which version. */
    val project: String? = null,
    /** A take's analysis (AnalysisText) and its letter. */
    val analysis: String? = null,
    val take: String? = null,
    val projectId: Long? = null,
)

/** One place: the scrap, where in it ("bar 3", "line 2", "take C 0:41", "key"), the root there, and what was written. */
data class YourHit(val scrapId: Long, val title: String, val where: String, val root: PitchName?, val text: String, val projectId: Long? = null)

object InYours {
    /** Every chord symbol in [s] with where it is. */
    fun chordsIn(s: YourSource): List<Pair<String, String>> = runCatching {
        val prefix = s.project?.let { "$it · " } ?: ""
        when {
            s.analysis != null -> AnalysisText.decode(s.analysis)?.chords?.beats.orEmpty().fold(ArrayList<Pair<String, String>>()) { acc, b ->
                if (acc.lastOrNull()?.first != b.label) acc += b.label to "${prefix}take ${s.take ?: ""} ${clock(b.time)}"; acc
            }
            s.body == null -> emptyList()
            s.format == "chord_chart" -> ChordChart.parse(s.body).bars.flatMapIndexed { i, bar ->
                bar.beats.filter { it != ChordChart.HOLD && it != ChordChart.REPEAT }.distinct().map { it to "${prefix}bar ${i + 1}" }
            }
            s.format == "lyric" -> LyricSheet.parse(s.body).filterIsInstance<LyricSheet.Line.Words>().flatMapIndexed { i, line ->
                line.chords.distinct().map { it to "${prefix}line ${i + 1}" }
            }
            s.format == "tab" || s.format == "sheet" -> ScoreText.decode(s.body).parts.flatMap { p ->
                p.bars.flatMapIndexed { i, bar -> bar.voices.flatten().mapNotNull { it.chord }.distinct().map { it to "${prefix}${p.name.ifEmpty { "part" }} bar ${i + 1}" } }
            }
            else -> emptyList()
        }
    }.getOrDefault(emptyList())

    fun clock(seconds: Double): String = "%d:%02d".format(seconds.toInt() / 60, seconds.toInt() % 60)

    /** Where chords of catalog quality [def] appear, on any root. */
    fun chord(def: ChordDef, sources: List<YourSource>, dialect: ChordDialect = ChordDialect()): List<YourHit> = sources.flatMap { s ->
        chordsIn(s).mapNotNull { (text, where) ->
            val c = ChordParser.parse(text, dialect).chord ?: return@mapNotNull null
            if (c.quality != def.id || c.poly != null) return@mapNotNull null
            YourHit(s.scrapId, s.title, where, (c.root as? ChordRoot.Name)?.name, text, s.projectId)
        }
    }

    /** Scraps in [def] by their key, and scores whose key is it. */
    fun scale(def: ScaleDef, sources: List<YourSource>): List<YourHit> = sources.flatMap { s ->
        val out = ArrayList<YourHit>()
        KeyMode.parseOrNull(s.key)?.takeIf { it.scaleId == def.id && s.project == null && s.analysis == null }?.let { out += YourHit(s.scrapId, s.title, "key", it.tonic, it.display) }
        if (s.format == "tab" || s.format == "sheet") runCatching { ScoreText.decode(s.body!!) }.getOrNull()?.let { score ->
            score.key?.takeIf { it.scaleId == def.id && KeyMode.parseOrNull(s.key) != it }?.let { out += YourHit(s.scrapId, s.title, "score key", it.tonic, it.display) }
        }
        out
    }

    /** Melodic steps of [i] (up or down, between neighbouring notes of each voice) in scores. */
    fun interval(i: Interval, sources: List<YourSource>): List<YourHit> = sources.filter { (it.format == "tab" || it.format == "sheet") && it.body != null && it.project == null }.flatMap { s ->
        val score = runCatching { ScoreText.decode(s.body!!) }.getOrNull() ?: return@flatMap emptyList()
        score.parts.flatMap { p ->
            val out = ArrayList<YourHit>()
            var last: com.betterappmafia.apophenia.core.pitch.SpelledPitch? = null
            p.bars.forEachIndexed { b, bar ->
                bar.voices.firstOrNull().orEmpty().forEach { e ->
                    val top = e.notes.maxByOrNull { it.pitch.midi }?.pitch
                    if (top == null) { last = null; return@forEach }
                    val prev = last
                    if (prev != null) {
                        val up = top.midi >= prev.midi
                        val step = runCatching { if (up) Interval.between(prev, top) else Interval.between(top, prev) }.getOrNull()
                        if (step == i) out += YourHit(s.scrapId, s.title, "${p.name.ifEmpty { "part" }} bar ${b + 1}", if (up) prev.name else top.name, "$prev → $top ${if (up) "up" else "down"}")
                    }
                    last = top
                }
            }
            out
        }
    }
}

/**
 * Naming a chord from its notes (the reverse of a chord page, as Chordopia's and Oolimo's chord namers do): every
 * catalog chord on any of the notes as root whose tones are exactly these, or these less tones the catalog lets a
 * voicing leave out. A bass that isn't the root makes it a slash chord.
 */
data class NamedChord(val def: ChordDef, val root: PitchName, val bass: PitchName?, val missing: List<String>, val symbol: String)

object ChordNamer {
    /** [notes] spelled as given (the root and bass keep these spellings); [bass] is the lowest. */
    fun name(notes: List<PitchName>, bass: PitchName?): List<NamedChord> {
        val pcs = notes.map { it.pitchClass }.toSet()
        if (pcs.size < 2) return emptyList()
        val out = ArrayList<Pair<Int, NamedChord>>()
        for (root in notes.distinctBy { it.pitchClass }) for (def in Chords.all) {
            val tones = def.degrees.associateBy { Math.floorMod(root.pitchClass + it.interval.semitones, 12) }
            if (!tones.keys.containsAll(pcs)) continue
            val missing = tones.filterKeys { it !in pcs }.values.toList()
            if (!def.omitOrder.containsAll(missing)) continue
            val slash = bass?.takeIf { it.pitchClass != root.pitchClass }
            val symbol = Reference.ascii(root) + def.symbols.first().removePrefix("C") + (slash?.let { "/" + Reference.ascii(it) } ?: "")
            val rank = missing.size * 10 + (if (slash != null) 5 else 0) + def.degrees.size
            out += rank to NamedChord(def, root, slash, missing.map { it.toString() }, symbol)
        }
        return out.sortedBy { it.first }.map { it.second }
    }
}
