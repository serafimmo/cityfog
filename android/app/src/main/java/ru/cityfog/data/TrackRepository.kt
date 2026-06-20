package ru.cityfog.data

import android.content.Context
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Доступ к трековым файлам для статистики.
 */
object TrackRepository {

    data class Summary(
        val todayDistanceM: Double,
        val todayActiveMs: Long,
        val totalDistanceM: Double,
        val totalActiveMs: Long
    )

    private const val EARTH_R = 6_371_000.0

    fun summary(context: Context): Summary {
        val dir = context.applicationContext.filesDir
        val files = dir.listFiles { f -> f.name.startsWith("track_") && f.name.endsWith(".bin") } ?: emptyArray()
        var totalDist = 0.0
        var totalTime = 0L
        var todayDist = 0.0
        var todayTime = 0L
        val todayFile = "track_${todaySuffix()}.bin"
        for (f in files) {
            val pts = TrackStore(f).loadAll()
            val dist = distanceM(pts)
            val time = activeDurationMs(pts)
            totalDist += dist
            totalTime += time
            if (f.name == todayFile) {
                todayDist = dist
                todayTime = time
            }
        }
        return Summary(todayDist, todayTime, totalDist, totalTime)
    }

    /** Очистить все треки. */
    fun clearAll(context: Context) {
        val dir = context.applicationContext.filesDir
        dir.listFiles { f -> f.name.startsWith("track_") && f.name.endsWith(".bin") }
            ?.forEach { it.delete() }
    }

    private fun distanceM(pts: List<TrackStore.Point>): Double {
        if (pts.size < 2) return 0.0
        var sum = 0.0
        for (i in 1 until pts.size) {
            sum += haversine(pts[i - 1], pts[i])
        }
        return sum
    }

    private fun activeDurationMs(pts: List<TrackStore.Point>): Long {
        if (pts.size < 2) return 0L
        var sum = 0L
        for (i in 1 until pts.size) {
            val dt = pts[i].ts - pts[i - 1].ts
            if (dt in 0..120_000L) sum += dt
        }
        return sum
    }

    private fun haversine(a: TrackStore.Point, b: TrackStore.Point): Double {
        val dLat = Math.toRadians(b.lat - a.lat)
        val dLon = Math.toRadians(b.lon - a.lon)
        val lat1 = Math.toRadians(a.lat)
        val lat2 = Math.toRadians(b.lat)
        val x = sin(dLat / 2) * sin(dLat / 2) +
                sin(dLon / 2) * sin(dLon / 2) * cos(lat1) * cos(lat2)
        val y = 2 * atan2(sqrt(x), sqrt(1 - x))
        return EARTH_R * y
    }

    private fun todaySuffix(): String {
        return SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())
    }
}
