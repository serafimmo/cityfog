package ru.cityfog

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import ru.cityfog.ui.MapScreen
import ru.cityfog.ui.theme.CityFogTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Маску/клетки грузит MapScreen на фоне (см. LaunchedEffect в MapScreen).
        enableEdgeToEdge()
        setContent {
            CityFogTheme {
                // Карта во весь экран (edge-to-edge); отступы под системные бары — у оверлеев.
                Surface(modifier = Modifier.fillMaxSize()) {
                    MapScreen(modifier = Modifier.fillMaxSize())
                }
            }
        }
    }
}
