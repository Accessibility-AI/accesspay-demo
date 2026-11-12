package com.accesspay.demo.ui.accessai

import android.content.Context
import android.provider.Settings
import android.view.accessibility.AccessibilityManager
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.max
import kotlin.math.min

/**
 * Five colour roles from the AccessAI UI spec (1f / 1i).
 * Derived roles (`onPrimary`, `container`, `mutedText`, `outline`, `warning`) and
 * 7:1+ high-contrast dark overrides are computed automatically.
 */
@Immutable
data class AccessAiColors(
    val primary: Color,
    val surface: Color,
    val onSurface: Color,
    val positive: Color,
    val critical: Color,
)

@Immutable
data class AccessAiType(
    val heading: FontFamily = FontFamily.SansSerif,
    val body: FontFamily = FontFamily.SansSerif,
) {
    companion object {
        val Default = AccessAiType()
    }
}

@Immutable
data class AccessAiTheme(
    val colors: AccessAiColors,
    val typography: AccessAiType = AccessAiType.Default,
    val cornerRadius: Dp = 28.dp,
)

@Immutable
data class ResolvedAccessAiTheme(
    val primary: Color,
    val onPrimary: Color,
    val surface: Color,
    val onSurface: Color,
    val container: Color,
    val mutedText: Color,
    val outline: Color,
    val positive: Color,
    val critical: Color,
    val warning: Color,
    val typography: AccessAiType,
    val cornerRadius: Dp,
    val cardRadius: Dp,
    val strokeWidth: Dp,
    val isHighContrast: Boolean,
    val reducedMotion: Boolean,
)

object Space {
    val panel: Dp = 16.dp
    val block: Dp = 16.dp
    val button: Dp = 12.dp
    val icon: Dp = 10.dp
}

object Size {
    val minTarget: Dp = 56.dp
    val button: Dp = 64.dp
    val confirm: Dp = 72.dp
    val mic: Dp = 128.dp
    val micLarge: Dp = 160.dp
}

val LocalAccessAi = staticCompositionLocalOf<ResolvedAccessAiTheme> {
    error("No AccessAiTheme provided; wrap content in AccessAiPanel")
}

val AccessPayAiTheme = AccessAiTheme(
    colors = AccessAiColors(
        primary = Color(0xFF1B6B2A),
        surface = Color(0xFFFFFFFF),
        onSurface = Color(0xFF0B1F14),
        positive = Color(0xFF146B34),
        critical = Color(0xFFB3261E),
    ),
    typography = AccessAiType.Default,
    cornerRadius = 28.dp,
)

/**
 * Resolves the 5-role [AccessAiTheme] into full light or AAA high-contrast dark tokens (1f / 1i).
 */
fun resolveAccessAiTheme(
    theme: AccessAiTheme,
    highContrast: Boolean,
    reducedMotion: Boolean,
): ResolvedAccessAiTheme {
    val c = theme.colors
    if (highContrast) {
        val hcPrimary = if (contrastRatio(Color(0xFF6FE07A), Color.Black) >= 7.0 && c.primary == Color(0xFF1B6B2A)) {
            Color(0xFF6FE07A)
        } else {
            ensureHighContrastOnBlack(c.primary)
        }
        return ResolvedAccessAiTheme(
            primary = hcPrimary,
            onPrimary = higherContrastInk(hcPrimary),
            surface = Color(0xFF000000),
            onSurface = Color(0xFFFFFFFF),
            container = Color(0xFF121212),
            mutedText = Color(0xFFE8E8E8),
            outline = Color(0xFFFFFFFF),
            positive = Color(0xFF7CF08C),
            critical = Color(0xFFFF9A91),
            warning = Color(0xFFFFC46B),
            typography = theme.typography,
            cornerRadius = theme.cornerRadius,
            cardRadius = (theme.cornerRadius.value * 0.57f).dp,
            strokeWidth = 3.dp,
            isHighContrast = true,
            reducedMotion = reducedMotion,
        )
    }

    val container = tintTowards(c.surface, c.primary, 0.06f)
    val mutedText = blend(c.onSurface, c.surface, 0.24f)
    val outline = blend(c.onSurface, c.surface, 0.44f)
    return ResolvedAccessAiTheme(
        primary = c.primary,
        onPrimary = higherContrastInk(c.primary),
        surface = c.surface,
        onSurface = c.onSurface,
        container = if (c == AccessPayAiTheme.colors) Color(0xFFEEF3EF) else container,
        mutedText = if (c == AccessPayAiTheme.colors) Color(0xFF3D4A42) else mutedText,
        outline = if (c == AccessPayAiTheme.colors) Color(0xFF6B7A71) else outline,
        positive = c.positive,
        critical = c.critical,
        warning = Color(0xFF8A4B00),
        typography = theme.typography,
        cornerRadius = theme.cornerRadius,
        cardRadius = (theme.cornerRadius.value * 0.57f).dp,
        strokeWidth = 2.dp,
        isHighContrast = false,
        reducedMotion = reducedMotion,
    )
}

