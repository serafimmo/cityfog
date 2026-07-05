package ru.cityfog.ui.theme

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Фирменные поверхности «ночного сканера».
 *
 * [glassPanel] — дымчатое стекло: полупрозрачный градиент вглубь + световая кромка
 * (циан сверху-слева тает в фиолет). Панели «парят» над живой картой или авророй.
 *
 * [AuroraBackground] — медленно дрейфующие орбы света (аврора + фонари) за стеклом.
 * Дешёвая имитация «ambient gradient»: три radial-градиента на Canvas.
 */
fun Modifier.glassPanel(radius: Dp = 24.dp): Modifier {
    val shape = RoundedCornerShape(radius)
    return this
        .clip(shape)
        .background(
            Brush.verticalGradient(
                listOf(Color(0xF0111E42), Color(0xF7070D22))
            )
        )
        .border(
            1.dp,
            Brush.linearGradient(
                listOf(
                    CityFogColors.Cyan400.copy(alpha = 0.45f),
                    CityFogColors.Violet400.copy(alpha = 0.22f),
                    CityFogColors.Cyan400.copy(alpha = 0.05f)
                )
            ),
            shape
        )
}

@Composable
fun AuroraBackground(modifier: Modifier = Modifier) {
    val t = rememberInfiniteTransition(label = "aurora")
    // Один медленный параметр двигает все орбы в противофазе — живо, но не суетливо.
    val drift by t.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(18000, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "aurora-drift"
    )
    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height
        // Фиолетовая аврора — верхний левый угол.
        val c1 = Offset(w * (0.10f + 0.25f * drift), h * (0.10f + 0.06f * drift))
        drawCircle(
            brush = Brush.radialGradient(
                listOf(CityFogColors.Violet500.copy(alpha = 0.22f), Color.Transparent),
                center = c1, radius = w * 0.85f
            ),
            radius = w * 0.85f, center = c1
        )
        // Циановое свечение — правый край, середина.
        val c2 = Offset(w * (1.05f - 0.2f * drift), h * (0.45f - 0.1f * drift))
        drawCircle(
            brush = Brush.radialGradient(
                listOf(CityFogColors.Cyan400.copy(alpha = 0.14f), Color.Transparent),
                center = c2, radius = w * 0.75f
            ),
            radius = w * 0.75f, center = c2
        )
        // Тёплый натриевый фонарь — снизу слева, едва заметный.
        val c3 = Offset(w * (0.2f + 0.1f * drift), h * 0.95f)
        drawCircle(
            brush = Brush.radialGradient(
                listOf(CityFogColors.Amber400.copy(alpha = 0.08f), Color.Transparent),
                center = c3, radius = w * 0.7f
            ),
            radius = w * 0.7f, center = c3
        )
    }
}
