package com.example.ui.theme

import android.app.Activity
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

private val VertexAmoledColorScheme = darkColorScheme(
    primary = ElectricBlue,
    onPrimary = Color.White,
    primaryContainer = UserBubbleContainer,
    onPrimaryContainer = TextPrimary,
    secondary = ElectricBlueLight,
    onSecondary = AmoledBlack,
    secondaryContainer = AmoledSurfaceHigh,
    onSecondaryContainer = TextPrimary,
    tertiary = SoftViolet,
    onTertiary = Color.White,
    tertiaryContainer = SoftVioletContainer,
    onTertiaryContainer = TextPrimary,
    background = AmoledBlack,
    onBackground = TextPrimary,
    surface = AmoledBlack,
    onSurface = TextPrimary,
    surfaceVariant = AmoledSurfaceElevated,
    onSurfaceVariant = TextSecondary,
    surfaceContainerLowest = AmoledBlack,
    surfaceContainerLow = AmoledSurfaceCard,
    surfaceContainer = AmoledSurfaceElevated,
    surfaceContainerHigh = AmoledSurfaceHigh,
    surfaceContainerHighest = Color(0xFF222222),
    outline = AmoledBorder,
    outlineVariant = AmoledBorderSubtle,
    error = CrimsonError,
    onError = Color.White,
    errorContainer = CrimsonContainer,
    onErrorContainer = Color(0xFFFCA5A5)
)

@Composable
fun VertexChatTheme(
    content: @Composable () -> Unit
) {
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as? Activity)?.window
            if (window != null) {
                val controller = WindowCompat.getInsetsController(window, view)
                controller.isAppearanceLightStatusBars = false
                controller.isAppearanceLightNavigationBars = false
            }
        }
    }

    MaterialTheme(
        colorScheme = VertexAmoledColorScheme,
        typography = Typography,
        content = content
    )
}
