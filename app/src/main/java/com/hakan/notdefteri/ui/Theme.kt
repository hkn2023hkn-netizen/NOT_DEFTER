package com.hakan.notdefteri.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import com.hakan.notdefteri.data.Settings
import com.hakan.notdefteri.data.ThemeMode

val NoteYellow = Color(0xFFFFF176)
val NoteYellowDark = Color(0xFFFBC02D)
val NoteLine = Color(0x33795548)
val Navy = Color(0xFF1E3A5F)
val Gold = Color(0xFFE0A800)

/** Not kağıdı renkleri. Koyu temada kağıt sarı kalır ama göz yormaması için biraz koyulaşır. */
data class PaperColors(val paper: Color, val strip: Color, val line: Color, val ink: Color, val inkSoft: Color)

private val LightPaper = PaperColors(NoteYellow, NoteYellowDark, NoteLine, Color(0xFF212121), Color(0xFF4E342E))
private val DarkPaper = PaperColors(Color(0xFFD9C75A), Color(0xFFB8961E), Color(0x33000000), Color(0xFF1B1B1B), Color(0xFF3E2723))

val LocalPaper = staticCompositionLocalOf { LightPaper }

@Composable
fun isDarkTheme(): Boolean {
    val mode by Settings.theme.collectAsState()
    return when (mode) {
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
    }
}

@Composable
fun NotDefteriTheme(content: @Composable () -> Unit) {
    val dark = isDarkTheme()
    val scheme = if (dark) {
        darkColorScheme(
            primary = Color(0xFF9CC2F0),
            onPrimary = Color(0xFF0E1E36),
            primaryContainer = Color(0xFF1E3A5F),
            onPrimaryContainer = Color.White,
            secondary = Gold,
            onSecondary = Color.Black,
            secondaryContainer = Color(0xFF5C4A00),
            tertiary = Color(0xFFFF8A80),
            background = Color(0xFF121417),
            surface = Color(0xFF121417),
            surfaceVariant = Color(0xFF2A2E33),
            surfaceContainer = Color(0xFF1C1F23),
        )
    } else {
        lightColorScheme(
            primary = Navy,
            onPrimary = Color.White,
            primaryContainer = Color(0xFFD6E4F5),
            secondary = Gold,
            onSecondary = Color.Black,
            secondaryContainer = NoteYellow,
            tertiary = Color(0xFFE53935),
            background = Color(0xFFF5F3EE),
            surface = Color(0xFFF5F3EE),
            surfaceVariant = Color(0xFFE8E4DA),
        )
    }
    CompositionLocalProvider(LocalPaper provides if (dark) DarkPaper else LightPaper) {
        MaterialTheme(colorScheme = scheme, content = content)
    }
}

/** Üst çubuk rengi: açık temada lacivert, koyuda koyu gri-mavi. */
@Composable
fun barColor(): Color = if (isDarkTheme()) Color(0xFF0E1E36) else Navy
