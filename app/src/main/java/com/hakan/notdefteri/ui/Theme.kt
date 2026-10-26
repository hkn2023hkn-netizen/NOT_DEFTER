package com.hakan.notdefteri.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

val NoteYellow = Color(0xFFFFF176)
val NoteYellowDark = Color(0xFFFBC02D)
val NoteLine = Color(0x33795548)
val Navy = Color(0xFF1E3A5F)
val Gold = Color(0xFFE0A800)

@Composable
fun NotDefteriTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = lightColorScheme(
            primary = Navy,
            onPrimary = Color.White,
            primaryContainer = Color(0xFFD6E4F5),
            secondary = Gold,
            onSecondary = Color.Black,
            secondaryContainer = NoteYellow,
            tertiary = Color(0xFFE53935),
            background = Color(0xFFF5F3EE),
            surface = Color(0xFFF5F3EE),
        ),
        content = content
    )
}
