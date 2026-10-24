    // ---- C4: score editor menus ----
    const chk = (c) => c ? '✓' : '';
    const tTop = s.notView === 'roll' ? 182 : 232;
    const at = (side) => side + '; top: ' + tTop + 'px';
    const c1 = at('left: 8px'), c2 = at('left: 56px'), c3 = at('left: 110px'), c4 = at('right: 8px');
    const singing = s.screen === 'notation' && s.notView === 'notation' && s.notInput === 'sing';
    const partPick = (p) => set({ notPart: p, notInput: p === 'bass' ? 'fretboard' : 'piano' });
    M.part = { pos: 'right: 96px; top: 136px', title: 'PART · ONE SCORE', items: s.notView === 'tab'
      ? [['Bass', '✓ DROP D', set({})], ['Voice', 'NO TAB · NOT FRETTED', set({})], ['Piano', 'NO TAB · NOT FRETTED', set({})], ['Add a guitar part', '+', set({})]]
      : [['Voice', chk(s.notPart === 'voice') + ' TREBLE · LYRICS', partPick('voice')], ['Piano', chk(s.notPart === 'piano') + ' GRAND STAFF', partPick('piano')],
         ['Bass', chk(s.notPart === 'bass') + ' DROP D · TAB', partPick('bass')], ['Add an instrument', '+ ›', set({})]] };
    M.notView = { pos: 'right: 8px; top: 136px', title: 'SHOW THE SAME NOTES AS', items: [
      ['Notation', chk(s.notView === 'notation'), set({ notView: 'notation' })],
      ['Tab', chk(s.notView === 'tab') + ' FRETTED PARTS', set({ notView: 'tab', notInput: 'fretboard' })],
      ['Piano roll', chk(s.notView === 'roll'), set({ notView: 'roll', notPart: 'voice' })],
      ['Chord chart', chk(s.notView === 'chart'), set({ notView: 'chart' })]] };
    M.notInput = { pos: c1, title: 'ENTER NOTES WITH', items: [
      ['Piano keys', chk(s.notInput === 'piano'), set({ notInput: 'piano' })],
      ['Fretboard', chk(s.notInput === 'fretboard') + ' YOUR TUNING', set({ notInput: 'fretboard' })],
      ['Sing or play it', chk(s.notInput === 'sing') + ' PITCH TRACKER', set({ notInput: 'sing' })],
      ['Tap on the staff', chk(s.notInput === 'staff'), set({ notInput: 'staff' })],
      ['MIDI keyboard', chk(s.notInput === 'midi') + ' USB', set({ notInput: 'midi' })],
      ['From a recording', chk(s.notInput === 'rec') + ' BASIC PITCH ›', set({ notInput: 'rec' })]] };
    M.notMode = { pos: c2, title: 'ENTRY MODE', items: [
      ['Step', chk(s.notMode === 'step') + ' LENGTH, THEN PITCH', set({ notMode: 'step' })],
      ['Re-pitch', chk(s.notMode === 'repitch') + ' KEEP RHYTHM, NEW PITCHES', set({ notMode: 'repitch' })],
      ['Rhythm first', chk(s.notMode === 'rhythm') + ' TAP RHYTHM, PITCH LATER', set({ notMode: 'rhythm' })],
      ['Real-time', chk(s.notMode === 'real') + ' PLAY ALONG TO THE CLICK', set({ notMode: 'real' })],
      ['Insert', chk(s.notMode === 'insert') + ' PUSHES LATER NOTES ON', set({ notMode: 'insert' })]] };
    const ADD = [
      ['clefs', 'Clefs · keys · meters', [['Clef', 'TREBLE · BASS · ALTO · TENOR · 8VB · PERC ›'], ['Key signature', '15 KEYS · ANY MODE ›'], ['Key change here'], ['Time signature', '4/4 · 6/8 · 7/8 = 2+2+3 ›'], ['Meter change here'], ['Pickup bar', 'ANACRUSIS']]],
      ['acc', 'Accidentals', [['♯ Sharp'], ['♭ Flat'], ['♮ Natural'], ['𝄪 Double sharp'], ['𝄫 Double flat'], ['Courtesy accidental', '(♮)'], ['Respell', 'C♯ ⇄ D♭']]],
      ['art', 'Articulations', [['Staccato', '·'], ['Staccatissimo'], ['Accent', '>'], ['Tenuto', '–'], ['Marcato', '^'], ['Portato'], ['Fermata', '𝄐'], ['Breath mark', ','], ['Caesura', '//']]],
      ['orn', 'Ornaments', [['Trill', 'tr'], ['Trill with line'], ['Upper mordent'], ['Lower mordent'], ['Turn'], ['Inverted turn']]],
      ['dyn', 'Dynamics · hairpins', [['ppp to fff', 'PICK ›'], ['sfz · fp · sfp'], ['Crescendo hairpin', '<'], ['Diminuendo hairpin', '>'], ['cresc. · dim. as text'], ['subito']]],
      ['lines', 'Lines', [['Slur'], ['8va · 8vb · 15ma'], ['Pedal', 'Ped. ✱ OR LINE'], ['Glissando'], ['Arpeggio (rolled chord)'], ['Trill line'], ['Let ring'], ['Text line']]],
      ['rep', 'Repeats · barlines', [['Barline', 'DOUBLE · FINAL · DASHED'], ['Start · end repeat'], ['Voltas', '1. 2.'], ['Segno · Coda'], ['D.C. · D.S. al Coda · Fine'], ['Repeat-bar sign']]],
      ['text', 'Text · tempo · rehearsal', [['Tempo mark', '♩ = 96 · ANDANTE'], ['Tempo change', 'rit. · accel. · a tempo'], ['Expression', 'dolce'], ['Technique', 'pizz. · mute'], ['Rehearsal mark', 'A B C'], ['Staff text']]],
      ['lyr', 'Lyrics', [['Lyrics', 'VERSE 1 ›'], ['Add a verse', '2, 3…'], ['Hyphen', 'SYLLABLE BREAK'], ['Melisma line', 'ONE SYLLABLE, MANY NOTES'], ['Elision', '‿'], ['From the words window', 'LOW TIDE · WORDS']]],
      ['chd', 'Chord symbols · diagrams', [['Chord symbol', 'TYPE IT: Dm7, E7♯9, C/E'], ['Fretboard diagram', 'DOTS · YOUR TUNING'], ['Slash chord · polychord'], ['N.C.', 'NO CHORD'], ['From the chord chart', 'VERSE CHANGES']]],
      ['fing', 'Fingering', [['Piano', '1–5'], ['Left hand, strings', '1–4 · T'], ['Right hand, strings', 'p i m a'], ['String number', '① ② ③ ④'], ['Position', 'V · VII']]],
      ['head', 'Noteheads', [['Normal'], ['Dead note', '×'], ['Harmonic', '◇'], ['Slash', 'RHYTHM ONLY'], ['Ghost', '( )'], ['Cue size', 'SMALL']]],
      ['trem', 'Tremolo', [['One stroke'], ['Two strokes'], ['Three strokes'], ['Between two notes'], ['Buzz roll', 'z']]],
      ['grace', 'Grace notes', [['Acciaccatura', 'SLASHED'], ['Appoggiatura', 'TAKES TIME FROM THE NOTE'], ['Before the beat'], ['On the beat'], ['After the note']]]];
    M.add = { pos: c3, title: 'ADD TO THE SELECTION', items: ADD.map((a) => [a[1], a[2].length + ' ›', set({ menu: 'add_' + a[0] })]) };
    ADD.forEach((a) => { M['add_' + a[0]] = { pos: c3, title: 'ADD › ' + a[1].toUpperCase(),
      items: [['‹ All categories', '', set({ menu: 'add' })]].concat(a[2].map((it) => [it[0], it[1] || '', set({})])) }; });
    const FX = [
      ['dyn', 'Dynamics · tone', [['Ghost note', '( )'], ['Accent'], ['Heavy accent'], ['Staccato'], ['Let ring'], ['Palm mute', '✓ P.M.'], ['Dead note', '×'], ['Fade in · fade out'], ['Volume swell'], ['Sustain pedal']]],
      ['touch', 'Touch · slides', [['Hammer-on · pull-off', 'H · P'], ['Legato slide'], ['Shift slide'], ['Slide in from below', '/'], ['Slide in from above'], ['Slide out down · up'], ['Left-hand tapping'], ['Tapping', 'T']]],
      ['bend', 'Bends · whammy', [['Bend', '½ · FULL'], ['Bend and release'], ['Pre-bend'], ['Release'], ['Bend curve', 'DRAW IT ›'], ['Whammy bar', 'DIVE · DIP ›']]],
      ['harm', 'Harmonics', [['Natural harmonic', 'N.H.'], ['Artificial harmonic', 'A.H.']]],
      ['orn', 'Ornaments', [['Grace note before the beat'], ['Grace note on the beat'], ['Tremolo picking'], ['Upper mordent'], ['Lower mordent'], ['Turn'], ['Inverted turn'], ['Trill']]],
      ['vib', 'Vibrato · wah', [['Slight vibrato, left hand'], ['Wide vibrato, left hand'], ['Slight vibrato, bar'], ['Wide vibrato, bar'], ['Wah closed', '+'], ['Wah open', 'o']]],
      ['strum', 'Strumming · picking', [['Arpeggio up · down'], ['Brush up · down'], ['Rasgueado'], ['Pickstroke up · down'], ['Pick scrape']]],
      ['fing', 'Fingering', [['Left-hand fingering', '1–4 · T'], ['Right-hand fingering', 'p i m a'], ['String number']]],
      ['bass', 'Bass · percussive', [['Slap', 'S'], ['Pop', 'P'], ['Finger golpe'], ['Thumb golpe'], ['Dead slap']]]];
    M.fx = { pos: c2, title: 'EFFECTS ON THE SELECTION', items: FX.map((a) => [a[1], a[2].length + ' ›', set({ menu: 'fx_' + a[0] })]) };
    FX.forEach((a) => { M['fx_' + a[0]] = { pos: c2, title: 'EFFECTS › ' + a[1].toUpperCase(),
      items: [['‹ All effects', '', set({ menu: 'fx' })]].concat(a[2].map((it) => [it[0], it[1] || '', set({})])) }; });
    M.tuning = { pos: c3, title: 'INSTRUMENT', items: [['Bass · drop D', '✓ D A D G', set({})], ['Bass · standard', 'E A D G', set({})], ['Bass · D standard', 'D G C F', set({})], ['5-string bass', 'B E A D G', set({})], ['Capo', '0 ›', set({})], ['Show', 'TAB · TAB + NOTATION ›', set({})]] };
    M.notEdit = { pos: c4, title: 'EDIT', items: [['Undo', 'ADD mp', set({})], ['Redo', '', set({})], ['Cut · copy · paste', '', set({})], ['Select', 'NOTE · BAR · RANGE · ALL ›', set({})], ['Transpose', 'INTERVAL · KEY · DIATONIC ›', set({})], ['Respell', 'C♯ ⇄ D♭ · WHOLE KEY ›', set({})], ['Up · down an octave', '', set({})], ['Voices', 'SWAP 1 ⇄ 2 · MERGE ›', set({})], ['Re-beam', 'BY BEAT', set({})], ['Explode · reduce', 'CHORDS ⇄ STAVES', set({})], ['Delete', '', set({})]] };
    M.notMore = { pos: c4, title: 'SCORE', items: [['Score setup', 'PARTS · CLEFS · TRANSPOSING ›', set({})], ['Play', 'SOUNDING PITCH · PIANO SOUND', set({})], ['Follow playback', 'SCROLLS WITH THE NOTES', set({})], ['Text size', 'M ›', set({})], ['Export', 'MUSICXML · MIDI · PDF ›', set({})]] };
    M.tuplet = { pos: 'right: 54px; top: 186px', title: 'TUPLET', items: [['Triplet', '3:2', set({ tuplet: '3' })], ['Duplet', '2:3', set({ tuplet: '2' })], ['Quintuplet', '5:4', set({ tuplet: '5' })], ['Sextuplet', '6:4', set({ tuplet: '6' })], ['Septuplet', '7:4', set({ tuplet: '7' })], ['Any ratio', 'N:M ›', set({})], ['Nested', 'TUPLET IN A TUPLET ›', set({})], ['None', chk(s.tuplet === ''), set({ tuplet: '' })]] };
    M.voice = { pos: 'right: 8px; top: 186px', title: 'VOICE', items: [['Voice 1', chk(s.voiceN === 1) + ' STEMS UP', set({ voiceN: 1 })], ['Voice 2', chk(s.voiceN === 2) + ' STEMS DOWN', set({ voiceN: 2 })], ['Voice 3', chk(s.voiceN === 3), set({ voiceN: 3 })], ['Voice 4', chk(s.voiceN === 4), set({ voiceN: 4 })], ['Swap 1 ⇄ 2', '', set({})]] };
    const TOOLS = { draw: ['Draw', 'TAP TO ADD · DRAG FOR LENGTH'], paint: ['Paint', 'DRAG TO PAINT A RUN'], del: ['Delete', 'TAP OR DRAG ACROSS'], mute: ['Mute', 'KEEPS THE NOTE, SILENT'], slice: ['Slice', 'CUT A NOTE IN TWO'], sel: ['Select', 'BOX · LASSO'] };
    M.rollTool = { pos: c1, title: 'TOOL', items: Object.keys(TOOLS).map((k) => [TOOLS[k][0], chk(s.rollTool === k) + ' ' + TOOLS[k][1], set({ rollTool: k })]) };
    M.snap = { pos: c2, title: 'SNAP', items: [['Bar', '', set({})], ['♩ 1/4', '', set({})], ['♪ 1/8', '✓', set({})], ['1/16', '', set({})], ['1/32', '', set({})], ['Triplets', '1/8T · 1/16T', set({})], ['Off', 'FREE', set({})], ['Snap to scale', '✓ D DORIAN ›', set({})]] };
    const STAMPS = { off: 'Off', maj: 'Major', min: 'Minor', dom7: 'Dominant 7', m7: 'Minor 7' };
    M.stamp = { pos: c3, title: 'CHORD STAMP · ONE TAP, WHOLE CHORD', items: Object.keys(STAMPS).map((k) => [STAMPS[k], chk(s.stamp === k), set({ stamp: k })])
      .concat([['Any chord quality', '41 IN THE CATALOG ›', set({})], ['A scale', 'STAMP ALL ITS NOTES ›', set({})]]) };
    M.rollShow = { pos: c4, title: 'SHOW', items: [['Ghost notes', '✓ PIANO ›', set({})], ['Background waveform', '✓ HUM MELODY ›', set({})], ['Scale rows', '✓ D DORIAN', set({})], ['Key labels', 'NATURALS ›', set({})], ['Note names on notes', '', set({})]] };
    M.rollTools = { pos: c4, title: 'TOOLS · ON THE SELECTION', items: [['Quantize', 'REVERSIBLE · STRENGTH ›', set({})], ['Quantize start times only', '', set({})], ['Chop', 'EQUAL PARTS ›', set({})], ['Glue', 'JOIN SAME-PITCH NOTES', set({})], ['Legato', 'FILL THE GAPS', set({})], ['Arpeggiate', 'CHORD → PATTERN ›', set({})], ['Strum', 'OFFSET CHORD NOTES ›', set({})], ['Flam', '', set({})], ['Limit', 'FOLD INTO A RANGE ›', set({})], ['Flip', 'MIRROR PITCH OR TIME', set({})], ['Transpose', '± SEMITONE · OCTAVE', set({})], ['Scale levels', 'VELOCITY: SCALE · SQUEEZE', set({})], ['Cut · copy · paste · duplicate', '', set({})]] };
    M.lane = { pos: 'left: 12px; bottom: 166px', title: 'EVENT LANE', items: [['Velocity', chk(s.lane === 'vel'), set({ lane: 'vel' })], ['Release velocity', chk(s.lane === 'rel'), set({ lane: 'rel' })], ['Fine pitch', chk(s.lane === 'fine') + ' CENTS', set({ lane: 'fine' })]] };
