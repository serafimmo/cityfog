package ru.cityfog.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.LocalFireDepartment
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Straighten
import androidx.compose.material.icons.filled.Today
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import ru.cityfog.data.TrackRepository
import ru.cityfog.data.VisitRepository
import ru.cityfog.ui.theme.AuroraBackground
import ru.cityfog.ui.theme.CityFogColors
import ru.cityfog.ui.theme.CityFogTheme
import ru.cityfog.ui.theme.MonoFamily
import ru.cityfog.ui.theme.glassPanel
import java.util.Locale
import java.util.concurrent.TimeUnit

/**
 * «1 613» вместо «1613»: тонкий пробел (U+2009) между разрядами — большое число
 * читается с одного взгляда и не разваливается на куски. Locale.US фиксирует
 * запятую как разделитель независимо от языка системы, дальше меняем её сами.
 */
internal fun fmtInt(n: Int): String = "%,d".format(Locale.US, n).replace(',', ' ')

/**
 * Каскадное появление карточек: каждая следующая стартует на 55 мс позже —
 * экран «собирается» сверху вниз, а не вспыхивает целиком.
 * Общий helper: используется статистикой, наградами и настройками.
 */
internal fun Modifier.cascadeIn(index: Int): Modifier = composed {
    val a = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        delay(index * 55L)
        a.animateTo(1f, spring(dampingRatio = 0.8f, stiffness = 380f))
    }
    graphicsLayer {
        alpha = a.value.coerceIn(0f, 1f)
        translationY = (1f - a.value) * 24.dp.toPx()
    }
}

@Composable
fun StatsScreen(modifier: Modifier = Modifier) {
    val progress by VisitRepository.progress.collectAsStateWithLifecycle()
    val stats by VisitRepository.stats.collectAsStateWithLifecycle()
    val trackSummary = remember { mutableStateOf(TrackRepository.Summary(0.0, 0L, 0.0, 0L)) }

    // Загружаем трековую статистику на фоне.
    val context = androidx.compose.ui.platform.LocalContext.current
    LaunchedEffect(Unit) {
        withContext(Dispatchers.IO) {
            trackSummary.value = TrackRepository.summary(context.applicationContext)
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        // Аврора за стеклом: медленно дрейфующие орбы света.
        AuroraBackground(Modifier.fillMaxSize())
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp)
                // Низ: с запасом под плавающий навбар (≈92dp), чтобы последний район не прятался.
                .padding(top = 12.dp, bottom = 108.dp)
        ) {
        Text(
            "Статистика",
            style = MaterialTheme.typography.displaySmall,
            color = MaterialTheme.colorScheme.onBackground
        )
        Text(
            "Твой прогресс исследования Казани",
            style = MaterialTheme.typography.bodyMedium,
            color = CityFogColors.TextMuted,
            modifier = Modifier.padding(top = 4.dp, bottom = 20.dp)
        )

        // Главная карточка с общим %.
        HeroStatCard(
            percent = progress.percent,
            visited = progress.visited,
            total = progress.totalAvailable,
            modifier = Modifier.cascadeIn(0)
        )

        Spacer(Modifier.height(16.dp))

        // Сетка быстрых метрик. Каждой — свой из «трёх светов города»,
        // чтобы ряды не сливались в одну голубую массу.
        Row(
            Modifier
                .fillMaxWidth()
                .cascadeIn(1),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            QuickStatCard(
                icon = Icons.Default.Today,
                label = "Сегодня",
                value = "${stats.todayCells}",
                unit = "клеток",
                iconTint = CityFogColors.Cyan400,
                modifier = Modifier.weight(1f)
            )
            QuickStatCard(
                icon = Icons.Default.DateRange,
                label = "Неделя",
                value = "${stats.weekCells}",
                unit = "клеток",
                iconTint = CityFogColors.Violet400,
                modifier = Modifier.weight(1f)
            )
        }

        Spacer(Modifier.height(12.dp))

        Row(
            Modifier
                .fillMaxWidth()
                .cascadeIn(2),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            val (distValue, distUnit) = formatDistance(trackSummary.value.todayDistanceM)
            val (durValue, durUnit) = formatDuration(trackSummary.value.todayActiveMs)
            QuickStatCard(
                icon = Icons.Default.Straighten,
                label = "Расстояние",
                value = distValue,
                unit = distUnit,
                iconTint = CityFogColors.Teal400,
                modifier = Modifier.weight(1f)
            )
            QuickStatCard(
                icon = Icons.Default.Schedule,
                label = "Время в пути",
                value = durValue,
                unit = durUnit,
                iconTint = CityFogColors.Violet400,
                modifier = Modifier.weight(1f)
            )
        }

        Spacer(Modifier.height(12.dp))

        Row(
            Modifier
                .fillMaxWidth()
                .cascadeIn(3),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            QuickStatCard(
                icon = Icons.Default.LocalFireDepartment,
                label = "Стрик",
                value = "${stats.streakDays}",
                unit = "дней подряд",
                iconTint = CityFogColors.Amber400,
                modifier = Modifier.weight(1f)
            )
            QuickStatCard(
                icon = Icons.Default.EmojiEvents,
                label = "Лучший день",
                value = "${stats.bestDayCells}",
                unit = "клеток",
                iconTint = CityFogColors.Amber400,
                modifier = Modifier.weight(1f)
            )
        }

        Spacer(Modifier.height(24.dp))

        // График активности за 2 недели.
        ActivityChart(daily = stats.dailyCells, modifier = Modifier.cascadeIn(4))

        Spacer(Modifier.height(24.dp))

        // Топ-район.
        if (stats.topDistrictId != 0) {
            TopDistrictCard(stats, modifier = Modifier.cascadeIn(5))
            Spacer(Modifier.height(24.dp))
        }

        // Прогресс по районам.
        Text(
            "Прогресс по районам",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(bottom = 12.dp)
        )
        val sortedDistricts = progress.districts.sortedByDescending { it.percent }
        sortedDistricts.forEach { d ->
            DistrictBar(d)
            Spacer(Modifier.height(10.dp))
        }
        }
    }
}

