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
 * Типографика CityFog — «ночной сканер», три голоса:
 *  - Unbounded (широкий футуро-гротеск, кириллица) — заголовки экранов и hero-цифры.
 *    Это лицо приложения; используется дозированно: display/headline/titleLarge.
 *  - Manrope — весь рабочий текст (title/body/label): современный гротеск с
 *    кириллицей, чтобы нигде не проглядывал системный Roboto.
 *  - JetBrains Mono — вся «телеметрия»: проценты, метры, таймеры, счётчики клеток.
 *    Моноширинные цифры не прыгают при анимации и дают приборный характер.
 * Все три — вариативные TTF из res/font, вес выбирается через FontVariation.
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

@OptIn(ExperimentalTextApi::class)
private fun manrope(weight: Int, fw: FontWeight) = Font(
    R.font.manrope_wght,
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

/** Текстовый шрифт — всё остальное. */
val TextFamily = FontFamily(
    manrope(400, FontWeight.Normal),
    manrope(500, FontWeight.Medium),
    manrope(600, FontWeight.SemiBold),
    manrope(700, FontWeight.Bold),
    manrope(800, FontWeight.ExtraBold)
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
    // Системный слот заголовков диалогов (AlertDialog и т.п.) — Unbounded,
    // как у остальных заголовков; телеметрия задаёт моно inline-стилем сама.
    headlineSmall = TextStyle(
        fontFamily = DisplayFamily, fontWeight = FontWeight.SemiBold,
        fontSize = 19.sp, lineHeight = 24.sp, letterSpacing = 0.sp
    ),
    titleLarge = TextStyle(
        fontFamily = DisplayFamily, fontWeight = FontWeight.Medium,
        fontSize = 17.sp, lineHeight = 24.sp, letterSpacing = 0.sp
    ),
    titleMedium = TextStyle(
        fontFamily = TextFamily, fontWeight = FontWeight.SemiBold,
        fontSize = 17.sp, lineHeight = 22.sp, letterSpacing = 0.sp
    ),
    titleSmall = TextStyle(
        fontFamily = TextFamily, fontWeight = FontWeight.SemiBold,
        fontSize = 15.sp, lineHeight = 20.sp, letterSpacing = 0.1.sp
    ),
    bodyLarge = TextStyle(
        fontFamily = TextFamily, fontWeight = FontWeight.Normal,
        fontSize = 16.sp, lineHeight = 24.sp, letterSpacing = 0.1.sp
    ),
    bodyMedium = TextStyle(
        fontFamily = TextFamily, fontWeight = FontWeight.Normal,
        fontSize = 14.sp, lineHeight = 20.sp, letterSpacing = 0.1.sp
    ),
    bodySmall = TextStyle(
        fontFamily = TextFamily, fontWeight = FontWeight.Normal,
        fontSize = 13.sp, lineHeight = 18.sp, letterSpacing = 0.1.sp
    ),
    // Системный слот текстов кнопок (TextButton, диалоги) — Manrope, не моно:
    // кириллица «Отмена»/«Далее» в моноширинном читалась как терминал.
    labelLarge = TextStyle(
        fontFamily = TextFamily, fontWeight = FontWeight.SemiBold,
        fontSize = 14.sp, lineHeight = 20.sp, letterSpacing = 0.1.sp
    ),
    labelMedium = TextStyle(
        fontFamily = TextFamily, fontWeight = FontWeight.Medium,
        fontSize = 12.sp, lineHeight = 16.sp, letterSpacing = 0.4.sp,
        fontFeatureSettings = TNUM
    ),
    // Надзаголовок-«eyebrow»: широкий разряд, под UPPERCASE.
    labelSmall = TextStyle(
        fontFamily = TextFamily, fontWeight = FontWeight.SemiBold,
        fontSize = 11.sp, lineHeight = 14.sp, letterSpacing = 1.4.sp
    )
)
