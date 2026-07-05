package ru.cityfog.tracking

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Живая сессия прогулки: от нажатия «Старт» до «Стоп».
 *
 * Держит время старта, накопленные метры и счётчик клеток на момент старта —
 * из этого UI строит живой HUD на карте и итоговую карточку прогулки.
 *
 * Старт и дистанция дублируются в SharedPreferences: Android может убить процесс
 * и перезапустить сервис (START_STICKY) — тогда сессия продолжается с того же
 * места, а не обнуляется.
 */
object SessionTracker {

    data class Session(
        val active: Boolean = false,
        val startTs: Long = 0L,
        val distanceM: Double = 0.0,
        /** Сколько клеток было открыто ВСЕГО на момент старта (базовая отметка). */
        val startCells: Int = 0
    )

    private const val PREFS = "cityfog_prefs"
    private const val KEY_START_TS = "session_start_ts"
    private const val KEY_START_CELLS = "session_start_cells"
    private const val KEY_DISTANCE_BITS = "session_distance_bits"

    // Скачок больше 200 м между соседними фиксами — GPS-телепорт, в путь не считаем.
    private const val MAX_SEGMENT_M = 200.0
    private const val EARTH_R = 6_371_000.0

    private val _session = MutableStateFlow(Session())
    val session: StateFlow<Session> = _session.asStateFlow()

    private var lastLat = Double.NaN
    private var lastLon = Double.NaN

    /** Начать новую сессию; если процесс перезапускался при активной записи — продолжить старую. */
    fun startOrResume(context: Context, currentCells: Int) {
        if (_session.value.active) return
        val p = prefs(context)
        val savedStart = p.getLong(KEY_START_TS, 0L)
        if (savedStart > 0L) {
            _session.value = Session(
                active = true,
                startTs = savedStart,
                distanceM = Double.fromBits(p.getLong(KEY_DISTANCE_BITS, 0L)),
                startCells = p.getInt(KEY_START_CELLS, currentCells)
            )
        } else {
            val now = System.currentTimeMillis()
            p.edit()
                .putLong(KEY_START_TS, now)
                .putInt(KEY_START_CELLS, currentCells)
                .putLong(KEY_DISTANCE_BITS, 0.0.toRawBits())
                .apply()
            _session.value = Session(true, now, 0.0, currentCells)
        }
        lastLat = Double.NaN
        lastLon = Double.NaN
    }

    /** Учесть очередной GPS-фикс (уже прошедший фильтр точности). */
    fun onFix(context: Context, lat: Double, lon: Double) {
        val s = _session.value
        if (!s.active) return
        if (!lastLat.isNaN()) {
            val seg = haversine(lastLat, lastLon, lat, lon)
            if (seg <= MAX_SEGMENT_M) {
                val d = s.distanceM + seg
                _session.value = s.copy(distanceM = d)
                prefs(context).edit().putLong(KEY_DISTANCE_BITS, d.toRawBits()).apply()
            }
        }
        lastLat = lat
        lastLon = lon
    }

    /** Завершить сессию. Возвращает финальный снимок или null, если сессии не было. */
    fun finish(context: Context): Session? {
        val s = _session.value
        prefs(context).edit()
            .remove(KEY_START_TS)
            .remove(KEY_START_CELLS)
            .remove(KEY_DISTANCE_BITS)
            .apply()
        _session.value = Session()
        lastLat = Double.NaN
        lastLon = Double.NaN
        return if (s.active) s else null
    }

    private fun prefs(context: Context) =
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    private fun haversine(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val dLat = Math.toRadians(lat2 - lat1)
        val dLon = Math.toRadians(lon2 - lon1)
        val a = sin(dLat / 2) * sin(dLat / 2) +
            sin(dLon / 2) * sin(dLon / 2) *
            cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2))
        return EARTH_R * 2 * atan2(sqrt(a), sqrt(1 - a))
    }
}
