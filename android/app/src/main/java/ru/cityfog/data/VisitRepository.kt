package ru.cityfog.data

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import ru.cityfog.grid.GridMapper
import ru.cityfog.mask.AvailabilityMask
import java.io.File
import java.util.Arrays

/**
 * Единый источник правды по открытым клеткам в рантайме.
 *
 * - in-memory HashSet<Long> для O(1) проверки "была ли" и быстрой выборки в bbox;
 * - персист через [VisitStore] (visited.bin);
 * - фильтрация по [AvailabilityMask]: фиксы вне города / в воде / на заводах НЕ считаются,
 *   поэтому все записанные клетки доступны → % = visited / totalAvailable честный;
 * - прогресс (общий и по районам) отдаётся в UI через StateFlow.
 */
object VisitRepository {

    data class DistrictProgress(val id: Int, val name: String, val visited: Int, val total: Int) {
        val percent: Float get() = if (total > 0) visited * 100f / total else 0f
    }

    data class Progress(
        val visited: Int,
        val totalAvailable: Int,
        val districts: List<DistrictProgress>
    ) {
        val percent: Float get() = if (totalAvailable > 0) visited * 100f / totalAvailable else 0f
    }

    private lateinit var store: VisitStore
    private val visited = HashSet<Long>()
    private val perDistrictVisited = IntArray(9) // индексы 1..8
    private val lock = Any()

    @Volatile private var initialized = false

    private val _progress = MutableStateFlow(Progress(0, 0, emptyList()))
    val progress: StateFlow<Progress> = _progress.asStateFlow()

    private val _revision = MutableStateFlow(0L)
    /** Бамп при каждой новой клетке — сигнал для перерисовки тумана. */
    val revision: StateFlow<Long> = _revision.asStateFlow()

    /** Идемпотентно: грузит маску и visited.bin, считает прогресс. Звать с фонового потока. */
    fun init(context: Context) {
        if (initialized) return
        synchronized(lock) {
            if (initialized) return
            AvailabilityMask.ensureLoaded(context)
            store = VisitStore(File(context.applicationContext.filesDir, "visited.bin"))
            for (id in store.loadAll()) visited.add(id)
            recomputeDistricts()
            initialized = true
        }
        emitProgress()
    }

    private fun recomputeDistricts() {
        Arrays.fill(perDistrictVisited, 0)
        for (id in visited) {
            val d = AvailabilityMask.districtOf(GridMapper.unpackX(id), GridMapper.unpackY(id))
            if (d in 1..8) perDistrictVisited[d]++
        }
    }

    /** Записать GPS-фикс. Возвращает true, если открылась НОВАЯ доступная клетка. */
    fun recordFix(lat: Double, lon: Double): Boolean {
        val cx = GridMapper.cellX(lon)
        val cy = GridMapper.cellY(lat)
        if (!GridMapper.inBounds(cx, cy)) return false
        if (!AvailabilityMask.isAvailable(cx, cy)) return false // вода/завод/вне города — мимо
        val id = GridMapper.packId(cx, cy)
        val isNew: Boolean
        synchronized(lock) {
            isNew = visited.add(id)
            if (isNew) {
                store.appendOne(id)
                val d = AvailabilityMask.districtOf(cx, cy)
                if (d in 1..8) perDistrictVisited[d]++
            }
        }
        if (isNew) {
            emitProgress()
            _revision.value = _revision.value + 1
        }
        return isNew
    }

    private fun emitProgress() {
        val visitedCount: Int
        val perCopy: IntArray
        synchronized(lock) {
            visitedCount = visited.size
            perCopy = perDistrictVisited.copyOf()
        }
        val districts = AvailabilityMask.presentDistricts().map { d ->
            DistrictProgress(d, AvailabilityMask.districtName(d), perCopy[d], AvailabilityMask.districtTotal(d))
        }
        _progress.value = Progress(visitedCount, AvailabilityMask.totalAvailable, districts)
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
