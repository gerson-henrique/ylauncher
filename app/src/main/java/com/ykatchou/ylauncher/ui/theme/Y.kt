package com.ykatchou.ylauncher.ui.theme

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * The launcher's design system, in one consumable place: "Vidro & Mostarda".
 *
 * The home surface renders over an arbitrary wallpaper, so this is not a light/dark themed palette —
 * it is a fixed language of white text, one mustard accent, and smoked-glass panels. Colours, radii
 * and spacing are static ([Y]); type is exposed through composable getters so it still rides the
 * font-scale that [YLauncherTheme] applies to the Material3 typography. Settings/About keep using
 * MaterialTheme's real light/dark scheme — [Y] is for everything that sits on the wallpaper.
 *
 * Reach for the tokens instead of re-deriving a glass alpha or a corner radius per screen:
 *   Text(text = "…", style = Y.type.title, color = Y.text)
 *   Column(Modifier.glass().padding(Y.space.lg)) { … }
 */
object Y {

    /** The one accent. Mustard holds up over any wallpaper and never reads as an alert. */
    val accent = HomeAccent

    /** Alert state only — kept clearly apart from [accent] so mustard never means "wrong". */
    val warn = HomeWarn

    /** Text on the wallpaper, in three weights. Pair with [textShadow] for readability. */
    val text = HomeTextColor
    val textDim = HomeTextColorDim
    val textFaint = Color.White.copy(alpha = 0.5f)

    /** Dark ink for text/icons sitting on [accent] — mustard is light, so white is unreadable on it. */
    val onAccent = Color(0xFF231A05)

    /** Drop shadow every wallpaper-borne label carries so white survives a light photo. */
    val textShadow: Shadow = WallpaperTextShadow

    // Smoked-glass panel: a low-alpha black fill with a faint top-to-bottom sheen and a hairline
    // light edge — smoked glass over the wallpaper, not a bright card on top of it. No backdrop
    // blur on purpose; the alpha does the work and keeps the whole surface cheap to draw.
    val glassFillTop = Color(0xFF0C0A10).copy(alpha = 0.42f)
    val glassFillMid = Color(0xFF0C0A10).copy(alpha = 0.30f)
    val glassEdge = Color.White.copy(alpha = 0.13f)

    /**
     * Readability scrim for the wallpaper columns. A vertical black gradient that fades to nothing
     * at both ends, so it reads as shading rather than a panel — the opposite intent to [glass],
     * which is a bordered surface. Small text and 3dp meter bars need this separation; app labels
     * did not, which is why upstream had none.
     */
    val scrim: Brush = Brush.verticalGradient(
        colors = listOf(
            Color.Transparent,
            Color.Black.copy(alpha = 0.28f),
            Color.Black.copy(alpha = 0.42f),
            Color.Black.copy(alpha = 0.28f),
            Color.Transparent,
        ),
    )

    /** Corner radii, largest surface to smallest control. */
    object radius {
        val pill: Dp = 999.dp
        val panel: Dp = 24.dp
        val card: Dp = 16.dp
        val chip: Dp = 12.dp
    }

    /** A 4dp-based spacing scale — gaps and paddings come from here, not ad-hoc dp. */
    object space {
        val xs: Dp = 4.dp
        val sm: Dp = 8.dp
        val md: Dp = 12.dp
        val lg: Dp = 16.dp
        val xl: Dp = 24.dp
        val xxl: Dp = 32.dp
    }

    /**
     * Semantic names for the type scale. Each maps 1:1 onto a Material3 role that [YLauncherTheme]
     * already scales, so `Y.type.title` and the font-size slider stay in sync — the scale is not
     * duplicated here, only named.
     */
    object type {
        val display: TextStyle @Composable @ReadOnlyComposable get() = MaterialTheme.typography.displayLarge
        val heading: TextStyle @Composable @ReadOnlyComposable get() = MaterialTheme.typography.titleLarge
        val title: TextStyle @Composable @ReadOnlyComposable get() = MaterialTheme.typography.headlineMedium
        val subtitle: TextStyle @Composable @ReadOnlyComposable get() = MaterialTheme.typography.headlineSmall
        val body: TextStyle @Composable @ReadOnlyComposable get() = MaterialTheme.typography.bodyLarge
        val bodySm: TextStyle @Composable @ReadOnlyComposable get() = MaterialTheme.typography.bodyMedium
        val label: TextStyle @Composable @ReadOnlyComposable get() = MaterialTheme.typography.labelLarge
        val caption: TextStyle @Composable @ReadOnlyComposable get() = MaterialTheme.typography.labelMedium
    }
}

/**
 * The smoked-glass panel surface — clip, fill, hairline edge — in one modifier so no screen has to
 * remember the alpha recipe. Apply inner padding after it: `Modifier.glass().padding(Y.space.lg)`.
 */
fun Modifier.glass(shape: Shape = RoundedCornerShape(Y.radius.panel)): Modifier =
    this
        .clip(shape)
        .background(Brush.verticalGradient(listOf(Y.glassFillTop, Y.glassFillMid, Y.glassFillTop)))
        .border(width = 0.8.dp, color = Y.glassEdge, shape = shape)
