package com.mlingofeed.ui.theme

import androidx.compose.ui.graphics.Color

val Purple80 = Color(0xFFD0BCFF)
val PurpleGrey80 = Color(0xFFCCC2DC)
val Pink80 = Color(0xFFEFB8C8)

val Purple40 = Color(0xFF6650a4)
val PurpleGrey40 = Color(0xFF625b71)
val Pink40 = Color(0xFF7D5260)

val Primary = Color(0xFF1976D2)
val PrimaryDark = Color(0xFF1565C0)
val Secondary = Color(0xFF26A69A)
val Background = Color(0xFFF5F5F5)
val Surface = Color(0xFFFFFFFF)
val Error = Color(0xFFB00020)

/** Manual accent presets offered in Settings; "dynamic" uses the system palette. */
data class AccentPalette(
    val light: Color,
    val onLight: Color,
    val lightContainer: Color,
    val onLightContainer: Color,
    val dark: Color,
    val onDark: Color,
    val darkContainer: Color,
    val onDarkContainer: Color
)

val AccentPalettes: Map<String, AccentPalette> = mapOf(
    "blue" to AccentPalette(
        light = Color(0xFF1565C0), onLight = Color(0xFFFFFFFF),
        lightContainer = Color(0xFFD3E3FD), onLightContainer = Color(0xFF0A2E5C),
        dark = Color(0xFF90CAF9), onDark = Color(0xFF0A2540),
        darkContainer = Color(0xFF1C3A5E), onDarkContainer = Color(0xFFD3E3FD)
    ),
    "green" to AccentPalette(
        light = Color(0xFF2E7D32), onLight = Color(0xFFFFFFFF),
        lightContainer = Color(0xFFD7F0D8), onLightContainer = Color(0xFF103A12),
        dark = Color(0xFFA5D6A7), onDark = Color(0xFF0C2A0D),
        darkContainer = Color(0xFF24402A), onDarkContainer = Color(0xFFD7F0D8)
    ),
    "purple" to AccentPalette(
        light = Color(0xFF6A3FB5), onLight = Color(0xFFFFFFFF),
        lightContainer = Color(0xFFE7DDFA), onLightContainer = Color(0xFF2B1456),
        dark = Color(0xFFCFBCFF), onDark = Color(0xFF231046),
        darkContainer = Color(0xFF3B2964), onDarkContainer = Color(0xFFE7DDFA)
    ),
    "orange" to AccentPalette(
        light = Color(0xFFB4530A), onLight = Color(0xFFFFFFFF),
        lightContainer = Color(0xFFFFE0C2), onLightContainer = Color(0xFF4A2300),
        dark = Color(0xFFFFB77C), onDark = Color(0xFF3A1B00),
        darkContainer = Color(0xFF5C3413), onDarkContainer = Color(0xFFFFE0C2)
    ),
    "red" to AccentPalette(
        light = Color(0xFFB3261E), onLight = Color(0xFFFFFFFF),
        lightContainer = Color(0xFFFFDAD6), onLightContainer = Color(0xFF410E0B),
        dark = Color(0xFFF2B8B5), onDark = Color(0xFF3B0A08),
        darkContainer = Color(0xFF5C1F1B), onDarkContainer = Color(0xFFFFDAD6)
    )
)
