package ru.cityfog.data

import java.io.File
import java.io.RandomAccessFile
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Хранилище сырых GPS-фиксов: (lat, lon, timestamp) плоским бинарником.
 * Нужно для статистики расстояния и времени в пути. Файл ведётся дневной;
 при перезапуске трекинга в тот же день фиксы дописываются.
 */
class TrackStore(private val file: File) {

    data class Point(val lat: Double, val lon: Double, val ts: Long)

    /** Прочитать все точки. */
    fun loadAll(): List<Point> {
        if (!file.exists()) return emptyList()
        val bytes = file.readBytes()
        val count = bytes.size / 24
        val buf = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
        return List(count) {
            Point(buf.double, buf.double, buf.long)
        }
    }

    /** Дописать точку в конец. */
    @Synchronized
    fun append(lat: Double, lon: Double, ts: Long) {
        val buf = ByteBuffer.allocate(24).order(ByteOrder.LITTLE_ENDIAN)
        buf.putDouble(lat)
        buf.putDouble(lon)
        buf.putLong(ts)
        RandomAccessFile(file, "rw").use { raf ->
            raf.seek(raf.length())
            raf.write(buf.array())
            raf.fd.sync()
        }
    }

    /** Удалить все треки. */
    fun clear() {
        file.delete()
    }
}
