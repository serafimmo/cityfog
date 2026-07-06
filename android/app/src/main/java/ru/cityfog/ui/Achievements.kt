package ru.cityfog.ui

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Explore
import androidx.compose.material.icons.filled.Flag
import androidx.compose.material.icons.filled.Hub
import androidx.compose.material.icons.filled.LocalFireDepartment
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.Percent
import androidx.compose.material.icons.filled.PieChart
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Verified
import androidx.compose.material.icons.filled.Whatshot
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import ru.cityfog.data.VisitRepository
import ru.cityfog.ui.theme.CityFogColors

/**
 * Достижение. Всё считается из уже имеющихся [VisitRepository.Progress] и
 * [VisitRepository.Stats] — отдельного хранилища не нужно. [fraction] двигает прогресс-бар,
 * [progressLabel] — человекочитаемая подпись (клетки или проценты).
 */
data class Achievement(
    val id: String,
    val title: String,
    val desc: String,
    val icon: ImageVector,
    val accent: Color,
    val fraction: Float,
    val progressLabel: String,
    val unlocked: Boolean
)

private fun byCount(
    id: String, title: String, desc: String, icon: ImageVector, accent: Color,
    current: Int, target: Int
): Achievement {
    val f = if (target > 0) (current.toFloat() / target).coerceIn(0f, 1f) else 0f
    val shown = current.coerceAtMost(target)
    // Телеметрия с тонкими пробелами: «1 613 / 10 000» вместо слипшихся разрядов.
    return Achievement(id, title, desc, icon, accent, f, "${fmtInt(shown)} / ${fmtInt(target)}", current >= target)
}

private fun byPercent(
    id: String, title: String, desc: String, icon: ImageVector, accent: Color,
    current: Float, target: Float
): Achievement {
    val f = if (target > 0f) (current / target).coerceIn(0f, 1f) else 0f
    val label = "%.1f%% / %.0f%%".format(current.coerceAtMost(target), target)
    return Achievement(id, title, desc, icon, accent, f, label, current >= target)
}

/** Полный список достижений в фиксированном порядке (открытые и закрытые вперемешку). */
fun buildAchievements(
    progress: VisitRepository.Progress,
    stats: VisitRepository.Stats
): List<Achievement> {
    val cells = progress.visited
    val pct = progress.percent
    val maxDistrictPct = progress.districts.maxOfOrNull { it.percent } ?: 0f
    val started = progress.districts.count { it.visited > 0 }
    val totalDistricts = progress.districts.size.coerceAtLeast(1)
    val novoPct = progress.districts.firstOrNull { it.name == "Ново-Савиновский" }?.percent ?: 0f

    // Цветовая лестница «три света города»: каждая группа наград — свой свет,
    // чтобы группы различались с одного взгляда на скролле списка.
    val cyan = CityFogColors.Cyan400     // клетки — холодный LED
    val violet = CityFogColors.Violet400 // доля города — аврора
    val amber = CityFogColors.Amber400   // районы — натриевые фонари
    val gold = Color(0xFFFFD24A)
    val orange = Color(0xFFFB923C)

    return listOf(
        // — Открытые клетки —
        byCount("first_step", "Первый шаг", "Открой свою первую клетку", Icons.Filled.Flag, cyan, cells, 1),
        byCount("scout", "Разведчик", "100 открытых клеток", Icons.Filled.Explore, cyan, cells, 100),
        byCount("explorer", "Исследователь", "1 000 открытых клеток", Icons.Filled.Map, cyan, cells, 1_000),
        byCount("cartographer", "Картограф", "10 000 открытых клеток", Icons.Filled.Public, cyan, cells, 10_000),

        // — Доля города —
        byPercent("pct1", "Первый процент", "Исследуй 1% Казани", Icons.Filled.Percent, violet, pct, 1f),
        byPercent("pct5", "Пять процентов", "Исследуй 5% Казани", Icons.Filled.Percent, violet, pct, 5f),
        byPercent("pct10", "Десятая часть", "Исследуй 10% Казани", Icons.Filled.Percent, violet, pct, 10f),
        byPercent("pct25", "Четверть города", "Исследуй 25% Казани", Icons.Filled.Percent, violet, pct, 25f),

        // — Районы —
        byCount("all_started", "Город в движении", "Начни исследовать все районы", Icons.Filled.Hub, amber, started, totalDistricts),
        byPercent("district_half", "Полрайона", "Любой район на 50%", Icons.Filled.PieChart, amber, maxDistrictPct, 50f),
        byPercent("district_full", "Район закрыт", "Любой район на 100%", Icons.Filled.Verified, amber, maxDistrictPct, 100f),
        byPercent("novo_goal", "Цель: Ново-Савиновский", "Закрой Ново-Савиновский на 100%", Icons.Filled.Star, gold, novoPct, 100f),

        // — Регулярность —
        byCount("streak3", "В ритме", "3 дня подряд с открытиями", Icons.Filled.Bolt, amber, stats.streakDays, 3),
        byCount("streak7", "Неделя в пути", "7 дней подряд", Icons.Filled.LocalFireDepartment, orange, stats.streakDays, 7),
        byCount("big_day", "Большой день", "200 клеток за один день", Icons.Filled.Whatshot, orange, stats.bestDayCells, 200),
    )
}
