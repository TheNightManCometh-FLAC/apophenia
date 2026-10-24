// Builds Console4.dc.html from Console3.dc.html: the notation screen becomes a full
// score editor with notation / tab / piano roll / chord chart views of one set of notes.
// Every staff position is computed from the pitch name, not placed by hand.
const fs = require('fs');
const path = require('path');
const dir = path.join(__dirname, '..', 'project');
let src = fs.readFileSync(path.join(dir, 'Console3.dc.html'), 'utf8');

const LET = 'CDEFGAB';
const dia = (n) => LET.indexOf(n[0]) + 7 * parseInt(n.slice(-1), 10); // E4 = 30, G2 = 18
const INK = '#E4E1DA', DIM = '#5A5C60', ACC = '#D08A55', BG = '#121314';
const MUSIC = "'Noto Music', serif";
const MONO = "'JetBrains Mono', monospace";
const head = (x, y, open, rx, ry, col) => open
  ? `<ellipse cx="${x}" cy="${y}" rx="${rx}" ry="${ry}" transform="rotate(-20 ${x} ${y})" fill="${BG}" stroke="${col}" stroke-width="1.6"></ellipse>`
  : `<ellipse cx="${x}" cy="${y}" rx="${rx}" ry="${ry}" transform="rotate(-20 ${x} ${y})" fill="${col}"></ellipse>`;
const line = (x1, y1, x2, y2, col, w) => `<line x1="${x1}" y1="${y1}" x2="${x2}" y2="${y2}" stroke="${col}" stroke-width="${w || 1}"></line>`;
const text = (x, y, t, o) => { o = o || {};
  return `<text x="${x}" y="${y}" font-size="${o.size || 11}" fill="${o.fill || INK}" font-family="${o.font || MONO}"${o.weight ? ` font-weight="${o.weight}"` : ''}${o.anchor ? ` text-anchor="${o.anchor}"` : ''}${o.style ? ` font-style="${o.style}"` : ''}>${t}</text>`; };
const staff = (top, gap, x1, x2) => [0, 1, 2, 3, 4].map((i) => line(x1, top + i * gap, x2, top + i * gap, DIM)).join('');

// ---- Voice part: treble, step 6 px (line gap 12), bottom line E4 at y 88 ----
function voiceSvg() {
  const y = (n) => 88 - (dia(n) - 30) * 6;
  const notes = [ // name, x, duration
    ['A4', 88, 'q'], ['A4', 120, 'q'], ['G4', 152, 'q'], ['F4', 184, 'q'],
    ['F4', 232, 'e'], ['G4', 256, 'e'], ['A4', 288, 'q'], ['A4', 320, 'q'], ['C5', 352, 'q']];
  const lyr = ['Low', 'tide', 'low', 'tide', 'show', 'me', 'what', 'you', 'took'];
  let s = `<svg width="390" height="132" viewBox="0 0 390 132" role="img" aria-label="Chorus, voice part, treble clef, 4/4. Bar 1: A4 A4 G4 F4 quarters. Bar 2: F4 G4 eighths, A4 A4 C5 quarters. Chords F, C, G, Dm7. mp, crescendo in bar 2." style="display: block">`;
  s += staff(40, 12, 8, 382) + line(210, 40, 210, 88, DIM) + line(381, 40, 381, 88, DIM, 2);
  s += text(10, 86, '𝄞', { size: 52, fill: '#CFCCC4', font: MUSIC });
  s += text(52, 62, '4', { size: 20, weight: 800, fill: '#CFCCC4' }) + text(52, 84, '4', { size: 20, weight: 800, fill: '#CFCCC4' });
  // chord symbols above, at the beat they fall on
  [['F', 88], ['C', 152], ['G', 232], ['Dm7', 288]].forEach((c) => { s += text(c[1] - 6, 16, c[0], { size: 12, weight: 800 }); });
  // dynamics above the staff (vocal music keeps them clear of the lyrics)
  s += text(64, 32, 'mp', { size: 15, weight: 700, style: 'italic', font: 'serif', fill: '#CFCCC4' });
  s += line(232, 28, 286, 24, '#CFCCC4', 1.2) + line(232, 28, 286, 32, '#CFCCC4', 1.2);
  notes.forEach((n, i) => {
    const yy = y(n[0]); const col = i === 8 ? ACC : INK;
    s += head(n[1], yy, false, 6.5, 5, col);
    if (n[2] === 'q') s += dia(n[0]) >= 34 ? line(n[1] - 6, yy, n[1] - 6, yy + 36, col, 1.5) : line(n[1] + 6, yy, n[1] + 6, yy - 36, col, 1.5);
    s += text(n[1], 118, lyr[i], { size: 11, fill: '#AEABA4', anchor: 'middle' });
  });
  // beamed eighths F4 G4, stems up
  const yF = y('F4'), yG = y('G4');
  s += line(238, yF, 238, 44, INK, 1.5) + line(262, yG, 262, 40, INK, 1.5);
  s += `<polygon points="237.3,44 262.7,40 262.7,45 237.3,49" fill="${INK}"></polygon>`;
  return s + '</svg>';
}

