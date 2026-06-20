package ru.cityfog.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.background
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import ru.cityfog.ui.theme.CityFogColors

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
        AnimatedVisibility(
            visible = selected == 0,
            enter = fadeIn(),
            exit = fadeOut()
        ) {
            MapScreen(modifier = Modifier.fillMaxSize())
        }

        AnimatedVisibility(
            visible = selected == 1,
            enter = slideInHorizontally { it },
            exit = slideOutHorizontally { it }
        ) {
            StatsScreen(modifier = Modifier.fillMaxSize())
        }

        AnimatedVisibility(
            visible = selected == 2,
            enter = slideInHorizontally { it },
            exit = slideOutHorizontally { it }
        ) {
            AchievementsScreen(modifier = Modifier.fillMaxSize())
        }

        AnimatedVisibility(
            visible = selected == 3,
            enter = slideInHorizontally { it },
            exit = slideOutHorizontally { it }
        ) {
            SettingsScreen(modifier = Modifier.fillMaxSize())
        }

        NavigationBar(
            modifier = Modifier
                .align(androidx.compose.ui.Alignment.BottomCenter)
                .padding(horizontal = 16.dp, vertical = 12.dp)
                .clip(RoundedCornerShape(24.dp)),
            containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.95f),
            tonalElevation = 8.dp
        ) {
            screens.forEachIndexed { index, screen ->
                val selectedItem = selected == index
                NavigationBarItem(
                    icon = {
                        Icon(
                            imageVector = when (screen) {
                                Screen.Map -> Icons.Default.Map
                                Screen.Stats -> Icons.Default.BarChart
                                Screen.Achievements -> Icons.Default.EmojiEvents
                                Screen.Settings -> Icons.Default.Settings
                            },
                            contentDescription = screen.name
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
