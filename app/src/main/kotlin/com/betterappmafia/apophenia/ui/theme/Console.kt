package com.betterappmafia.apophenia.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.betterappmafia.apophenia.R

/** One colour scheme: every token the screens use. [light] = dark text on a light ground. */
data class Palette(
    val id: String, val label: String, val light: Boolean,
    val bg: Color, val panel: Color, val panel2: Color, val open: Color, val selected: Color,
    val line: Color, val line2: Color, val faint: Color, val text: Color, val hi: Color, val dim: Color,
    val wave: Color, val acc: Color, val rec: Color, val listening: Color, val scrim: Color = Color(0x99000000),
)

/**
 * The themes in Settings (Christopher, 25 Oct 2026: "a themes to let people change their color scheme").
 * [Claude] the six schemes and their colours; Console (C4) stays the default and Paper is the plan's light option.
 */
object Palettes {
    val CONSOLE = Palette(
        "console", "Console", false,
        bg = Color(0xFF121314), panel = Color(0xFF18191B), panel2 = Color(0xFF1F2023), open = Color(0xFF1C1D20), selected = Color(0xFF24221F),
        line = Color(0xFF2C2E31), line2 = Color(0xFF3A3C40), faint = Color(0xFF5A5C60), text = Color(0xFFCFCCC4), hi = Color(0xFFE4E1DA), dim = Color(0xFF8D8A84),
        wave = Color(0xFF8F8C85), acc = Color(0xFFD08A55), rec = Color(0xFFDA665A), listening = Color(0xFF7FA889),
    )
    val PAPER = Palette(
        "paper", "Paper (light)", true,
        bg = Color(0xFFEFEEEB), panel = Color(0xFFF8F7F5), panel2 = Color(0xFFE6E4E0), open = Color(0xFFF3F2EF), selected = Color(0xFFE9DFD4),
        line = Color(0xFFD6D3CD), line2 = Color(0xFFBDB9B1), faint = Color(0xFF9A968E), text = Color(0xFF2C2B28), hi = Color(0xFF141412), dim = Color(0xFF6A675F),
        wave = Color(0xFF6E6A62), acc = Color(0xFF9A4A16), rec = Color(0xFFB3362B), listening = Color(0xFF3F7D52), scrim = Color(0x66000000),
    )
    val MIDNIGHT = Palette(
        "midnight", "Midnight", false,
        bg = Color(0xFF0E1117), panel = Color(0xFF141922), panel2 = Color(0xFF1A202B), open = Color(0xFF171C26), selected = Color(0xFF1C2533),
        line = Color(0xFF262D3A), line2 = Color(0xFF344052), faint = Color(0xFF56627A), text = Color(0xFFC9D1DD), hi = Color(0xFFE6ECF4), dim = Color(0xFF8892A3),
        wave = Color(0xFF8C96A8), acc = Color(0xFF5FB8C9), rec = Color(0xFFE26B63), listening = Color(0xFF7FBF94),
    )
    val PHOSPHOR = Palette(
        "phosphor", "Phosphor", false,
        bg = Color(0xFF0B0F0C), panel = Color(0xFF111712), panel2 = Color(0xFF172019), open = Color(0xFF141B15), selected = Color(0xFF1A2A1C),
        line = Color(0xFF233026), line2 = Color(0xFF314234), faint = Color(0xFF4F6553), text = Color(0xFFC4D6C6), hi = Color(0xFFE2F0E3), dim = Color(0xFF85998A),
        wave = Color(0xFF7FA486), acc = Color(0xFF9BE36A), rec = Color(0xFFE86A58), listening = Color(0xFF6FD3C0),
    )
    val PLUM = Palette(
        "plum", "Plum", false,
        bg = Color(0xFF141114), panel = Color(0xFF1B171B), panel2 = Color(0xFF221D22), open = Color(0xFF1E1A1E), selected = Color(0xFF2A2026),
        line = Color(0xFF322B31), line2 = Color(0xFF42393F), faint = Color(0xFF66595F), text = Color(0xFFD6CBCF), hi = Color(0xFFEDE3E6), dim = Color(0xFF998C91),
        wave = Color(0xFF9A8C92), acc = Color(0xFFE08AA6), rec = Color(0xFFE0675B), listening = Color(0xFF85B892),
    )
    val HIGH_CONTRAST = Palette(
        "contrast", "High contrast", false,
        bg = Color(0xFF000000), panel = Color(0xFF0A0A0A), panel2 = Color(0xFF141414), open = Color(0xFF101010), selected = Color(0xFF262000),
        line = Color(0xFF4A4A4A), line2 = Color(0xFF6A6A6A), faint = Color(0xFF8A8A8A), text = Color(0xFFF0F0F0), hi = Color(0xFFFFFFFF), dim = Color(0xFFC8C8C8),
        wave = Color(0xFFBDBDBD), acc = Color(0xFFFFD23F), rec = Color(0xFFFF5A4E), listening = Color(0xFF6BE38A),
    )
    val ALL = listOf(CONSOLE, PAPER, MIDNIGHT, PHOSPHOR, PLUM, HIGH_CONTRAST)
    fun byId(id: String?) = ALL.firstOrNull { it.id == id } ?: CONSOLE
}

