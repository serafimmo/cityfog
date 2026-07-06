package ru.cityfog.ui

import android.content.Context
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import kotlin.math.abs
import kotlinx.coroutines.launch
import ru.cityfog.ui.theme.AuroraBackground
import ru.cityfog.ui.theme.CityFogColors
import ru.cityfog.ui.theme.CityFogTheme
import ru.cityfog.ui.theme.DisplayFamily
import ru.cityfog.ui.theme.glassPanel

private const val PREFS_NAME = "cityfog_prefs"
private const val KEY_ONBOARDED = "onboarded"

@Composable
fun OnboardingScreen(onFinish: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val pagerState = rememberPagerState(pageCount = { 3 })

    // Онбординг живёт в той же атмосфере, что и остальное приложение:
    // фон-сканер за контентом, а не плоская заливка темы.
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(CityFogColors.Void)
    ) {
        AuroraBackground(Modifier.fillMaxSize())

        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            HorizontalPager(
                state = pagerState,
                modifier = Modifier.weight(1f)
            ) { page ->
                // Смещение страницы относительно видимой позиции пейджера:
                // текст листается с обычной скоростью, а иллюстрация «отстаёт».
                val off = (pagerState.currentPage - page) + pagerState.currentPageOffsetFraction
                OnboardingPage(
                    page = page,
                    pageCount = pagerState.pageCount,
                    pageOffset = off,
                    visible = pagerState.currentPage == page
                )
            }

            // Индикатор страниц: активная точка растягивается пружиной и светится,
            // чтобы переключение читалось боковым зрением, без скачка размеров.
            Row(
                modifier = Modifier.padding(bottom = 24.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                repeat(pagerState.pageCount) { index ->
                    val active = pagerState.currentPage == index
                    val w by animateDpAsState(
                        targetValue = if (active) 24.dp else 10.dp,
                        animationSpec = spring(dampingRatio = 0.7f, stiffness = 400f),
                        label = "dot-w"
                    )
                    val dotColor by animateColorAsState(
                        targetValue = if (active) CityFogColors.Cyan400 else CityFogColors.TextMuted,
                        label = "dot-c"
                    )
                    Box(
                        modifier = Modifier
                            .size(width = w, height = 10.dp)
                            .then(
                                if (active) Modifier.shadow(
                                    8.dp, CircleShape,
                                    ambientColor = CityFogColors.Cyan400,
                                    spotColor = CityFogColors.Cyan400
                                ) else Modifier
                            )
                            .clip(RoundedCornerShape(5.dp))
                            .background(dotColor)
                            .then(
                                if (active) Modifier.background(
                                    Brush.horizontalGradient(
                                        listOf(CityFogColors.Teal400, CityFogColors.Cyan400)
                                    )
                                ) else Modifier
                            )
                    )
                }
            }

            // Одна зона под кнопки с кросс-фейдом состояний, чтобы лейаут
            // не прыгал при переходе на последнюю страницу.
            AnimatedContent(
                targetState = pagerState.currentPage == pagerState.pageCount - 1,
                transitionSpec = {
                    (fadeIn(tween(220)) + slideInVertically { it / 3 })
                        .togetherWith(fadeOut(tween(150)))
                },
                label = "onboarding-cta"
            ) { isLastPage ->
                if (isLastPage) {
                    // Финальный CTA — фирменная неоновая капсула, как «Старт» на карте.
                    Box(
                        modifier = Modifier.fillMaxWidth(),
                        contentAlignment = Alignment.Center
                    ) {
                        Box(
                            modifier = Modifier
                                .shadow(
                                    20.dp, CircleShape,
                                    ambientColor = CityFogColors.Cyan400,
                                    spotColor = CityFogColors.Cyan400
                                )
                                .clip(CircleShape)
                                .background(
                                    Brush.horizontalGradient(
                                        listOf(CityFogColors.Teal400, CityFogColors.Cyan400)
                                    )
                                )
                                .clickable {
                                    markOnboarded(context)
                                    onFinish()
                                }
                                .padding(horizontal = 28.dp, vertical = 16.dp)
                        ) {
                            Text(
                                "Начать исследование",
                                style = MaterialTheme.typography.titleMedium.copy(
                                    fontFamily = DisplayFamily,
                                    fontWeight = FontWeight.SemiBold
                                ),
                                color = CityFogColors.Navy900
                            )
                        }
                    }
                } else {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        TextButton(
                            onClick = {
                                markOnboarded(context)
                                onFinish()
                            }
                        ) {
                            Text(
                                "Пропустить",
                                style = MaterialTheme.typography.titleSmall,
                                color = CityFogColors.TextMuted
                            )
                        }
                        // «Далее» — стеклянная капсула: второстепенное действие
                        // не спорит по яркости с будущим CTA.
                        Row(
                            modifier = Modifier
                                .glassPanel(18.dp)
                                .clickable {
                                    scope.launch {
                                        pagerState.animateScrollToPage(pagerState.currentPage + 1)
                                    }
                                }
                                .padding(horizontal = 22.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                "Далее",
                                style = MaterialTheme.typography.titleMedium,
                                color = CityFogColors.Cyan400
                            )
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                                contentDescription = null,
                                tint = CityFogColors.Cyan400,
                                modifier = Modifier
                                    .padding(start = 8.dp)
                                    .size(18.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun OnboardingPage(
    page: Int,
    pageCount: Int,
    pageOffset: Float,
    visible: Boolean
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        // Иллюстрация — сетка клеток «за стеклом», как карта под HUD-панелями.
        // Параллакс: картинка едет на четверть скорости и слегка гаснет по краям.
        Box(
            modifier = Modifier
                .graphicsLayer {
                    translationX = pageOffset * size.width * 0.25f
                    alpha = 1f - 0.4f * abs(pageOffset)
                }
                .size(180.dp)
                .glassPanel(28.dp),
            contentAlignment = Alignment.Center
        ) {
            when (page) {
                0 -> GridIllustration(openCenter = true, visible = visible)
                1 -> GridIllustration(openPath = true, visible = visible)
                2 -> GridIllustration(openDistricts = true, visible = visible)
            }
        }

        Spacer(Modifier.height(40.dp))

        val (title, desc) = when (page) {
            0 -> "Туман войны по клеткам" to "Казань разбита на квадратные клетки 12×12 метров. Когда ты проходишь клетку — она открывается навсегда."
            1 -> "Ходи, езжай, исследуй" to "Включи запись маршрута. GPS будет открывать клетки по ходу движения: пешком, на велосипеде или самокате."
            else -> "Закрывай районы" to "Следи за прогрессом по семи районам Казани. Первая цель — Ново-Савиновский на 100%!"
        }

        // Честная типографическая шкала: eyebrow 11sp → титул 26sp → текст 16sp.
        Text(
            "ШАГ ${page + 1} / $pageCount",
            style = MaterialTheme.typography.labelSmall,
            color = CityFogColors.Cyan400
        )
        Spacer(Modifier.height(10.dp))
        Text(
            title,
            style = MaterialTheme.typography.displaySmall,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onBackground
        )
        Spacer(Modifier.height(16.dp))
        Text(
            desc,
            style = MaterialTheme.typography.bodyLarge,
            textAlign = TextAlign.Center,
            color = CityFogColors.TextSecondary,
            modifier = Modifier.widthIn(max = 300.dp)
        )
    }
}

@Composable
private fun GridIllustration(
    openCenter: Boolean = false,
    openPath: Boolean = false,
    openDistricts: Boolean = false,
    visible: Boolean = true
) {
    val cells = 5
    val gap = 6.dp
    val cellSize = 18.dp
    // Порядок клеток «прогулки»: слева направо по улице, затем вниз по перекрёстку.
    val pathCells = listOf(2 to 0, 2 to 1, 2 to 2, 2 to 3, 3 to 3, 4 to 3)

    // Бегущее «сканирование» для страницы с маршрутом: t пробегает индексы клеток
    // пути с запасом в 2 такта, чтобы трейл держался целиком перед новым циклом.
    val scanT = if (openPath) {
        rememberInfiniteTransition(label = "trail").animateFloat(
            initialValue = 0f,
            targetValue = pathCells.size + 2f,
            animationSpec = infiniteRepeatable(tween(2400, easing = LinearEasing)),
            label = "trail-t"
        ).value
    } else 0f

    // Сквозной номер активной клетки — для входного stagger'а статичных страниц.
    var staggerIdx = 0
    Column(verticalArrangement = Arrangement.spacedBy(gap)) {
        repeat(cells) { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(gap)) {
                repeat(cells) { col ->
                    val active = when {
                        openCenter -> row in 1..3 && col in 1..3
                        openPath -> pathCells.contains(row to col)
                        openDistricts -> (row + col) % 2 == 0
                        else -> false
                    }
                    val cellAlpha: Float = when {
                        !active -> 1f
                        // Клетки пути зажигаются последовательно и зацикливаются.
                        openPath -> (scanT - pathCells.indexOf(row to col)).coerceIn(0f, 1f)
                        // Статичные клетки проявляются каскадом при показе страницы.
                        else -> {
                            val idx = staggerIdx++
                            val a by animateFloatAsState(
                                targetValue = if (visible) 1f else 0f,
                                animationSpec = tween(
                                    260,
                                    delayMillis = if (visible) idx * 60 else 0
                                ),
                                label = "cell-in"
                            )
                            a
                        }
                    }
                    Box(
                        modifier = Modifier
                            .size(cellSize)
                            .graphicsLayer { alpha = cellAlpha },
                        contentAlignment = Alignment.Center
                    ) {
                        if (active) {
                            // Мягкий ореол за клеткой — то же свечение, что у открытых
                            // зон на карте; выходит за границы слота осознанно.
                            Box(
                                Modifier
                                    .requiredSize(cellSize + 12.dp)
                                    .background(
                                        Brush.radialGradient(
                                            listOf(
                                                CityFogColors.Cyan400.copy(alpha = 0.35f),
                                                Color.Transparent
                                            )
                                        )
                                    )
                            )
                        }
                        Box(
                            modifier = Modifier
                                .size(cellSize)
                                .then(
                                    // Центральная клетка — «ты здесь», ей больше света.
                                    if (active && row == 2 && col == 2) Modifier.shadow(
                                        8.dp, RoundedCornerShape(4.dp),
                                        spotColor = CityFogColors.Cyan400
                                    ) else Modifier
                                )
                                .clip(RoundedCornerShape(4.dp))
                                .background(
                                    if (active) Brush.verticalGradient(
                                        listOf(CityFogColors.Cyan300, CityFogColors.Cyan400)
                                    )
                                    else SolidColor(CityFogColors.Navy600.copy(alpha = 0.5f))
                                )
                        )
                    }
                }
            }
        }
    }
}

@Preview(showBackground = true, backgroundColor = 0xFF050914, widthDp = 380, heightDp = 700)
@Composable
private fun OnboardingPagePreview() {
    CityFogTheme {
        Box(Modifier.background(CityFogColors.Void)) {
            OnboardingPage(page = 0, pageCount = 3, pageOffset = 0f, visible = true)
        }
    }
}

fun isOnboarded(context: Context): Boolean {
    return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        .getBoolean(KEY_ONBOARDED, false)
}

fun markOnboarded(context: Context) {
    context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        .edit().putBoolean(KEY_ONBOARDED, true).apply()
}