@Composable
private fun HeroStatCard(
    percent: Float,
    visited: Int,
    total: Int,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .glassPanel(28.dp)
    ) {
        // Аврора живёт и внутри hero-карточки: фиолет сверху-справа, циан
        // снизу-слева — перекликается с градиентом самой цифры.
        Box(
            Modifier
                .matchParentSize()
                .background(
                    Brush.radialGradient(
                        listOf(CityFogColors.Violet500.copy(alpha = 0.14f), Color.Transparent),
                        center = Offset(1000f, 0f),
                        radius = 600f
                    )
                )
        )
        Box(
            Modifier
                .matchParentSize()
                .background(
                    Brush.radialGradient(
                        listOf(CityFogColors.Cyan400.copy(alpha = 0.10f), Color.Transparent),
                        center = Offset(0f, 600f),
                        radius = 600f
                    )
                )
        )
        Column(Modifier.padding(24.dp)) {
            Text(
                "КАЗАНЬ ИССЛЕДОВАНА",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(10.dp))
            // Сигнатура: hero-процент в Unbounded с переливом cyan → violet.
            AnimatedStatNumber(
                value = percent,
                style = MaterialTheme.typography.displayLarge,
                fontWeight = FontWeight.Bold,
                color = CityFogColors.Cyan400,
                format = { fmtPct(it) },
                brush = Brush.linearGradient(
                    listOf(
                        CityFogColors.Cyan300,
                        CityFogColors.Cyan400,
                        CityFogColors.Violet400
                    )
                )
            )
            Spacer(Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "${fmtInt(visited)} / ${fmtInt(total)}",
                    style = MaterialTheme.typography.titleMedium.copy(fontFamily = MonoFamily),
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onBackground
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    "клеток открыто",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun ActivityChart(daily: IntArray, modifier: Modifier = Modifier) {
    val max = (daily.maxOrNull() ?: 0)
    val sum = daily.sum()
    Box(
        modifier = modifier
            .fillMaxWidth()
            .glassPanel(24.dp)
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Bottom
            ) {
                Column {
                    Text(
                        "АКТИВНОСТЬ",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        "Открыто клеток по дням",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onBackground
                    )
                }
                Text(
                    "${daily.size} дней",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Spacer(Modifier.height(16.dp))

            if (sum == 0) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(92.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        "Пока тихо — выйди прогуляться,\nи здесь появятся столбики",
                        style = MaterialTheme.typography.bodySmall,
                        color = CityFogColors.TextMuted
                    )
                }
            } else {
                // «Сегодня» дышит: верхний цвет столбика мягко пульсирует,
                // как живой сигнал среди уже остывших дней.
                val breath = rememberInfiniteTransition(label = "today-breath")
                val todayAlpha by breath.animateFloat(
                    initialValue = 0.75f,
                    targetValue = 1f,
                    animationSpec = infiniteRepeatable(
                        animation = tween(1600),
                        repeatMode = RepeatMode.Reverse
                    ),
                    label = "today-alpha"
                )
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(92.dp),
                    verticalAlignment = Alignment.Bottom,
                    horizontalArrangement = Arrangement.spacedBy(5.dp)
                ) {
                    daily.forEachIndexed { i, count ->
                        val isToday = i == daily.lastIndex
                        val frac = if (max > 0) (count.toFloat() / max).coerceIn(0f, 1f) else 0f
                        // Волна слева направо: каждый столбик стартует чуть позже
                        // соседнего и «допрыгивает» пружиной до своей высоты.
                        val anim = remember { Animatable(0f) }
                        LaunchedEffect(frac) {
                            delay(i * 35L)
                            anim.animateTo(frac, spring(dampingRatio = 0.6f, stiffness = 300f))
                        }
                        // Высота столбика — доля от максимума; пустой день — едва заметный «огрызок».
                        val shown = if (count == 0) 0.02f else anim.value.coerceIn(0.06f, 1f)
                        // «Сегодня» — тёплый натриевый фонарь среди холодных LED.
                        val barColor = if (isToday) CityFogColors.Amber400 else CityFogColors.Cyan400
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxHeight(shown)
                                .clip(RoundedCornerShape(topStart = 3.dp, topEnd = 3.dp))
                                .background(
                                    if (count == 0) SolidColor(CityFogColors.Navy600)
                                    else Brush.verticalGradient(
                                        listOf(
                                            barColor.copy(alpha = if (isToday) todayAlpha else 1f),
                                            barColor.copy(alpha = 0.55f)
                                        )
                                    )
                                )
                        )
                    }
                }
                Spacer(Modifier.height(10.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        "$sum за 2 недели",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        "макс. $max/день",
                        style = MaterialTheme.typography.labelMedium,
                        color = CityFogColors.Cyan400,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
        }
    }
}

@Composable
private fun QuickStatCard(
    icon: ImageVector,
    label: String,
    value: String,
    unit: String,
    modifier: Modifier = Modifier,
    iconTint: Color = CityFogColors.Cyan400
) {
    Box(modifier = modifier.glassPanel(20.dp)) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // Верхняя строка: медальон + eyebrow-ярлык. Цифра ниже доминирует.
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(30.dp)
                        .clip(CircleShape)
                        .background(iconTint.copy(alpha = 0.15f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = iconTint,
                        modifier = Modifier.size(18.dp)
                    )
                }
                Text(
                    label.uppercase(),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1
                )
            }
            // Значение — крупная моно-телеметрия, единица прижата к её базовой линии.
            Row(verticalAlignment = Alignment.Bottom) {
                Text(
                    value,
                    style = MaterialTheme.typography.headlineSmall.copy(
                        fontFamily = MonoFamily, fontWeight = FontWeight.Bold,
                        fontSize = 28.sp, lineHeight = 32.sp,
                        letterSpacing = 0.sp, fontFeatureSettings = "tnum"
                    ),
                    color = MaterialTheme.colorScheme.onBackground,
                    maxLines = 1,
                    modifier = Modifier.alignByBaseline()
                )
                Text(
                    unit,
                    style = MaterialTheme.typography.bodySmall,
                    color = CityFogColors.TextMuted,
                    maxLines = 1,
                    modifier = Modifier
                        .alignByBaseline()
                        .padding(start = 4.dp)
                )
            }
        }
    }
}

