package ru.cityfog.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.scaleIn
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import ru.cityfog.data.VisitRepository
import ru.cityfog.ui.theme.AuroraBackground
import ru.cityfog.ui.theme.CityFogColors
import ru.cityfog.ui.theme.CityFogTheme
import ru.cityfog.ui.theme.MonoFamily
import ru.cityfog.ui.theme.glassPanel

@Composable
fun AchievementsScreen(modifier: Modifier = Modifier) {
    val progress by VisitRepository.progress.collectAsStateWithLifecycle()
    val stats by VisitRepository.stats.collectAsStateWithLifecycle()
    val achievements = buildAchievements(progress, stats)
    val unlocked = achievements.count { it.unlocked }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        AuroraBackground(Modifier.fillMaxSize())
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp)
                // Низ: с запасом под плавающий навбар (≈92dp), чтобы последняя награда не пряталась.
                .padding(top = 12.dp, bottom = 108.dp)
        ) {
            Text(
                "Награды",
                style = MaterialTheme.typography.displaySmall,
                color = MaterialTheme.colorScheme.onBackground
            )
            Text(
                "Цели и вехи исследования Казани",
                style = MaterialTheme.typography.bodyMedium,
                color = CityFogColors.TextMuted,
                modifier = Modifier.padding(top = 4.dp, bottom = 20.dp)
            )

            SummaryCard(
                unlocked = unlocked,
                total = achievements.size,
                modifier = Modifier.cascadeIn(0)
            )
            Spacer(Modifier.height(24.dp))

            // Каскад: карточки «собираются» сверху вниз вслед за сводкой.
            achievements.forEachIndexed { index, a ->
                AchievementCard(a, modifier = Modifier.cascadeIn(index + 1))
                Spacer(Modifier.height(12.dp))
            }
        }
    }
}

@Composable
private fun SummaryCard(unlocked: Int, total: Int, modifier: Modifier = Modifier) {
    val fraction = if (total > 0) unlocked.toFloat() / total else 0f
    Box(
        modifier = modifier
            .fillMaxWidth()
            .glassPanel(28.dp)
    ) {
        Row(
            modifier = Modifier.padding(24.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            Box(contentAlignment = Alignment.Center, modifier = Modifier.size(72.dp)) {
                // Свет авроры за кольцом — прожектор на главную цифру экрана.
                Box(
                    Modifier
                        .matchParentSize()
                        .background(
                            Brush.radialGradient(
                                listOf(CityFogColors.Violet500.copy(alpha = 0.14f), Color.Transparent)
                            )
                        )
                )
                // Кольцо едет от нуля при входе: animateFloatAsState стартовал бы
                // сразу с целевого значения — движения не было бы никогда.
                val anim = remember { Animatable(0f) }
                LaunchedEffect(fraction) {
                    anim.animateTo(fraction, tween(900, easing = FastOutSlowInEasing))
                }
                Canvas(Modifier.fillMaxSize()) {
                    val sw = 7.dp.toPx()
                    drawArc(
                        color = CityFogColors.Navy600, startAngle = -90f, sweepAngle = 360f,
                        useCenter = false, style = Stroke(width = sw, cap = StrokeCap.Round)
                    )
                    drawArc(
                        color = CityFogColors.Cyan400, startAngle = -90f,
                        sweepAngle = 360f * anim.value,
                        useCenter = false, style = Stroke(width = sw, cap = StrokeCap.Round)
                    )
                }
                Text(
                    "$unlocked",
                    style = MaterialTheme.typography.displaySmall.copy(
                        fontSize = 30.sp, fontWeight = FontWeight.ExtraBold
                    ),
                    color = CityFogColors.Cyan400
                )
            }
            Column {
                // Та же схема, что у hero-карточки статистики: eyebrow сверху,
                // телеметрия под ним — цифра доминирует над подписью.
                Text(
                    "ОТКРЫТО НАГРАД",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    "$unlocked из $total",
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontFamily = MonoFamily, fontFeatureSettings = "tnum"
                    ),
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onBackground
                )
            }
        }
    }
}

