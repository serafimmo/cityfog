package ru.cityfog.map

import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.maps.Style
import org.maplibre.android.style.expressions.Expression
import org.maplibre.android.style.layers.FillLayer
import org.maplibre.android.style.layers.LineLayer
import org.maplibre.android.style.layers.Property
import org.maplibre.android.style.layers.PropertyFactory
import org.maplibre.android.style.sources.GeoJsonSource
import org.maplibre.geojson.Feature
import org.maplibre.geojson.FeatureCollection
import org.maplibre.geojson.MultiLineString
import org.maplibre.geojson.Point
import org.maplibre.geojson.Polygon
import ru.cityfog.data.VisitRepository
import ru.cityfog.grid.GridMapper

/**
 * «Туман войны» в стиле ночного города.
 *
 * 1) Тёмно-синий полигон затемняет всю видимую область; там, где ты прошёл — в нём вырезаны
 *    «дыры» (holes), сквозь которые видна яркая дневная карта. Соседние клетки объединяются
 *    в прямоугольники ([mergeRects]) — меньше вершин, нет швов.
 * 2) По кромке исследованной зоны рисуется мягко светящийся бирюзовый контур. Контур —
 *    это только ВНЕШНИЕ рёбра (между открытой и закрытой клеткой), без внутренней сетки.
 *
 * LOD: дыры и контур считаются только на уличном зуме (>= MIN_ZOOM); на городском остаётся
 * ровный туман (клетки 12 м всё равно мельче пикселя). Прозрачность тумана растёт с зумом,
 * чтобы вдали картой было удобно ориентироваться.
 */
class FogRenderer(style: Style) {

    private val fogSource = GeoJsonSource(FOG_SRC)
    private val edgeSource = GeoJsonSource(EDGE_SRC)

    init {
        style.addSource(fogSource)
        style.addSource(edgeSource)

        style.addLayer(
            FillLayer(FOG_FILL, FOG_SRC).withProperties(
                PropertyFactory.fillColor(FOG_COLOR),
                PropertyFactory.fillAntialias(true),
                PropertyFactory.fillOpacity(
                    Expression.interpolate(
                        Expression.linear(), Expression.zoom(),
                        Expression.stop(11f, 0.32f),
                        Expression.stop(15f, 0.56f)
                    )
                )
            )
        )
        // Внешнее размытое свечение кромки.
        style.addLayer(
            LineLayer(EDGE_GLOW, EDGE_SRC).withProperties(
                PropertyFactory.lineColor(GLOW_COLOR),
                PropertyFactory.lineWidth(6f),
                PropertyFactory.lineOpacity(0.35f),
                PropertyFactory.lineBlur(4f),
                PropertyFactory.lineCap(Property.LINE_CAP_ROUND),
                PropertyFactory.lineJoin(Property.LINE_JOIN_ROUND)
            )
        )
        // Яркое ядро линии поверх свечения.
        style.addLayer(
            LineLayer(EDGE_CORE, EDGE_SRC).withProperties(
                PropertyFactory.lineColor(CORE_COLOR),
                PropertyFactory.lineWidth(1.6f),
                PropertyFactory.lineOpacity(0.95f),
                PropertyFactory.lineCap(Property.LINE_CAP_ROUND),
                PropertyFactory.lineJoin(Property.LINE_JOIN_ROUND)
            )
        )
    }

    fun refresh(map: MapLibreMap) {
        val b = map.projection.visibleRegion.latLngBounds
        val ne = b.northEast
        val sw = b.southWest

        // Внешнее кольцо тумана с запасом 60% — чтобы при панораме не отставало от края.
        val dLat = (ne.latitude - sw.latitude) * 0.6
        val dLon = (ne.longitude - sw.longitude) * 0.6
        val rings = ArrayList<List<Point>>()
        rings.add(
            rectRing(
                sw.longitude - dLon, sw.latitude - dLat,
                ne.longitude + dLon, ne.latitude + dLat
            )
        )

        if (map.cameraPosition.zoom >= MIN_ZOOM) {
            // Берём клетки чуть шире экрана (15%), чтобы контур у кромки экрана не «обрезался».
            val mLat = (ne.latitude - sw.latitude) * 0.15
            val mLon = (ne.longitude - sw.longitude) * 0.15
            val ids = VisitRepository.cellsInBounds(
                sw.longitude - mLon, sw.latitude - mLat,
                ne.longitude + mLon, ne.latitude + mLat
            )
            if (ids.isNotEmpty()) {
                val set = HashSet(ids)
                // Дыры (объединённые прямоугольники). mergeRects съедает копию.
                for (r in mergeRects(HashSet(ids))) {
                    rings.add(
                        rectRing(
                            GridMapper.westLon(r[0]), GridMapper.southLat(r[1]),
                            GridMapper.eastLon(r[2]), GridMapper.northLat(r[3])
                        )
                    )
                }
                edgeSource.setGeoJson(Feature.fromGeometry(MultiLineString.fromLngLats(boundary(set))))
            } else {
                edgeSource.setGeoJson(FeatureCollection.fromFeatures(emptyList()))
            }
        } else {
            edgeSource.setGeoJson(FeatureCollection.fromFeatures(emptyList()))
        }

        fogSource.setGeoJson(Feature.fromGeometry(Polygon.fromLngLats(rings)))
    }

    /** Внешние рёбра области (ребро между открытой и закрытой клеткой) как отрезки. */
    private fun boundary(set: HashSet<Long>): List<List<Point>> {
        val segs = ArrayList<List<Point>>()
        for (id in set) {
            val x = GridMapper.unpackX(id)
            val y = GridMapper.unpackY(id)
            val w = GridMapper.westLon(x)
            val e = GridMapper.eastLon(x)
            val s = GridMapper.southLat(y)
            val n = GridMapper.northLat(y)
            if (!set.contains(GridMapper.packId(x, y + 1)))
                segs.add(listOf(Point.fromLngLat(w, n), Point.fromLngLat(e, n)))
            if (!set.contains(GridMapper.packId(x, y - 1)))
                segs.add(listOf(Point.fromLngLat(w, s), Point.fromLngLat(e, s)))
            if (!set.contains(GridMapper.packId(x - 1, y)))
                segs.add(listOf(Point.fromLngLat(w, s), Point.fromLngLat(w, n)))
            if (!set.contains(GridMapper.packId(x + 1, y)))
                segs.add(listOf(Point.fromLngLat(e, s), Point.fromLngLat(e, n)))
        }
        return segs
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
            var x1 = x0
            while (cells.contains(GridMapper.packId(x1 + 1, y0))) x1++
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
            for (yy in y0..y1) for (xx in x0..x1) cells.remove(GridMapper.packId(xx, yy))
            rects.add(intArrayOf(x0, y0, x1, y1))
        }
        return rects
    }

    companion object {
        private const val FOG_SRC = "fog-src"
        private const val EDGE_SRC = "fog-edge-src"
        private const val FOG_FILL = "fog-fill"
        private const val EDGE_GLOW = "fog-edge-glow"
        private const val EDGE_CORE = "fog-edge-core"
        private const val MIN_ZOOM = 13.5
        private const val FOG_COLOR = "#0A0E27"  // глубокая полночная синь
        private const val GLOW_COLOR = "#22D3EE" // cyan-400 — свечение кромки
        private const val CORE_COLOR = "#CFFAFE" // cyan-50 — яркое ядро контура
    }
}
