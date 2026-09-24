package io.github.ryugi62.lectureloop.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** One brand blue + three status colours (SPEC §8 item 9). */
@Immutable
data class LoopColors(
    val blue: Color,
    val blueSoft: Color,
    val text: Color,
    val textSub: Color,
    val caption: Color,
    val surface: Color,
    val background: Color,
    val ok: Color,
    val warn: Color,
    val error: Color,
)

private val Light = LoopColors(
    blue = Color(0xFF3182F6), blueSoft = Color(0xFFE8F3FF), text = Color(0xFF191F28), textSub = Color(0xFF4E5968),
    caption = Color(0xFF6B7684), surface = Color(0xFFF2F4F6), background = Color(0xFFFFFFFF),
    ok = Color(0xFF03A15F), warn = Color(0xFFB86E00), error = Color(0xFFE42939),
)
private val Dark = LoopColors(
    blue = Color(0xFF4C93FF), blueSoft = Color(0xFF1B2A44), text = Color(0xFFF2F4F6), textSub = Color(0xFFC1C8D0),
    caption = Color(0xFF9EA7B1), surface = Color(0xFF1C1E24), background = Color(0xFF101114),
    ok = Color(0xFF2FCB84), warn = Color(0xFFFFB331), error = Color(0xFFFF6B77),
)

val LocalLoopColors = staticCompositionLocalOf { Light }

object Loop {
    val colors: LoopColors @Composable get() = LocalLoopColors.current
    val gap = 24.dp
    val radius = 20.dp
    val cta = 56.dp
    val side = 20.dp
}

/** Three levels: title 24/bold, body 16, caption 13 — plus a number style for "numbers first". */
object LoopType {
    val number = TextStyle(fontSize = 40.sp, fontWeight = FontWeight.Bold, lineHeight = 46.sp)
    val title = TextStyle(fontSize = 24.sp, fontWeight = FontWeight.Bold, lineHeight = 32.sp)
    val section = TextStyle(fontSize = 18.sp, fontWeight = FontWeight.Bold, lineHeight = 26.sp)
    val body = TextStyle(fontSize = 16.sp, lineHeight = 24.sp)
    val bodyStrong = TextStyle(fontSize = 16.sp, fontWeight = FontWeight.SemiBold, lineHeight = 24.sp)
    val caption = TextStyle(fontSize = 13.sp, lineHeight = 18.sp)
}

@Composable
fun LectureLoopTheme(dark: Boolean = isSystemInDarkTheme(), content: @Composable () -> Unit) {
    val c = if (dark) Dark else Light
    val scheme = if (dark) {
        darkColorScheme(primary = c.blue, background = c.background, surface = c.background, onBackground = c.text, onSurface = c.text, surfaceVariant = c.surface)
    } else {
        lightColorScheme(primary = c.blue, background = c.background, surface = c.background, onBackground = c.text, onSurface = c.text, surfaceVariant = c.surface)
    }
    androidx.compose.runtime.CompositionLocalProvider(LocalLoopColors provides c) {
        MaterialTheme(colorScheme = scheme, typography = Typography(), content = content)
    }
}