// ---- Piano part: grand staff, step 4 px (gap 8). Treble E4 at y 76, bass G2 at y 146 ----
function pianoSvg() {
  const yt = (n) => 76 - (dia(n) - 30) * 4;
  const yb = (n) => 146 - (dia(n) - 18) * 4;
  const rh = [ // notes low→high, fingers low→high, x, chord symbol
    [['F4', 'A4', 'C5'], ['1', '3', '5'], 112, 'F'],
    [['E4', 'G4', 'C5'], ['1', '2', '5'], 176, 'C'],
    [['D4', 'G4', 'B4'], ['1', '3', '5'], 256, 'G'],
    [['D4', 'F4', 'A4', 'C5'], ['1', '2', '3', '5'], 320, 'Dm7']];
  const lh = [[['F2', 'F3'], 112], [['C2', 'C3'], 176], [['G2', 'G3'], 256], [['D2', 'D3'], 320]];
  let s = `<svg width="390" height="178" viewBox="0 0 390 178" role="img" aria-label="Chorus, piano part, grand staff, half notes. Right hand: F A C fingered 1 3 5, E G C fingered 1 2 5, D G B fingered 1 3 5, D F A C fingered 1 2 3 5. Left hand octaves F, C, G, D fingered 5 and 1." style="display: block">`;
  s += staff(44, 8, 14, 382) + staff(114, 8, 14, 382);
  s += line(14, 44, 14, 146, DIM) + line(210, 44, 210, 146, DIM) + line(381, 44, 381, 146, DIM, 2);
  s += `<path d="M10,44 C2,60 12,82 4,95 C12,108 2,130 10,146" fill="none" stroke="${DIM}" stroke-width="2"></path>`;
  s += text(16, 82, '𝄞', { size: 36, fill: '#CFCCC4', font: MUSIC }) + text(18, 140, '𝄢', { size: 30, fill: '#CFCCC4', font: MUSIC });
  [[61, 76], [131, 146]].forEach((p) => { s += text(54, p[0], '4', { size: 15, weight: 800, fill: '#CFCCC4' }) + text(54, p[1], '4', { size: 15, weight: 800, fill: '#CFCCC4' }); });
  rh.forEach((c, ci) => {
    const x = c[2]; const ys = c[0].map(yt);
    s += text(x - 5, 18, c[3], { size: 12, weight: 800 });
    c[0].forEach((n, i) => {
      const sel = ci === 0 && n === 'C5'; const col = sel ? ACC : INK;
      s += head(x, ys[i], true, 5.2, 3.8, col);
      s += text(x - 9, ys[i] + 3.5, c[1][i], { size: 9, weight: 800, fill: ACC, anchor: 'end' });
    });
    s += line(x + 5, Math.max.apply(null, ys), x + 5, Math.min.apply(null, ys) - 28, INK, 1.3);
  });
  lh.forEach((o) => {
    const x = o[1]; const ys = o[0].map(yb);
    o[0].forEach((n, i) => {
      const d = dia(n);
      if (d <= 16) for (let ld = 16; ld >= d; ld -= 2) s += line(x - 9, yb('E2') + (16 - ld) * 4, x + 9, yb('E2') + (16 - ld) * 4, DIM, 1.2);
      s += head(x, ys[i], true, 5.2, 3.8, INK);
      s += text(x - 9, ys[i] + 3.5, i === 0 ? '5' : '1', { size: 9, weight: 800, fill: '#8D8A84', anchor: 'end' });
    });
    s += line(x + 5, Math.max.apply(null, ys), x + 5, Math.min.apply(null, ys) - 28, INK, 1.3);
  });
  s += text(4, 172, 'FINGERING: RH COPPER · LH GREY', { size: 8, fill: '#8D8A84' });
  return s + '</svg>';
}

// ---- Bass part (sounding F1 C2 C2 | G1 D1 D1 E1), written an octave up in bass clef ----
// tab in drop D, strings top→bottom G2 D2 A1 D1
const BASS = [ // written, x, dur, string (0=G … 3=low D), fret
  ['F2', 90, 'h', 3, 3], ['C3', 150, 'q', 2, 3], ['C3', 182, 'q', 2, 3],
  ['G2', 232, 'h', 3, 5], ['D2', 292, 'q', 3, 0], ['D2', 326, 'e', 3, 0], ['E2', 352, 'e', 3, 2]];
