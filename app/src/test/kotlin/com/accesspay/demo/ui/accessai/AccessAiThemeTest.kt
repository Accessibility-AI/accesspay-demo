package com.accesspay.demo.ui.accessai

import androidx.compose.ui.graphics.Color
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AccessAiThemeTest {
    @Test
    fun `light mode meets WCAG AA contrast on surface and primary`() {
        val resolved = resolveAccessAiTheme(AccessPayAiTheme, highContrast = false, reducedMotion = false)
        assertTrue(contrastRatio(resolved.onSurface, resolved.surface) >= 4.5)
        assertTrue(contrastRatio(resolved.onPrimary, resolved.primary) >= 4.5)
        assertTrue(contrastRatio(resolved.mutedText, resolved.surface) >= 4.5)
        assertTrue(contrastRatio(resolved.positive, resolved.surface) >= 4.5)
        assertTrue(contrastRatio(resolved.critical, resolved.surface) >= 4.5)
        assertEquals(Color(0xFFEEF3EF), resolved.container)
    }

    @Test
    fun `high contrast dark mode meets WCAG AAA 7 to 1 contrast`() {
        val resolved = resolveAccessAiTheme(AccessPayAiTheme, highContrast = true, reducedMotion = true)
        assertEquals(Color.Black, resolved.surface)
        assertEquals(Color.White, resolved.onSurface)
        assertTrue(contrastRatio(resolved.onSurface, resolved.surface) >= 7.0)
        assertTrue(contrastRatio(resolved.onPrimary, resolved.primary) >= 7.0)
        assertTrue(contrastRatio(resolved.primary, resolved.surface) >= 7.0)
        assertTrue(contrastRatio(resolved.positive, resolved.surface) >= 7.0)
        assertTrue(contrastRatio(resolved.critical, resolved.surface) >= 7.0)
        assertTrue(contrastRatio(resolved.warning, resolved.surface) >= 7.0)
        assertTrue(contrastRatio(resolved.mutedText, resolved.surface) >= 7.0)
    }

    @Test
    fun `neutral custom theme derives onPrimary and high contrast overrides`() {
        val neutral = AccessAiTheme(
            colors = AccessAiColors(
                primary = Color(0xFF2B4C7E),
                surface = Color(0xFFFFFFFF),
                onSurface = Color(0xFF14171A),
                positive = Color(0xFF146B34),
                critical = Color(0xFFB3261E),
            ),
        )
        val light = resolveAccessAiTheme(neutral, highContrast = false, reducedMotion = false)
        assertEquals(Color.White, light.onPrimary)
        assertTrue(contrastRatio(light.onPrimary, light.primary) >= 4.5)

        val hc = resolveAccessAiTheme(neutral, highContrast = true, reducedMotion = false)
        assertTrue(contrastRatio(hc.primary, hc.surface) >= 7.0)
    }
}
