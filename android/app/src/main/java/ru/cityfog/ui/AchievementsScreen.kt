package ru.cityfog.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import ru.cityfog.data.VisitRepository
import ru.cityfog.ui.theme.CityFogColors
import ru.cityfog.ui.theme.CityFogTheme

@Composable
fun AchievementsScreen(modifier: Modifier = Modifier) {
    val progress by VisitRepository.progress.collectAsStateWithLifecycle()
    val stats by VisitRepository.stats.collectAsStateWithLifecycle()
    val achievements = buildAchievements(progress, stats)
    val unlocked = achievements.count { it.unlocked }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .statusBarsPadding()
            .navigationBarsPadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp)
            // Низ: с запасом под плавающий навбар (≈92dp), чтобы последняя награда не пряталась.
            .padding(top = 12.dp, bottom = 108.dp)
    ) {
        Text(
            "Награды",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onBackground
        )
        Text(
            "Цели и вехи исследования Казани",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 4.dp, bottom = 20.dp)
        )

        SummaryCard(unlocked = unlocked, total = achievements.size)
        Spacer(Modifier.height(20.dp))

        achievements.forEach { a ->
            AchievementCard(a)
            Spacer(Modifier.height(12.dp))
        }
    }
}

@Composable
private fun SummaryCard(unlocked: Int, total: Int) {
    val fraction = if (total > 0) unlocked.toFloat() / total else 0f
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(28.dp),
        colors = CardDefaults.cardColors(containerColor = CityFogColors.Navy800),
        elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
    ) {
        Row(
            modifier = Modifier.padding(24.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            Box(contentAlignment = Alignment.Center, modifier = Modifier.size(72.dp)) {
                val animated by animateFloatAsState(targetValue = fraction, label = "ach-ring")
                Canvas(Modifier.fillMaxSize()) {
                    val sw = 7.dp.toPx()
                    drawArc(
                        color = CityFogColors.Navy600, startAngle = -90f, sweepAngle = 360f,
                        useCenter = false, style = Stroke(width = sw, cap = StrokeCap.Round)
                    )
                    drawArc(
                        color = CityFogColors.Cyan400, startAngle = -90f, sweepAngle = 360f * animated,
                        useCenter = false, style = Stroke(width = sw, cap = StrokeCap.Round)
                    )
                }
                Text(
                    "$unlocked",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.ExtraBold,
                    color = CityFogColors.Cyan400
                )
            }
            Column {
                Text(
                    "Открыто наград",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onBackground
                )
                Text(
                    "$unlocked из $total",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun AchievementCard(a: Achievement) {
    val container =
        if (a.unlocked) a.accent.copy(alpha = 0.12f) else MaterialTheme.colorScheme.surface
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .then(
                if (a.unlocked) Modifier.border(1.dp, a.accent.copy(alpha = 0.4f), RoundedCornerShape(22.dp))
                else Modifier
            ),
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(containerColor = container),
        elevation = CardDefaults.cardElevation(defaultElevation = if (a.unlocked) 6.dp else 3.dp)
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Иконка в кружке: яркая при открытой награде, приглушённая при закрытой.
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(CircleShape)
                    .background(
                        if (a.unlocked) a.accent.copy(alpha = 0.22f)
                        else CityFogColors.Navy700
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = a.icon,
                    contentDescription = a.title,
                    tint = if (a.unlocked) a.accent else CityFogColors.TextMuted,
                    modifier = Modifier.size(24.dp)
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
                ProgressLine(fraction = a.fraction, color = a.accent, unlocked = a.unlocked)
                Spacer(Modifier.height(4.dp))
                Text(
                    if (a.unlocked) "Получено" else a.progressLabel,
                    style = MaterialTheme.typography.labelSmall,
                    color = if (a.unlocked) a.accent else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Box(
                modifier = Modifier
                    .size(28.dp)
                    .clip(CircleShape)
                    .background(if (a.unlocked) a.accent else Color.Transparent),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = if (a.unlocked) Icons.Filled.Check else Icons.Filled.Lock,
                    contentDescription = null,
                    tint = if (a.unlocked) CityFogColors.Navy900 else CityFogColors.TextMuted,
                    modifier = Modifier.size(16.dp)
                )
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
private fun ProgressLine(fraction: Float, color: Color, unlocked: Boolean) {
    val animated by animateFloatAsState(targetValue = fraction.coerceIn(0f, 1f), label = "ach-bar")
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(6.dp)
            .clip(CircleShape)
            .background(CityFogColors.Navy700)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth(animated)
                .height(6.dp)
                .clip(CircleShape)
                .background(if (unlocked) color else color.copy(alpha = 0.7f))
        )
    }
}
