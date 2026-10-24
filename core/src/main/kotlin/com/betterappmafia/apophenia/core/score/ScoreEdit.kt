package com.betterappmafia.apophenia.core.score

import com.betterappmafia.apophenia.core.catalog.KeyMode
import com.betterappmafia.apophenia.core.instrument.Instrument
import com.betterappmafia.apophenia.core.rhythm.Duration
import com.betterappmafia.apophenia.core.rhythm.Rational
import com.betterappmafia.apophenia.core.tools.KeySpeller

/**
 * Editing a part (plan §10.5a, C4), Guitar Pro's and MuseScore's way: a selected beat in one of up to four
 * voices, a duration bar, pitches or frets entered on it, NEXT to step on, and MuseScore's other input modes
 * (rhythm, re-pitch, insert, real-time). Every edit returns a new score, so undo is a list of scores. Bars
 * may be left short or over-full while you work; [fill] says by how much, and the view marks it. Voice 1 is
 * the reference voice; voices 2–4 start as rests filling the bar and may hide them.
 */
object ScoreEdit {
    /** An event of a part: bar, index in the voice, and the voice (0 = voice 1). */
    data class Pos(val bar: Int, val index: Int, val voice: Int = 0)

    private fun Score.withPart(p: Int, f: (Part) -> Part) = copy(parts = parts.mapIndexed { i, part -> if (i == p) f(part) else part })

    private fun Part.withVoice(bar: Int, voice: Int, f: (List<Event>) -> List<Event>) =
        copy(bars = bars.mapIndexed { i, b -> if (i == bar) b.copy(voices = b.voices.mapIndexed { v, ev -> if (v == voice) f(ev) else ev }) else b })

    private fun Score.withEvent(p: Int, at: Pos, f: (Event) -> Event) =
        withPart(p) { part -> part.withVoice(at.bar, at.voice) { evs -> evs.mapIndexed { i, e -> if (i == at.index) f(e) else e } } }

    fun event(score: Score, p: Int, at: Pos): Event? = score.parts.getOrNull(p)?.bars?.getOrNull(at.bar)?.voices?.getOrNull(at.voice)?.getOrNull(at.index)

    /** How far bar [bar]'s [voice] is from a full bar: negative short, positive over (an absent voice is full). */
    fun fill(score: Score, part: Part, bar: Int, voice: Int = 0): Rational {
        val evs = part.bars[bar].voices.getOrNull(voice) ?: return Rational.ZERO
        return evs.fold(Rational.ZERO) { a, e -> a + e.length } - score.meterAt(bar).barLength
    }

    /** Where in its bar an event starts. */
    fun onset(part: Part, at: Pos): Rational =
        part.bars[at.bar].voices.getOrNull(at.voice)?.take(at.index)?.fold(Rational.ZERO) { a, e -> a + e.length } ?: Rational.ZERO

    fun setDuration(score: Score, p: Int, at: Pos, d: Duration) = score.withEvent(p, at) { it.copy(duration = d) }

    /**
     * The note on [string] at [fret]: replaced if the beat has a note on that string, added to it otherwise (a rest
     * becomes a note). Spelled in the score's key.
     */
    fun setFret(score: Score, p: Int, at: Pos, string: Int, fret: Int): Score {
        val inst = score.parts[p].instrument ?: return score
        val pitch = speller(score).spell(inst.midiAt(string, fret))
        return score.withEvent(p, at) { e ->
            val kept = e.notes.filter { it.string != string }
            val old = e.notes.firstOrNull { it.string == string }
            e.copy(notes = (kept + (old?.copy(pitch = pitch, fret = fret) ?: Note(pitch, string, fret))).sortedBy { it.string })
        }
    }

    /**
     * Notation and piano entry: [pitch] on the beat, added to its chord when [add], else the beat becomes that one
     * note (a rest becomes a note). On a fretted part it goes on the lowest free fret that plays it; null when
     * the instrument can't (the score is unchanged).
     */
    fun setPitch(score: Score, p: Int, at: Pos, pitch: com.betterappmafia.apophenia.core.pitch.SpelledPitch, add: Boolean): Score? {
        val inst = score.parts[p].instrument
        val e = event(score, p, at) ?: return null
        if (add && e.notes.any { it.pitch == pitch }) return score
        val keep = if (add) e.notes else emptyList()
        val place = inst?.let { i ->
            Fingering.places(i, pitch.midi).filter { pl -> keep.none { it.string == pl.string } }.minByOrNull { it.fret } ?: return null
        }
        val old = e.notes.firstOrNull()
        val n = if (add || old == null) Note(pitch, place?.string, place?.fret) else old.copy(pitch = pitch, string = place?.string, fret = place?.fret)
        return score.withEvent(p, at) { it.copy(notes = (keep + n).sortedWith(compareBy({ x -> x.string ?: 0 }, { x -> -x.pitch.midi }))) }
    }