@Composable
private fun TopDistrictCard(stats: VisitRepository.Stats, modifier: Modifier = Modifier) {
    val color = districtColor(stats.topDistrictId)
    Box(
        modifier = modifier
            .fillMaxWidth()
            .glassPanel(24.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    "Лидирует район",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    stats.topDistrictName,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = color,
                    modifier = Modifier.padding(top = 4.dp)
                )
            }
            Spacer(Modifier.width(16.dp))
            Box(
                modifier = Modifier
                    .size(64.dp)
                    .clip(CircleShape)
                    .background(color.copy(alpha = 0.15f)),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    fmtPct(stats.topDistrictPercent),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = color
                )
            }
        }
    }
}

@Composable
private fun DistrictBar(d: VisitRepository.DistrictProgress) {
    val color = districtColor(d.id)
    // Полоска едет от нуля к значению: animateFloatAsState стартовал бы сразу
    // с целевого — движения не было бы никогда.
    val target = (d.percent / 100f).coerceIn(0f, 1f)
    val anim = remember { Animatable(0f) }
    LaunchedEffect(target) {
        anim.animateTo(target, tween(900, easing = FastOutSlowInEasing))
    }
    Column {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                d.name,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium
            )
            Text(
                "${fmtInt(d.visited)} / ${fmtInt(d.total)}",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Spacer(Modifier.height(6.dp))
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(8.dp)
                .clip(RoundedCornerShape(4.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(anim.value.coerceIn(0f, 1f))
                    .height(8.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .background(
                        Brush.horizontalGradient(
                            listOf(color, color.copy(alpha = 0.7f))
                        )
                    )
            )
        }
    }
}

@Composable
private fun AnimatedStatNumber(
    value: Float,
    style: androidx.compose.ui.text.TextStyle,
    fontWeight: FontWeight,
    color: Color,
    format: (Float) -> String,
    brush: Brush? = null
) {
    // Hero-цифра «набегает» с нуля при входе на экран — живой прибор, а не картинка.
    val anim = remember { Animatable(0f) }
    LaunchedEffect(value) {
        anim.animateTo(value, tween(900, easing = FastOutSlowInEasing))
    }
    Text(
        format(anim.value),
        // brush красит глифы градиентом; при нём цвет не задаём (иначе перекроет).
        style = if (brush != null) style.copy(brush = brush) else style,
        fontWeight = fontWeight,
        color = if (brush != null) Color.Unspecified else color
    )
}

@Preview(showBackground = true, backgroundColor = 0xFF0A0E27, widthDp = 380)
@Composable
private fun StatsCardsPreview() {
    CityFogTheme {
        Column(Modifier.background(CityFogColors.Navy900).padding(16.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                QuickStatCard(Icons.Default.Today, "Сегодня", "42", "клеток", Modifier.weight(1f))
                QuickStatCard(
                    Icons.Default.LocalFireDepartment, "Стрик", "4", "дня подряд",
                    Modifier.weight(1f)
                )
            }
            Spacer(Modifier.height(16.dp))
            ActivityChart(intArrayOf(0, 0, 12, 30, 8, 0, 45, 60, 22, 0, 18, 73, 40, 55))
            Spacer(Modifier.height(16.dp))
            DistrictBar(VisitRepository.DistrictProgress(5, "Ново-Савиновский", 2600, 5000))
        }
    }
}

// Значение и единица измерения отдаются парой: цифру карточка рисует крупной
// моно-телеметрией, единицу — мелко у базовой линии.
private fun formatDistance(meters: Double): Pair<String, String> = when {
    meters < 1000 -> "${meters.toInt()}" to "м"
    else -> "%.1f".format(meters / 1000) to "км"
}

private fun formatDuration(ms: Long): Pair<String, String> {
    val hours = TimeUnit.MILLISECONDS.toHours(ms)
    val minutes = TimeUnit.MILLISECONDS.toMinutes(ms) % 60
    return if (hours > 0) "%d:%02d".format(hours, minutes) to "ч" else "$minutes" to "мин"
}
