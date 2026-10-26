package com.betterappmafia.apophenia.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.betterappmafia.apophenia.core.chord.ChordDialect
import com.betterappmafia.apophenia.core.chord.ChordStyle
import com.betterappmafia.apophenia.core.pitch.OctaveLabel
import com.betterappmafia.apophenia.data.TRASH_DAYS
import com.betterappmafia.apophenia.ui.theme.C
import com.betterappmafia.apophenia.ui.theme.Palette
import com.betterappmafia.apophenia.ui.theme.Palettes
import com.betterappmafia.apophenia.ui.theme.T
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.abs

/**
 * Settings, behind the ⚙ in the top bar (Christopher, 25 Oct 2026: "a settings gear somewhere with anything that should
 * be as well as a themes"). [Claude] what's gathered here: the theme, how chord symbols are shown and read, pitch
 * (A4, octave numbers), what Reference pages are drawn on, the mic and overdub latency, backup, the trash, licences.
 * The ones that also live next to what they change (CHORDS ▾, the tuner, SHOW ON ▾) are the same setting, not a copy.
 */
@Composable
fun SettingsScreen(model: AppModel, requestMic: (() -> Unit) -> Unit) {
    val tm = model.tools
    val tuner = tm.tuner
    val d = model.chordDialect
    val s = model.chordStyle
    Column(Modifier.fillMaxSize()) {
        TitleBar {
            Sq("‹", { model.showSettings = false })
            Text("SETTINGS", style = T.title, modifier = Modifier.weight(1f))
            Text(versionOf(), style = T.label)
        }
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState())) {
            Section("THEME")
            ThemePicker(model)

            Section("CHORD SYMBOLS")
            MenuRow("SHOW AS", s.label, "SHOW CHORDS AS", ChordStyle.entries.map { st -> MenuItem(st.label, if (s == st) "✓" else st.note) { model.chooseChordStyle(st) } })
            MenuRow("READ TYPED CHORDS AS", d.style.label, "READ TYPED CHORDS AS", ChordDialect.Style.entries.map { st ->
                MenuItem(st.label, if (d.style == st) "✓" else if (st == ChordDialect.Style.POP) "C2 = ADD9 · C° = TRIAD" else "C2 = SUS2 · C° = °7") { model.chooseChordDialect(d.copy(style = st)) }
            })
            MenuRow("NOTE NAMES", d.names.label, "NOTE NAMES", ChordDialect.Names.entries.map { n -> MenuItem(n.label, if (d.names == n) "✓" else "") { model.chooseChordDialect(d.copy(names = n)) } })

            Section("PITCH")
            MenuRow("A4", "${fmtHz(tuner.a4)} Hz", "REFERENCE PITCH", A4S.map { a -> MenuItem("$a Hz", if (abs(a - tuner.a4) < 0.01) "✓" else "") { tm.setTuner(tuner.copy(a4 = a)) } })
            MenuRow("OCTAVE NUMBERS", if (tuner.octaveLabel == OctaveLabel.MIDDLE_C_IS_C4) "Middle C = C4" else "Middle C = C3", "OCTAVE NUMBERS", OctaveLabel.entries.map { o ->
                MenuItem(if (o == OctaveLabel.MIDDLE_C_IS_C4) "Middle C = C4" else "Middle C = C3 (Yamaha, FL Studio)", if (o == tuner.octaveLabel) "✓" else "") { tm.setTuner(tuner.copy(octaveLabel = o)) }
            })

            Section("REFERENCE")
            MenuRow("DRAW PAGES ON", model.learnShow.lowercase().replaceFirstChar(Char::uppercase), "SHOW ON",
                listOf("GUITAR" to model.learnGuitar.name, "BASS" to model.learnBass.name, "PIANO" to "", "STAFF" to "").map { (k, note) ->
                    MenuItem(k.lowercase().replaceFirstChar(Char::uppercase), if (model.learnShow == k) "✓ ${note.uppercase()}" else note.uppercase()) { model.chooseLearnShow(k) }
                })

            Section("MIC AND OVERDUB")
            ToolsExtras(model, requestMic)

            Section("YOUR DATA")
            val last by produceState<Long?>(null, model.backupBusy) { value = model.app.backups.lastBackup() }
            Link("Back up now", last?.let { "LAST " + SimpleDateFormat("d MMM HH:mm", Locale.getDefault()).format(Date(it)) } ?: "PICK A FOLDER ›", enabled = !model.backupBusy) { model.backupNow() }
            Link("Backup folder", "CHANGE ›") { model.pickBackupFolder() }
            Link("Restore from a backup", "PICK THE FILE ›", enabled = !model.backupBusy) { model.pickBackupFile() }
            Link("Trash", "KEPT $TRASH_DAYS DAYS ›") { model.showSettings = false; model.showTrash = true }

            Section("ABOUT")
            Link("Licences and privacy", "WHAT THE APP IS MADE WITH ›") { model.showSettings = false; model.showNotices = true }
        }
    }
}

@Composable
private fun Section(name: String) =
    Text(name, style = T.label.copy(color = C.acc), modifier = Modifier.padding(start = 14.dp, top = 18.dp, bottom = 4.dp))

@Composable
private fun Link(name: String, value: String, enabled: Boolean = true, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().height(52.dp).underline().clickable(enabled = enabled, role = Role.Button, onClick = onClick).padding(horizontal = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(name, style = T.body.copy(color = if (enabled) C.hi else C.faint, fontWeight = FontWeight.Bold), modifier = Modifier.weight(1f))
        Text(value, style = T.label)
    }
}

/** Every theme as a row of its own colours, so you see it before you pick it. */
@Composable
private fun ThemePicker(model: AppModel) {
    Palettes.ALL.forEach { p ->
        val on = C.palette.id == p.id
        Row(
            Modifier.fillMaxWidth().height(56.dp).underline().background(if (on) C.selected else C.bg)
                .clickable(role = Role.RadioButton) { model.chooseTheme(p) }.padding(horizontal = 14.dp)
                .semantics { contentDescription = "Theme ${p.label}${if (on) ", chosen" else ""}" },
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Swatch(p)
            Text(p.label, style = T.body.copy(color = C.hi, fontWeight = FontWeight.Bold), modifier = Modifier.weight(1f))
            Text(if (on) "✓" else "", style = T.hi.copy(color = C.acc))
        }
    }
}

/** A small preview: the theme's ground, a panel with a line of text, and its accent and record colours. */
@Composable
private fun Swatch(p: Palette) {
    Row(Modifier.border(1.dp, C.line2).background(p.bg).padding(4.dp), horizontalArrangement = Arrangement.spacedBy(3.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(width = 30.dp, height = 22.dp).background(p.panel), contentAlignment = Alignment.Center) {
            Box(Modifier.size(width = 18.dp, height = 3.dp).background(p.hi))
        }
        Box(Modifier.size(10.dp, 22.dp).background(p.acc))
        Box(Modifier.size(10.dp, 22.dp).background(p.rec))
    }
}

@Composable
private fun versionOf(): String {
    val ctx = LocalContext.current
    return runCatching { "v" + ctx.packageManager.getPackageInfo(ctx.packageName, 0).versionName }.getOrDefault("")
}