@Composable
private fun AchievementCard(a: Achievement, modifier: Modifier = Modifier) {
    // Пресс-отклик: карточка едва заметно поджимается под пальцем — стекло живое.
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed) 0.97f else 1f,
        animationSpec = spring(dampingRatio = 0.5f, stiffness = 650f),
        label = "ach-press"
    )
    Box(
        modifier = modifier
            .fillMaxWidth()
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .glassPanel(22.dp)
            .then(
                // Открытая награда очерчена своим акцентом: кромка тает по диагонали,
                // как свет от иконки. Закрытая живёт на общем стекле без рамки.
                if (a.unlocked) Modifier.border(
                    1.dp,
                    Brush.linearGradient(
                        listOf(a.accent.copy(alpha = 0.65f), a.accent.copy(alpha = 0.08f))
                    ),
                    RoundedCornerShape(22.dp)
                ) else Modifier
            )
            .clickable(interactionSource = interaction, indication = null) { /* отклик — сама карточка */ }
    ) {
        if (a.unlocked) {
            // Тёплый свет из-под иконки — награда «включена» в сеть города.
            Box(
                Modifier
                    .matchParentSize()
                    .background(
                        Brush.radialGradient(
                            listOf(a.accent.copy(alpha = 0.16f), Color.Transparent),
                            center = Offset(120f, 90f),
                            radius = 500f
                        )
                    )
            )
        }
        Row(
            modifier = Modifier
                .padding(16.dp)
                // Закрытые притушены целиком — иерархия «получено/впереди» читается издалека.
                .graphicsLayer { alpha = if (a.unlocked) 1f else 0.75f },
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Медальон иконки — единый размер 44/22, как в настройках.
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(CircleShape)
                    .background(a.accent.copy(alpha = 0.15f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = a.icon,
                    contentDescription = a.title,
                    tint = if (a.unlocked) a.accent else CityFogColors.TextMuted,
                    modifier = Modifier.size(22.dp)
                )
            }

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    a.title,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.SemiBold,
                    color = if (a.unlocked) MaterialTheme.colorScheme.onBackground
                    else MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    a.desc,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(8.dp))
                if (a.unlocked) {
                    // Полный 100%-й брус — монотонный мусор; статус говорит словом.
                    Text(
                        "ПОЛУЧЕНО",
                        style = MaterialTheme.typography.labelSmall,
                        color = a.accent
                    )
                } else {
                    ProgressLine(fraction = a.fraction, color = a.accent)
                    Spacer(Modifier.height(4.dp))
                    Text(
                        a.progressLabel,
                        style = MaterialTheme.typography.labelMedium.copy(
                            fontFamily = MonoFamily, letterSpacing = 0.sp
                        ),
                        color = CityFogColors.TextMuted
                    )
                }
            }

            // Правый индикатор всегда одной формы: круг 28 — меняется только начинка.
            Box(
                modifier = Modifier
                    .size(28.dp)
                    .clip(CircleShape)
                    .background(if (a.unlocked) a.accent else CityFogColors.Navy700),
                contentAlignment = Alignment.Center
            ) {
                // Явный вызов top-level перегрузки: внутри Row неявный receiver
                // подтягивает RowScope-вариант, который здесь не годится.
                androidx.compose.animation.AnimatedVisibility(
                    visible = a.unlocked,
                    enter = scaleIn(animationSpec = spring(dampingRatio = 0.4f, stiffness = 300f))
                ) {
                    Icon(
                        imageVector = Icons.Filled.Check,
                        contentDescription = null,
                        tint = CityFogColors.Navy900,
                        modifier = Modifier.size(16.dp)
                    )
                }
                if (!a.unlocked) {
                    Icon(
                        imageVector = Icons.Filled.Lock,
                        contentDescription = null,
                        tint = CityFogColors.TextMuted,
                        modifier = Modifier.size(14.dp)
                    )
                }
            }
        }
    }
}

@Preview(showBackground = true, backgroundColor = 0xFF0A0E27, widthDp = 380, heightDp = 740)
@Composable
private fun AchievementsPreview() {
    val sample = buildAchievements(
        VisitRepository.Progress(
            visited = 1240,
            totalAvailable = 3_600_000,
            districts = listOf(
                VisitRepository.DistrictProgress(5, "Ново-Савиновский", 2600, 5000),
                VisitRepository.DistrictProgress(3, "Кировский", 90, 200)
            )
        ),
        VisitRepository.Stats(1240, 18, 60, 120, 4, 95, 5, "Ново-Савиновский", 52f)
    )
    CityFogTheme {
        Column(Modifier.background(CityFogColors.Navy900).padding(16.dp)) {
            SummaryCard(unlocked = sample.count { it.unlocked }, total = sample.size)
            Spacer(Modifier.height(16.dp))
            sample.take(4).forEach { AchievementCard(it); Spacer(Modifier.height(12.dp)) }
        }
    }
}

@Composable
private fun ProgressLine(fraction: Float, color: Color) {
    // Полоска доезжает до значения от нуля — тот же живой вход, что у районов.
    val anim = remember { Animatable(0f) }
    LaunchedEffect(fraction) {
        anim.animateTo(fraction.coerceIn(0f, 1f), tween(900, easing = FastOutSlowInEasing))
    }
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(6.dp)
            .clip(CircleShape)
            .background(CityFogColors.Navy700)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth(anim.value.coerceIn(0f, 1f))
                .height(6.dp)
                .clip(CircleShape)
                .background(
                    Brush.horizontalGradient(listOf(color, color.copy(alpha = 0.55f)))
                )
        )
    }
}