    /** Note [note] with a new alteration on the same letter (F → F♯); a fretted note finds its place again. */
    fun setAlter(score: Score, p: Int, at: Pos, note: Int, alter: Int): Score? {
        val n = event(score, p, at)?.notes?.getOrNull(note) ?: return null
        return replacePitch(score, p, at, note, n.pitch.copy(alter = alter))
    }

    /** The same sound spelled the next way round (C♯ → D♭ → B𝄪 …): pitch and fret unchanged. */
    fun respell(score: Score, p: Int, at: Pos, note: Int): Score? {
        val n = event(score, p, at)?.notes?.getOrNull(note) ?: return null
        val options = (-2..2).flatMap { d ->
            val letter = n.pitch.letter + d
            val octaveShift = Math.floorDiv(n.pitch.letter.ordinal + d, 7)
            val base = com.betterappmafia.apophenia.core.pitch.SpelledPitch(letter, 0, n.pitch.octave + octaveShift)
            val alter = n.pitch.midi - base.midi
            if (alter in -2..2) listOf(base.copy(alter = alter)) else emptyList()
        }.sortedBy { Notation.diatonic(it) }
        if (options.size < 2) return score
        val next = options[(options.indexOf(n.pitch) + 1) % options.size]
        return score.withEvent(p, at) { e -> e.copy(notes = e.notes.mapIndexed { i, x -> if (i == note) x.copy(pitch = next) else x }) }
    }

    private fun replacePitch(score: Score, p: Int, at: Pos, note: Int, pitch: com.betterappmafia.apophenia.core.pitch.SpelledPitch): Score? {
        val inst = score.parts[p].instrument
        val e = event(score, p, at) ?: return null
        val others = e.notes.filterIndexed { i, _ -> i != note }
        val place = inst?.let { i -> Fingering.places(i, pitch.midi).filter { pl -> others.none { it.string == pl.string } }.minByOrNull { it.fret } ?: return null }
        return score.withEvent(p, at) { ev -> ev.copy(notes = ev.notes.mapIndexed { i, x -> if (i == note) x.copy(pitch = pitch, string = place?.string, fret = place?.fret) else x }) }
    }

    /** Moves note [note] of the beat to another place with the same pitch. */
    fun move(score: Score, p: Int, at: Pos, note: Int, string: Int, fret: Int) = score.withEvent(p, at) { e ->
        e.copy(notes = e.notes.mapIndexed { i, n -> if (i == note) n.copy(string = string, fret = fret) else n }.sortedBy { it.string })
    }

    /** Takes note [note] out of the beat; the last one out leaves a rest. */
    fun deleteNote(score: Score, p: Int, at: Pos, note: Int) = score.withEvent(p, at) { e -> e.copy(notes = e.notes.filterIndexed { i, _ -> i != note }) }

    fun toggleEffect(score: Score, p: Int, at: Pos, note: Int, fx: Effect) = score.withEvent(p, at) { e ->
        e.copy(notes = e.notes.mapIndexed { i, n -> if (i == note) n.copy(effects = if (fx in n.effects) n.effects - fx else n.effects + fx) else n })
    }

    /** Ties every note of the beat on to the next one, or unties them. */
    fun toggleTie(score: Score, p: Int, at: Pos) = score.withEvent(p, at) { e ->
        val on = e.notes.any { !it.tie }
        e.copy(notes = e.notes.map { it.copy(tie = on) })
    }

    /** A rest of [d] after [at]; returns the score and where it is. */
    fun insertAfter(score: Score, p: Int, at: Pos, d: Duration): Pair<Score, Pos> =
        score.withPart(p) { it.withVoice(at.bar, at.voice) { evs -> evs.take(at.index + 1) + Event(d) + evs.drop(at.index + 1) } } to at.copy(index = at.index + 1)

    fun deleteEvent(score: Score, p: Int, at: Pos): Score = score.withPart(p) { it.withVoice(at.bar, at.voice) { evs -> evs.filterIndexed { i, _ -> i != at.index } } }

    /**
     * NEXT: the beat after [at] in its voice. Past the end of a bar that isn't full yet, a rest of [d] is added to
     * fill on; past a full last bar, a new bar starts (voice 1 as rests, the voice being entered beginning with [d]).
     */
    fun next(score: Score, p: Int, at: Pos, d: Duration): Pair<Score, Pos> {
        val part = score.parts[p]
        val events = part.bars[at.bar].voices.getOrNull(at.voice) ?: emptyList()
        if (at.index + 1 < events.size) return score to at.copy(index = at.index + 1)
        if (fill(score, part, at.bar, at.voice) < Rational.ZERO) return insertAfter(score, p, at, minOf(d, score.meterAt(at.bar).barLength - events.fold(Rational.ZERO) { a, e -> a + e.length }))
        if (at.bar + 1 < part.bars.size) return ensureVoice(score, p, at.bar + 1, at.voice) to Pos(at.bar + 1, 0, at.voice)
        val s = if (at.voice == 0) score.withPart(p) { it.copy(bars = it.bars + Bar(listOf(listOf(Event(d))))) }
        else ensureVoice(insertBar(score, p, at.bar), p, at.bar + 1, at.voice)
        return s to Pos(at.bar + 1, 0, at.voice)
    }