@Composable
fun AccessAiPanel(
    modifier: Modifier = Modifier,
    theme: AccessAiTheme = AccessPayAiTheme,
    highContrast: Boolean = isSystemInDarkTheme() || isSystemHighContrast(),
    reducedMotion: Boolean = !animationsEnabled(),
    content: @Composable ColumnScope.() -> Unit,
) {
    val resolved = remember(theme, highContrast, reducedMotion) {
        resolveAccessAiTheme(theme, highContrast, reducedMotion)
    }
    val shape = RoundedCornerShape(topStart = resolved.cornerRadius, topEnd = resolved.cornerRadius)
    CompositionLocalProvider(
        LocalAccessAi provides resolved,
        LocalContentColor provides resolved.onSurface,
        LocalTextStyle provides AccessAiTypography.body(resolved),
    ) {
        Surface(
            modifier = modifier
                .fillMaxWidth()
                .drawWithContent {
                    drawContent()
                    val strokePx = resolved.strokeWidth.toPx()
                    val r = resolved.cornerRadius.toPx().coerceAtMost(size.width / 2f)
                    val half = strokePx / 2f
                    val path = Path().apply {
                        moveTo(half, r)
                        arcTo(
                            rect = Rect(
                                left = half,
                                top = half,
                                right = r * 2f - half,
                                bottom = r * 2f - half,
                            ),
                            startAngleDegrees = 180f,
                            sweepAngleDegrees = 90f,
                            forceMoveTo = false,
                        )
                        lineTo(size.width - r, half)
                        arcTo(
                            rect = Rect(
                                left = size.width - r * 2f + half,
                                top = half,
                                right = size.width - half,
                                bottom = r * 2f - half,
                            ),
                            startAngleDegrees = 270f,
                            sweepAngleDegrees = 90f,
                            forceMoveTo = false,
                        )
                    }
                    drawPath(path, color = resolved.outline, style = Stroke(width = strokePx))
                },
            color = resolved.surface,
            contentColor = resolved.onSurface,
            shape = shape,
            shadowElevation = 8.dp,
        ) {
            Box(
                Modifier
                    .fillMaxWidth()
                    .padding(start = Space.panel, end = Space.panel, top = Space.panel, bottom = 28.dp),
            ) {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    content = content,
                )
            }
        }
    }
}

object AccessAiTypography {
    /** Display: 56 / 64 Bold, capped at 76sp effective size at 200% font scale (1d / 1i). */
    @Composable
    fun display(t: ResolvedAccessAiTheme = LocalAccessAi.current): TextStyle {
        val fontScale = LocalDensity.current.fontScale.coerceAtLeast(1f)
        val rawSize = 56f
        val cappedSp: TextUnit = if (rawSize * fontScale > 76f) (76f / fontScale).sp else 56.sp
        val cappedLineSp: TextUnit = if (64f * fontScale > 84f) (84f / fontScale).sp else 64.sp
        return TextStyle(
            fontFamily = t.typography.heading,
            fontWeight = FontWeight.Bold,
            fontSize = cappedSp,
            lineHeight = cappedLineSp,
        )
    }

