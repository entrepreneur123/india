package com.lottodost.app.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

val LuckyRed = Color(0xFFB3261E)
val Gold = Color(0xFFF2B705)
val WinGreen = Color(0xFF1B7F3B)

private val Light = lightColorScheme(
    primary = LuckyRed,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFFFDAD5),
    onPrimaryContainer = Color(0xFF410001),
    secondary = Color(0xFF7A5900),
    secondaryContainer = Color(0xFFFFE08A),
    onSecondaryContainer = Color(0xFF261A00),
    tertiary = WinGreen,
    tertiaryContainer = Color(0xFFB7F1C0),
    onTertiaryContainer = Color(0xFF00210B),
    // Paper-like ground with white result slips on it: lottery sheets are printed, not glossy.
    background = Color(0xFFF7F3F1),
    surface = Color(0xFFF7F3F1),
    surfaceContainerLowest = Color.White,
    surfaceVariant = Color(0xFFF1E6E3),
    outlineVariant = Color(0xFFE2D4D0),
)

private val Dark = darkColorScheme(
    primary = Color(0xFFFFB4A9),
    onPrimary = Color(0xFF690002),
    primaryContainer = Color(0xFF930006),
    onPrimaryContainer = Color(0xFFFFDAD5),
    secondary = Gold,
    secondaryContainer = Color(0xFF5C4300),
    onSecondaryContainer = Color(0xFFFFE08A),
    tertiary = Color(0xFF9BD5A5),
    tertiaryContainer = Color(0xFF005322),
    onTertiaryContainer = Color(0xFFB7F1C0),
    background = Color(0xFF191514),
    surface = Color(0xFF191514),
    surfaceContainerLowest = Color(0xFF231E1D),
    surfaceVariant = Color(0xFF2E2725),
    outlineVariant = Color(0xFF433835),
)

@Composable
fun LottoTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = if (isSystemInDarkTheme()) Dark else Light, content = content)
}
