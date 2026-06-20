package ru.cityfog.ui.theme

import androidx.compose.ui.graphics.Color

// Цветовая палитра CityFog — ночной город + бирюзовое свечение тумана.
object CityFogColors {
    // Основа — глубокая полночная синь.
    val Navy900 = Color(0xFF0A0E27)
    val Navy800 = Color(0xFF111835)
    val Navy700 = Color(0xFF1A2342)
    val Navy600 = Color(0xFF243055)

    // Акценты — циан/бирюза, как кромка открытых клеток.
    val Cyan400 = Color(0xFF22D3EE)
    val Cyan300 = Color(0xFF67E8F9)
    val Cyan200 = Color(0xFFA5F3FC)
    val Teal500 = Color(0xFF14B8A6)
    val Teal400 = Color(0xFF2DD4BF)

    // Состояния.
    val Recording = Color(0xFFEF4444)
    val RecordingGlow = Color(0xFFF87171)
    val Success = Color(0xFF34D399)
    val Warning = Color(0xFFFBBF24)

    // Текст.
    val TextPrimary = Color(0xFFF8FAFC)
    val TextSecondary = Color(0xFFCBD5E1)
    val TextMuted = Color(0xFF94A3B8)

    // Районы (для UI-панели; должны узнаваться на карте).
    val DistrictColors = listOf(
        Color(0xFF22D3EE), // 1 Авиастроительный — cyan
        Color(0xFFA78BFA), // 2 Вахитовский — violet
        Color(0xFF34D399), // 3 Кировский — emerald
        Color(0xFFF472B6), // 4 Московский — pink
        Color(0xFFFBBF24), // 5 Ново-Савиновский — amber
        Color(0xFFFB923C), // 6 Приволжский — orange
        Color(0xFF60A5FA), // 7 Советский — blue
        Color(0xFF94A3B8)  // 8 Остальной город — slate
    )
}
