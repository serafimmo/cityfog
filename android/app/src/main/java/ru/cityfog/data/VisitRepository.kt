package ru.cityfog.data

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import ru.cityfog.grid.GridMapper
import ru.cityfog.mask.AvailabilityMask
import java.io.File
import java.util.Arrays
import java.util.Calendar
import java.util.TimeZone
import java.util.concurrent.TimeUnit

/**
 * Единый источник правды по открытым клеткам в рантайме.
 *
 * - in-memory HashMap<Long, Long> (cellId -> firstVisitTs) для O(1) проверки и статистики;
 * - персист через [VisitStore] (visited.bin);
 * - фильтрация по [AvailabilityMask];
 * - прогресс и статистика отдаются в UI через StateFlow.
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

    data class Stats(
        val totalCells: Int,
        val todayCells: Int,
        val weekCells: Int,
        val monthCells: Int,
        val streakDays: Int,
        val bestDayCells: Int,
        val topDistrictId: Int,
        val topDistrictName: String,
        val topDistrictPercent: Float,
        // Открыто клеток по дням за последние DAILY_DAYS дней (старые→новые, последний = сегодня).
        val dailyCells: IntArray = IntArray(DAILY_DAYS)
    )

    const val DAILY_DAYS = 14

    private lateinit var store: VisitStore
    private val visited = HashMap<Long, Long>()
    private val perDistrictVisited = IntArray(9) // индексы 1..8
    private val lock = Any()

    @Volatile private var initialized = false

    private val _progress = MutableStateFlow(Progress(0, 0, emptyList()))
    val progress: StateFlow<Progress> = _progress.asStateFlow()

    private val _stats = MutableStateFlow(emptyStats())
    val stats: StateFlow<Stats> = _stats.asStateFlow()

    private val _revision = MutableStateFlow(0L)
    /** Бамп при каждой новой клетке — сигнал для перерисовки тумана. */
    val revision: StateFlow<Long> = _revision.asStateFlow()

    private val _lastOpened = MutableStateFlow(0L)
    /** cellId последней открытой клетки — для «вспышки» на карте. */
    val lastOpened: StateFlow<Long> = _lastOpened.asStateFlow()

    /** Идемпотентно: грузит маску и visited.bin, считает прогресс и статистику. */
    fun init(context: Context) {
        if (initialized) return
        synchronized(lock) {
            if (initialized) return
            AvailabilityMask.ensureLoaded(context)
            store = VisitStore(File(context.applicationContext.filesDir, "visited.bin"))
            for ((id, ts) in store.loadAll()) visited[id] = ts
            recomputeDistricts()
            initialized = true
        }
        emitProgress()
        emitStats()
    }

    private fun recomputeDistricts() {
        Arrays.fill(perDistrictVisited, 0)
        for (id in visited.keys) {
            val d = AvailabilityMask.districtOf(GridMapper.unpackX(id), GridMapper.unpackY(id))
            if (d in 1..8) perDistrictVisited[d]++
        }
    }

    /** Записать GPS-фикс. Возвращает true, если открылась НОВАЯ доступная клетка. */
    fun recordFix(lat: Double, lon: Double, ts: Long = System.currentTimeMillis()): Boolean {
        val cx = GridMapper.cellX(lon)
        val cy = GridMapper.cellY(lat)
        if (!GridMapper.inBounds(cx, cy)) return false
        if (!AvailabilityMask.isAvailable(cx, cy)) return false
        val id = GridMapper.packId(cx, cy)
        val isNew: Boolean
        synchronized(lock) {
            isNew = !visited.containsKey(id)
            if (isNew) {
                visited[id] = ts
                store.appendOne(id, ts)
                val d = AvailabilityMask.districtOf(cx, cy)
                if (d in 1..8) perDistrictVisited[d]++
            }
        }
        if (isNew) {
            emitProgress()
            emitStats()
            _revision.value = _revision.value + 1
            _lastOpened.value = id
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

    private fun emitStats() {
        val entries: List<Pair<Long, Long>>
        synchronized(lock) { entries = visited.toList() }
        _stats.value = computeStats(entries)
    }

    private fun computeStats(entries: List<Pair<Long, Long>>): Stats {
        if (entries.isEmpty()) return emptyStats()

        val now = System.currentTimeMillis()
        val todayStart = startOfDay(now)
        val weekStart = todayStart - TimeUnit.DAYS.toMillis(6)
        val monthStart = todayStart - TimeUnit.DAYS.toMillis(29)

        var today = 0
        var week = 0
        var month = 0
        val perDay = HashMap<Long, Int>()
        val perDistrict = IntArray(9)

        for ((id, ts) in entries) {
            if (ts >= todayStart) today++
            if (ts >= weekStart) week++
            if (ts >= monthStart) month++
            perDay[startOfDay(ts)] = perDay.getOrDefault(startOfDay(ts), 0) + 1
            val d = AvailabilityMask.districtOf(GridMapper.unpackX(id), GridMapper.unpackY(id))
            if (d in 1..8) perDistrict[d]++
        }

        val bestDay = perDay.values.maxOrNull() ?: 0
        val streak = computeStreak(perDay.keys.sorted())

        // Ряд «клеток в день» за последние DAILY_DAYS дней: индекс 0 — самый старый, последний — сегодня.
        val daily = IntArray(DAILY_DAYS)
        for (i in 0 until DAILY_DAYS) {
            val dayStart = todayStart - TimeUnit.DAYS.toMillis((DAILY_DAYS - 1 - i).toLong())
            daily[i] = perDay.getOrDefault(dayStart, 0)
        }

        val topId = (1..8).maxByOrNull { perDistrict[it] } ?: 0
        val topName = if (topId in 1..8) AvailabilityMask.districtName(topId) else ""
        val topTotal = if (topId in 1..8) AvailabilityMask.districtTotal(topId) else 0
        val topPercent = if (topTotal > 0) perDistrict[topId] * 100f / topTotal else 0f

        return Stats(
            totalCells = entries.size,
            todayCells = today,
            weekCells = week,
            monthCells = month,
            streakDays = streak,
            bestDayCells = bestDay,
            topDistrictId = topId,
            topDistrictName = topName,
            topDistrictPercent = topPercent,
            dailyCells = daily
        )
    }

    private fun startOfDay(ts: Long): Long {
        val cal = Calendar.getInstance(TimeZone.getDefault())
        cal.timeInMillis = ts
        cal.set(Calendar.HOUR_OF_DAY, 0)
        cal.set(Calendar.MINUTE, 0)
        cal.set(Calendar.SECOND, 0)
        cal.set(Calendar.MILLISECOND, 0)
        return cal.timeInMillis
    }

    private fun computeStreak(sortedDays: List<Long>): Int {
        if (sortedDays.isEmpty()) return 0
        val today = startOfDay(System.currentTimeMillis())
        if (sortedDays.last() < today - TimeUnit.DAYS.toMillis(1)) return 0
        var streak = 1
        for (i in sortedDays.size - 1 downTo 1) {
            if (sortedDays[i] - sortedDays[i - 1] == TimeUnit.DAYS.toMillis(1)) {
                streak++
            } else {
                break
            }
        }
        return streak
    }

    /** cellId открытых клеток внутри прямоугольника координат (для отрисовки видимой области). */
    fun cellsInBounds(west: Double, south: Double, east: Double, north: Double): List<Long> {
        val minX = GridMapper.cellX(west)
        val maxX = GridMapper.cellX(east)
        val minY = GridMapper.cellY(south)
        val maxY = GridMapper.cellY(north)
        val out = ArrayList<Long>()
        synchronized(lock) {
            for (id in visited.keys) {
                val x = GridMapper.unpackX(id)
                val y = GridMapper.unpackY(id)
                if (x in minX..maxX && y in minY..maxY) out.add(id)
            }
        }
        return out
    }

    /** Пары [cellId, firstVisitTs] внутри прямоугольника — для отрисовки со «свежестью». */
    fun cellsInBoundsWithTs(west: Double, south: Double, east: Double, north: Double): List<LongArray> {
        val minX = GridMapper.cellX(west)
        val maxX = GridMapper.cellX(east)
        val minY = GridMapper.cellY(south)
        val maxY = GridMapper.cellY(north)
        val out = ArrayList<LongArray>()
        synchronized(lock) {
            for ((id, ts) in visited) {
                val x = GridMapper.unpackX(id)
                val y = GridMapper.unpackY(id)
                if (x in minX..maxX && y in minY..maxY) out.add(longArrayOf(id, ts))
            }
        }
        return out
    }

    /** Снимок id всех открытых клеток (для тепловой карты). */
    fun allCells(): LongArray {
        synchronized(lock) { return visited.keys.toLongArray() }
    }

    fun emptyStats() = Stats(0, 0, 0, 0, 0, 0, 0, "", 0f)

    /** Очистить все посещённые клетки. */
    fun clearAll(context: Context) {
        synchronized(lock) {
            visited.clear()
            Arrays.fill(perDistrictVisited, 0)
            store.clear()
        }
        emitProgress()
        emitStats()
        _revision.value = _revision.value + 1
    }
}
