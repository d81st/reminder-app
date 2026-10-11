package com.h4isenb.reminder

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

val Indigo = Color(0xFF4F46E5)
val IndigoDark = Color(0xFF3730A3)
val Green = Color(0xFF22C55E)
val Rose = Color(0xFFF43F5E)

/** Цвета карточек: индиго, бирюзовый, розовый, янтарный, зелёный, голубой. */
val Accents = listOf(Indigo, Color(0xFF14B8A6), Rose, Color(0xFFF59E0B), Green, Color(0xFF0EA5E9))

/** Палитра приложения поверх Material: те же цвета, что были в прежней версии. */
@Immutable
data class Palette(
    val dark: Boolean,
    val bg: Color,
    val card: Color,
    val ink: Color,
    val muted: Color,
    val line: Color,
    val tint: Color,
    val tintText: Color,
)

fun palette(dark: Boolean) = Palette(
    dark = dark,
    bg = if (dark) Color(0xFF0F1220) else Color(0xFFF4F5FB),
    card = if (dark) Color(0xFF1A1E33) else Color.White,
    ink = if (dark) Color(0xFFECEEFB) else Color(0xFF1B1F3B),
    muted = if (dark) Color(0xFF9AA0C4) else Color(0xFF6B7094),
    line = if (dark) Color(0xFF2A2F4A) else Color(0xFFE3E5F2),
    tint = if (dark) Color(0xFF2A2D5C) else Color(0xFFE9E8FC),
    tintText = if (dark) Color(0xFFB4AFFF) else Indigo,
)

val LocalPalette = staticCompositionLocalOf { palette(false) }

@Composable
fun ReminderTheme(dark: Boolean, content: @Composable () -> Unit) {
    val p = palette(dark)
    val scheme = if (dark) {
        darkColorScheme(
            primary = Indigo, onPrimary = Color.White, secondary = Accents[1],
            background = p.bg, surface = p.card, onBackground = p.ink, onSurface = p.ink,
            surfaceVariant = p.tint, onSurfaceVariant = p.muted, outline = p.line, outlineVariant = p.line,
            secondaryContainer = p.tint, onSecondaryContainer = p.tintText,
        )
    } else {
        lightColorScheme(
            primary = Indigo, onPrimary = Color.White, secondary = Accents[1],
            background = p.bg, surface = p.card, onBackground = p.ink, onSurface = p.ink,
            surfaceVariant = p.tint, onSurfaceVariant = p.muted, outline = p.line, outlineVariant = p.line,
            secondaryContainer = p.tint, onSecondaryContainer = p.tintText,
        )
    }
    androidx.compose.runtime.CompositionLocalProvider(LocalPalette provides p) {
        MaterialTheme(colorScheme = scheme, content = content)
    }
}
