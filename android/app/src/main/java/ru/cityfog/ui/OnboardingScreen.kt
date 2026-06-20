package ru.cityfog.ui

import android.content.Context
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import ru.cityfog.ui.theme.CityFogColors

private const val PREFS_NAME = "cityfog_prefs"
private const val KEY_ONBOARDED = "onboarded"

@Composable
fun OnboardingScreen(onFinish: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val pagerState = rememberPagerState(pageCount = { 3 })

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        HorizontalPager(
            state = pagerState,
            modifier = Modifier.weight(1f)
        ) { page ->
            OnboardingPage(page)
        }

        // Индикатор страниц.
        Row(
            modifier = Modifier.padding(bottom = 24.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            repeat(pagerState.pageCount) { index ->
                val active = pagerState.currentPage == index
                Box(
                    modifier = Modifier
                        .size(width = if (active) 24.dp else 10.dp, height = 10.dp)
                        .clip(RoundedCornerShape(5.dp))
                        .background(
                            if (active) CityFogColors.Cyan400
                            else CityFogColors.Navy600
                        )
                )
            }
        }

        // Кнопки навигации.
        AnimatedVisibility(
            visible = pagerState.currentPage == pagerState.pageCount - 1,
            enter = fadeIn()
        ) {
            Button(
                onClick = {
                    markOnboarded(context)
                    onFinish()
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp),
                shape = RoundedCornerShape(16.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = CityFogColors.Cyan400,
                    contentColor = CityFogColors.Navy900
                )
            ) {
                Text(
                    "Начать исследование",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
            }
        }

        AnimatedVisibility(
            visible = pagerState.currentPage != pagerState.pageCount - 1,
            enter = fadeIn()
        ) {
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
                    Text("Пропустить", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Button(
                    onClick = {
                        scope.launch {
                            pagerState.animateScrollToPage(pagerState.currentPage + 1)
                        }
                    },
                    shape = RoundedCornerShape(16.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = CityFogColors.Navy700,
                        contentColor = CityFogColors.Cyan400
                    )
                ) {
                    Text("Далее", fontWeight = FontWeight.SemiBold)
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                        contentDescription = null,
                        modifier = Modifier.padding(start = 8.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun OnboardingPage(page: Int) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        // Иллюстрация — стилизованная сетка/туман.
        Box(
            modifier = Modifier
                .size(180.dp)
                .clip(RoundedCornerShape(32.dp))
                .background(CityFogColors.Navy800),
            contentAlignment = Alignment.Center
        ) {
            when (page) {
                0 -> GridIllustration(openCenter = true)
                1 -> GridIllustration(openPath = true)
                2 -> GridIllustration(openDistricts = true)
            }
        }

        Spacer(Modifier.height(40.dp))

        val (title, desc) = when (page) {
            0 -> "Туман войны по клеткам" to "Казань разбита на квадратные клетки 12×12 метров. Когда ты проходишь клетку — она открывается навсегда."
            1 -> "Ходи, езжай, исследуй" to "Включи запись маршрута. GPS будет открывать клетки по ходу движения: пешком, на велосипеде или самокате."
            else -> "Закрывай районы" to "Следи за прогрессом по семи районам Казани. Первая цель — Ново-Савиновский на 100%!"
        }

        Text(
            title,
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onBackground
        )
        Spacer(Modifier.height(16.dp))
        Text(
            desc,
            style = MaterialTheme.typography.bodyLarge,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun GridIllustration(
    openCenter: Boolean = false,
    openPath: Boolean = false,
    openDistricts: Boolean = false
) {
    val cells = 5
    val gap = 6.dp
    val cellSize = 18.dp
    Column(verticalArrangement = Arrangement.spacedBy(gap)) {
        repeat(cells) { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(gap)) {
                repeat(cells) { col ->
                    val active = when {
                        openCenter -> row in 1..3 && col in 1..3
                        openPath -> (row == 2 && col <= 3) || (col == 3 && row in 2..4)
                        openDistricts -> (row + col) % 2 == 0
                        else -> false
                    }
                    Box(
                        modifier = Modifier
                            .size(cellSize)
                            .clip(RoundedCornerShape(4.dp))
                            .background(
                                if (active) CityFogColors.Cyan400
                                else CityFogColors.Navy600.copy(alpha = 0.5f)
                            )
                    )
                }
            }
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
