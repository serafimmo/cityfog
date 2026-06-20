package ru.cityfog.mask

import android.content.Context
import org.json.JSONObject

/**
 * Маска доступности Казани — знаменатель процента.
 *
 * Готовится оффлайн на Python (tools/build_mask.py) и лежит в assets как сырой
 * массив uint8 (row-major, kazan_mask.bin; в APK пожат deflate'ом). На устройстве
 * грузим его в [ByteArray] и проверяем O(1)
 * индексацией — без геометрии (играет на твой опыт с растрами/OpenCV).
 *
 * Значение пикселя:
 *   0     — недоступно (вне города или исключено: вода/завод/...)
 *   1..7  — доступно, id известного района
 *   8     — доступно, город без района (Авиастроительный/Советский + мелочь)
 *
 * Соответствие пиксель ⇔ клетка — ровно как в grid_config.py / [ru.cityfog.grid.GridMapper]:
 * col = cellX, row = height-1-cellY.
 */
object AvailabilityMask {

    @Volatile private var loaded = false
    private val lock = Any()

    private var width = 0
    private var height = 0
    private var mask: ByteArray = ByteArray(0)

    var totalAvailable = 0
        private set
    private val perDistrictTotal = IntArray(9)        // индексы 1..8
    private val districtNames = HashMap<Int, String>()

    fun isLoaded(): Boolean = loaded

    /** Идемпотентно грузит маску и тоталы из assets. Тяжёлая (~МБ) — звать с фонового потока. */
    fun ensureLoaded(context: Context) {
        if (loaded) return
        synchronized(lock) {
            if (loaded) return
            val am = context.applicationContext.assets

            val mf = JSONObject(am.open("grid_manifest.json").bufferedReader().use { it.readText() })
            width = mf.getInt("width")
            height = mf.getInt("height")

            // Сырой uint8 row-major. AGP хранит ассет пожатым deflate'ом; AssetManager.open()
            // отдаёт уже распакованный поток (~11 МБ) — читаем целиком в ByteArray.
            mask = am.open("kazan_mask.bin").use { it.readBytes() }

            val tj = JSONObject(am.open("district_totals.json").bufferedReader().use { it.readText() })
            totalAvailable = tj.getInt("totalAvailable")
            val per = tj.getJSONObject("perDistrict")
            val names = tj.getJSONObject("names")
            for (d in 1..8) {
                perDistrictTotal[d] = per.optInt(d.toString(), 0)
                districtNames[d] = names.optString(d.toString(), "")
            }
            loaded = true
        }
    }

    /** Значение пикселя для клетки (cellX, cellY); 0 если не загружено или вне маски. */
    fun valueAt(cellX: Int, cellY: Int): Int {
        if (!loaded || cellX < 0 || cellY < 0 || cellX >= width || cellY >= height) return 0
        val row = height - 1 - cellY
        return mask[row * width + cellX].toInt() and 0xFF
    }

    fun isAvailable(cellX: Int, cellY: Int): Boolean = valueAt(cellX, cellY) != 0
    fun districtOf(cellX: Int, cellY: Int): Int = valueAt(cellX, cellY)

    fun districtTotal(d: Int): Int = if (d in 1..8) perDistrictTotal[d] else 0
    fun districtName(d: Int): String = districtNames[d] ?: ""

    /** Районы, реально присутствующие в маске (есть доступные клетки), по id. */
    fun presentDistricts(): List<Int> = (1..8).filter { perDistrictTotal[it] > 0 }
}
