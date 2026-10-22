package com.parfumecreator.presentation.ui.theme

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

// Luxury Apothecary Color Palette
val PerfumePrimary = Color(0xFFD4A853)        // Amber Gold
val PerfumeOnPrimary = Color(0xFF3D2800)
val PerfumePrimaryContainer = Color(0xFF5A3C00)
val PerfumeOnPrimaryContainer = Color(0xFFFFDEA0)

val PerfumeSecondary = Color(0xFFB5C8A8)      // Sage Green
val PerfumeOnSecondary = Color(0xFF213320)
val PerfumeSecondaryContainer = Color(0xFF374935)
val PerfumeOnSecondaryContainer = Color(0xFFD1E4C3)

val PerfumeTertiary = Color(0xFFD4B8C7)       // Dusty Rose
val PerfumeOnTertiary = Color(0xFF3D2333)

val PerfumeBackground = Color(0xFF100E09)     // Warm near-black
val PerfumeOnBackground = Color(0xFFEDE1D0)   // Warm cream
val PerfumeSurface = Color(0xFF1C190F)        // Dark warm surface
val PerfumeOnSurface = Color(0xFFEDE1D0)
val PerfumeSurfaceVariant = Color(0xFF282315) // Card surface
val PerfumeOutline = Color(0xFF8A7C6A)

val PerfumeError = Color(0xFFFFB4AB)
val PerfumeOnError = Color(0xFF690005)

val GlassOverlay = Color(0x1AFFF8E7)
val GlassBorder = Color(0x33D4A853)

private val DarkColorScheme = darkColorScheme(
    primary = PerfumePrimary,
    onPrimary = PerfumeOnPrimary,
    primaryContainer = PerfumePrimaryContainer,
    onPrimaryContainer = PerfumeOnPrimaryContainer,
    secondary = PerfumeSecondary,
    onSecondary = PerfumeOnSecondary,
    secondaryContainer = PerfumeSecondaryContainer,
    onSecondaryContainer = PerfumeOnSecondaryContainer,
    tertiary = PerfumeTertiary,
    onTertiary = PerfumeOnTertiary,
    background = PerfumeBackground,
    onBackground = PerfumeOnBackground,
    surface = PerfumeSurface,
    onSurface = PerfumeOnSurface,
    surfaceVariant = PerfumeSurfaceVariant,
    onSurfaceVariant = PerfumeOnSurface,
    outline = PerfumeOutline,
    error = PerfumeError,
    onError = PerfumeOnError
)

val PerfumeTypography = Typography(
    displayLarge = TextStyle(
        fontFamily = FontFamily.Serif,
        fontWeight = FontWeight.Light,
        fontSize = 42.sp,
        letterSpacing = (-0.25).sp,
        color = PerfumeOnBackground
    ),
    headlineMedium = TextStyle(
        fontFamily = FontFamily.Serif,
        fontWeight = FontWeight.Medium,
        fontSize = 24.sp,
        color = PerfumeOnBackground
    ),
    titleLarge = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.SemiBold,
        fontSize = 20.sp,
        color = PerfumeOnBackground
    ),
    titleMedium = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Medium,
        fontSize = 16.sp,
        color = PerfumeOnBackground
    ),
    bodyLarge = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Normal,
        fontSize = 15.sp,
        color = PerfumeOnBackground
    ),
    bodyMedium = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Normal,
        fontSize = 13.sp,
        color = PerfumeOutline
    ),
    labelLarge = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Medium,
        fontSize = 14.sp,
        color = PerfumePrimary
    )
)

fun Modifier.glassCard(cornerRadius: Int = 16): Modifier = this
    .background(
        brush = Brush.linearGradient(
            colors = listOf(Color(0x2A352B1E), Color(0x18201A10))
        ),
        shape = RoundedCornerShape(cornerRadius.dp)
    )
    .border(
        width = 1.dp,
        brush = Brush.linearGradient(
            colors = listOf(GlassBorder, Color(0x11D4A853))
        ),
        shape = RoundedCornerShape(cornerRadius.dp)
    )

@Composable
fun ParfumeCreatorTheme(
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = DarkColorScheme,
        typography = PerfumeTypography,
        content = content
    )
}
