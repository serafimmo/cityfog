package ru.cityfog.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ru.cityfog.ui.theme.CityFogColors
import ru.cityfog.ui.theme.CityFogRadius
import ru.cityfog.ui.theme.glassPanel

enum class Screen { Map, Stats, Achievements, Settings }

/**
 * Единый зазор снизу под плавающий glass-док: контент вкладок и кнопки карты
 * отступают на одну и ту же величину, чтобы ничего не ныряло под стекло.
 */
internal val DockClearance = 100.dp

@Composable
fun MainScreen(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    var onboarded by remember { mutableStateOf(isOnboarded(context)) }

    // Онбординг не обрубается жёстким катом: после «Начать исследование» он
    // мягко утапливается назад, а приложение проявляется сквозь него — тот же
    // киноязык, что у растворения сплэша на карте.
    AnimatedContent(
        targetState = onboarded,
        transitionSpec = {
            fadeIn(tween(600, easing = FastOutSlowInEasing)) togetherWith
                (fadeOut(tween(350)) + scaleOut(targetScale = 0.94f, animationSpec = tween(350)))
        },
        label = "onboarding-gate",
        modifier = modifier
    ) { ob ->
        if (ob) {
            MainContent(modifier = Modifier.fillMaxSize())
        } else {
            OnboardingScreen(onFinish = { onboarded = true })
        }
    }
}

@Composable
private fun MainContent(modifier: Modifier = Modifier) {
    var selected by rememberSaveable { mutableIntStateOf(0) }

    Box(modifier = modifier.fillMaxSize()) {
        // Карта всегда живёт в композиции, чтобы не пересоздавать MapView.
        // Единый плавный переход между вкладками: мягкий кросс-фейд с лёгким подъёмом.
        val tabEnter = fadeIn(tween(260)) + slideInVertically(tween(260)) { it / 14 }
        val tabExit = fadeOut(tween(180))

        AnimatedVisibility(
            visible = selected == 0,
            enter = fadeIn(tween(260)),
            exit = fadeOut(tween(180))
        ) {
            MapScreen(modifier = Modifier.fillMaxSize())
        }

        AnimatedVisibility(visible = selected == 1, enter = tabEnter, exit = tabExit) {
            StatsScreen(modifier = Modifier.fillMaxSize())
        }

        AnimatedVisibility(visible = selected == 2, enter = tabEnter, exit = tabExit) {
            AchievementsScreen(modifier = Modifier.fillMaxSize())
        }

        AnimatedVisibility(visible = selected == 3, enter = tabEnter, exit = tabExit) {
            SettingsScreen(modifier = Modifier.fillMaxSize())
        }

        GlassDock(
            selected = selected,
            onSelect = { selected = it },
            modifier = Modifier.align(Alignment.BottomCenter)
        )
    }
}

/**
 * Собственный плавающий док вместо стокового NavigationBar: без раздутой высоты
 * с проглоченным жестовым пиллом, без серой M3-пилюли индикатора и без ripple.
 */
@Composable
private fun GlassDock(
    selected: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(horizontal = 16.dp, vertical = 10.dp)
            .glassPanel(CityFogRadius.Card)
            .height(68.dp),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Screen.entries.forEachIndexed { index, screen ->
            DockItem(
                screen = screen,
                selected = selected == index,
                onClick = { onSelect(index) }
            )
        }
    }
}

@Composable
private fun DockItem(screen: Screen, selected: Boolean, onClick: () -> Unit) {
    val tint by animateColorAsState(
        targetValue = if (selected) CityFogColors.Cyan400 else CityFogColors.TextMuted,
        animationSpec = tween(250),
        label = "dock-tint"
    )
    // Селекция — не пилюля, а мягкое неоновое свечение, разгорающееся за иконкой.
    val glow by animateFloatAsState(
        targetValue = if (selected) 1f else 0f,
        animationSpec = tween(250),
        label = "dock-glow"
    )
    val iconScale by animateFloatAsState(
        targetValue = if (selected) 1.18f else 1f,
        animationSpec = spring(dampingRatio = 0.45f, stiffness = 420f),
        label = "dock-icon-scale"
    )
    Column(
        modifier = Modifier
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null
            ) { onClick() }
            .padding(horizontal = 10.dp, vertical = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(contentAlignment = Alignment.Center) {
            Box(
                Modifier
                    .size(40.dp)
                    .graphicsLayer { alpha = glow }
                    .background(
                        Brush.radialGradient(
                            listOf(
                                CityFogColors.Cyan400.copy(alpha = 0.30f),
                                Color.Transparent
                            )
                        )
                    )
            )
            Icon(
                imageVector = when (screen) {
                    Screen.Map -> Icons.Default.Map
                    Screen.Stats -> Icons.Default.BarChart
                    Screen.Achievements -> Icons.Default.EmojiEvents
                    Screen.Settings -> Icons.Default.Settings
                },
                contentDescription = null,
                tint = tint,
                modifier = Modifier
                    .size(24.dp)
                    .scale(iconScale)
            )
        }
        Spacer(Modifier.height(2.dp))
        Text(
            when (screen) {
                Screen.Map -> "Карта"
                Screen.Stats -> "Статистика"
                Screen.Achievements -> "Награды"
                Screen.Settings -> "Настройки"
            },
            style = MaterialTheme.typography.labelMedium.copy(
                fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                letterSpacing = 0.3.sp
            ),
            color = tint
        )
    }
}
