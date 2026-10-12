package com.h4isenb.reminder

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight

// Основные цвета (дизайн «Лента дня»)
val Indigo = Color(0xFF4338CA)
val IndigoDark = Color(0xFF312E81)
val Green = Color(0xFF15803D)
val Rose = Color(0xFFBE123C)
val Amber = Color(0xFFFCD34D)

/** Цвета карточек для светлой темы: индиго, бирюзовый, розовый, янтарный, зелёный, голубой. */
val Accents = listOf(Indigo, Color(0xFF0F766E), Rose, Color(0xFFB45309), Green, Color(0xFF0369A1))

/** Те же цвета, посветлее, чтобы читались на тёмном фоне. */
private val AccentsDark = listOf(
    Color(0xFF8B85FF), Color(0xFF2DD4BF), Color(0xFFFB7185), Color(0xFFFBBF24), Color(0xFF4ADE80), Color(0xFF38BDF8),
)

fun accent(i: Int, dark: Boolean): Color = (if (dark) AccentsDark else Accents)[i.coerceIn(0, Accents.size - 1)]

/** Текст поверх цвета акцента. */
fun onAccent(dark: Boolean): Color = if (dark) Color(0xFF0E0D1F) else Color.White

/** Onest — основной шрифт; Unbounded — крупные цифры и заголовки. Оба с кириллицей, лицензия OFL. */
val Onest = FontFamily(
    Font(R.font.onest_400, FontWeight.Normal),
    Font(R.font.onest_500, FontWeight.Medium),
    Font(R.font.onest_600, FontWeight.SemiBold),
    Font(R.font.onest_700, FontWeight.Bold),
)
val Unbounded = FontFamily(
    Font(R.font.unbounded_500, FontWeight.Medium),
    Font(R.font.unbounded_700, FontWeight.Bold),
)

/** Палитра приложения поверх Material. */
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
    /** точка выключенного дня недели */
    val off: Color,
    /** карточка будильника: тёмная в обеих темах */
    val alarmCard: Color,
    val alarmTile: Color,
    val alarmMuted: Color,
    val primary: Color,
    val onPrimary: Color,
)

fun palette(dark: Boolean) = if (dark) {
    Palette(
        dark = true,
        bg = Color(0xFF0E0D1F), card = Color(0xFF1A1934), ink = Color(0xFFEEEDFB), muted = Color(0xFFA6A4CB),
        line = Color(0xFF2C2A52), tint = Color(0xFF26244F), tintText = Color(0xFFB9B4FF), off = Color(0xFF3A3866),
        alarmCard = Color(0xFF2B2862), alarmTile = Color(0xFF3B378A), alarmMuted = Color(0xFFCFCCF0),
        primary = Color(0xFF8B85FF), onPrimary = Color(0xFF0E0D1F),
    )
} else {
    Palette(
        dark = false,
        bg = Color(0xFFF3F2FA), card = Color.White, ink = Color(0xFF17163A), muted = Color(0xFF5F5E80),
        line = Color(0xFFE4E2F2), tint = Color(0xFFECEBFA), tintText = Indigo, off = Color(0xFFDCDAEA),
        alarmCard = Color(0xFF17163A), alarmTile = Color(0xFF2B2960), alarmMuted = Color(0xFFC9C7E6),
        primary = Indigo, onPrimary = Color.White,
    )
}

val LocalPalette = staticCompositionLocalOf { palette(false) }

private fun TextStyle.onest() = copy(fontFamily = Onest)

private val AppTypography = Typography().let { t ->
    Typography(
        displayLarge = t.displayLarge.copy(fontFamily = Unbounded),
        displayMedium = t.displayMedium.copy(fontFamily = Unbounded),
        displaySmall = t.displaySmall.copy(fontFamily = Unbounded),
        headlineLarge = t.headlineLarge.copy(fontFamily = Unbounded),
        headlineMedium = t.headlineMedium.copy(fontFamily = Unbounded),
        headlineSmall = t.headlineSmall.copy(fontFamily = Unbounded),
        titleLarge = t.titleLarge.onest(),
        titleMedium = t.titleMedium.onest(),
        titleSmall = t.titleSmall.onest(),
        bodyLarge = t.bodyLarge.onest(),
        bodyMedium = t.bodyMedium.onest(),
        bodySmall = t.bodySmall.onest(),
        labelLarge = t.labelLarge.onest(),
        labelMedium = t.labelMedium.onest(),
        labelSmall = t.labelSmall.onest(),
    )
}

/** Во сколько раз текст приложения мельче системного. */
const val TEXT_SCALE = 0.9f

@Composable
fun ReminderTheme(dark: Boolean, content: @Composable () -> Unit) {
    val p = palette(dark)
    val scheme = if (dark) {
        darkColorScheme(
            primary = p.primary, onPrimary = p.onPrimary, secondary = accent(1, true),
            background = p.bg, surface = p.card, onBackground = p.ink, onSurface = p.ink,
            surfaceVariant = p.tint, onSurfaceVariant = p.muted, outline = p.line, outlineVariant = p.line,
            secondaryContainer = p.tint, onSecondaryContainer = p.tintText,
            surfaceContainerHigh = p.card, surfaceContainerHighest = p.tint, surfaceContainerLow = p.card,
            primaryContainer = p.tint, onPrimaryContainer = p.tintText,
        )
    } else {
        lightColorScheme(
            primary = p.primary, onPrimary = p.onPrimary, secondary = accent(1, false),
            background = p.bg, surface = p.card, onBackground = p.ink, onSurface = p.ink,
            surfaceVariant = p.tint, onSurfaceVariant = p.muted, outline = p.line, outlineVariant = p.line,
            secondaryContainer = p.tint, onSecondaryContainer = p.tintText,
            surfaceContainerHigh = p.card, surfaceContainerHighest = p.tint, surfaceContainerLow = p.card,
            primaryContainer = p.tint, onPrimaryContainer = p.tintText,
        )
    }
    // весь текст чуть мельче системного размера; настройка размера шрифта в системе по-прежнему действует
    val d = LocalDensity.current
    CompositionLocalProvider(LocalPalette provides p, LocalDensity provides Density(d.density, d.fontScale * TEXT_SCALE)) {
        MaterialTheme(colorScheme = scheme, typography = AppTypography, content = content)
    }
}