    /** The bar's voices up to [voice] exist, each new one a run of rests filling the bar. */
    fun ensureVoice(score: Score, p: Int, bar: Int, voice: Int): Score = score.withPart(p) { part ->
        val b = part.bars[bar]
        if (b.voices.size > voice) part else {
            val m = score.meterAt(bar)
            val rests = { FromNotes.durations(0, m.barTicks.toLong(), m, 4).map { Event(it) } }
            part.copy(bars = part.bars.mapIndexed { i, x -> if (i == bar) x.copy(voices = x.voices + List(voice + 1 - x.voices.size) { rests() }) else x })
        }
    }

    /**
     * Clears [voice] (2–4) in [bar]: the highest voice goes away; a lower one becomes rests again (voices keep
     * their numbers, so voice 3 stays voice 3).
     */
    fun clearVoice(score: Score, p: Int, bar: Int, voice: Int): Score {
        if (voice == 0) return score
        return score.withPart(p) { part ->
            val b = part.bars[bar]
            if (voice >= b.voices.size) part
            else if (voice == b.voices.lastIndex) part.copy(bars = part.bars.mapIndexed { i, x -> if (i == bar) x.copy(voices = x.voices.dropLast(1)) else x })
            else {
                val m = score.meterAt(bar)
                val rests = FromNotes.durations(0, m.barTicks.toLong(), m, 4).map { Event(it) }
                part.copy(bars = part.bars.mapIndexed { i, x -> if (i == bar) x.copy(voices = x.voices.mapIndexed { v, evs -> if (v == voice) rests else evs }) else x })
            }
        }
    }

    /** Voices [a] and [b] of [bar] trade places (MuseScore's Exchange voice): both exist afterwards. */
    fun swapVoices(score: Score, p: Int, bar: Int, a: Int, b: Int): Score {
        val s = ensureVoice(score, p, bar, maxOf(a, b))
        return s.withPart(p) { part ->
            part.copy(bars = part.bars.mapIndexed { i, x ->
                if (i != bar) x else x.copy(voices = x.voices.mapIndexed { v, evs -> when (v) { a -> x.voices[b]; b -> x.voices[a]; else -> evs } })
            })
        }
    }

    /**
     * The note [note] (or the whole beat when null) moved into voice [to]: it needs a beat there starting at the
     * same time with the same length (as in MuseScore, notes stay where they are when the rhythms differ: null). A
     * beat left empty becomes a rest.
     */
    fun moveToVoice(score: Score, p: Int, at: Pos, note: Int?, to: Int): Pair<Score, Pos>? {
        if (to == at.voice) return null
        val s = ensureVoice(score, p, at.bar, to)
        val part = s.parts[p]
        val e = event(s, p, at) ?: return null
        val moving = if (note == null) e.notes else listOfNotNull(e.notes.getOrNull(note))
        if (moving.isEmpty()) return null
        val start = onset(part, at)
        val targets = part.bars[at.bar].voices[to]
        val idx = targets.indices.firstOrNull { onset(part, Pos(at.bar, it, to)) == start } ?: return null
        if (targets[idx].length != e.length) return null
        val dest = Pos(at.bar, idx, to)
        var out = s.withEvent(p, dest) { d -> d.copy(notes = d.notes + moving.filter { m -> d.notes.none { it.pitch == m.pitch } }, hidden = false) }
        out = out.withEvent(p, at) { x -> x.copy(notes = x.notes.filter { it !in moving }) }
        return out to dest
    }

    /** Rests of voices 2–4 can be hidden (MuseScore's V); voice 1 is the reference voice and keeps its rests. */
    fun toggleHidden(score: Score, p: Int, at: Pos): Score =
        if (at.voice == 0) score else score.withEvent(p, at) { e -> if (e.isRest) e.copy(hidden = !e.hidden) else e }

    // MuseScore's input modes besides step entry.

    /** Rhythm mode: a beat of [d] holding [pitch] (MuseScore puts it on the middle line), or a rest when null; then the next beat. */
    fun enterRhythm(score: Score, p: Int, at: Pos, d: Duration, pitch: com.betterappmafia.apophenia.core.pitch.SpelledPitch?): Pair<Score, Pos>? {
        event(score, p, at) ?: return null
        var s = score.withEvent(p, at) { it.copy(duration = d, notes = emptyList(), hidden = false) }
        if (pitch != null) s = setPitch(s, p, at, pitch, add = false) ?: return null
        return next(s, p, at, d)
    }

