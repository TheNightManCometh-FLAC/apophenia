      // ---- C4: score editor values ----
      notViewText: { notation: 'NOTATION', tab: 'TAB', roll: 'ROLL', chart: 'CHART' }[s.notView],
      partText: s.notView === 'tab' ? 'BASS' : (s.notView === 'roll' ? 'VOICE' : s.notPart.toUpperCase()),
      mPart: menu('part'), mNotMode: menu('notMode'), mAdd: menu('add'), mNotEdit: menu('notEdit'), mNotMore: menu('notMore'),
      mTuplet: menu('tuplet'), mVoice: menu('voice'), mFx: menu('fx'), mTuning: menu('tuning'), mRollTool: menu('rollTool'),
      mSnap: menu('snap'), mStamp: menu('stamp'), mRollShow: menu('rollShow'), mRollTools: menu('rollTools'), mLane: menu('lane'),
      vNotation: s.notView === 'notation', vTab: s.notView === 'tab', vRoll: s.notView === 'roll', vChart: s.notView === 'chart',
      showDurBar: s.notView === 'notation' || s.notView === 'tab',
      pVoice: s.notPart === 'voice', pPiano: s.notPart === 'piano', pBass: s.notPart === 'bass',
      durBar: [['1', '𝅝', 'whole'], ['2', '𝅗𝅥', 'half'], ['4', '♩', 'quarter'], ['8', '♪', 'eighth'], ['16', '𝅘𝅥𝅯', 'sixteenth'], ['32', '𝅘𝅥𝅰', 'thirty-second'], ['64', '𝅘𝅥𝅱', 'sixty-fourth']]
        .map((d) => ({ g: d[1], n: d[0] === '1' ? '1' : '1/' + d[0], name: d[2], cls: s.dur === d[0] ? 'db on' : 'db', pick: () => this.setState({ dur: d[0], menu: '' }) })),
      dotCls: s.dot ? 'db on' : 'db', dotGlyph: s.dot === 2 ? '··' : '·', cycleDot: () => this.setState({ dot: (s.dot + 1) % 3 }),
      restCls: s.rest ? 'db on' : 'db', toggleRest: () => this.setState({ rest: !s.rest }),
      tieCls: s.tie ? 'db on' : 'db', toggleTie: () => this.setState({ tie: !s.tie }),
      tupletGlyph: s.tuplet || '3', voiceN: s.voiceN,
      tbar: s.notView === 'roll'
        ? [{ k: 'TOOL', v: { draw: 'DRAW', paint: 'PAINT', del: 'DELETE', mute: 'MUTE', slice: 'SLICE', sel: 'SELECT' }[s.rollTool] + ' ▾', act: menu('rollTool') },
           { k: 'SNAP', v: '1/8 · SCALE ▾', act: menu('snap') },
           { k: 'STAMP', v: { off: 'OFF', maj: 'MAJ', min: 'MIN', dom7: '7', m7: 'm7' }[s.stamp] + ' ▾', act: menu('stamp') },
           { k: 'SHOW', v: 'GHOSTS ▾', act: menu('rollShow') },
           { k: 'TOOLS', v: 'EDIT ▾', act: menu('rollTools') }]
        : (s.notView === 'tab'
          ? [{ k: 'INPUT', v: 'FRETS ▾', act: menu('notInput') }, { k: 'EFFECTS', v: 'P.M. ▾', act: menu('fx') },
             { k: 'TUNING', v: 'DROP D ▾', act: menu('tuning') }, { k: 'EDIT', v: 'UNDO ▾', act: menu('notEdit') }, { k: 'SCORE', v: '⋯', act: menu('notMore') }]
          : [{ k: 'INPUT', v: { piano: 'PIANO', fretboard: 'FRETS', sing: 'SING', staff: 'STAFF', midi: 'MIDI', rec: 'AUDIO' }[s.notInput] + ' ▾', act: menu('notInput') },
             { k: 'MODE', v: { step: 'STEP', repitch: 'RE-PITCH', rhythm: 'RHYTHM', real: 'REAL-TIME', insert: 'INSERT' }[s.notMode] + ' ▾', act: menu('notMode') },
             { k: 'ADD', v: '14 KINDS ▾', act: menu('add') }, { k: 'EDIT', v: 'UNDO ▾', act: menu('notEdit') }, { k: 'SCORE', v: '⋯', act: menu('notMore') }]),
      scoreLine: s.notPart === 'piano' ? 'PIANO · GRAND STAFF · FINGERING SHOWN · ♩ = ' + s.bpm
        : (s.notPart === 'bass' ? 'BASS · DROP D · WRITTEN 8VA · TAB LINKED BELOW' : 'VOICE · TREBLE · 4/4 · D DORIAN · ♩ = ' + s.bpm),
      selText: s.notPart === 'piano' ? 'RH C5 · half · finger 5 · the 5th of F'
        : (s.notPart === 'bass' ? 'C2 (written C3) · quarter · A string, fret 3 · palm mute · root of C' : 'C5 · quarter · ♭7 of Dm7 · drag up or down to change it, hearing it'),
      enterText: (s.rest ? 'rest' : { '1': 'whole', '2': 'half', '4': 'quarter', '8': 'eighth', '16': '16th', '32': '32nd', '64': '64th' }[s.dur] + (s.dot === 2 ? ', double-dotted' : (s.dot ? ', dotted' : '')))
        + (s.tuplet ? ' · in a ' + s.tuplet + '-tuplet' : '') + (s.tie ? ' · tied on' : '') + ' · voice ' + s.voiceN + ' · ' + { step: 'step', repitch: 're-pitch', rhythm: 'rhythm first', real: 'real-time', insert: 'insert' }[s.notMode],
      inPiano: s.notInput === 'piano', inFret: s.notInput === 'fretboard',
      inOther: ['sing', 'staff', 'midi', 'rec'].indexOf(s.notInput) >= 0,
      inOtherText: { sing: 'Sing or play one note. The pitch tracker shows it (now: A4, +6¢) and enters it at the chosen length when it holds steady. The mic is on only while this input is picked.',
        staff: 'Tap a line or space to place the note at the chosen length; drag up or down before you let go, hearing each pitch. Accidentals follow the key; respell from Edit.',
        midi: 'No MIDI keyboard connected. Plug one in over USB: each key enters a note at the chosen length, chords included; Real-time mode records what you play against the click.',
        rec: 'Pick a recording or stem. Basic Pitch turns it into notes on the phone; they come in as a new voice you can correct. The recording itself is never changed.' }[s.notInput] || '',
      laneText: { vel: 'VELOCITY', rel: 'RELEASE', fine: 'FINE PITCH' }[s.lane],
      toolHelp: { draw: 'Draw: tap to add a note at the snap length; drag its end for length.', paint: 'Paint: drag across to lay down a run of notes at the snap length.', del: 'Delete: tap a note or drag across several.', mute: 'Mute: the note stays in the part but makes no sound.', slice: 'Slice: drag down through a note to cut it in two.', sel: 'Select: draw a box, or lasso a shape of notes.' }[s.rollTool]
        + (s.stamp !== 'off' ? ' Chord stamp is on: each note becomes a ' + { maj: 'major', min: 'minor', dom7: 'dominant 7', m7: 'minor 7' }[s.stamp] + ' chord, spelled in the key.' : ''),
      rollRows: [74, 73, 72, 71, 70, 69, 68, 67, 66, 65, 64, 63, 62].map((p, i) => {
        const nat = [62, 64, 65, 67, 69, 71, 72, 74].indexOf(p) >= 0;
        const nm = { 74: 'D5', 72: 'C5', 71: 'B4', 69: 'A4', 67: 'G4', 65: 'F4', 64: 'E4', 62: 'D4' }[p] || '';
        return { label: nm, style: 'left: 0; top: ' + (i * 14) + 'px; width: 100%; height: 14px; border-bottom: 1px solid #202124; background: ' + (nat ? '#1C1D20' : '#141517'),
          lstyle: 'position: absolute; left: 0; top: 0; width: 28px; height: 13px; font-size: 8px; font-weight: 800; line-height: 13px; padding-left: 3px; box-sizing: border-box; color: #121314; background: ' + (nat ? '#CFCCC4' : '#3A3C40') };
      }),
      rollGrid: [0, 1, 2, 3, 4, 5, 6, 7, 8].map((b) => 'left: ' + (30 + b * 42) + 'px; top: 0; width: 1px; height: 100%; background: ' + (b % 4 === 0 ? '#4A4C50' : '#26272A')),
      rollWave: shape(84, 120, 1.7).map((h, i) => 'left: ' + (30 + i * 4) + 'px; top: ' + (91 - parseInt(h, 10) / 2) + 'px; width: 2px; height: ' + h + '; background: #8F8C85; opacity: 0.14'),
      rollGhost: [[65, 0, 2], [69, 0, 2], [72, 0, 2], [64, 2, 2], [67, 2, 2], [72, 2, 2], [62, 4, 2], [67, 4, 2], [71, 4, 2], [62, 6, 2], [65, 6, 2], [69, 6, 2], [72, 6, 2]]
        .map((n) => 'left: ' + (31 + n[1] * 42) + 'px; top: ' + ((74 - n[0]) * 14 + 1) + 'px; width: ' + (n[2] * 42 - 2) + 'px; height: 12px; border: 1px solid #6A6C70; background: rgba(143, 140, 133, 0.10)'),
      rollNotes: [[69, 0, 1], [69, 1, 1], [67, 2, 1], [65, 3, 1], [65, 4, 0.5], [67, 4.5, 0.5], [69, 5, 1], [69, 6, 1], [72, 7, 1]]
        .map((n, i) => 'left: ' + (31 + n[1] * 42) + 'px; top: ' + ((74 - n[0]) * 14 + 1) + 'px; width: ' + (n[2] * 42 - 3) + 'px; height: 12px; background: #D08A55; border: 1px solid ' + (i === 8 ? '#E4E1DA' : '#121314')),
      rollVel: [[0, 88], [1, 80], [2, 96], [3, 84], [4, 70], [4.5, 76], [5, 92], [6, 88], [7, 104]].map((v, i) =>
        'left: ' + (31 + v[0] * 42) + 'px; bottom: 0; width: 4px; height: ' + Math.round(v[1] / 127 * 50) + 'px; background: ' + (i === 8 ? '#E4E1DA' : '#D08A55')),
