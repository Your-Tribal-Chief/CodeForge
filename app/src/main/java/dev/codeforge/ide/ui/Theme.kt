package dev.codeforge.ide.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import dev.codeforge.ide.R

val Mono = FontFamily(Font(R.font.jetbrains_mono))

/** Slate workbench with a single ember accent reserved for "do something" affordances (Run, active state). */
object Palette {
    val Ember = Color(0xFFF0B35A)
    val Ok = Color(0xFF6CC98F)
    val Err = Color(0xFFF06A6A)
}

private val Dark = darkColorScheme(
    primary = Palette.Ember,
    onPrimary = Color(0xFF2A1A04),
    secondary = Color(0xFF7FA7F2),
    onSecondary = Color(0xFF0E1A33),
    tertiary = Palette.Ok,
    background = Color(0xFF171B22),
    onBackground = Color(0xFFD5DAE3),
    surface = Color(0xFF1A1E26),
    onSurface = Color(0xFFD5DAE3),
    surfaceVariant = Color(0xFF222833),
    onSurfaceVariant = Color(0xFF8A94A6),
    surfaceContainerLowest = Color(0xFF13161C),
    surfaceContainerLow = Color(0xFF171B22),
    surfaceContainer = Color(0xFF1D222B),
    surfaceContainerHigh = Color(0xFF222833),
    surfaceContainerHighest = Color(0xFF2A303C),
    outline = Color(0xFF3A4252),
    outlineVariant = Color(0xFF2C3340),
    error = Palette.Err,
    onError = Color(0xFF330A0A),
)

private val Light = lightColorScheme(
    primary = Color(0xFFB86E00),
    onPrimary = Color.White,
    secondary = Color(0xFF2F5FB8),
    tertiary = Color(0xFF2E8B57),
    background = Color(0xFFF1F3F6),
    onBackground = Color(0xFF1E232B),
    surface = Color(0xFFFBFBFC),
    onSurface = Color(0xFF1E232B),
    surfaceVariant = Color(0xFFE6E9EE),
    onSurfaceVariant = Color(0xFF5C6676),
    surfaceContainerLowest = Color(0xFFE4E7EC),
    surfaceContainerLow = Color(0xFFF1F3F6),
    surfaceContainer = Color(0xFFEBEEF2),
    surfaceContainerHigh = Color(0xFFE6E9EE),
    surfaceContainerHighest = Color(0xFFDDE1E7),
    outline = Color(0xFFB9C0CB),
    outlineVariant = Color(0xFFD5DAE1),
    error = Color(0xFFC62828),
)

private val Type = Typography().let { t ->
    t.copy(
        titleMedium = t.titleMedium.copy(fontWeight = FontWeight.SemiBold, fontSize = 16.sp),
        labelSmall = t.labelSmall.copy(fontSize = 11.sp, letterSpacing = 0.2.sp),
    )
}

val CodeText = TextStyle(fontFamily = Mono, fontSize = 13.sp)

@Composable
fun CodeForgeTheme(light: Boolean, content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = if (light) Light else Dark, typography = Type, content = content)
}
