package ru.cityfog.map

import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.maps.Style
import org.maplibre.android.style.layers.FillLayer
import org.maplibre.android.style.layers.LineLayer
import org.maplibre.android.style.layers.PropertyFactory
import org.maplibre.android.style.sources.GeoJsonSource
import org.maplibre.geojson.Feature
import org.maplibre.geojson.Point
import org.maplibre.geojson.Polygon
import ru.cityfog.data.VisitRepository
import ru.cityfog.grid.GridMapper

/**
 * «Туман войны». Вся видимая область затемнена одним тёмным полигоном, а там, где ты
 * прошёл — в этом полигоне вырезаны «дыры» (holes), сквозь которые видна яркая карта.
 *
 * Геометрия = ОДИН полигон: внешнее кольцо (видимая область с запасом) + по дыре на каждую
 * исследованную область. Чтобы не плодить тысячи дыр-квадратов (швы + тормоза), соседние
 * клетки объединяются в прямоугольники жадным алгоритмом ([mergeRects]).
 *
 * LOD: на уличном зуме рисуем дыры; на городском — просто ровный туман (отдельные клетки
 * 12 м всё равно меньше пикселя). При панораме/зуме и открытии клеток зовётся [refresh].
 */
class FogRenderer(style: Style) {

    private val source = GeoJsonSource(SOURCE_ID)

    init {
        style.addSource(source)
        style.addLayer(
            FillLayer(FILL_ID, SOURCE_ID).withProperties(
                PropertyFactory.fillColor(FOG_COLOR),
                PropertyFactory.fillOpacity(FOG_OPACITY),
                PropertyFactory.fillAntialias(true)
            )
        )
        // Светящийся бирюзовый кант по кромке тумана (внешнее кольцо уезжает за экран, его не видно).
        style.addLayer(
            LineLayer(LINE_ID, SOURCE_ID).withProperties(
                PropertyFactory.lineColor(RIM_COLOR),
                PropertyFactory.lineWidth(1.4f),
                PropertyFactory.lineOpacity(0.8f),
                PropertyFactory.lineBlur(1.0f)
            )
        )
    }

    fun refresh(map: MapLibreMap) {
        val b = map.projection.visibleRegion.latLngBounds
        val ne = b.northEast
        val sw = b.southWest
        // Внешнее кольцо с запасом 60% — чтобы при панораме туман не отставал от края экрана.
        val dLat = (ne.latitude - sw.latitude) * 0.6
        val dLon = (ne.longitude - sw.longitude) * 0.6
        val outer = rectRing(
            sw.longitude - dLon, sw.latitude - dLat,
            ne.longitude + dLon, ne.latitude + dLat
        )

        val rings = ArrayList<List<Point>>()
        rings.add(outer)

        if (map.cameraPosition.zoom >= MIN_ZOOM) {
            val ids = VisitRepository.cellsInBounds(
                sw.longitude, sw.latitude, ne.longitude, ne.latitude
            )
            if (ids.isNotEmpty()) {
                for (r in mergeRects(HashSet(ids))) {
                    rings.add(
                        rectRing(
                            GridMapper.westLon(r[0]), GridMapper.southLat(r[1]),
                            GridMapper.eastLon(r[2]), GridMapper.northLat(r[3])
                        )
                    )
                }
            }
        }

        source.setGeoJson(Feature.fromGeometry(Polygon.fromLngLats(rings)))
    }

    private fun rectRing(w: Double, s: Double, e: Double, n: Double): List<Point> = listOf(
        Point.fromLngLat(w, s),
        Point.fromLngLat(e, s),
        Point.fromLngLat(e, n),
        Point.fromLngLat(w, n),
        Point.fromLngLat(w, s)
    )

    /**
     * Жадное объединение клеток в максимальные прямоугольники (greedy meshing).
     * Возвращает список [x0, y0, x1, y1] (включительно) в индексах клеток.
     */
    private fun mergeRects(cells: HashSet<Long>): List<IntArray> {
        val rects = ArrayList<IntArray>()
        val sorted = cells.sortedWith(
            compareBy({ GridMapper.unpackY(it) }, { GridMapper.unpackX(it) })
        )
        for (id in sorted) {
            if (id !in cells) continue
            val x0 = GridMapper.unpackX(id)
            val y0 = GridMapper.unpackY(id)
            // Растём вправо по X, пока клетки есть.
            var x1 = x0
            while (cells.contains(GridMapper.packId(x1 + 1, y0))) x1++
            // Растём вверх по Y, пока вся строка [x0..x1] заполнена.
            var y1 = y0
            grow@ while (true) {
                val ny = y1 + 1
                var x = x0
                while (x <= x1) {
                    if (!cells.contains(GridMapper.packId(x, ny))) break@grow
                    x++
                }
                y1 = ny
            }
            // Помечаем прямоугольник использованным.
            for (yy in y0..y1) for (xx in x0..x1) cells.remove(GridMapper.packId(xx, yy))
            rects.add(intArrayOf(x0, y0, x1, y1))
        }
        return rects
    }

    companion object {
        private const val SOURCE_ID = "fog-src"
        private const val FILL_ID = "fog-fill"
        private const val LINE_ID = "fog-rim"
        private const val MIN_ZOOM = 13.5
        private const val FOG_COLOR = "#0B1026"  // глубокий тёмно-синий «неизведанное»
        private const val FOG_OPACITY = 0.55f
        private const val RIM_COLOR = "#5EEAD4"  // бирюзовый кант исследованной зоны
    }
}
