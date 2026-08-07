package io.legado.app.ui.design.components.compose

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import io.legado.app.ui.design.theme.NgLegacyThemeInput
import io.legado.app.ui.design.theme.NgThemeResolver
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class NgGlassSurfaceTest {

    @Test
    fun defaultStyleUsesThemeDrawerAlphaAndSemanticContentColor() {
        val snapshot = NgThemeResolver.resolve(lightInput())
        val style = resolveNgGlassStyle(snapshot)

        assertEquals(snapshot.effects.drawerAlpha, style.containerBottom.alpha, 0.001f)
        assertEquals(
            (snapshot.effects.drawerAlpha + 0.06f).coerceAtMost(1f),
            style.containerTop.alpha,
            0.001f
        )
        assertEquals(Color(snapshot.colors.onSurface), style.contentColor)
        assertEquals(snapshot.effects.blurRadiusDp.dp, style.blurRadius)
        assertTrue(style.edgeHighlight.alpha > 0f)
    }

    @Test
    fun styleFollowsThemePaletteInsteadOfUsingFixedWarmColors() {
        val warm = resolveNgGlassStyle(
            NgThemeResolver.resolve(lightInput())
        )
        val bamboo = resolveNgGlassStyle(
            NgThemeResolver.resolve(
                lightInput().copy(
                    primaryColor = 0xFF7F9554.toInt(),
                    accentColor = 0xFF496A2E.toInt(),
                    bottomBackground = 0xFFEFF7EA.toInt()
                )
            )
        )

        assertNotEquals(warm.containerTop, bamboo.containerTop)
        assertNotEquals(warm.containerBottom, bamboo.containerBottom)
        assertNotEquals(warm.accentGlow, bamboo.accentGlow)
    }

    @Test
    fun floatingStyleKeepsLiveBackgroundVisibleAndStrengthensCardEdge() {
        val snapshot = NgThemeResolver.resolve(lightInput())
        val drawer = resolveNgGlassStyle(snapshot)
        val floating = resolveNgFloatingGlassStyle(snapshot)

        assertTrue(floating.containerTop.alpha < drawer.containerTop.alpha)
        assertTrue(floating.containerBottom.alpha < drawer.containerBottom.alpha)
        assertEquals(0.80f, floating.containerTop.alpha, 0.001f)
        assertEquals(0.76f, floating.containerBottom.alpha, 0.001f)
        assertEquals(0.dp, floating.blurRadius)
        assertTrue(floating.borderColor.alpha > drawer.borderColor.alpha)
        assertTrue(floating.edgeHighlight.alpha > drawer.edgeHighlight.alpha)
        assertTrue(floating.surfaceGloss.alpha < drawer.surfaceGloss.alpha)
        assertTrue(floating.depthEdge.alpha > drawer.depthEdge.alpha)
        assertEquals(0.dp, floating.shadowElevation)
    }

    @Test
    fun dialogAlphaKeepsDenseGlassReadableWithoutMakingItOpaque() {
        val snapshot = NgThemeResolver.resolve(lightInput())
        val style = resolveNgGlassStyle(
            snapshot,
            requestedContainerAlpha = snapshot.effects.dialogAlpha
        )

        assertEquals(0.94f, style.containerTop.alpha, 0.001f)
        assertEquals(0.88f, style.containerBottom.alpha, 0.001f)
        assertTrue(style.containerTop.alpha < 1f)
        assertTrue(style.containerBottom.alpha < 1f)
    }

    @Test
    fun einkStyleDisablesGlassEffectsAndTransparency() {
        val snapshot = NgThemeResolver.resolve(lightInput().copy(isEInk = true))
        val style = resolveNgGlassStyle(snapshot, requestedContainerAlpha = 0.5f)

        assertEquals(1f, style.containerTop.alpha, 0.001f)
        assertEquals(1f, style.containerBottom.alpha, 0.001f)
        assertEquals(0f, style.accentGlow.alpha, 0.001f)
        assertEquals(0f, style.edgeHighlight.alpha, 0.001f)
        assertEquals(0f, style.surfaceGloss.alpha, 0.001f)
        assertEquals(0f, style.depthEdge.alpha, 0.001f)
        assertEquals(0.dp, style.blurRadius)
        assertEquals(0.dp, style.shadowElevation)
    }

    private fun lightInput() = NgLegacyThemeInput(
        primaryColor = 0xFFFFF1E8.toInt(),
        accentColor = 0xFFF78E66.toInt(),
        backgroundColor = 0xFFFFF9F5.toInt(),
        bottomBackground = 0xFFFFF1E8.toInt(),
        errorColor = 0xFFB3261E.toInt(),
        isDark = false,
        isEInk = false
    )
}