/** The design tokens (C4 "Console"): one place for every colour the screens use, read from the chosen [Palette]. */
object C {
    /** Compose state, so every screen redraws when the theme changes. */
    var palette by mutableStateOf(Palettes.CONSOLE)
    val bg get() = palette.bg
    val panel get() = palette.panel
    val panel2 get() = palette.panel2
    val open get() = palette.open
    val selected get() = palette.selected
    val line get() = palette.line
    val line2 get() = palette.line2
    val faint get() = palette.faint
    val text get() = palette.text
    val hi get() = palette.hi
    val dim get() = palette.dim
    val wave get() = palette.wave
    val acc get() = palette.acc
    val rec get() = palette.rec
    val listening get() = palette.listening
    val scrim get() = palette.scrim
}

val Mono = FontFamily(
    Font(R.font.jetbrains_mono_regular, FontWeight.Normal),
    Font(R.font.jetbrains_mono_medium, FontWeight.Medium),
    Font(R.font.jetbrains_mono_bold, FontWeight.Bold),
    Font(R.font.jetbrains_mono_extrabold, FontWeight.ExtraBold),
)

/** Music symbols (𝄞, ♯, 𝄪…) that JetBrains Mono doesn't carry. */
val MusicFont = FontFamily(Font(R.font.noto_music))

object T {
    val body get() = TextStyle(fontFamily = Mono, fontSize = 13.sp, color = C.text)
    val hi get() = body.copy(color = C.hi, fontWeight = FontWeight.ExtraBold)
    val title get() = TextStyle(fontFamily = Mono, fontSize = 14.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = 0.04.em, color = C.hi)
    /** The small uppercase labels ("TAGS", "BPM · KEY"). */
    val label get() = TextStyle(fontFamily = Mono, fontSize = 9.sp, letterSpacing = 0.14.em, color = C.dim)
    val button get() = TextStyle(fontFamily = Mono, fontSize = 11.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = 0.08.em, color = C.hi)
    val small get() = TextStyle(fontFamily = Mono, fontSize = 10.5.sp, color = C.dim)
}

@Composable
fun ConsoleTheme(content: @Composable () -> Unit) {
    val p = C.palette
    MaterialTheme(
        colorScheme = if (p.light) lightColorScheme(
            background = p.bg, surface = p.panel, surfaceContainer = p.panel2, primary = p.acc,
            onPrimary = p.bg, onSurface = p.text, onBackground = p.text, outline = p.line2,
        ) else darkColorScheme(
            background = p.bg, surface = p.panel, surfaceContainer = p.panel2, primary = p.acc,
            onPrimary = p.bg, onSurface = p.text, onBackground = p.text, outline = p.line2,
        ),
        content = content,
    )
}
