package ru.cityfog.data

import java.io.File
import java.io.RandomAccessFile
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Хранилище открытых клеток: бинарный файл, по 16 байт на клетку.
 * Формат записи: cellId (Long, 8 байт) + firstVisitTs (Long, 8 байт).
 * Append-only. Дедупликация — выше по стеку (VisitRepository).
 */
class VisitStore(private val file: File) {

    data class Entry(val cellId: Long, val firstVisitTs: Long)

    /** Прочитать все сохранённые клетки с временем первого посещения. */
    fun loadAll(): List<Entry> {
        if (!file.exists()) return emptyList()
        val bytes = file.readBytes()
        val count = bytes.size / 16
        val buf = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
        return List(count) {
            Entry(buf.long, buf.long)
        }
    }

    /** Дописать новую клетку в конец файла. */
    @Synchronized
    fun appendOne(id: Long, ts: Long) {
        val buf = ByteBuffer.allocate(16).order(ByteOrder.LITTLE_ENDIAN)
        buf.putLong(id)
        buf.putLong(ts)
        RandomAccessFile(file, "rw").use { raf ->
            raf.seek(raf.length())
            raf.write(buf.array())
            raf.fd.sync()
        }
    }

    /** Удалить все посещённые клетки. */
    fun clear() {
        file.delete()
    }
}
