package com.telerelay.ui

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

// Telegram-adjacent deep teal, used below API 31 where the system dynamic
// palette is unavailable. All other colors come from the M3 baseline scheme.
private val TealPrimary = Color(0xFF00687A)
private val TealPrimaryDark = Color(0xFF5DD5F3)

private val LightColors = lightColorScheme(
    primary = TealPrimary,
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFB4EBFF),
    onPrimaryContainer = Color(0xFF001F28),
)

private val DarkColors = darkColorScheme(
    primary = TealPrimaryDark,
    onPrimary = Color(0xFF003542),
    primaryContainer = Color(0xFF004D5E),
    onPrimaryContainer = Color(0xFFB4EBFF),
)

/** Material 3 theme; uses the system dynamic palette on Android 12+. */
@Composable
fun TeleRelayTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    val colorScheme = when {
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        darkTheme -> DarkColors
        else -> LightColors
    }
    MaterialTheme(colorScheme = colorScheme, content = content)
}
