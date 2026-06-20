package ru.cityfog.tracking

import android.content.Context

/**
 * Намерение пользователя «вести запись», сохранённое на диск.
 *
 * Нужно, потому что Android может убить процесс приложения, а сервис перезапустить
 * (START_STICKY) — тогда Compose-состояние кнопки сбрасывается в false и врёт.
 * А ещё по этому флагу [BootReceiver] решает, поднимать ли трекинг после перезагрузки.
 *
 * Флаг ставится в true только при ЯВНОМ старте и в false только при ЯВНОЙ остановке
 * (кнопка/«Стоп» в уведомлении). Системное убийство сервиса флаг НЕ трогает —
 * иначе автоперезапуск был бы невозможен.
 */
object TrackingState {
    private const val PREFS = "cityfog_prefs"
    private const val KEY = "tracking_active"

    fun setActive(context: Context, active: Boolean) {
        context.applicationContext
            .getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().putBoolean(KEY, active).apply()
    }

    fun isActive(context: Context): Boolean =
        context.applicationContext
            .getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getBoolean(KEY, false)
}
