package com.betterappmafia.apophenia.core.learn

import com.betterappmafia.apophenia.core.analysis.BasicPitch
import com.betterappmafia.apophenia.core.catalog.Chords
import com.betterappmafia.apophenia.core.catalog.KeyMode
import com.betterappmafia.apophenia.core.chord.ChordDialect
import com.betterappmafia.apophenia.core.chord.ChordParser
import com.betterappmafia.apophenia.core.chord.ChordRoot
import com.betterappmafia.apophenia.core.pitch.PitchClass
import com.betterappmafia.apophenia.core.pitch.PitchName
import kotlin.random.Random

/*
 * The Ear on your own music (plan §9 "Your own music"): the chords of your own progressions, the keys of your own
 * recordings, and phrases from your own takes to play back by ear (on the piano, or on your guitar or bass: tab it by
 * ear). The drills exist only when your material has enough for them; they list what's there and never suggest.
 */

/** A stretch of a recording to play: [path] is the take's file, from [start] for [length] seconds. */
data class YourClip(val path: String, val start: Double, val length: Double)

data class YourChord(val root: PitchName, val quality: String, val title: String, val where: String, val text: String)
data class YourKey(val key: KeyMode, val title: String, val clip: YourClip)
data class YourPhrase(val notes: List<Int>, val title: String, val take: String, val clip: YourClip)

data class YourPool(val chords: List<YourChord> = emptyList(), val keys: List<YourKey> = emptyList(), val phrases: List<YourPhrase> = emptyList())

object YourEar {
    const val CHAPTER = "Your music"
    /** Seconds of a recording played for its key. [Claude] */
    const val KEY_CLIP = 12.0

    /** Your chord symbols that name a catalog chord, with where they are. */
    fun chords(sources: List<YourSource>, dialect: ChordDialect): List<YourChord> = sources.flatMap { s ->
        InYours.chordsIn(s).mapNotNull { (text, where) ->
            val c = ChordParser.parse(text, dialect).chord ?: return@mapNotNull null
            val root = (c.root as? ChordRoot.Name)?.name ?: return@mapNotNull null
            val q = c.quality?.takeIf { id -> Chords.all.any { it.id == id } } ?: return@mapNotNull null
            if (c.bass != null || c.poly != null) null else YourChord(root, q, s.title, where, text)
        }
    }

    /**
     * Single-line phrases in a take's transcription: 4 to 6 notes in a row that don't overlap, with gaps under 0.6 s,
     * spanning no more than a 12th. At most [perTake] from each take. [Claude]
     */
    fun phrases(notes: List<BasicPitch.TimedNote>, perTake: Int = 6): List<List<BasicPitch.TimedNote>> {
        val sorted = notes.sortedBy { it.start }
        val out = ArrayList<List<BasicPitch.TimedNote>>()
        var run = ArrayList<BasicPitch.TimedNote>()
        fun close() {
            var i = 0
            while (run.size - i >= 4 && out.size < perTake) {
                val take = run.subList(i, minOf(run.size, i + 6)).toList()
                if (take.maxOf { it.midi } - take.minOf { it.midi } <= 19) out += take
                i += take.size
            }
            run = ArrayList()
        }
        for (n in sorted) {
            val last = run.lastOrNull()
            if (last == null || (n.start >= last.end - 0.05 && n.start - last.end < 0.6)) run += n else { close(); run += n }
        }
        close()
        return out
    }

    fun clipOf(path: String, phrase: List<BasicPitch.TimedNote>) =
        YourClip(path, maxOf(0.0, phrase.first().start - 0.15), phrase.last().end - phrase.first().start + 0.45)

    /** The drills your material supports right now. */
    fun drills(pool: YourPool): List<EarDrill> = buildList {
        val qualities = pool.chords.map { it.quality }.distinct()
        if (qualities.size >= 2) add(EarDrill("own-chords", CHAPTER, "Chords of your progressions", EarKind.OWN_CHORD, qualities, "${pool.chords.size} CHORDS FROM YOUR CHARTS, LYRICS AND TAKES", pool = pool))
        val keys = pool.keys.map { it.key.token }.distinct()
        if (keys.size >= 2) add(EarDrill("own-keys", CHAPTER, "The key of your recordings", EarKind.OWN_KEY, keys, "${pool.keys.size} RECORDINGS WITH A KEY", pool = pool))
        if (pool.phrases.isNotEmpty()) add(EarDrill("own-phrases", CHAPTER, "Phrases from your takes", EarKind.OWN_PHRASE, listOf("phrase"), "${pool.phrases.size} PHRASES · PLAY THEM BACK, OR TAB THEM BY EAR", pool = pool))
    }

