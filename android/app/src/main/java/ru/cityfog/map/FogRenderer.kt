package ru.cityfog.map

import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.maps.Style
import org.maplibre.android.style.expressions.Expression
import org.maplibre.android.style.layers.FillLayer
import org.maplibre.android.style.layers.HeatmapLayer
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
    private val fogLayer = FillLayer(FOG_FILL, FOG_SRC)
    private val claimedSource = GeoJsonSource(CLAIMED_SRC)
    private val claimedGlow = FillLayer(CLAIMED_GLOW, CLAIMED_SRC)
    private val claimedFill = FillLayer(CLAIMED_FILL, CLAIMED_SRC)
    private val edgeSource = GeoJsonSource(EDGE_SRC)
    private val heatSource = GeoJsonSource(HEAT_SRC)
    private val heatLayer = HeatmapLayer(HEAT_LAYER, HEAT_SRC)
    private var heatOn = false

    init {
        style.addSource(fogSource)
        style.addSource(claimedSource)
        style.addSource(edgeSource)
        style.addSource(heatSource)

        // Прозрачность задаём плавно из кода в refresh() (надёжнее зум-выражения стиля,
        // которое на части рендереров вырождалось в ~0 — туман пропадал).
        fogLayer.setProperties(
            PropertyFactory.fillColor(FOG_COLOR),
            PropertyFactory.fillAntialias(true),
            PropertyFactory.fillOpacity(0.45f)
        )
        style.addLayer(fogLayer)

        // «Захваченная территория»: два слоя поверх тумана.
        //  1) Глубокий синий подслой (большая зона, чтобы было «тело» свечения).
        claimedGlow.setProperties(
            PropertyFactory.fillColor(GLOW_DEEP),
            PropertyFactory.fillAntialias(true),
            PropertyFactory.fillOpacity(0.22f)
        )
        style.addLayer(claimedGlow)
        //  2) Бирюзовая «засветка» сверху — собственно цвет освоенной зоны.
        claimedFill.setProperties(
            PropertyFactory.fillColor(GLOW_COLOR),
            PropertyFactory.fillAntialias(true),
            PropertyFactory.fillOpacity(0.26f)
        )
        style.addLayer(claimedFill)

        // Внешнее размытое свечение кромки — широкое и мягкое, как неоновый ореол.
        style.addLayer(
            LineLayer(EDGE_GLOW, EDGE_SRC).withProperties(
                PropertyFactory.lineColor(GLOW_COLOR),
                PropertyFactory.lineWidth(9f),
                PropertyFactory.lineOpacity(0.5f),
                PropertyFactory.lineBlur(8f),
                PropertyFactory.lineCap(Property.LINE_CAP_ROUND),
                PropertyFactory.lineJoin(Property.LINE_JOIN_ROUND)
            )
        )
        // Яркое ядро линии поверх свечения.
        style.addLayer(
            LineLayer(EDGE_CORE, EDGE_SRC).withProperties(
                PropertyFactory.lineColor(CORE_COLOR),
                PropertyFactory.lineWidth(1.8f),
                PropertyFactory.lineOpacity(0.95f),
                PropertyFactory.lineBlur(0.5f),
                PropertyFactory.lineCap(Property.LINE_CAP_ROUND),
                PropertyFactory.lineJoin(Property.LINE_JOIN_ROUND)
            )
        )

        // Тепловая карта (по умолчанию выключена): «где я бывал чаще/плотнее всего».
        // Плотность открытых клеток превращается в свечение от синего к бело-горячему.
        heatLayer.setProperties(
            PropertyFactory.heatmapColor(
                Expression.interpolate(
                    Expression.linear(), Expression.heatmapDensity(),
                    Expression.stop(0.0, Expression.rgba(10, 14, 39, 0.0)),
                    Expression.stop(0.15, Expression.rgba(13, 71, 161, 0.55)),
                    Expression.stop(0.4, Expression.rgba(34, 211, 238, 0.78)),
                    Expression.stop(0.65, Expression.rgba(45, 212, 191, 0.85)),
                    Expression.stop(0.85, Expression.rgba(250, 204, 21, 0.9)),
                    Expression.stop(1.0, Expression.rgba(248, 250, 252, 1.0))
                )
            ),
            PropertyFactory.heatmapRadius(
                Expression.interpolate(
                    Expression.linear(), Expression.zoom(),
                    Expression.stop(10f, 6f),
                    Expression.stop(13f, 14f),
                    Expression.stop(16f, 30f)
                )
            ),
            PropertyFactory.heatmapIntensity(
                Expression.interpolate(
                    Expression.linear(), Expression.zoom(),
                    Expression.stop(10f, 0.5f),
                    Expression.stop(16f, 1.3f)
                )
            ),
            PropertyFactory.heatmapWeight(0.7f),
            PropertyFactory.heatmapOpacity(0.85f),
            PropertyFactory.visibility(Property.NONE)
        )
        style.addLayer(heatLayer)
    }

    /** Включить/выключить тепловую карту. При включении заполняет источник центрами клеток. */
    fun setHeatmap(on: Boolean) {
        heatOn = on
        heatLayer.setProperties(
            PropertyFactory.visibility(if (on) Property.VISIBLE else Property.NONE)
        )
        if (on) refreshHeat()
    }

    private fun refreshHeat() {
        val ids = VisitRepository.allCells()
        val feats = ArrayList<Feature>(ids.size)
        for (id in ids) {
            val x = GridMapper.unpackX(id)
            val y = GridMapper.unpackY(id)
            val lon = (GridMapper.westLon(x) + GridMapper.eastLon(x)) / 2.0
            val lat = (GridMapper.southLat(y) + GridMapper.northLat(y)) / 2.0
            feats.add(Feature.fromGeometry(Point.fromLngLat(lon, lat)))
        }
        heatSource.setGeoJson(FeatureCollection.fromFeatures(feats))
    }

    fun refresh(map: MapLibreMap) {
        val b = map.projection.visibleRegion.latLngBounds
        val ne = b.northEast
        val sw = b.southWest

        // Туман гуще при приближении. Даже вдали полог достаточно плотный, чтобы
        // непройденный город читался как «тёмная неизвестность» (мудовый ночной вид),
        // но крупные ориентиры — река, береговая линия, магистрали — ещё проступают.
        val z = map.cameraPosition.zoom
        val op = (0.60 + (z - 11.0) / 5.0 * (0.80 - 0.60)).coerceIn(0.55, 0.80)
        fogLayer.setProperties(PropertyFactory.fillOpacity(op.toFloat()))
        // Свечение «захвата» ярче издалека (чтобы освоенное читалось поверх тёмного
        // полога), мягче вблизи (там и так раскрыта карта сквозь дыры).
        val glowOp = (0.34 - (z - 11.0) / 5.0 * (0.34 - 0.16)).coerceIn(0.16, 0.34)
        claimedFill.setProperties(PropertyFactory.fillOpacity(glowOp.toFloat()))
        claimedGlow.setProperties(PropertyFactory.fillOpacity((glowOp * 0.8).toFloat()))

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

        if (z >= REVEAL_ZOOM) {
            // Берём клетки чуть шире экрана (15%), чтобы контур у кромки экрана не «обрезался».
            val mLat = (ne.latitude - sw.latitude) * 0.15
            val mLon = (ne.longitude - sw.longitude) * 0.15
            val ids = VisitRepository.cellsInBounds(
                sw.longitude - mLon, sw.latitude - mLat,
                ne.longitude + mLon, ne.latitude + mLat
            )
            if (ids.isNotEmpty()) {
                val cutHoles = z >= HOLE_ZOOM
                // Объединённые прямоугольники: один проход на свечение «захвата» и дыры.
                val claimed = ArrayList<Feature>()
                for (r in mergeRects(HashSet(ids))) {
                    val ring = rectRing(
                        GridMapper.westLon(r[0]), GridMapper.southLat(r[1]),
                        GridMapper.eastLon(r[2]), GridMapper.northLat(r[3])
                    )
                    claimed.add(Feature.fromGeometry(Polygon.fromLngLats(listOf(ring))))
                    if (cutHoles) rings.add(ring)  // вблизи прорезаем дыру — видно дневную карту
                }
                claimedSource.setGeoJson(FeatureCollection.fromFeatures(claimed))
                if (z >= RIM_ZOOM) {
                    edgeSource.setGeoJson(
                        Feature.fromGeometry(MultiLineString.fromLngLats(boundary(HashSet(ids))))
                    )
                } else {
                    edgeSource.setGeoJson(FeatureCollection.fromFeatures(emptyList()))
                }
            } else {
                claimedSource.setGeoJson(FeatureCollection.fromFeatures(emptyList()))
                edgeSource.setGeoJson(FeatureCollection.fromFeatures(emptyList()))
            }
        } else {
            claimedSource.setGeoJson(FeatureCollection.fromFeatures(emptyList()))
            edgeSource.setGeoJson(FeatureCollection.fromFeatures(emptyList()))
        }

        fogSource.setGeoJson(Feature.fromGeometry(Polygon.fromLngLats(rings)))

        if (heatOn) refreshHeat()
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
        private const val CLAIMED_SRC = "claimed-src"
        private const val EDGE_SRC = "fog-edge-src"
        private const val FOG_FILL = "fog-fill"
        private const val CLAIMED_GLOW = "claimed-glow"
        private const val CLAIMED_FILL = "claimed-fill"
        private const val EDGE_GLOW = "fog-edge-glow"
        private const val EDGE_CORE = "fog-edge-core"
        private const val HEAT_SRC = "heat-src"
        private const val HEAT_LAYER = "heat-layer"
        // Освоенная зона светится уже с городского зума (видно издалека); дыры и
        // яркая кромка появляются на уличном — прогрессивное раскрытие деталей.
        private const val REVEAL_ZOOM = 10.0
        private const val HOLE_ZOOM = 13.0
        private const val RIM_ZOOM = 13.0
        private const val FOG_COLOR = "#0A0E27"  // глубокая полночная синь
        private const val GLOW_COLOR = "#22D3EE" // cyan-400 — засветка освоенной зоны
        private const val GLOW_DEEP = "#0EA5E9"  // sky-500 — синее «тело» свечения
        private const val CORE_COLOR = "#CFFAFE" // cyan-50 — яркое ядро контура
    }
}
