# Notation, piano-roll and tab editors: what real software ships (21 Oct 2026)

Christopher asked (21 Oct 2026) that Apophenia have **a real sheet-music writer and editor**, informed by PC notation software, **FL Studio's piano roll menus**, and **tablature software**. This collects what those programs actually offer, as a feature inventory for the design.

Each item is marked:
- **[verified]** read from the vendor's own documentation on 21 Oct 2026;
- **[secondary]** from third-party sources;
- **[from knowledge, unverified]** to be checked against the manual before building.

## The programs musicians write with
- **Sibelius** (Avid) and **Dorico** (Steinberg) are the professional standards. **MuseScore 4** is free and open source.
- **Finale ended development in August 2024**; MakeMusic recommends Dorico. [secondary: Gearnews, Heise, AlternativeTo]
- **Guitar Pro 8** is the standard tab editor. **TuxGuitar** is an open-source tab editor [from knowledge, unverified; check its licence before reusing any code].

## Notation editors

### Sibelius ribbon tabs [secondary, Sibelius 7 course index + overview]
File · Home · Note Input · Notations · Text · Play · Layout · Appearance · Parts · Review · View.
- **Notations** holds clefs, time and key signatures, lines, symbols, barlines, repeats, voltas, rehearsal marks and chord symbols.
- **Text** holds title, composer, lyrics, expression, technique and tempo.
- **Layout** holds breaks, page size, staves and systems.

### Dorico's modes [verified: steinberg.help]
- **Setup:** instruments, players, flows, layouts.
- **Write:** input and edit notes and notation items, from toolboxes and panels.
- **Engrave:** fine positioning and page layout.
- **Play:** sound, automation, mix.
- **Print:** print and export.

### MuseScore 4
- Notes go in by step-time by default; other modes exist [verified: musescore.org handbook].
- Durations and dots come from the note input toolbar, the period key adds a dot, and arrow keys move pitch with accidentals added automatically [verified].
- Voices handle independent lines on one staff [verified].
- **Palettes** [the default list is from knowledge, unverified; the handbook names only Key signatures and Articulations in text]: Clefs, Key signatures, Time signatures, Accidentals, Articulations, Ornaments, Breaths & pauses, Dynamics, Hairpins, Barlines, Repeats & jumps, Tempo, Text, Lines, Arpeggios & glissandos, Fingering, Tremolos, Noteheads, Grace notes, Fretboard diagrams, Layout. More are available through "Add palettes".
- **Input modes** [from knowledge, unverified]: step-time, re-pitch, rhythm-only, real-time (auto and manual), insert.

### What a real notation editor has to do (combined inventory)
- **Score setup:** instruments and staves (treble/bass/grand staff, tab staff linked to notation), transposing instruments, clef, key and time signatures (including changes mid-piece), pickup bar, tempo marks.
- **Note entry:**
  - durations from whole to 64th, dots and double dots, rests;
  - ties; tuplets of any ratio, nested;
  - grace notes before and on the beat;
  - chords; voices 1–4;
  - accidentals and enharmonic respelling; cross-staff notes.
  - Input by computer/on-screen keyboard, mouse or touch on the staff, MIDI keyboard, step-time, rhythm-first then pitch, re-pitch existing rhythm, and real-time recording.
- **Notation items:**
  - articulations (staccato, accent, tenuto, marcato…), ornaments (trill, mordents, turns), fermatas, breath marks;
  - dynamics and hairpins; slurs and phrase marks;
  - pedal and octave (8va/8vb) lines, glissando, arpeggio, tremolo, noteheads;
  - fingering (piano 1–5, string fingering), chord symbols, fretboard diagrams;
  - lyrics with verses, melismas and hyphens;
  - text (expression, technique, tempo, rehearsal marks), barlines, repeats, voltas, segno/coda/D.S./D.C.
- **Editing:** select ranges, copy/paste, transpose, respell, re-beam, swap voices, explode/reduce, undo/redo.
- **Layout:** system and page breaks, spacing, parts versus full score. In a phone app, readable rather than engraved.
- **Play:** sounding pitch, playback of ornaments and dynamics, mixer.
- **Interchange:** MusicXML, MIDI, PDF.