    /**
     * Re-pitch mode: [pitch] replaces the notes of the first sounding beat at or after [at] (rests and notes tied
     * in are passed over), keeping its rhythm, ties and markings; notes tied on from it follow. With [add] it joins
     * the beat at [at] instead and the cursor stays. Returns the score and the beat to re-pitch next (null past the end).
     */
    fun repitch(score: Score, p: Int, at: Pos, pitch: com.betterappmafia.apophenia.core.pitch.SpelledPitch, add: Boolean): Pair<Score, Pos?>? {
        val target = if (add) at else sounding(score, p, at, includeSelf = true) ?: return null
        val e = event(score, p, target) ?: return null
        if (add && e.isRest) return null
        var s = if (add) setPitch(score, p, target, pitch, add = true) ?: return null
        else {
            val keepTie = e.notes.any { it.tie }
            val placed = setPitch(score.withEvent(p, target) { it.copy(notes = it.notes.take(1)) }, p, target, pitch, add = false) ?: return null
            placed.withEvent(p, target) { it.copy(notes = it.notes.map { n -> n.copy(tie = keepTie) }) }
        }
        // The tie chain follows the new pitches.
        var cur = target
        while (true) {
            val here = event(s, p, cur) ?: break
            if (here.notes.none { it.tie }) break
            val nxt = after(s, p, cur) ?: break
            val pitches = here.notes.filter { it.tie }.map { it.pitch }
            val tieOn = event(s, p, nxt)?.notes?.any { it.tie } ?: false
            s = s.withEvent(p, nxt) { ev -> if (ev.isRest) ev else ev.copy(notes = pitches.mapIndexed { k, pt -> (ev.notes.getOrNull(k) ?: Note(pt)).copy(pitch = pt, tie = tieOn) }) }
            s = refingerBeat(s, p, nxt)
            cur = nxt
        }
        return s to if (add) at else sounding(s, p, target, includeSelf = false)
    }

    /**
     * Insert mode: a beat of [d] with [pitch] (a rest when null) goes in before [at]; what follows moves later and
     * the bar grows (its number goes red until it adds up again). Returns where the next insert goes: before the
     * same beat as before.
     */
    fun insertBefore(score: Score, p: Int, at: Pos, d: Duration, pitch: com.betterappmafia.apophenia.core.pitch.SpelledPitch?): Pair<Score, Pos>? {
        var s = score.withPart(p) { it.withVoice(at.bar, at.voice) { evs -> evs.take(at.index) + Event(d) + evs.drop(at.index) } }
        if (pitch != null) s = setPitch(s, p, at, pitch, add = false) ?: return null
        return s to at.copy(index = at.index + 1)
    }

    /**
     * Real-time mode, one click (or one tap of ADVANCE): the beat at [at] becomes [d] holding [pitches] (a rest
     * when none are held); with [tieOn] the [previous] beat's notes that are still held tie into it. Then the next beat.
     */
    fun realtimeStep(score: Score, p: Int, at: Pos, d: Duration, pitches: List<com.betterappmafia.apophenia.core.pitch.SpelledPitch>, previous: Pos?, tieOn: Boolean): Pair<Score, Pos> {
        var s = score.withEvent(p, at) { it.copy(duration = d, notes = emptyList(), hidden = false) }
        for ((k, pt) in pitches.withIndex()) s = setPitch(s, p, at, pt, add = k > 0) ?: s
        if (tieOn && previous != null) s = s.withEvent(p, previous) { ev -> ev.copy(notes = ev.notes.map { n -> if (pitches.any { it == n.pitch }) n.copy(tie = true) else n }) }
        return next(s, p, at, d)
    }

    /**
     * After real-time entry: runs of the same notes tied beat to beat inside a bar are rewritten as the fewest
     * durations that show the beat ([FromNotes.durations]), e.g. four tied 16ths on a beat become a quarter.
     * Only plain beats merge (nothing else written on them).
     */
    fun mergeTies(score: Score, p: Int, voice: Int = 0): Score = score.withPart(p) { part ->
        part.copy(bars = part.bars.mapIndexed { b, bar ->
            val evs = bar.voices.getOrNull(voice) ?: return@mapIndexed bar
            val meter = score.meterAt(b)
            val out = ArrayList<Event>()
            fun plain(e: Event) = e.marks.isEmpty() && e.dynamic == null && e.spans.isEmpty() && e.chord == null && e.texts.isEmpty() &&
                e.lyrics.isEmpty() && e.graces.isEmpty() && e.tremolo == 0 && e.duration.tuplets.isEmpty()
            var i = 0; var pos = 0L
            while (i < evs.size) {
                var j = i
                while (j + 1 < evs.size && !evs[j].isRest && plain(evs[j]) && plain(evs[j + 1]) && evs[j].notes.all { it.tie } &&
                    evs[j + 1].notes.map { it.pitch }.toSet() == evs[j].notes.map { it.pitch }.toSet()) j++
                val ticks = (i..j).sumOf { ticksOf(evs[it].length) }
                if (j > i) {
                    val ds = FromNotes.durations(pos, ticks, meter, 4)
                    val lastTie = evs[j].notes.associate { it.pitch to it.tie }
                    if (ds.size < j - i + 1) ds.forEachIndexed { k, d ->
                        val last = k == ds.lastIndex
                        out += evs[i].copy(duration = d, notes = evs[i].notes.map { it.copy(tie = if (last) lastTie[it.pitch] ?: false else true) })
                    } else for (k in i..j) out += evs[k]
                } else out += evs[i]
                pos += ticks; i = j + 1
            }
            bar.copy(voices = bar.voices.mapIndexed { v, x -> if (v == voice) out else x })
        })
    }

