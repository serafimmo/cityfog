package ru.cityfog.grid

import kotlin.math.cos
import kotlin.math.floor

/**
 * Координатная сетка Казани. Чистые функции lat/lon <-> клетка <-> cellId.
 *
 * КРИТИЧНО: эти константы и формулы ОБЯЗАНЫ один-в-один совпадать с
 * tools/grid_config.py. Если разойдутся — пиксели растровой маски (доступность/
 * районы) перестанут совпадать с клетками на телефоне.
 *
 * Проекция — локальная equirectangular ("плоская земля") вокруг Казани.
 * cellX растёт на восток, cellY — на север. Origin = юго-западный угол.
 */
object GridMapper {
    const val CELL_SIZE_M = 12.0
    const val M_PER_DEG_LAT = 111_320.0
    const val LAT0 = 55.79
    // ≈ 62587.19 м на 1° долготы на широте Казани.
    val M_PER_DEG_LON = M_PER_DEG_LAT * cos(Math.toRadians(LAT0))

    const val LAT_ORIGIN = 55.58
    const val LON_ORIGIN = 48.78
    const val LAT_MAX = 55.96
    const val LON_MAX = 49.40

    // Размеры выведены из охватывающего прямоугольника — должны совпадать с grid_config.py.
    val WIDTH = floor((LON_MAX - LON_ORIGIN) * M_PER_DEG_LON / CELL_SIZE_M).toInt()   // 3233
    val HEIGHT = floor((LAT_MAX - LAT_ORIGIN) * M_PER_DEG_LAT / CELL_SIZE_M).toInt()  // 3525

    fun cellX(lon: Double): Int = floor((lon - LON_ORIGIN) * M_PER_DEG_LON / CELL_SIZE_M).toInt()
    fun cellY(lat: Double): Int = floor((lat - LAT_ORIGIN) * M_PER_DEG_LAT / CELL_SIZE_M).toInt()

    fun inBounds(cx: Int, cy: Int): Boolean = cx in 0 until WIDTH && cy in 0 until HEIGHT

    // cellId упаковывает (cellX, cellY) в один Long: старшие 32 бита — X, младшие — Y.
    fun packId(cx: Int, cy: Int): Long = (cx.toLong() shl 32) or (cy.toLong() and 0xFFFFFFFFL)
    fun unpackX(id: Long): Int = (id ushr 32).toInt()
    fun unpackY(id: Long): Int = (id and 0xFFFFFFFFL).toInt()

    /** lat/lon -> cellId, либо null если точка вне охватывающего прямоугольника Казани. */
    fun cellIdOrNull(lat: Double, lon: Double): Long? {
        val cx = cellX(lon)
        val cy = cellY(lat)
        return if (inBounds(cx, cy)) packId(cx, cy) else null
    }

    // Границы клетки в координатах — для отрисовки квадрата.
    fun westLon(cx: Int): Double = LON_ORIGIN + cx * CELL_SIZE_M / M_PER_DEG_LON
    fun eastLon(cx: Int): Double = LON_ORIGIN + (cx + 1) * CELL_SIZE_M / M_PER_DEG_LON
    fun southLat(cy: Int): Double = LAT_ORIGIN + cy * CELL_SIZE_M / M_PER_DEG_LAT
    fun northLat(cy: Int): Double = LAT_ORIGIN + (cy + 1) * CELL_SIZE_M / M_PER_DEG_LAT
}