## FL Studio piano roll [verified: image-line.com manual, "Piano roll" and "Piano roll menu"]
- **Toolbar:** Snap to grid, Snap to scale, Chord Stamp, Draw, Paint, Paint drum sequencer, Delete, Mute, Interpolate, Slice, Select, Zoom to selection, Play selected, Play/Pause.
- **Note properties:** velocity, pan, release velocity, Mod X, Mod Y, fine pitch, colour (16 groups), slide, portamento, start time, duration.
- **Tools menu:** Riff machine, Generate chord progression, Quick legato, Articulate, Quick quantize, Quick quantize start times, Quantize, Quick chop, Chop, Glue, Arpeggiate, Strum, Flam, Claw machine, Limit, Flip, Randomize, Scale levels, LFO, Script, Stamp.
- **Edit menu:**
  - cut, copy, paste, duplicate, delete;
  - shift and rotate left/right; transpose ±1 semitone and ±1 octave;
  - discard lengths, resize from left, mouse-wheel nudge, change colour, mute;
  - insert space, slice & insert space, delete space, trim selection.
- **Select menu:** select all, at random, by colour, odd (swung) notes, muted, overlapping, stacked; invert selection; time around selection; magic lasso.
- **File menu:** open/save score, import/export MIDI, MIDI clipboard, export as a PDF score sheet.
- **View menu:**
  - **scale highlighting** (chosen or auto-detected scale), note grid highlights;
  - **ghost channels** (other parts shown greyed, optionally editable), **background waveform**;
  - keyboard style and labels; grid colour and contrast; note colours.
- **Chord Stamp presets:** automatic chord top-down or bottom-up, melodic chords, scales, percussion.
- **Event editor lane:** draw any note property or automation under the notes.

## Guitar Pro 8 effect symbols [verified: guitar-pro.com docs, "Effects Symbols"]
- **Dynamics and tone:** ghost note, accent, heavy accent, staccato, let ring, sustain pedal, palm mute, dead note, fade-in, fade-out, volume swell.
- **Touch:** legato, hammer-on/pull-off, legato slide, shift slide, slide in from below/above, slide out down/up, bend, whammy bar, left-hand tapping, tapping.
- **Harmonics:** natural, artificial.
- **Ornaments:** grace notes before or on the beat, tremolo picking, upper/lower mordent, turn, inverted turn, trill.
- **Vibrato and wah:** slight or wide left-hand vibrato, slight or wide bridge vibrato, closed and open wah.
- **Strumming and picking:** up/down arpeggio, up/down brush, rasgueado, up/down pickstroke, pick scrapes.
- **Fingering:** left-hand fingering, right-hand fingering, string number.
- **Bass and percussive:** slap, pop, finger golpe, thumb golpe, dead slap.
- Also, from the bends and slides pages: bend curves (bend, release, pre-bend), and five slide variants [verified that the pages exist].

## What this means for Apophenia (proposal for Christopher)
**One music model, three views, plus a chord chart.**
- **Notation, tab and piano roll are three views of the same notes.** An edit in one shows in the others. The music core already stores spelled pitch, string/fret and exact rhythm (§6), and MusicXML 4.0 carries all three.
- **Notation view** (Dorico's Write mode as the model, MuseScore's palettes as the menu shape):
  - a duration bar always at hand;
  - everything else in categorised menus that mirror the palettes: clefs/keys/meters, accidentals, articulations, ornaments, dynamics/hairpins, lines (slur, 8va, pedal, gliss), repeats/barlines, text/tempo/rehearsal, lyrics, chord symbols and diagrams, fingering, noteheads, tremolo, grace notes, tuplets, voices.
- **Tab view:** Guitar Pro's whole effect set in the same menu pattern, linked to notation, in the instrument's tuning and capo.
- **Piano roll view** (FL Studio as the model):
  - draw/paint/slice/select tools; snap to grid and **snap to scale**; chord stamp;
  - ghost notes of other parts; background waveform; an event lane for velocity;
  - a Tools menu: quantize (reversible, plan §10.3), chop, glue, arpeggiate, strum, flam, legato, limit to range, flip, transpose, and scale levels for velocity.
- **Entry methods on the phone:** on-screen piano, fretboard, sing or play it (pitch tracker), tap the staff, MIDI keyboard over USB, and a recording through Basic Pitch.
- **Not planned:**
  - **engraving-grade page layout** (plan §10.5 already says readable, not engraving-quality, with MusicXML out to MuseScore/Dorico/Sibelius);
  - **Riff machine / Generate chord progression / Randomize**, because the app makes no suggestions (CLAUDE.md non-negotiable). These need Christopher's call if he wants any of them.