    /** A length in ticks (960 a quarter). */
    private fun ticksOf(r: Rational): Long = Math.round(r.toDouble() * 3840)

    /** The beat after [at] in its voice, across bars (no new beats made). */
    private fun after(score: Score, p: Int, at: Pos): Pos? {
        val bars = score.parts[p].bars
        val evs = bars[at.bar].voices.getOrNull(at.voice) ?: return null
        if (at.index + 1 < evs.size) return at.copy(index = at.index + 1)
        for (b in at.bar + 1 until bars.size) if ((bars[b].voices.getOrNull(at.voice)?.size ?: 0) > 0) return Pos(b, 0, at.voice)
        return null
    }

    /** The beat before [at] in its voice, across bars. */
    private fun before(score: Score, p: Int, at: Pos): Pos? {
        if (at.index > 0) return at.copy(index = at.index - 1)
        val bars = score.parts[p].bars
        for (b in at.bar - 1 downTo 0) bars[b].voices.getOrNull(at.voice)?.takeIf { it.isNotEmpty() }?.let { return Pos(b, it.lastIndex, at.voice) }
        return null
    }

    /** The first beat at (or, without [includeSelf], after) [at] that has notes not tied in from the beat before. */
    private fun sounding(score: Score, p: Int, at: Pos, includeSelf: Boolean): Pos? {
        var cur: Pos? = if (includeSelf) at else after(score, p, at)
        var tiedIn = (if (includeSelf) before(score, p, at) else at)?.let { event(score, p, it) }?.notes?.any { it.tie } ?: false
        while (cur != null) {
            val e = event(score, p, cur) ?: return null
            if (!e.isRest && !tiedIn) return cur
            tiedIn = e.notes.any { it.tie }
            cur = after(score, p, cur)
        }
        return null
    }

    /** A fretted beat's notes put back on strings after their pitches changed. */
    private fun refingerBeat(score: Score, p: Int, at: Pos): Score {
        val inst = score.parts[p].instrument ?: return score
        val e = event(score, p, at) ?: return score
        val places = Fingering.choose(inst, listOf(e.notes.map { it.pitch.midi })).first()
        return score.withEvent(p, at) { ev -> ev.copy(notes = ev.notes.mapIndexedNotNull { i, n -> places.getOrNull(i)?.let { n.copy(string = it.string, fret = it.fret) } }.sortedBy { it.string }) }
    }

    /** The longest of [d] and the plain note values that fits in [room]. */
    private fun minOf(d: Duration, room: Rational): Duration =
        if (d.length <= room) d else com.betterappmafia.apophenia.core.rhythm.NoteValue.entries
            .map { Duration(it) }.filter { it.length <= room }.maxByOrNull { it.length.toDouble() } ?: d

    /** An empty bar (a whole-bar rest, written as the rests that fill it) after bar [bar]. */
    fun insertBar(score: Score, p: Int, bar: Int): Score = score.withPart(p) { part ->
        val m = score.meterAt(bar)
        val rests = FromNotes.durations(0, m.barTicks.toLong(), m, 4).map { Event(it) }
        part.copy(bars = part.bars.take(bar + 1) + Bar(listOf(rests)) + part.bars.drop(bar + 1))
    }

    fun deleteBar(score: Score, p: Int, bar: Int): Score = score.withPart(p) { part ->
        if (part.bars.size <= 1) part else part.copy(bars = part.bars.filterIndexed { i, _ -> i != bar })
    }

    /** A copy of bar [from] put after bar [after]. */
    fun pasteBar(score: Score, p: Int, copied: Bar, after: Int): Score = score.withPart(p) { part ->
        part.copy(bars = part.bars.take(after + 1) + copied + part.bars.drop(after + 1))
    }

