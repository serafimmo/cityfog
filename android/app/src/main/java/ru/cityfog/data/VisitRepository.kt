package ru.cityfog.data

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import ru.cityfog.grid.GridMapper
import java.io.File

/**
 * Единый источник правды по открытым клеткам в рантайме.
 *
 * - in-memory HashSet<Long> для O(1) проверки "была ли клетка" и быстрой выборки в bbox;
 * - персист через [VisitStore] (visited.bin);
 * - StateFlow'ы для UI и рендера тумана.
 *
 * Это object-синглтон: и сервис трекинга, и UI обращаются к одному экземпляру.
 */
object VisitRepository {
    private lateinit var store: VisitStore
    private val visited = HashSet<Long>()
    private val lock = Any()

    @Volatile private var initialized = false

    private val _count = MutableStateFlow(0)
    /** Сколько всего клеток открыто. */
    val count: StateFlow<Int> = _count.asStateFlow()

    private val _revision = MutableStateFlow(0L)
    /** Бамп при каждой новой клетке — сигнал для перерисовки тумана. */
    val revision: StateFlow<Long> = _revision.asStateFlow()

    /** Идемпотентно: грузит visited.bin в память при первом вызове. */
    fun init(context: Context) {
        if (initialized) return
        synchronized(lock) {
            if (initialized) return
            store = VisitStore(File(context.applicationContext.filesDir, "visited.bin"))
            for (id in store.loadAll()) visited.add(id)
            _count.value = visited.size
            initialized = true
        }
    }

    /** Записать GPS-фикс. Возвращает true, если открылась НОВАЯ клетка. */
    fun recordFix(lat: Double, lon: Double): Boolean {
        val id = GridMapper.cellIdOrNull(lat, lon) ?: return false
        val isNew: Boolean
        synchronized(lock) {
            isNew = visited.add(id)
            if (isNew) {
                store.appendOne(id)
                _count.value = visited.size
            }
        }
        if (isNew) _revision.value = _revision.value + 1
        return isNew
    }

    /** cellId открытых клеток внутри прямоугольника координат (для отрисовки видимой области). */
    fun cellsInBounds(west: Double, south: Double, east: Double, north: Double): List<Long> {
        val minX = GridMapper.cellX(west)
        val maxX = GridMapper.cellX(east)
        val minY = GridMapper.cellY(south)
        val maxY = GridMapper.cellY(north)
        val out = ArrayList<Long>()
        synchronized(lock) {
            for (id in visited) {
                val x = GridMapper.unpackX(id)
                val y = GridMapper.unpackY(id)
                if (x in minX..maxX && y in minY..maxY) out.add(id)
            }
        }
        return out
    }
}
