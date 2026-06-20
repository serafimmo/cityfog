package ru.cityfog

import android.animation.ObjectAnimator
import android.os.Bundle
import android.view.View
import android.view.animation.AnticipateInterpolator
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.core.animation.doOnEnd
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import ru.cityfog.ui.MainScreen
import ru.cityfog.ui.theme.CityFogTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        val splashScreen = installSplashScreen()
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            CityFogTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    MainScreen(modifier = Modifier.fillMaxSize())
                }
            }
        }

        // Анимированный выход splash-иконки вверх, после чего появляется основной экран.
        splashScreen.setOnExitAnimationListener { splashView ->
            val slideUp = ObjectAnimator.ofFloat(
                splashView.view,
                View.TRANSLATION_Y,
                0f,
                -splashView.view.height.toFloat()
            )
            slideUp.apply {
                duration = 400
                interpolator = AnticipateInterpolator()
                doOnEnd { splashView.remove() }
                start()
            }
        }
    }
}