    /**
     * Retune (plan §6.4): [keepNotes] keeps the pitches and fingers them again for the new tuning; otherwise
     * the strings and frets stay and the pitches move with the strings.
     */
    fun retune(score: Score, p: Int, to: Instrument, keepNotes: Boolean): Score {
        val sp = speller(score)
        return score.withPart(p) { part ->
            if (keepNotes) refinger(part.copy(instrument = to), to)
            else part.copy(instrument = to, bars = part.bars.map { b ->
                b.copy(voices = b.voices.map { evs ->
                    evs.map { e ->
                        e.copy(notes = e.notes.mapNotNull { n ->
                            val s = n.string ?: return@mapNotNull n
                            if (s > to.tuning.stringCount) null
                            else n.copy(pitch = sp.spell(to.midiAt(s, (n.fret ?: 0).coerceAtMost(to.frets))))
                        })
                    }
                })
            })
        }
    }

    /**
     * Every note [semitones] up or down, spelled in the key, fingered again. Notes that leave the neck are dropped.
     * Grace notes and chord-symbol roots and basses move too; a chord diagram no longer fits and is taken off.
     */
    fun transpose(score: Score, p: Int, semitones: Int): Score {
        val sp = speller(score)
        fun move(n: Note) = n.copy(pitch = sp.spell(n.pitch.midi + semitones))
        return score.withPart(p) { part ->
            val moved = part.copy(bars = part.bars.map { b ->
                b.copy(voices = b.voices.map { evs ->
                    evs.map { e ->
                        e.copy(
                            notes = e.notes.map(::move), graces = e.graces.map { g -> g.copy(notes = g.notes.map(::move)) },
                            chord = e.chord?.let { transposeChord(it, semitones, sp) }, diagram = if (semitones == 0) e.diagram else null,
                        )
                    }
                })
            })
            part.instrument?.let { refinger(moved, it) } ?: moved
        }
    }

    /** Strings and frets chosen again for the whole part by the fingering chooser, keeping ties and effects. */
    fun refinger(part: Part, inst: Instrument): Part {
        // Each voice is fingered as its own line.
        val voices = part.bars.maxOfOrNull { it.voices.size } ?: 1
        val places = (0 until voices).map { v -> Fingering.choose(inst, part.bars.flatMap { it.voices.getOrNull(v) ?: emptyList() }.map { e -> e.notes.map { it.pitch.midi } }) }
        val k = IntArray(voices)
        return part.copy(bars = part.bars.map { b ->
            b.copy(voices = b.voices.mapIndexed { v, evs ->
                evs.map { e ->
                    val pl = places[v][k[v]++]
                    e.copy(notes = e.notes.mapIndexedNotNull { i, n -> pl[i]?.let { n.copy(string = it.string, fret = it.fret) } }.sortedBy { it.string })
                }
            })
        })
    }


    // ADD ▾ (C4): what goes on an event, a note or a bar besides pitch and rhythm.

    fun editEvent(score: Score, p: Int, at: Pos, f: (Event) -> Event): Score = score.withEvent(p, at, f)

    fun editNote(score: Score, p: Int, at: Pos, note: Int, f: (Note) -> Note): Score =
        score.withEvent(p, at) { e -> e.copy(notes = e.notes.mapIndexed { i, n -> if (i == note) f(n) else n }) }

    /** A bar's own fields; [allParts] for what belongs to the whole score (meter, key, tempo, repeats, rehearsal). */
    fun editBar(score: Score, p: Int, bar: Int, allParts: Boolean = true, f: (Bar) -> Bar): Score =
        score.copy(parts = score.parts.mapIndexed { i, part ->
            if (i != p && !allParts) part else part.copy(bars = part.bars.mapIndexed { b, x -> if (b == bar) f(x) else x })
        })

    fun toggleMark(score: Score, p: Int, at: Pos, mark: Mark) = score.withEvent(p, at) { e ->
        // One arpeggio sign at a time.
        val others = if (mark.arpeggio) e.marks.filterNot { it.arpeggio }.toSet() else e.marks
        e.copy(marks = if (mark in e.marks) e.marks - mark else others + mark)
    }

    /** Accent, marcato and staccato are note effects (the tab shows them per note): on or off for the whole chord. */
    fun toggleEffectAll(score: Score, p: Int, at: Pos, fx: Effect) = score.withEvent(p, at) { e ->
        val on = e.notes.any { fx !in it.effects }
        e.copy(notes = e.notes.map { it.copy(effects = if (on) it.effects + fx else it.effects - fx) })
    }

    /** How many events from [at] to the end of the part's first voice, [at] included: the longest a line can be. */
    fun eventsFrom(score: Score, p: Int, at: Pos): Int {
        val bars = score.parts[p].bars
        return bars[at.bar].voices[0].size - at.index + (at.bar + 1 until bars.size).sumOf { bars[it].voices[0].size }
    }

    /** A line from [at] over [count] events (clamped to what's left; under 2 removes it). */
    fun setSpan(score: Score, p: Int, at: Pos, span: Span, count: Int): Score {
        val n = minOf(count, eventsFrom(score, p, at))
        return score.withEvent(p, at) { e -> e.copy(spans = if (n < 2) e.spans - span else e.spans + (span to n)) }
    }