    /** What each drill needs when your material doesn't have it yet. */
    fun missing(pool: YourPool): List<String> = buildList {
        if (pool.chords.map { it.quality }.distinct().size < 2) add("Chords of your progressions: needs two or more kinds of chord in your chord charts, lyric sheets, scores or analysed takes.")
        if (pool.keys.map { it.key.token }.distinct().size < 2) add("The key of your recordings: needs recordings in two or more keys (set on the recording, or found by its analysis).")
        if (pool.phrases.isEmpty()) add("Phrases from your takes: needs a take turned into notes (a recording's AI ▾ → Notes → MIDI).")
    }

    fun question(drill: EarDrill, answer: String, random: Random): EarQuestion {
        val pool = drill.pool ?: YourPool()
        return when (drill.kind) {
            EarKind.OWN_CHORD -> {
                val c = pool.chords.filter { it.quality == answer }.let { it[random.nextInt(it.size)] }
                EarQuestion(drill, answer, RefSound.midis(c.root.pitchClass, Chords.byId(answer).semitones, 48), c.root, EarDirection.UP, note = "${c.title} · ${c.where} · ${c.text}")
            }
            EarKind.OWN_KEY -> {
                val k = pool.keys.filter { it.key.token == answer }.let { it[random.nextInt(it.size)] }
                EarQuestion(drill, answer, emptyList(), k.key.tonic, EarDirection.UP, tonic = RefSound.midis(k.key.tonic.pitchClass, listOf(0), 60).first(), note = k.title, clip = k.clip)
            }
            else -> {
                val p = pool.phrases[random.nextInt(pool.phrases.size)]
                EarQuestion(drill, answer, p.notes, PitchClass(p.notes.first()).defaultSpelling(), EarDirection.UP, tonic = p.notes.first(), note = "${p.title} · take ${p.take}", clip = p.clip)
            }
        }
    }

    fun audio(q: EarQuestion, settings: EarSettings): EarAudio = when (q.drill.kind) {
        EarKind.OWN_CHORD -> Ear.sound(q.copy(drill = Ear.drill("ch-triads")!!, answer = q.answer), settings)
        EarKind.OWN_KEY -> if (q.clip != null) EarAudio(emptyList(), clip = q.clip) else {
            val key = KeyMode.parseOrNull(q.answer)
            EarAudio(Ear.cadence(q.tonic!!, minor = key?.scale?.semitones?.getOrNull(2) == 3))
        }
        else -> EarAudio(emptyList(), clip = q.clip)
    }

    /** Another answer to hear against the right one: another chord on the same root, or a cadence in another key. */
    fun asAnswer(q: EarQuestion, answer: String): EarQuestion = when (q.drill.kind) {
        EarKind.OWN_CHORD -> q.copy(answer = answer, midis = RefSound.midis(q.root.pitchClass, Chords.byId(answer).semitones, 48))
        EarKind.OWN_KEY -> KeyMode.parseOrNull(answer)!!.let { k -> q.copy(answer = answer, clip = null, root = k.tonic, tonic = RefSound.midis(k.tonic.pitchClass, listOf(0), 60).first()) }
        else -> q
    }

    fun label(drill: EarDrill, answer: String): String = when (drill.kind) {
        EarKind.OWN_CHORD -> Ear.label(Ear.drill("ch-7-all")!!, answer)
        EarKind.OWN_KEY -> KeyMode.parseOrNull(answer)?.display ?: answer
        else -> "Phrase"
    }

    fun explain(q: EarQuestion, names: (PitchName) -> String): String = when (q.drill.kind) {
        EarKind.OWN_CHORD -> {
            val page = ChordPages.page(Chords.byId(q.answer), q.root)
            "${page.symbols.first()}: ${page.notes.joinToString(" ") { names(it) }}. From ${q.note}."
        }
        EarKind.OWN_KEY -> "${KeyMode.parseOrNull(q.answer)?.display ?: q.answer}: ${q.note}" + (if (q.clip == null) " (a cadence in this key)." else ".")
        else -> q.midis.joinToString(" ") { names(PitchClass(it).defaultSpelling()) + (it / 12 - 1) } + ". From ${q.note}."
    }
}
