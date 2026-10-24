# M1 (builds 0.1.0–0.1.2) — phone walkthrough

Device: Galaxy S22 Ultra (SM-S908U), Android 16. Install `Apophenia-0.1.2-debug.zip` (unzip, install over any earlier build; same key, data kept). Try with headphones and then the speaker.

## 1. Launch
1. Open Apophenia. It opens on **BOARD**: two top bars, five modes at the bottom (BOARD · LIBRARY · TOOLS · LEARN · AI), the player bar above them.
2. Check: the mic chip says **MIC OFF** with a hollow dot. Nothing asks for the microphone yet.
3. Tap **TOOLS**, **LEARN**, **AI**: each says what arrives and in which milestone. No dead buttons.

## 2. Click
1. Tap **CLICK 96 ▾**. Press **+** a few times, then **Click is off → TURN ON**. Clicks start; the lamp flashes on every beat, brighter on beat 1 of 4.
2. In the same menu tap **Tap tempo** in time 4–5 times: the BPM follows your tapping.
3. Turn the click off.

## 3. Quick record
1. Tap **● REC**. Android asks for the microphone the first time; allow it.
2. The recording screen: the clock counts, the level meter moves, the top says **RECORDING · TO LIBRARY**.
3. **Lock the phone** for 20 s while playing something, unlock, come back: the clock kept going (notification "Recording" while it runs).
4. Tap **■ STOP**. The save sheet says **Take A · saved**. Give it a title, add tags ("bass", "dark"): type one and press enter, or tap an existing one. Close the sheet any way (DONE, tap outside, Back). Nothing is lost.
5. Record again **with the click on**: on the save sheet, BPM shows the click tempo marked **CLICK**.

## 4. Library
1. **LIBRARY**: one row per recording: REC, title (or "Untitled · date"), tags, BPM, key, ▶.
2. Tap **▶** on a row: it plays in the player bar at the bottom; ■ there stops it; switching modes keeps it playing.
3. **FIND**: type part of a title or a tag.
4. **SORT ▾**: captured, edited, name, tempo (no tempo last).
5. **FILTER ▾**: tap a tag once (+ include), twice (− exclude), three times (off). With two included tags, toggle ANY/ALL. BPM from/to, ½ and 2×, + FREE TIME, STARRED ONLY. The title shows how many match; **SHOW n** closes.

## 5. A recording's full view
1. Tap a row (not ▶). Waveform, **▶ PLAY TAKE A**, TAGS, BPM, KEY, TAKES, RECORDED.
2. Tap the waveform: playback jumps there.
3. **TAKE A ▾ → Record take B**: records the next take of the same thing. Back in the full view, TAKE ▾ lists A and B.
4. Star a take; the row in the Library shows ★.
5. **BPM**: type 92 or 88-96, or FREE TIME / UNKNOWN. **KEY**: home note (natural, flat, sharp rows), mode (seven, or all 50 scales). Your entries show **YOURS**.
6. Tap the title to rename it.

## 6. ~~Listening and pre-roll~~ (removed in 0.3.7: Christopher chose no pre-roll; skip this section)
1. Tap the mic chip: **LISTENING · PRE-ROLL** (green).
2. Play a riff, then press **● REC** a few seconds into it, and stop.
3. In the full view a green line marks where ● REC fell; **PRE-ROLL** shows how many seconds before it were kept (up to 10). Play from the start: the riff is there from before you pressed.
4. Press Home: Listening turns off by itself (it never runs in the background). The chip says MIC OFF when you come back.

## 7. Trash
1. Full view → TAKE ▾ → **Delete take B** (with two takes) or **Delete this recording**. **UNDO** on the bar brings it back.
2. LIBRARY → **⋯ → Trash**: deleted recordings with **RESTORE**. They are emptied after 30 days.

## 8. Interruptions and crashes
1. Record, then take a phone call or play a YouTube video over it: note what happens to the take (the app doesn't handle interruptions specially yet; tell me what you hear).
2. **Force-kill while recording**: Android settings → Apps → Apophenia → Force stop, mid-take. Open the app again: a notice says a take was recovered; it is in the Library titled "Recovered take", tagged "recovered", playable up to within ~5 s of the kill.
3. If the app ever crashes, the next launch shows the log with **COPY**. Paste it to me.

## 9. Added in 0.1.1
1. **Widget:** long-press the home screen → Widgets → Apophenia · Record. Tap **● REC** on it with the app closed: the app opens and starts recording straight away. Lock the phone, unlock, stop.
2. **Shortcut:** long-press the app icon → **Record**. Same.
3. **Backup:** LIBRARY → ⋯ → **Back up now**. The first time, Android asks for a folder (make one, e.g. "Apophenia backups"). A file "Apophenia backup <date>.zip" appears there.
4. **Restore on a wiped install:** uninstall Apophenia (this deletes its data), install 0.1.1 again, LIBRARY → ⋯ → **Restore from a backup**, pick the zip. Check the summary, RESTORE, CLOSE APOPHENIA, open it again: every recording, take, tag, BPM and key is back and plays.
5. **Interruptions:** play a take with headphones in, pull them out: it pauses with a notice. Play a take, then start music in another app: it pauses. Record, then get a call (or open the voice recorder): if Android takes the mic away, the take stops and is saved with a notice.
6. **Mic control:** TOOLS shows the mic state and permission. On Android 13+, **REVOKE THE MIC PERMISSION** takes effect when the app closes.

## 10. Added in 0.1.2: multi-select and Groups
1. LIBRARY: **long-press** a row, or **⋯ → Select**. Tap more rows; **ALL** picks everything shown; **DONE** or Back ends it.
2. **ACTIONS ▾ → Tag them**: a tag picked goes on all of them; tags they all share show with ✕.
3. **ACTIONS ▾ → Groups**: type a name ("Dark riffs") and make it with the selected rows, or tap an existing Group to put them in (tap again to take them out). ✕ deletes a Group; its scraps stay (UNDO).
4. A recording's full view has a **GROUPS** row doing the same for that one.
5. **FILTER → GROUP**: pick Groups; **UNUSED ONLY** keeps scraps no project uses (all of them until projects arrive in M2).
6. **ACTIONS ▾ → Delete** sends them all to the trash with one UNDO.

## Not in this build
Projects and the Board (M2), stacks and record-over (M2), tools (M3), analysis and AI (M4). The one-tap "remove from its other Groups when used in a project" (plan §3.5) arrives with projects.
