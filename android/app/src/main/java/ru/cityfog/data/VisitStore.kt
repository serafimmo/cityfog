package ru.cityfog.data

import java.io.File
import java.io.RandomAccessFile
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Хранилище открытых клеток: бинарный файл, по 8 байт (Long, little-endian) на клетку.
 * Append-only. Дедупликация — выше по стеку (HashSet в [VisitRepository]); сюда долетают
 * только реально новые id.
 *
 * Почему не Room/SQLite: на этом этапе храним лишь множество Long. Плоский файл проще,
 * без annotation-processor'ов, и идеально ложится на формат данных. Когда понадобится
 * visitCount/теплокарта — мигрируем на Room.
 */
class VisitStore(private val file: File) {

    /** Прочитать все сохранённые cellId. */
    fun loadAll(): LongArray {
        if (!file.exists()) return LongArray(0)
        val bytes = file.readBytes()
        val count = bytes.size / 8
        val buf = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
        return LongArray(count) { buf.long }
    }

    /** Дописать новый cellId в конец файла (с fsync — клетки открываются редко, это дёшево). */
    @Synchronized
    fun appendOne(id: Long) {
        val buf = ByteBuffer.allocate(8).order(ByteOrder.LITTLE_ENDIAN)
        buf.putLong(id)
        RandomAccessFile(file, "rw").use { raf ->
            raf.seek(raf.length())
            raf.write(buf.array())
            raf.fd.sync()
        }
    }
}