const SEL_BASS = 2;
function tabNum(x, y, t, col) {
  return `<rect x="${x - 6}" y="${y - 6}" width="12" height="12" fill="${BG}"></rect>` + text(x, y + 4, t, { size: 11, weight: 800, fill: col, anchor: 'middle' });
}
function bassSvg() {
  const yb = (n) => 72 - (dia(n) - 18) * 4;
  const tabY = [110, 122, 134, 146];
  let s = `<svg width="390" height="160" viewBox="0 0 390 160" role="img" aria-label="Chorus, bass part in drop D, bass clef sounding an octave lower, with tab linked below. Bar 1: F half (low D string fret 3), C, C quarters (A string fret 3, palm muted). Bar 2: G half (low D string fret 5, slide in from below), D quarter (open), D and E eighths, hammer-on 0 to 2." style="display: block">`;
  s += staff(40, 8, 8, 382) + line(210, 40, 210, 72, DIM) + line(381, 40, 381, 72, DIM, 2);
  s += text(10, 74, '𝄢', { size: 30, fill: '#CFCCC4', font: MUSIC });
  s += text(48, 55, '4', { size: 15, weight: 800, fill: '#CFCCC4' }) + text(48, 71, '4', { size: 15, weight: 800, fill: '#CFCCC4' });
  s += text(10, 30, 'BASS · SOUNDS 8VB', { size: 8, fill: '#8D8A84' });
  BASS.forEach((b, i) => {
    const x = b[1], d = dia(b[0]), yy = yb(b[0]); const col = i === SEL_BASS ? ACC : INK;
    if (d <= 16) for (let ld = 16; ld >= d; ld -= 2) s += line(x - 9, yb('E2') + (16 - ld) * 4, x + 9, yb('E2') + (16 - ld) * 4, DIM, 1.2);
    s += head(x, yy, b[2] === 'h', 5.2, 3.8, col);
    if (b[2] !== 'e') s += line(x + 5, yy, x + 5, yy - 28, col, 1.3);
  });
  // beamed eighths D2 E2
  s += line(331, yb('D2'), 331, 52, INK, 1.3) + line(357, yb('E2'), 357, 50, INK, 1.3);
  s += `<polygon points="330.4,52 357.6,50 357.6,54 330.4,56" fill="${INK}"></polygon>`;
  // tab staff
  tabY.forEach((ty) => { s += line(8, ty, 382, ty, DIM); });
  s += line(8, 110, 8, 146, DIM) + line(210, 110, 210, 146, DIM) + line(381, 110, 381, 146, DIM, 2);
  s += text(18, 118, 'T', { size: 10, weight: 800, fill: '#8D8A84', anchor: 'middle' }) + text(18, 131, 'A', { size: 10, weight: 800, fill: '#8D8A84', anchor: 'middle' }) + text(18, 144, 'B', { size: 10, weight: 800, fill: '#8D8A84', anchor: 'middle' });
  BASS.forEach((b, i) => { s += tabNum(b[1], tabY[b[3]], String(b[4]), i === SEL_BASS ? ACC : INK); });
  return s + '</svg>';
}

// ---- Tab view: the same bass notes, larger, with Guitar Pro style effects and rhythm stems ----
function tabSvg() {
  const tabY = [50, 66, 82, 98];
  const X = [60, 120, 160, 236, 296, 330, 356];
  let s = `<svg width="390" height="150" viewBox="0 0 390 150" role="img" aria-label="Bass tab, drop D. Bar 1: low D string 3 (half), A string 3, A string 3 palm muted (quarters). Bar 2: low D 5 slid into from below (half), open D (quarter), open D then 2 with a hammer-on (eighths)." style="display: block">`;
  ['G', 'D', 'A', 'D'].forEach((n, i) => { s += text(14, tabY[i] + 4, n, { size: 10, weight: 800, fill: '#8D8A84', anchor: 'middle' }); });
  tabY.forEach((ty) => { s += line(28, ty, 382, ty, DIM); });
  s += line(28, 50, 28, 98, DIM) + line(206, 50, 206, 98, DIM) + line(381, 50, 381, 98, DIM, 2);
  s += text(31, 44, '1', { size: 8, fill: '#8D8A84' }) + text(209, 44, '2', { size: 8, fill: '#8D8A84' });
  // effects above the staff
  s += text(146, 30, 'P.M.', { size: 9, weight: 800, fill: '#CFCCC4' }) + `<line x1="172" y1="27" x2="184" y2="27" stroke="#CFCCC4" stroke-dasharray="2 2"></line>` + line(184, 27, 184, 32, '#CFCCC4');
  s += `<path d="M330,40 Q343,30 356,40" fill="none" stroke="#CFCCC4" stroke-width="1.2"></path>` + text(343, 30, 'H', { size: 9, weight: 800, fill: '#CFCCC4', anchor: 'middle' });
  s += line(219, 104, 228, 99, '#CFCCC4', 1.4); // slide in from below, into the 5
  BASS.forEach((b, i) => { s += tabNum(X[i], tabY[b[3]], String(b[4]), i === SEL_BASS ? ACC : INK); });
  // rhythm under the tab (half = short stem, quarter = full stem, eighths beamed)
  BASS.forEach((b, i) => { s += line(X[i], 112, X[i], b[2] === 'h' ? 124 : 136, '#AEABA4', 1.3); });
  s += `<rect x="329.4" y="133" width="27.2" height="3.5" fill="#AEABA4"></rect>`;
  return s + '</svg>';
}