    /**
     * A grace note before (or after) the event, a copy of its top note's pitch without string or fret
     * (MuseScore's way: add, then move it); null on a rest.
     */
    fun addGrace(score: Score, p: Int, at: Pos, value: com.betterappmafia.apophenia.core.rhythm.NoteValue, slash: Boolean, after: Boolean): Score? {
        val e = event(score, p, at) ?: return null
        val top = e.notes.maxByOrNull { it.pitch.midi } ?: return null
        return score.withEvent(p, at) { it.copy(graces = it.graces + Grace(value, listOf(Note(top.pitch)), slash, after)) }
    }

    /** The event's last-added grace note a staff step up or down, in the key. */
    fun stepGrace(score: Score, p: Int, at: Pos, by: Int): Score = score.withEvent(p, at) { e ->
        if (e.graces.isEmpty()) e else {
            val fifths = Notation.fifths(score.keyAt(at.bar))
            val g = e.graces.last()
            e.copy(graces = e.graces.dropLast(1) + g.copy(notes = g.notes.map { it.copy(pitch = Notation.stepped(it.pitch, by, fifths)) }))
        }
    }

    /** Syllable [text] for [verse] on the event (blank removes it). */
    fun setLyric(score: Score, p: Int, at: Pos, verse: Int, text: String, hyphen: Boolean, extend: Boolean) = score.withEvent(p, at) { e ->
        val rest = e.lyrics.filter { it.verse != verse }
        e.copy(lyrics = (if (text.isBlank()) rest else rest + Lyric(verse, text.trim(), hyphen, extend)).sortedBy { it.verse })
    }

    /** The next event after [at] that has notes (lyrics skip rests and tied-on notes), or null at the end. */
    fun nextSung(score: Score, p: Int, at: Pos): Pos? {
        val bars = score.parts[p].bars
        var prevTied = event(score, p, at)?.notes?.any { it.tie } ?: false
        var b = at.bar; var i = at.index + 1
        while (b < bars.size) {
            val evs = bars[b].voices[0]
            while (i < evs.size) {
                val e = evs[i]
                if (!e.isRest && !prevTied) return Pos(b, i)
                prevTied = e.notes.any { it.tie }
                i++
            }
            b++; i = 0
        }
        return null
    }

    /** The frets of the event's notes as a diagram, lowest string first (fretted parts only). */
    fun diagramOf(part: Part, e: Event): List<Int?>? {
        val inst = part.instrument ?: return null
        if (e.notes.none { it.fret != null }) return null
        val count = inst.tuning.stringCount
        // String 1 is the highest-positioned; a diagram reads from the lowest.
        return (count downTo 1).map { s -> e.notes.firstOrNull { it.string == s }?.fret }
    }

    /** A chord symbol's root and bass moved by [semitones], spelled by [sp]; the rest of the text kept as typed. */
    /**
     * A chord symbol moved [semitones], root and bass spelled for the target key ([sp]) and the rest kept as typed
     * (C→E♭: D7 becomes F7, not E♯7). Read by the chord parser; text it can't read has its leading note name moved
     * the old way, and a polychord is left as it is.
     */
    internal fun transposeChord(text: String, semitones: Int, sp: KeySpeller): String {
        val p = com.betterappmafia.apophenia.core.chord.ChordParser.parse(text)
        val c = p.chord ?: return transposeChordText(text, semitones, sp)
        if (c.isNoChord || c.poly != null) return text
        fun move(n: com.betterappmafia.apophenia.core.pitch.PitchName): String {
            val q = sp.spell(n.at(4).midi + semitones)
            return com.betterappmafia.apophenia.core.chord.ChordParser.name(com.betterappmafia.apophenia.core.pitch.PitchName(q.letter, q.alter), com.betterappmafia.apophenia.core.chord.ChordDialect.Names.ENGLISH)
        }
        val root = (c.root as? com.betterappmafia.apophenia.core.chord.ChordRoot.Name)?.name ?: return text
        return move(root) + p.rest + (c.bass?.let { "/" + move(it) } ?: "")
    }

    private fun transposeChordText(text: String, semitones: Int, sp: KeySpeller): String {
        val root = ROOT.find(text) ?: return text
        fun name(letter: String, acc: String): String {
            val p = com.betterappmafia.apophenia.core.pitch.SpelledPitch.parse(letter + acc.replace("##", "x") + "4")
            val q = sp.spell(p.midi + semitones)
            return q.letter.name + when (q.alter) { -2 -> "bb"; -1 -> "b"; 1 -> "#"; 2 -> "##"; else -> "" }
        }
        var out = name(root.groupValues[1], root.groupValues[2]) + text.substring(root.range.last + 1)
        val bass = BASS.find(out)
        if (bass != null) out = out.substring(0, bass.range.first) + "/" + name(bass.groupValues[1], bass.groupValues[2])
        return out
    }

    private val ROOT = Regex("^([A-G])(##|bb|#|b)?")
    private val BASS = Regex("/([A-G])(##|bb|#|b)?$")

