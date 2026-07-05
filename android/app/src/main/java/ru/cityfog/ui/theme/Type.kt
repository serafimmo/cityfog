package ru.cityfog.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import ru.cityfog.R

/**
 * Типографика CityFog — «ночной сканер»:
 *  - Unbounded (широкий футуро-гротеск, кириллица) — заголовки экранов и hero-цифры.
 *    Это лицо приложения; используется дозированно: display/headline/titleLarge.
 *  - JetBrains Mono — вся «телеметрия»: проценты, метры, таймеры, счётчики клеток.
 *    Моноширинные цифры не прыгают при анимации и дают приборный характер.
 *  - Системный шрифт — рабочий текст (body/label): нейтральный и читабельный.
 * Оба шрифта — вариативные TTF из res/font, вес выбирается через FontVariation.
 */

@OptIn(ExperimentalTextApi::class)
private fun unbounded(weight: Int, fw: FontWeight) = Font(
    R.font.unbounded_wght,
    weight = fw,
    variationSettings = FontVariation.Settings(FontVariation.weight(weight))
)

@OptIn(ExperimentalTextApi::class)
private fun mono(weight: Int, fw: FontWeight) = Font(
    R.font.jetbrains_mono_wght,
    weight = fw,
    variationSettings = FontVariation.Settings(FontVariation.weight(weight))
)

/** Дисплейный шрифт — заголовки, hero-цифры, wordmark. */
val DisplayFamily = FontFamily(
    unbounded(500, FontWeight.Medium),
    unbounded(600, FontWeight.SemiBold),
    unbounded(700, FontWeight.Bold),
    unbounded(800, FontWeight.ExtraBold)
)

/** Моноширинный шрифт — числа и телеметрия. */
val MonoFamily = FontFamily(
    mono(400, FontWeight.Normal),
    mono(500, FontWeight.Medium),
    mono(600, FontWeight.SemiBold),
    mono(700, FontWeight.Bold)
)

private val TNUM = "tnum"

val Typography = Typography(
    // Display — Unbounded широкий: размеры меньше обычного, трекинг нулевой.
    displayLarge = TextStyle(
        fontFamily = DisplayFamily, fontWeight = FontWeight.Bold,
        fontSize = 40.sp, lineHeight = 46.sp, letterSpacing = (-0.5).sp
    ),
    displayMedium = TextStyle(
        fontFamily = DisplayFamily, fontWeight = FontWeight.Bold,
        fontSize = 32.sp, lineHeight = 38.sp, letterSpacing = (-0.5).sp
    ),
    displaySmall = TextStyle(
        fontFamily = DisplayFamily, fontWeight = FontWeight.SemiBold,
        fontSize = 26.sp, lineHeight = 32.sp, letterSpacing = 0.sp
    ),
    headlineLarge = TextStyle(
        fontFamily = DisplayFamily, fontWeight = FontWeight.SemiBold,
        fontSize = 25.sp, lineHeight = 32.sp, letterSpacing = 0.sp
    ),
    headlineMedium = TextStyle(
        fontFamily = DisplayFamily, fontWeight = FontWeight.SemiBold,
        fontSize = 21.sp, lineHeight = 28.sp, letterSpacing = 0.sp
    ),
    // Значения быстрых метрик — телеметрия: моноширинные цифры.
    headlineSmall = TextStyle(
        fontFamily = MonoFamily, fontWeight = FontWeight.Bold,
        fontSize = 20.sp, lineHeight = 26.sp, letterSpacing = (-0.5).sp,
        fontFeatureSettings = TNUM
    ),
    titleLarge = TextStyle(
        fontFamily = DisplayFamily, fontWeight = FontWeight.Medium,
        fontSize = 17.sp, lineHeight = 24.sp, letterSpacing = 0.sp
    ),
    titleMedium = TextStyle(
        fontFamily = FontFamily.Default, fontWeight = FontWeight.SemiBold,
        fontSize = 17.sp, lineHeight = 22.sp, letterSpacing = 0.sp
    ),
    titleSmall = TextStyle(
        fontFamily = FontFamily.Default, fontWeight = FontWeight.SemiBold,
        fontSize = 15.sp, lineHeight = 20.sp, letterSpacing = 0.1.sp
    ),
    bodyLarge = TextStyle(
        fontFamily = FontFamily.Default, fontWeight = FontWeight.Normal,
        fontSize = 16.sp, lineHeight = 24.sp, letterSpacing = 0.1.sp
    ),
    bodyMedium = TextStyle(
        fontFamily = FontFamily.Default, fontWeight = FontWeight.Normal,
        fontSize = 14.sp, lineHeight = 20.sp, letterSpacing = 0.1.sp
    ),
    bodySmall = TextStyle(
        fontFamily = FontFamily.Default, fontWeight = FontWeight.Normal,
        fontSize = 13.sp, lineHeight = 18.sp, letterSpacing = 0.1.sp
    ),
    // Процент в кольце на карте и похожие плотные цифры — моно.
    labelLarge = TextStyle(
        fontFamily = MonoFamily, fontWeight = FontWeight.SemiBold,
        fontSize = 14.sp, lineHeight = 18.sp, letterSpacing = 0.sp,
        fontFeatureSettings = TNUM
    ),
    labelMedium = TextStyle(
        fontFamily = FontFamily.Default, fontWeight = FontWeight.Medium,
        fontSize = 12.sp, lineHeight = 16.sp, letterSpacing = 0.4.sp,
        fontFeatureSettings = TNUM
    ),
    // Надзаголовок-«eyebrow»: широкий разряд, под UPPERCASE.
    labelSmall = TextStyle(
        fontFamily = FontFamily.Default, fontWeight = FontWeight.SemiBold,
        fontSize = 11.sp, lineHeight = 14.sp, letterSpacing = 1.4.sp
    )
)
