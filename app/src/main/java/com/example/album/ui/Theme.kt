package com.example.album.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

val AppleBlue = Color(0xFF007AFF)
val SecondaryGray = Color(0xFF8E8E93)

@Composable
fun AlbumTheme(content: @Composable () -> Unit) {
    val scheme = if (isSystemInDarkTheme()) {
        darkColorScheme(
            primary = Color(0xFF0A84FF),
            background = Color.Black,
            surface = Color(0xFF1C1C1E),
            onBackground = Color.White,
            onSurface = Color.White,
        )
    } else {
        lightColorScheme(
            primary = AppleBlue,
            background = Color.White,
            surface = Color(0xFFF2F2F7),
            onBackground = Color.Black,
            onSurface = Color.Black,
        )
    }
    MaterialTheme(colorScheme = scheme, content = content)
}
