package ru.cityfog.tracking

import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.location.Location
import android.os.Build
import android.os.HandlerThread
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import ru.cityfog.MainActivity
import ru.cityfog.R
import ru.cityfog.data.TrackStore
import ru.cityfog.data.VisitRepository
import ru.cityfog.grid.GridMapper
import java.io.File

/**
 * Foreground Service трекинга. Главная техническая сложность проекта — Android 12+
 * убивает фоновые процессы, поэтому запись GPS живёт ИМЕННО в foreground-сервисе
 * с постоянным уведомлением (foregroundServiceType="location").
 *
 * Поток: FusedLocationProvider отдаёт фиксы на ФОНОВЫЙ Looper (HandlerThread) →
 * фильтр точности → перевод в клетку (GridMapper) → запись в VisitRepository.
 * Фиксы приходят на фоновом потоке, поэтому запись в файл безопасна (не блокирует UI).
 */
class LocationService : Service() {

    private lateinit var client: FusedLocationProviderClient
    private lateinit var trackStore: TrackStore
    private var handlerThread: HandlerThread? = null

    // Быстрый фильтр "стоим на месте": не дёргаем репозиторий, пока клетка та же.
    @Volatile private var lastCellId: Long = Long.MIN_VALUE
    @Volatile private var lastTrackTs: Long = 0L

    private val callback = object : LocationCallback() {
        override fun onLocationResult(result: LocationResult) {
            for (loc in result.locations) handleLocation(loc)
        }
    }

    override fun onCreate() {
        super.onCreate()
        client = LocationServices.getFusedLocationProviderClient(this)
        trackStore = TrackStore(File(applicationContext.filesDir, dailyTrackFile()))
        createChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            TrackingState.setActive(this, false) // явная остановка — больше не поднимать
            stopSelf()
            return START_NOT_STICKY
        }
        TrackingState.setActive(this, true) // намерение «вести запись» переживёт перезапуск
        startForegroundCompat()
        startUpdates()
        return START_STICKY // система перезапустит сервис, если убьёт его под нагрузкой
    }

    private fun handleLocation(loc: Location) {
        // Фильтр точности: в городе GPS прыгает; мусорные фиксы дают "рандомные" клетки.
        if (loc.hasAccuracy() && loc.accuracy > MAX_ACCURACY_M) return
        val ts = System.currentTimeMillis()

        // Пишем трек: не чаще раза в 2 секунды, чтобы не раздувать файл.
        if (ts - lastTrackTs >= 2000L) {
            trackStore.append(loc.latitude, loc.longitude, ts)
            lastTrackTs = ts
        }

        val id = GridMapper.cellIdOrNull(loc.latitude, loc.longitude) ?: return
        if (id == lastCellId) return
        lastCellId = id
        VisitRepository.recordFix(loc.latitude, loc.longitude, ts)
    }

    @SuppressLint("MissingPermission") // разрешение гарантирует UI до старта сервиса
    private fun startUpdates() {
        val req = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, INTERVAL_MS)
            .setMinUpdateIntervalMillis(MIN_INTERVAL_MS)
            .setMinUpdateDistanceMeters(MIN_DISTANCE_M)
            .build()
        val ht = HandlerThread("cityfog-loc").also { it.start() }
        handlerThread = ht
        // Грузим маску/репозиторий на ЭТОМ же Looper'е до первого фикса (FIFO гарантирует порядок).
        android.os.Handler(ht.looper).post { VisitRepository.init(applicationContext) }
        client.requestLocationUpdates(req, callback, ht.looper)
    }

    private fun startForegroundCompat() {
        val notif = buildNotification()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(NOTIF_ID, notif, ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION)
        } else {
            startForeground(NOTIF_ID, notif)
        }
    }

    private fun createChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val ch = NotificationChannel(
                CHANNEL_ID, "Трекинг маршрута", NotificationManager.IMPORTANCE_LOW
            ).apply { description = "Идёт запись маршрута и открытие клеток" }
            getSystemService(NotificationManager::class.java).createNotificationChannel(ch)
        }
    }

    private fun buildNotification(): Notification {
        val open = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val stop = PendingIntent.getService(
            this, 1, Intent(this, LocationService::class.java).setAction(ACTION_STOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("CityFog — запись маршрута")
            .setContentText("Открываю клетки по ходу движения")
            .setSmallIcon(R.mipmap.ic_launcher)
            .setOngoing(true)
            .setContentIntent(open)
            .addAction(0, "Стоп", stop)
            .build()
    }

    override fun onDestroy() {
        client.removeLocationUpdates(callback)
        handlerThread?.quitSafely()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    companion object {
        private const val NOTIF_ID = 1
        private const val CHANNEL_ID = "tracking"
        private const val ACTION_STOP = "ru.cityfog.action.STOP"

        private const val MAX_ACCURACY_M = 25f   // фиксы хуже — отбрасываем
        private const val INTERVAL_MS = 4000L    // желаемый интервал фиксов
        private const val MIN_INTERVAL_MS = 2000L
        private const val MIN_DISTANCE_M = 5f    // не репортить, пока не сдвинулись на 5 м

        fun start(context: Context) {
            ContextCompat.startForegroundService(
                context, Intent(context, LocationService::class.java)
            )
        }

        fun stop(context: Context) {
            TrackingState.setActive(context, false)
            context.stopService(Intent(context, LocationService::class.java))
        }

        private fun dailyTrackFile(): String {
            val sdf = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.getDefault())
            return "track_${sdf.format(java.util.Date())}.bin"
        }
    }
}
