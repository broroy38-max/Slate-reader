package com.example.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val EbookDroidScheme = darkColorScheme(
    primary = MD3Primary,
    onPrimary = MD3OnPrimary,
    primaryContainer = MD3PrimaryContainer,
    onPrimaryContainer = MD3OnPrimaryContainer,
    secondary = MD3Secondary,
    onSecondary = MD3OnSecondary,
    secondaryContainer = MD3SecondaryContainer,
    onSecondaryContainer = MD3OnSecondaryContainer,
    tertiary = MD3Tertiary,
    onTertiary = MD3OnTertiary,
    background = Color(0xFF000000),      // Black title bars / pure black background
    onBackground = Color(0xFFFFFFFF),    // White typography
    surface = Color(0xFF16161A),         // Dark charcoal menu surfaces
    onSurface = Color(0xFFFFFFFF),       // White typography on surfaces
    surfaceVariant = Color(0xFF24242A),  // Separators and submenu shapes
    onSurfaceVariant = Color(0xFFDEDEE3),
    outline = Color(0xFF555555),         // Gray separators
    outlineVariant = Color(0xFF333333)
)

@Composable
fun MyApplicationTheme(
    darkTheme: Boolean = true, // Force dark-style desktop aesthetic
    dynamicColor: Boolean = false, // Disable dynamic colors for correct custom palette
    content: @Composable () -> Unit,
) {
    // We enforce the EbookDroidScheme for both light/dark system settings to guarantee
    // the requested classic desktop visual feel (Black backgrounds, charcoal menus, white text).
    MaterialTheme(
        colorScheme = EbookDroidScheme,
        typography = Typography,
        content = content
    )
}