    // The chord chart view (C4): the same chord symbols, bar by bar and beat by beat.

    /** One beat of a bar in the chart: where it starts (ticks into the bar) and the chord symbols starting inside it. */
    data class ChartBeat(val tick: Long, val chords: List<String>)

    /** Each bar's beats with the chord symbols on voice 1's beats that start in them. */
    fun chart(score: Score, part: Part): List<List<ChartBeat>> = part.bars.mapIndexed { b, bar ->
        val meter = score.meterAt(b)
        val beats = meter.beatStarts.map { ticksOf(it) }
        val starts = ArrayList<Pair<Long, String>>()
        var t = 0L
        for (e in bar.voices[0]) { e.chord?.let { starts += t to it }; t += ticksOf(e.length) }
        beats.mapIndexed { i, bt ->
            val end = beats.getOrElse(i + 1) { meter.barTicks.toLong() }
            ChartBeat(bt, starts.filter { it.first >= bt && it.first < end }.map { it.second })
        }
    }

    /**
     * The chord symbol at [tick] of [bar] set to [text] (null takes it off). It lives on voice 1's beat starting
     * there; when a longer note or rest covers that moment, it is split there (tied, for a note) so the symbol has
     * a beat to sit on, as a chord change mid-note needs.
     */
    fun setChordAt(score: Score, p: Int, bar: Int, tick: Long, text: String?): Score {
        var s = splitAt(score, p, bar, tick)
        val evs = s.parts[p].bars[bar].voices[0]
        var t = 0L
        for ((i, e) in evs.withIndex()) {
            // On the beat starting there, or (a tuplet that wasn't cut) the one sounding then.
            if (t == tick || tick in t until t + ticksOf(e.length)) return s.withEvent(p, Pos(bar, i)) { it.copy(chord = text?.trim()?.ifEmpty { null }) }
            t += ticksOf(e.length)
        }
        return score
    }

    /** Voice 1 of [bar] gets a beat starting at [tick]: the event across it is cut in two, tied over for notes. */
    private fun splitAt(score: Score, p: Int, bar: Int, tick: Long): Score {
        val evs = score.parts[p].bars[bar].voices[0]
        val meter = score.meterAt(bar)
        var t = 0L
        for ((i, e) in evs.withIndex()) {
            val len = ticksOf(e.length)
            if (tick == t) return score
            if (tick in (t + 1) until (t + len)) {
                if (e.duration.tuplets.isNotEmpty()) return score   // a tuplet isn't cut; the symbol goes on its first beat instead
                val first = FromNotes.durations(t, tick - t, meter, 4)
                val second = FromNotes.durations(tick, t + len - tick, meter, 4)
                val pieces = first + second
                val parts = pieces.mapIndexed { k, d ->
                    val last = k == pieces.lastIndex
                    val base = if (k == 0) e else Event(d)
                    base.copy(duration = d, notes = e.notes.map { n -> n.copy(tie = if (last) n.tie else true) }, hidden = e.hidden)
                }
                return score.withPart(p) { it.withVoice(bar, 0) { list -> list.take(i) + parts + list.drop(i + 1) } }
            }
            t += len
        }
        return score
    }
    private fun speller(score: Score) = KeySpeller(score.key ?: KeyMode.parseOrNull("C major")!!)

    /** A new tab: [bars] empty bars for [inst] at the given meter and tempo. */
    fun emptyTab(name: String, inst: Instrument, meter: com.betterappmafia.apophenia.core.rhythm.TimeSignature, tempo: com.betterappmafia.apophenia.core.rhythm.Tempo, key: KeyMode?, bars: Int = 4): Score {
        val clef = if (inst.tuning.strings.first().midi < 40) Clef.BASS else Clef.TREBLE_8VB
        val base = Score("", meter, key, tempo, listOf(Part(name, clef, inst, listOf(Bar(listOf(emptyList()))))))
        var s = base.withPart(0) { it.copy(bars = emptyList()) }
        s = s.withPart(0) { it.copy(bars = listOf(Bar(listOf(FromNotes.durations(0, meter.barTicks.toLong(), meter, 4).map { d -> Event(d) })))) }
        repeat(bars - 1) { s = insertBar(s, 0, it) }
        return s
    }

    /** New sheet music: [bars] empty bars of one part on [clef], no instrument (no tab). */
    fun emptySheet(name: String, clef: Clef, meter: com.betterappmafia.apophenia.core.rhythm.TimeSignature, tempo: com.betterappmafia.apophenia.core.rhythm.Tempo, key: KeyMode?, bars: Int = 4): Score {
        var s = Score("", meter, key, tempo, listOf(Part(name, clef, null, listOf(Bar(listOf(FromNotes.durations(0, meter.barTicks.toLong(), meter, 4).map { Event(it) }))))))
        repeat(bars - 1) { s = insertBar(s, 0, it) }
        return s
    }
}
