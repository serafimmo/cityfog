package ru.cityfog.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import ru.cityfog.ui.theme.CityFogColors
import ru.cityfog.ui.theme.glassPanel

enum class Screen { Map, Stats, Achievements, Settings }

@Composable
fun MainScreen(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    var onboarded by remember { mutableStateOf(isOnboarded(context)) }

    if (!onboarded) {
        OnboardingScreen(onFinish = {
            onboarded = true
        })
        return
    }

    var selected by rememberSaveable { mutableIntStateOf(0) }
    val screens = Screen.entries

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

        NavigationBar(
            // Плавающий стеклянный док — та же поверхность, что у панелей на карте.
            modifier = Modifier
                .align(androidx.compose.ui.Alignment.BottomCenter)
                .padding(horizontal = 16.dp, vertical = 12.dp)
                .glassPanel(26.dp),
            containerColor = androidx.compose.ui.graphics.Color.Transparent,
            tonalElevation = 0.dp
        ) {
            screens.forEachIndexed { index, screen ->
                val selectedItem = selected == index
                val iconScale by animateFloatAsState(
                    targetValue = if (selectedItem) 1.18f else 1f,
                    animationSpec = spring(dampingRatio = 0.45f, stiffness = 420f),
                    label = "nav-icon-scale"
                )
                NavigationBarItem(
                    icon = {
                        Icon(
                            imageVector = when (screen) {
                                Screen.Map -> Icons.Default.Map
                                Screen.Stats -> Icons.Default.BarChart
                                Screen.Achievements -> Icons.Default.EmojiEvents
                                Screen.Settings -> Icons.Default.Settings
                            },
                            contentDescription = screen.name,
                            modifier = Modifier.scale(iconScale)
                        )
                    },
                    label = {
                        Text(
                            when (screen) {
                                Screen.Map -> "Карта"
                                Screen.Stats -> "Статистика"
                                Screen.Achievements -> "Награды"
                                Screen.Settings -> "Настройки"
                            }
                        )
                    },
                    selected = selectedItem,
                    onClick = { selected = index },
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = CityFogColors.Cyan400,
                        selectedTextColor = CityFogColors.Cyan400,
                        indicatorColor = CityFogColors.Navy700,
                        unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                        unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                )
            }
        }
    }
}
