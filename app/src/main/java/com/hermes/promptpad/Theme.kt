package com.hermes.promptpad

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import kotlin.math.roundToInt

val Black = Color.Black
val White = Color.White
const val DEFAULT_ACCENT_HEX = "#FC7703"
private val DefaultAccent = Color(0xFFFC7703)
private var accentState by mutableStateOf(DefaultAccent)
val Accent: Color get() = accentState
val DotIdle = Color(0xFF777777)
val DotBad = Color(0xFFFF453A)
val Dim = Color(0xFFE6E6E6)

fun normalizeAccentHex(value: String): String? = value.trim().uppercase().takeIf { it.matches(Regex("#[0-9A-F]{6}")) }

fun colorForAccentHex(value: String): Color = normalizeAccentHex(value)?.let {
    Color(it.substring(1).toLong(16).toInt() or 0xFF000000.toInt())
} ?: DefaultAccent

fun accentHex(color: Color): String = "#%02X%02X%02X".format(
    (color.red * 255).roundToInt(), (color.green * 255).roundToInt(), (color.blue * 255).roundToInt(),
)

fun accentIsReadableOnBlack(color: Color): Boolean {
    fun channel(value: Float) = if (value <= 0.04045f) value / 12.92f else ((value + 0.055f) / 1.055f).let { it * it * it }
    return (0.2126f * channel(color.red) + 0.7152f * channel(color.green) + 0.0722f * channel(color.blue) + 0.05f) / 0.05f >= 4.5f
}

fun loadAccent(value: String) { accentState = colorForAccentHex(value) }

private val LatoFamily = FontFamily(
    Font(R.font.lato_regular, FontWeight.Normal),
    Font(R.font.lato_bold, FontWeight.Bold),
)

@Composable
fun MinimalTheme(prefs: Prefs, revision: Int = 0, content: @Composable () -> Unit) {
    val scale = remember(revision) { prefs.textScale / 100f }
    val accent = Accent
    fun style(size: Int, weight: FontWeight = FontWeight.Normal) = TextStyle(
        fontFamily = LatoFamily,
        fontSize = (size * scale).sp,
        fontWeight = weight,
        color = White,
    )
    MaterialTheme(
        colorScheme = darkColorScheme(
            primary = accent, onPrimary = Black, background = Black, onBackground = White,
            surface = Black, onSurface = White, surfaceVariant = Black, onSurfaceVariant = Dim,
            secondary = accent, error = DotBad,
        ),
        typography = Typography(
            headlineMedium = style(26, FontWeight.Bold),
            headlineSmall = style(20, FontWeight.Bold),
            titleMedium = style(15, FontWeight.Bold),
            bodyMedium = style(13),
            bodySmall = style(11),
            labelSmall = style(10),
        ),
        content = content,
    )
}
