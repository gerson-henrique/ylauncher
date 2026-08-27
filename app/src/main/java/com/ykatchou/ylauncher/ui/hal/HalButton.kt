package com.ykatchou.ylauncher.ui.hal

import android.graphics.drawable.Drawable
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import androidx.core.graphics.drawable.toBitmap
import androidx.compose.ui.graphics.asImageBitmap
import com.ykatchou.ylauncher.ui.theme.HomeAccent

@Composable
fun HalButton(
    onClick: () -> Unit,
    onLongClick: () -> Unit = {},
    onDoubleTap: () -> Unit = {},
    modifier: Modifier = Modifier,
    icon: Drawable? = null,
) {
    var isPressed by remember { mutableStateOf(false) }

    // The glow was an endless pulse — rememberInfiniteTransition breathing between 0.15 and 0.45
    // forever. That forces a redraw every frame for as long as the home screen is visible, which
    // measured at 107% CPU on this device: more than a full core, with surfaceflinger and the
    // compositor burning another 100% behind it. Raising the panel to 120Hz had doubled the cost.
    //
    // It also no longer meant anything. The pulse imitated HAL 9000's eye; this button opens the
    // browser. A steady glow says the same thing for free.
    val glowAlpha = 0.30f

    val scale by animateFloatAsState(
        targetValue = if (isPressed) 1.1f else 1f,
        animationSpec = spring(stiffness = 800f),
        label = "press_scale",
    )

    // Was a hardcoded HAL-9000 orange-red. Uses the shared accent so the glow around the
    // button belongs to the same palette as the meters and the search underline.
    val glowColor = HomeAccent

    Box(
        modifier = modifier
            .size(52.dp)
            .scale(scale)
            .drawBehind {
                // Subtle ambient glow behind the icon
                drawCircle(
                    color = glowColor.copy(alpha = glowAlpha * 0.4f),
                    radius = size.minDimension / 2 * 1.35f,
                )
                drawCircle(
                    color = glowColor.copy(alpha = glowAlpha * 0.15f),
                    radius = size.minDimension / 2 * 1.6f,
                )
            }
            .pointerInput(Unit) {
                detectTapGestures(
                    onPress = {
                        isPressed = true
                        tryAwaitRelease()
                        isPressed = false
                    },
                    onTap = { onClick() },
                    onLongPress = { onLongClick() },
                    onDoubleTap = { onDoubleTap() },
                )
            },
        contentAlignment = Alignment.Center,
    ) {
        if (icon != null) {
            val bitmap = remember(icon) {
                icon.toBitmap(width = 48, height = 48).asImageBitmap()
            }
            Image(
                bitmap = bitmap,
                contentDescription = "Action button",
                modifier = Modifier
                    .size(44.dp)
                    .clip(CircleShape),
            )
        }
    }
}
