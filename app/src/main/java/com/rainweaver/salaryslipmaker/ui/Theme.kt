package com.rainweaver.salaryslipmaker.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val Teal = Color(0xFF1D5B54)
private val TealLight = Color(0xFF5EEAD4)
private val Ink = Color(0xFF17150F)
private val Paper = Color(0xFFFBFAF7)

private val DarkColors = darkColorScheme(primary = TealLight, background = Ink, surface = Color(0xFF15170F))
private val LightColors = lightColorScheme(primary = Teal, background = Paper, surface = Color.White)

@Composable
fun SalarySlipTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = if (isSystemInDarkTheme()) DarkColors else LightColors, content = content)
}
