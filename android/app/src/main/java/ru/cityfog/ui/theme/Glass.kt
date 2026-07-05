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
    val t = rememberInfiniteTransition(label = "scanner")
    // Сканлайн медленно проходит сверху вниз и начинает заново.
    val scan by t.animateFloat(
        initialValue = -0.15f,
        targetValue = 1.15f,
        animationSpec = infiniteRepeatable(
            animation = tween(11000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "scanline"
    )
    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height
        val cell = w / 12f // сетка «кварталов» — отсылка к клеткам 12 м

        // 1. Тонкая сетка сканера: едва заметные линии, как миллиметровка прибора.
        val gridColor = CityFogColors.Cyan300.copy(alpha = 0.08f)
        var x = 0f
        while (x <= w) {
            drawLine(gridColor, Offset(x, 0f), Offset(x, h), strokeWidth = 1f)
            x += cell
        }
        var y = 0f
        while (y <= h) {
            drawLine(gridColor, Offset(0f, y), Offset(w, y), strokeWidth = 1f)
            y += cell
        }

        // 2. Редкие «зажжённые кварталы»: детерминированный псевдослучай по (i, j),
        //    три света города — циан, фиолет, янтарь.
        val cols = (w / cell).toInt() + 1
        val rows = (h / cell).toInt() + 1
        for (j in 0 until rows) {
            for (i in 0 until cols) {
                val hash = (i * 73856093) xor (j * 19349663)
                val v = (hash ushr 4) and 0xFF
                if (v < 14) { // ~5% клеток подсвечены
                    val tint = when (v % 3) {
                        0 -> CityFogColors.Cyan400
                        1 -> CityFogColors.Violet400
                        else -> CityFogColors.Amber400
                    }
                    val a = 0.06f + (v % 5) * 0.016f
                    drawRect(
                        color = tint.copy(alpha = a),
                        topLeft = Offset(i * cell + 1f, j * cell + 1f),
                        size = androidx.compose.ui.geometry.Size(cell - 2f, cell - 2f)
                    )
                }
            }
        }

        // 3. Сканлайн: мягкая полоса света, проходящая по сетке сверху вниз.
        val scanY = scan * h
        drawRect(
            brush = Brush.verticalGradient(
                listOf(
                    Color.Transparent,
                    CityFogColors.Cyan400.copy(alpha = 0.07f),
                    CityFogColors.Cyan300.copy(alpha = 0.10f),
                    CityFogColors.Cyan400.copy(alpha = 0.07f),
                    Color.Transparent
                ),
                startY = scanY - 140f,
                endY = scanY + 140f
            ),
            topLeft = Offset(0f, scanY - 140f),
            size = androidx.compose.ui.geometry.Size(w, 280f)
        )

        // 4. Аврора сверху: одно компактное фиолетовое свечение за заголовком.
        val c1 = Offset(w * 0.25f, -h * 0.05f)
        drawCircle(
            brush = Brush.radialGradient(
                listOf(
                    CityFogColors.Violet500.copy(alpha = 0.20f),
                    CityFogColors.Violet500.copy(alpha = 0.07f),
                    Color.Transparent
                ),
                center = c1, radius = w * 0.6f
            ),
            radius = w * 0.6f, center = c1
        )
    }
}
