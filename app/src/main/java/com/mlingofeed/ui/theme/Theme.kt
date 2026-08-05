package com.mlingofeed.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

private val DarkColorScheme = darkColorScheme(
    primary = Purple80,
    secondary = PurpleGrey80,
    tertiary = Pink80
)

private val LightColorScheme = lightColorScheme(
    primary = Purple40,
    secondary = PurpleGrey40,
    tertiary = Pink40
)

private val EyeCareColorScheme = lightColorScheme(
    primary = Color(0xFF5D8A66),
    secondary = Color(0xFF8AA691),
    tertiary = Color(0xFFC4A381),
    background = Color(0xFFF5F0E8),
    surface = Color(0xFFF5F0E8),
    onBackground = Color(0xFF3E3C36),
    onSurface = Color(0xFF3E3C36),
    surfaceVariant = Color(0xFFE8E0D0),
    onSurfaceVariant = Color(0xFF5A5650),
    primaryContainer = Color(0xFFD0E8D4),
    onPrimaryContainer = Color(0xFF1A3A22)
)

@Composable
fun WebReaderTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = true,
    themeMode: String = "system",
    content: @Composable () -> Unit
) {
    val colorScheme = when (themeMode) {
        "light" -> if (dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            dynamicLightColorScheme(LocalContext.current)
        } else {
            LightColorScheme
        }
        "dark" -> if (dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            dynamicDarkColorScheme(LocalContext.current)
        } else {
            DarkColorScheme
        }
        "eyecare" -> EyeCareColorScheme
        else -> when {
            dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
                val context = LocalContext.current
                if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
            }
            darkTheme -> DarkColorScheme
            else -> LightColorScheme
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}
