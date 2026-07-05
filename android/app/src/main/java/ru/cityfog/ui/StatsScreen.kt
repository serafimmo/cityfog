package ru.cityfog.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import ru.cityfog.data.TrackRepository
import ru.cityfog.data.VisitRepository
import ru.cityfog.ui.theme.AuroraBackground
import ru.cityfog.ui.theme.CityFogColors
import ru.cityfog.ui.theme.CityFogTheme
import ru.cityfog.ui.theme.MonoFamily
import ru.cityfog.ui.theme.glassPanel
import java.util.concurrent.TimeUnit

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
            style = MaterialTheme.typography.headlineMedium,
            color = MaterialTheme.colorScheme.onBackground
        )
        Text(
            "Твой прогресс исследования Казани",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 4.dp, bottom = 20.dp)
        )

        // Главная карточка с общим %.
        HeroStatCard(
            percent = progress.percent,
            visited = progress.visited,
            total = progress.totalAvailable
        )

        Spacer(Modifier.height(16.dp))

        // Сетка быстрых метрик.
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            QuickStatCard(
                icon = Icons.Default.Today,
                label = "Сегодня",
                value = "${stats.todayCells}",
                unit = "клеток",
                modifier = Modifier.weight(1f)
            )
            QuickStatCard(
                icon = Icons.Default.DateRange,
                label = "Неделя",
                value = "${stats.weekCells}",
                unit = "клеток",
                modifier = Modifier.weight(1f)
            )
        }

        Spacer(Modifier.height(12.dp))

        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            QuickStatCard(
                icon = Icons.Default.Straighten,
                label = "Расстояние сегодня",
                value = formatDistance(trackSummary.value.todayDistanceM),
                unit = "",
                modifier = Modifier.weight(1f)
            )
            QuickStatCard(
                icon = Icons.Default.Schedule,
                label = "Время в пути",
                value = formatDuration(trackSummary.value.todayActiveMs),
                unit = "",
                modifier = Modifier.weight(1f)
            )
        }

        Spacer(Modifier.height(12.dp))

        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
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
                modifier = Modifier.weight(1f)
            )
        }

        Spacer(Modifier.height(24.dp))

        // График активности за 2 недели.
        ActivityChart(daily = stats.dailyCells)

        Spacer(Modifier.height(24.dp))

        // Топ-район.
        if (stats.topDistrictId != 0) {
            TopDistrictCard(stats)
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
private fun HeroStatCard(percent: Float, visited: Int, total: Int) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .glassPanel(28.dp)
            .padding(24.dp)
    ) {
        Column {
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
                    "$visited / $total",
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
private fun ActivityChart(daily: IntArray) {
    val max = (daily.maxOrNull() ?: 0)
    val sum = daily.sum()
    Box(
        modifier = Modifier
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
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(92.dp),
                    verticalAlignment = Alignment.Bottom,
                    horizontalArrangement = Arrangement.spacedBy(5.dp)
                ) {
                    daily.forEachIndexed { i, count ->
                        val isToday = i == daily.lastIndex
                        val frac = if (max > 0) count.toFloat() / max else 0f
                        val animated by animateFloatAsState(
                            targetValue = frac.coerceIn(0f, 1f),
                            label = "bar-$i"
                        )
                        // Высота столбика — доля от максимума; пустой день — едва заметный «огрызок».
                        val shown = if (count == 0) 0.02f else animated.coerceAtLeast(0.06f)
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
                                        listOf(barColor, barColor.copy(alpha = 0.55f))
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
    iconTint: androidx.compose.ui.graphics.Color = CityFogColors.Cyan400
) {
    Box(modifier = modifier.glassPanel(20.dp)) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .background(CityFogColors.Navy700),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = iconTint,
                    modifier = Modifier.size(20.dp)
                )
            }
            Text(
                label,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1
            )
            Text(
                value,
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onBackground,
                maxLines = 1
            )
            // Строку единиц рисуем всегда (пустую — пробелом), чтобы все карточки
            // в ряду были одной высоты и сетка не выглядела кривой.
            Text(
                unit.ifEmpty { " " },
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1
            )
        }
    }
}

@Composable
private fun TopDistrictCard(stats: VisitRepository.Stats) {
    val color = districtColor(stats.topDistrictId)
    Box(
        modifier = Modifier
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
    val animatedProgress by animateFloatAsState(
        targetValue = (d.percent / 100f).coerceIn(0f, 1f),
        label = "bar-${d.id}"
    )
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
                "${d.visited} / ${d.total}",
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
                    .fillMaxWidth(animatedProgress)
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
    color: androidx.compose.ui.graphics.Color,
    format: (Float) -> String,
    brush: Brush? = null
) {
    val animated by animateFloatAsState(
        targetValue = value,
        label = "stat-number"
    )
    Text(
        format(animated),
        // brush красит глифы градиентом; при нём цвет не задаём (иначе перекроет).
        style = if (brush != null) style.copy(brush = brush) else style,
        fontWeight = fontWeight,
        color = if (brush != null) androidx.compose.ui.graphics.Color.Unspecified else color
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

private fun formatDistance(meters: Double): String = when {
    meters < 1000 -> "${meters.toInt()} м"
    else -> "%.1f км".format(meters / 1000)
}

private fun formatDuration(ms: Long): String {
    val hours = TimeUnit.MILLISECONDS.toHours(ms)
    val minutes = TimeUnit.MILLISECONDS.toMinutes(ms) % 60
    return if (hours > 0) "%d:%02d ч".format(hours, minutes) else "$minutes мин"
}
