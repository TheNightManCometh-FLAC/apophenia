// Turns C3's notation screen into C4's score editor. Called by build-c4.js with the SVGs.
const fs = require('fs');
const path = require('path');

function must(src, from, to) {
  if (src.indexOf(from) < 0) throw new Error('anchor not found: ' + from.slice(0, 80));
  return src.replace(from, to);
}

module.exports = function (SVG, src, dir) {
  src = must(src, '<title>Apophenia · C3</title>', '<title>Apophenia · C4</title>');
  src = must(src, 'family=JetBrains+Mono:wght@400;500;700;800&amp;display=swap', 'family=JetBrains+Mono:wght@400;500;700;800&amp;family=Noto+Music&amp;display=swap');

  const css = `
.menu{max-height:590px;overflow-y:auto}
.durbar{flex:none;display:flex;overflow-x:auto;border-bottom:1px solid #2C2E31;background:#18191B}
.db{flex:none;width:46px;height:50px;display:flex;flex-direction:column;align-items:center;justify-content:center;gap:2px;border-right:1px solid #2C2E31;color:#CFCCC4;text-align:center}
.db .g{font-family:'Noto Music',serif;font-size:19px;line-height:22px;height:22px}
.db .n{font-size:8px;letter-spacing:0.06em;color:#8D8A84;font-weight:800}
.db.on{background:#24221F;box-shadow:inset 0 -2px 0 #D08A55;color:#E4E1DA}
.db.on .n{color:#D08A55}
.tbar{flex:none;display:grid;grid-template-columns:repeat(5,minmax(0,1fr));border-bottom:1px solid #2C2E31}
.tbar > button{height:46px;border-right:1px solid #2C2E31;display:flex;flex-direction:column;justify-content:center;padding:0 7px;gap:3px;font-size:10.5px;font-weight:800;color:#E4E1DA;overflow:hidden;white-space:nowrap}
.tbar > button:last-child{border-right:0}
.tbar .k{font-size:8px}
.roll{position:relative;height:182px;margin:8px 12px 0;overflow:hidden;border:1px solid #2C2E31}
.roll > div, .lane > div{position:absolute;box-sizing:border-box}
.lane{position:relative;height:56px;margin:0 12px;border:1px solid #2C2E31;border-top:0;background:#141517}
.chart{display:grid;grid-template-columns:1fr 1fr;margin:0 12px;border-left:1px solid #5A5C60}
.chart .bar{display:grid;grid-template-columns:repeat(4,minmax(0,1fr));border-right:1px solid #5A5C60;height:64px;align-items:center}
.chart .bar span{text-align:center;font-size:18px;font-weight:800;color:#E4E1DA}
.chart .bar span.sl{color:#5A5C60}
.chart .bar:last-child{border-right:3px double #8D8A84}
`;
  src = must(src, '.layer > div{padding:10px}\n', '.layer > div{padding:10px}\n' + css);

  // ---------- markup ----------
  const start = src.indexOf('<sc-if value="{{isNotation}}"');
  const end = src.indexOf('<sc-if value="{{isRec}}"');
  if (start < 0 || end < 0) throw new Error('notation block not found');
  const block = `<sc-if value="{{isNotation}}" hint-placeholder-val="{{false}}">
<div class="ttl"><button class="sq" onClick="{{goBoard}}">‹</button><b style="flex: 1">Chorus</b><button class="sq" onClick="{{mPart}}">{{partText}} ▾</button><button class="sq on" onClick="{{mNotView}}">{{notViewText}} ▾</button></div>
<sc-if value="{{showDurBar}}" hint-placeholder-val="{{true}}">
<div class="durbar">
  <sc-for list="{{durBar}}" as="d" hint-placeholder-count="7"><button class="{{d.cls}}" onClick="{{d.pick}}" aria-label="{{d.name}}"><span class="g">{{d.g}}</span><span class="n">{{d.n}}</span></button></sc-for>
  <button class="{{dotCls}}" onClick="{{cycleDot}}" aria-label="Dot: none, single, double"><span class="g">{{dotGlyph}}</span><span class="n">DOT</span></button>
  <button class="{{restCls}}" onClick="{{toggleRest}}" aria-label="Rest"><span class="g">𝄽</span><span class="n">REST</span></button>
  <button class="{{tieCls}}" onClick="{{toggleTie}}" aria-label="Tie"><span class="g">‿</span><span class="n">TIE</span></button>
  <button class="db" onClick="{{mTuplet}}" aria-label="Tuplet menu"><span class="g">{{tupletGlyph}}</span><span class="n">TUPLET ▾</span></button>
  <button class="db" onClick="{{mVoice}}" aria-label="Voice menu"><span class="g" style="font-family: 'JetBrains Mono', monospace; font-size: 14px; font-weight: 800">V{{voiceN}}</span><span class="n">VOICE ▾</span></button>
</div>
</sc-if>
<div class="tbar"><sc-for list="{{tbar}}" as="b" hint-placeholder-count="5"><button onClick="{{b.act}}"><span class="k">{{b.k}}</span>{{b.v}}</button></sc-for></div>
<div class="scroll">
  <sc-if value="{{vNotation}}" hint-placeholder-val="{{true}}">
    <div class="k" style="padding: 8px 12px 0">{{scoreLine}}</div>
    <sc-if value="{{pVoice}}" hint-placeholder-val="{{true}}">${SVG.voice}</sc-if>
    <sc-if value="{{pPiano}}" hint-placeholder-val="{{false}}">${SVG.piano}</sc-if>
    <sc-if value="{{pBass}}" hint-placeholder-val="{{false}}">${SVG.bass}</sc-if>
    <div class="kv" style="border-top: 1px solid #2C2E31"><div class="k">SELECTED</div><div>{{selText}}</div></div>
    <div class="kv"><div class="k">ENTERING</div><div>{{enterText}}</div></div>
    <sc-if value="{{inPiano}}" hint-placeholder-val="{{true}}">
      <div role="img" aria-label="Piano keyboard, two octaves from C4, C5 selected" style="position: relative; height: 120px; margin-top: 8px">
        <sc-for list="{{whiteKeys}}" as="kw" hint-placeholder-count="14"><div style="{{kw.style}}"></div></sc-for>
        <sc-for list="{{blackKeys}}" as="kb" hint-placeholder-count="10"><div style="{{kb.style}}"></div></sc-for>
        <span class="k" style="position: absolute; left: 8px; bottom: 6px; color: #5A5C60">C4</span><span class="k" style="position: absolute; left: 200px; bottom: 6px; color: #121314">C5</span>
      </div>
    </sc-if>
    <sc-if value="{{inFret}}" hint-placeholder-val="{{false}}"><div class="k" style="padding: 10px 12px 4px">YOUR BASS · DROP D · TAP A FRET</div>${SVG.fret}</sc-if>
    <sc-if value="{{inOther}}" hint-placeholder-val="{{false}}"><div class="para">{{inOtherText}}</div></sc-if>
  </sc-if>
  <sc-if value="{{vTab}}" hint-placeholder-val="{{false}}">
    <div class="k" style="padding: 8px 12px 0">BASS · DROP D (D A D G) · CAPO 0 · LINKED TO THE NOTATION</div>
    ${SVG.tab}
    <div class="kv" style="border-top: 1px solid #2C2E31"><div class="k">SELECTED</div><div>C2 · A string, fret 3 · quarter · palm mute · also on low D, fret 10</div></div>
    <div class="kv"><div class="k">ON IT</div><div>Palm mute <span class="k">TAP ✕ TO REMOVE</span></div></div>
    <div class="k" style="padding: 10px 12px 4px">FRETBOARD · TAP A FRET TO SET THE NOTE</div>
    ${SVG.fret}
  </sc-if>
  <sc-if value="{{vRoll}}" hint-placeholder-val="{{false}}">
    <div class="k" style="padding: 8px 12px 0">VOICE · GHOSTS: PIANO · BEHIND: HUM MELODY · D DORIAN ROWS LIT</div>
    <div class="roll" role="img" aria-label="Piano roll, D4 to D5, two bars. Voice notes in copper over grey outlines of the piano chords, with a faint waveform behind.">
      <sc-for list="{{rollRows}}" as="r" hint-placeholder-count="13"><div style="{{r.style}}"><span style="{{r.lstyle}}">{{r.label}}</span></div></sc-for>
      <sc-for list="{{rollWave}}" as="w" hint-placeholder-count="40"><div style="{{w}}"></div></sc-for>
      <sc-for list="{{rollGrid}}" as="g" hint-placeholder-count="9"><div style="{{g}}"></div></sc-for>
      <sc-for list="{{rollGhost}}" as="n" hint-placeholder-count="13"><div style="{{n}}"></div></sc-for>
      <sc-for list="{{rollNotes}}" as="n" hint-placeholder-count="9"><div style="{{n}}"></div></sc-for>
    </div>
    <div class="lane" role="img" aria-label="Velocity lane: one bar per note">
      <sc-for list="{{rollVel}}" as="v" hint-placeholder-count="9"><div style="{{v}}"></div></sc-for>
      <span class="k" style="position: absolute; left: 4px; top: 4px">VEL</span>
    </div>
    <div style="display: flex; gap: 6px; padding: 8px 12px"><button class="sq" onClick="{{mLane}}">LANE: {{laneText}} ▾</button><span class="k" style="align-self: center">BAR 1 · BAR 2</span></div>
    <div class="kv" style="border-top: 1px solid #2C2E31"><div class="k">SELECTED</div><div>C5 · bar 2, beat 4 · quarter · velocity 104 · ♭7 of Dm7</div></div>
    <div class="kv"><div class="k">TOOL</div><div>{{toolHelp}}</div></div>
  </sc-if>
  <sc-if value="{{vChart}}" hint-placeholder-val="{{false}}">
    <div class="k" style="padding: 10px 12px">CHORUS · 4/4 · D DORIAN · ONE SLASH PER BEAT</div>
    <div class="chart"><div class="bar"><span>F</span><span class="sl">/</span><span>C</span><span class="sl">/</span></div><div class="bar"><span>G</span><span class="sl">/</span><span>Dm7</span><span class="sl">/</span></div></div>
    <div class="kv" style="border-top: 1px solid #2C2E31; margin-top: 12px"><div class="k">SAME AS</div><div>The chord symbols over the notation. Change one here and it changes there.</div></div>
    <div class="kv"><div class="k">EDIT</div><div>Tap a beat and type a chord (Dm7, E7♯9, C/E, N.C.). It is checked as you type and spelled in the key.</div></div>
  </sc-if>
</div>
</sc-if>

`;
  src = src.slice(0, start) + block + src.slice(end);

  // board thumbnail of the score
  src = src.replace(/<svg width="200" height="54" viewBox="0 0 200 54" aria-hidden="true">[\s\S]*?<\/svg>/, SVG.thumb);
  src = must(src, "Object.assign(txt('NOT', 'Chorus melody', 'notation · vocal', '', 'notation', pos(0, 146, 222, 124)), { isText: false, isNot: true, meta: 'TREBLE · 2 BARS' })",
    "Object.assign(txt('NOT', 'Chorus', 'notation · vocal · bass', '', 'notation', pos(0, 146, 222, 124)), { isText: false, isNot: true, meta: 'VOICE · PIANO · BASS · 2 BARS' })");

  // performance check: the reference now has the corrected nine-note chorus ("what you took")
  src = must(src, '<rect x="10" y="60" width="40" height="10"></rect><rect x="52" y="60" width="40" height="10"></rect><rect x="94" y="72" width="40" height="10"></rect><rect x="136" y="84" width="40" height="10"></rect><rect x="178" y="84" width="40" height="10"></rect><rect x="220" y="72" width="40" height="10"></rect><rect x="262" y="60" width="40" height="10"></rect><rect x="304" y="42" width="40" height="10"></rect>',
    '<rect x="10" y="60" width="40" height="10"></rect><rect x="52" y="60" width="40" height="10"></rect><rect x="94" y="72" width="40" height="10"></rect><rect x="136" y="84" width="40" height="10"></rect><rect x="178" y="84" width="19" height="10"></rect><rect x="199" y="72" width="19" height="10"></rect><rect x="220" y="60" width="40" height="10"></rect><rect x="262" y="60" width="40" height="10"></rect><rect x="304" y="42" width="40" height="10"></rect>');
  src = must(src, 'points="12,66 48,65 54,65 90,64 96,92 104,86 112,80 134,79 138,89 176,88 180,89 216,90 224,77 258,77 266,65 300,64 312,54 344,53"',
    'points="12,66 48,65 54,65 90,64 96,92 104,86 112,80 134,79 138,89 176,88 178,96 186,94 190,89 196,89 204,77 216,77 222,65 258,65 264,65 300,64 312,54 344,53"');
  src = must(src, '<g fill="#8D8A84" font-size="9" font-family="JetBrains Mono, monospace"><text x="12" y="146">Low</text><text x="54" y="146">tide</text><text x="96" y="146">low</text><text x="138" y="146">tide</text><text x="180" y="146">show</text><text x="222" y="146">me</text><text x="264" y="146">what</text><text x="306" y="146">took</text></g>',
    '<g fill="#8D8A84" font-size="8.5" font-family="JetBrains Mono, monospace"><text x="12" y="146">Low</text><text x="54" y="146">tide</text><text x="96" y="146">low</text><text x="138" y="146">tide</text><text x="178" y="146">show</text><text x="200" y="146">me</text><text x="222" y="146">what</text><text x="264" y="146">you</text><text x="306" y="146">took</text></g>');
  src = must(src, 'Chorus melody (notation)', 'Chorus · voice part');
  src = must(src, "{ where: 'BAR 2 · “show me”', what: '140 MS LATE', why: 'Late entry on the downbeat, then rushed the next two notes to catch up.' }",
    "{ where: 'BAR 2 · “show me”', what: '140 MS LATE', why: 'Late on the downbeat, then rushed the two eighths to catch up.' }");

  // ---------- logic ----------
  src = must(src, "refView: 'guitar', notView: 'notation', notInput: 'piano', recKind: 'quick',",
    "refView: 'guitar', notView: 'notation', notInput: 'piano', recKind: 'quick', notPart: 'voice', dur: '4', dot: 0, rest: false, tie: false,\n      notMode: 'step', voiceN: 1, tuplet: '', rollTool: 'draw', stamp: 'off', lane: 'vel',");

  const menus = fs.readFileSync(path.join(__dirname, 'menus-c4.js'), 'utf8');
  const oldMenus = /      notView: \{[^\n]*\n      notInput: \{[^\n]*\n      dur: \{[^\n]*\n/;
  if (!oldMenus.test(src)) throw new Error('menu lines not found');
  src = src.replace(oldMenus, '');
  src = must(src, '    const m = M[s.menu];', menus + '    const m = M[s.menu];');

  const vals = fs.readFileSync(path.join(__dirname, 'vals-c4.js'), 'utf8');
  const oldVals = /      notViewText: [^\n]*\n      notInputText: [^\n]*\n/;
  if (!oldVals.test(src)) throw new Error('value lines not found');
  src = src.replace(oldVals, vals);
  src = must(src, "micText: (s.screen === 'tuner' || s.listening) ? 'LISTENING' : 'MIC OFF',",
    "micText: (s.screen === 'tuner' || s.listening || singing) ? 'LISTENING' : 'MIC OFF',");
  src = must(src, "micDot: (s.screen === 'tuner' || s.listening) ?", "micDot: (s.screen === 'tuner' || s.listening || singing) ?");

  // AI is the fifth mode in the bottom navigation (Christopher, 21 Oct 2026), not a section of Tools
  src = must(src, '.nav-wrap{flex:none;display:grid;grid-template-columns:repeat(4,minmax(0,1fr));', '.nav-wrap{flex:none;display:grid;grid-template-columns:repeat(5,minmax(0,1fr));');
  src = must(src, '  <button class="{{navLearn}}" onClick="{{goLearn}}"><i>4</i>LEARN</button>\n',
    '  <button class="{{navLearn}}" onClick="{{goLearn}}"><i>4</i>LEARN</button>\n  <button class="{{navAI}}" onClick="{{goAI}}"><i>5</i>AI</button>\n');
  src = must(src, '<div class="ttl"><button class="dd" onClick="{{mToolsCat}}">{{toolsCatText}}</button><span class="k">{{toolsCatNote}}</span></div>',
    '<div class="ttl"><b>{{toolsCatText}}</b><span class="k">{{toolsCatNote}}</span></div>');
  src = must(src, "toolsCatText: s.tools === 'ai' ? 'AI TOOLS' : 'PRACTICE TOOLS'", "toolsCatText: s.tools === 'ai' ? 'AI' : 'Tools'");
  src = must(src, ", ['AI tools', '›', go('tools', { tools: 'ai' })]] },", '] },');
  src = must(src, "navTools: nav(['tools', 'metro', 'tuner', 'check']),",
    "navTools: (['tools', 'metro', 'tuner'].indexOf(s.screen) >= 0 && s.tools !== 'ai') ? 'nav on' : 'nav',\n      navAI: ((s.screen === 'tools' && s.tools === 'ai') || s.screen === 'check') ? 'nav on' : 'nav', goAI: go('tools', { tools: 'ai' }),");
  src = must(src, "goTools: go('tools'), goToolsAI:", "goTools: go('tools', { tools: 'practice' }), goToolsAI:");

  fs.writeFileSync(path.join(dir, 'Console4.dc.html'), src);
  console.log('wrote Console4.dc.html', src.length, 'bytes');
};