// ---- Fretboard input panel: 4-string bass, drop D, frets 0–12 ----
function fretSvg() {
  const sy = [12, 28, 44, 60];
  const fx = (f) => 26 + f * 28;
  let s = `<svg width="366" height="86" viewBox="0 0 366 86" role="img" aria-label="Bass fretboard in drop D. Selected: A string fret 3, C2. Also playable at low D string fret 10." style="display: block; margin: 0 12px">`;
  ['G', 'D', 'A', 'D'].forEach((n, i) => { s += text(8, sy[i] + 4, n, { size: 10, weight: 800, fill: '#8D8A84', anchor: 'middle' }); });
  for (let f = 0; f <= 12; f++) s += line(fx(f), 12, fx(f), 60, f === 0 ? '#CFCCC4' : DIM, f === 0 ? 3 : 1);
  sy.forEach((y, i) => { s += line(fx(0), y, fx(12), y, '#8F8C85', 1 + i * 0.4); });
  [3, 5, 7, 9].forEach((f) => { s += `<circle cx="${fx(f) - 14}" cy="36" r="3" fill="#3A3C40"></circle>`; });
  s += `<circle cx="${fx(12) - 14}" cy="20" r="3" fill="#3A3C40"></circle><circle cx="${fx(12) - 14}" cy="52" r="3" fill="#3A3C40"></circle>`;
  s += `<circle cx="${fx(3) - 14}" cy="44" r="8" fill="${ACC}"></circle>` + text(fx(3) - 14, 47.5, 'C', { size: 9, weight: 800, fill: BG, anchor: 'middle' });
  s += `<circle cx="${fx(10) - 14}" cy="60" r="8" fill="none" stroke="${ACC}" stroke-width="1.5"></circle>` + text(fx(10) - 14, 63.5, 'C', { size: 9, weight: 800, fill: ACC, anchor: 'middle' });
  [0, 3, 5, 7, 9, 12].forEach((f) => { s += text(f === 0 ? fx(0) : fx(f) - 14, 80, String(f), { size: 9, fill: '#8D8A84', anchor: 'middle' }); });
  return s + '</svg>';
}

// ---- Board thumbnail of the voice line (step 4, bottom line E4 at y 42) ----
function thumbSvg() {
  const y = (n) => 42 - (dia(n) - 30) * 4;
  const ns = [['A4', 34], ['A4', 52], ['G4', 70], ['F4', 88], ['F4', 124], ['G4', 138], ['A4', 154], ['A4', 170], ['C5', 186]];
  let s = `<svg width="200" height="54" viewBox="0 0 200 54" aria-hidden="true"><g stroke="#4A4C50">`;
  [10, 18, 26, 34, 42].forEach((yy) => { s += `<line x1="0" y1="${yy}" x2="200" y2="${yy}"></line>`; });
  s += `<line x1="110" y1="10" x2="110" y2="42"></line></g>` + text(2, 40, '𝄞', { size: 30, fill: '#AEABA4', font: MUSIC }) + `<g fill="#E4E1DA">`;
  ns.forEach((n) => { s += `<ellipse cx="${n[1]}" cy="${y(n[0])}" rx="4" ry="3"></ellipse>`; });
  return s + '</g></svg>';
}

const SVG = { voice: voiceSvg(), piano: pianoSvg(), bass: bassSvg(), tab: tabSvg(), fret: fretSvg(), thumb: thumbSvg() };
fs.writeFileSync(path.join(__dirname, 'preview.html'),
  `<!doctype html><meta charset="utf-8"><link href="https://fonts.googleapis.com/css2?family=JetBrains+Mono:wght@400;700;800&family=Noto+Music&display=swap" rel="stylesheet"><body style="background:#121314;margin:0;width:400px">` +
  Object.keys(SVG).map((k) => `<div style="color:#888;font:10px monospace;padding:4px">${k}</div>${SVG[k]}`).join('') + '</body>');
module.exports = { SVG, src, dir };
if (require.main === module && process.argv[2] === 'svg') process.exit(0);
require('./assemble-c4.js')(SVG, src, dir);
