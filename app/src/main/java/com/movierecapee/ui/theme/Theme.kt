package com.movierecapee.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

/** Adobe-style dark workspace colours. */
object Adobe {
    val Bg = Color(0xFF121212)
    val Panel = Color(0xFF1E1E1E)
    val Panel2 = Color(0xFF2A2A2A)
    val Line = Color(0xFF3A3A3A)
    val Blue = Color(0xFF005CC5)
    val BlueLight = Color(0xFF4B9BFF)
    val Orange = Color(0xFFFF7C00)
    val Green = Color(0xFF2EB67D)
    val Red = Color(0xFFE5484D)
    val Text = Color(0xFFF2F2F2)
    val TextDim = Color(0xFFA8A8A8)
}

private val scheme = darkColorScheme(
    primary = Adobe.BlueLight,
    onPrimary = Color.White,
    primaryContainer = Adobe.Blue,
    onPrimaryContainer = Color.White,
    secondary = Adobe.Orange,
    onSecondary = Color.Black,
    background = Adobe.Bg,
    onBackground = Adobe.Text,
    surface = Adobe.Panel,
    onSurface = Adobe.Text,
    surfaceVariant = Adobe.Panel2,
    onSurfaceVariant = Adobe.TextDim,
    outline = Adobe.Line,
    error = Adobe.Red
)

@Composable
fun RecapeeTheme(content: @Composable () -> Unit) {
    // The studio look is always dark, like Premiere Pro.
    @Suppress("UNUSED_VARIABLE") val ignored = isSystemInDarkTheme()
    MaterialTheme(colorScheme = scheme, content = content)
}
