package com.betterappmafia.apophenia.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.betterappmafia.apophenia.R

/** C4 "Console" design tokens: one place for every colour the screens use. */
object C {
    val bg = Color(0xFF121314)
    val panel = Color(0xFF18191B)
    val panel2 = Color(0xFF1F2023)
    val open = Color(0xFF1C1D20)
    val selected = Color(0xFF24221F)
    val line = Color(0xFF2C2E31)
    val line2 = Color(0xFF3A3C40)
    val faint = Color(0xFF5A5C60)
    val text = Color(0xFFCFCCC4)
    val hi = Color(0xFFE4E1DA)
    val dim = Color(0xFF8D8A84)
    val wave = Color(0xFF8F8C85)
    val acc = Color(0xFFD08A55)
    val rec = Color(0xFFC9564B)
    val listening = Color(0xFF7FA889)
    val scrim = Color(0x99000000)
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
    val body = TextStyle(fontFamily = Mono, fontSize = 13.sp, color = C.text)
    val hi = body.copy(color = C.hi, fontWeight = FontWeight.ExtraBold)
    val title = TextStyle(fontFamily = Mono, fontSize = 14.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = 0.04.em, color = C.hi)
    /** The small uppercase labels ("TAGS", "BPM · KEY"). */
    val label = TextStyle(fontFamily = Mono, fontSize = 9.sp, letterSpacing = 0.14.em, color = C.dim)
    val button = TextStyle(fontFamily = Mono, fontSize = 11.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = 0.08.em, color = C.hi)
    val small = TextStyle(fontFamily = Mono, fontSize = 10.5.sp, color = C.dim)
}

@Composable
fun ConsoleTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = darkColorScheme(
            background = C.bg, surface = C.panel, surfaceContainer = C.panel2, primary = C.acc,
            onPrimary = C.bg, onSurface = C.text, onBackground = C.text, outline = C.line2,
        ),
        content = content,
    )
}