    fun headline(t: ResolvedAccessAiTheme): TextStyle = TextStyle(
        fontFamily = t.typography.heading,
        fontWeight = FontWeight.Bold,
        fontSize = 28.sp,
        lineHeight = 34.sp,
    )

    fun recipient(t: ResolvedAccessAiTheme): TextStyle = TextStyle(
        fontFamily = t.typography.heading,
        fontWeight = FontWeight.Bold,
        fontSize = 32.sp,
        lineHeight = 38.sp,
    )

    fun title(t: ResolvedAccessAiTheme): TextStyle = TextStyle(
        fontFamily = t.typography.heading,
        fontWeight = FontWeight.Bold,
        fontSize = 22.sp,
        lineHeight = 28.sp,
    )

    fun body(t: ResolvedAccessAiTheme): TextStyle = TextStyle(
        fontFamily = t.typography.body,
        fontWeight = FontWeight.Normal,
        fontSize = 18.sp,
        lineHeight = 26.sp,
    )

    fun label(t: ResolvedAccessAiTheme): TextStyle = TextStyle(
        fontFamily = t.typography.body,
        fontWeight = FontWeight.Bold,
        fontSize = 18.sp,
        lineHeight = 24.sp,
    )

    fun caption(t: ResolvedAccessAiTheme): TextStyle = TextStyle(
        fontFamily = t.typography.body,
        fontWeight = FontWeight.Bold,
        fontSize = 14.sp,
        lineHeight = 20.sp,
    )
}

@Composable
fun isSystemHighContrast(): Boolean {
    val context = LocalContext.current
    return remember(context) {
        runCatching {
            Settings.Secure.getInt(context.contentResolver, "high_text_contrast_enabled", 0) == 1
        }.getOrDefault(false)
    }
}

@Composable
fun animationsEnabled(): Boolean {
    val context = LocalContext.current
    return remember(context) {
        val scale = runCatching {
            Settings.Global.getFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f)
        }.getOrDefault(1f)
        val a11yOff = runCatching {
            val am = context.getSystemService(Context.ACCESSIBILITY_SERVICE) as? AccessibilityManager
            am?.isEnabled == true && scale == 0f
        }.getOrDefault(false)
        scale > 0f && !a11yOff
    }
}

internal fun contrastRatio(a: Color, b: Color): Double {
    val l1 = a.luminance().toDouble()
    val l2 = b.luminance().toDouble()
    val lighter = max(l1, l2)
    val darker = min(l1, l2)
    return (lighter + 0.05) / (darker + 0.05)
}

private fun higherContrastInk(bg: Color): Color =
    if (contrastRatio(Color.White, bg) >= contrastRatio(Color.Black, bg)) Color.White else Color.Black

private fun ensureHighContrastOnBlack(color: Color): Color {
    if (contrastRatio(color, Color.Black) >= 7.0) return color
    var factor = 0.2f
    while (factor <= 1.0f) {
        val candidate = blend(color, Color.White, factor)
        if (contrastRatio(candidate, Color.Black) >= 7.0) return candidate
        factor += 0.1f
    }
    return Color.White
}

private fun tintTowards(base: Color, accent: Color, amount: Float): Color = blend(base, accent, amount)

private fun blend(from: Color, to: Color, fraction: Float): Color {
    val f = fraction.coerceIn(0f, 1f)
    return Color(
        red = from.red + (to.red - from.red) * f,
        green = from.green + (to.green - from.green) * f,
        blue = from.blue + (to.blue - from.blue) * f,
        alpha = 1f,
    )
}
