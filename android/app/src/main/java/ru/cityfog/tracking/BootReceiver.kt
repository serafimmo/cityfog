package ru.cityfog.tracking

import android.Manifest
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat

/**
 * Поднимает запись маршрута после перезагрузки телефона, если пользователь её не выключал.
 *
 * BOOT_COMPLETED — один из немногих случаев, когда Android разрешает стартовать
 * foreground-сервис из фона. Поднимаем только при наличии фоновой геолокации, иначе
 * сервис упадёт на старте обновлений.
 */
class BootReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent?) {
        val action = intent?.action
        if (action != Intent.ACTION_BOOT_COMPLETED &&
            action != "android.intent.action.QUICKBOOT_POWERON"
        ) return

        if (!TrackingState.isActive(context)) return
        if (!hasLocationPermissions(context)) return

        LocationService.start(context)
    }

    private fun hasLocationPermissions(c: Context): Boolean {
        val fine = ContextCompat.checkSelfPermission(
            c, Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
        val background = Build.VERSION.SDK_INT < Build.VERSION_CODES.Q ||
            ContextCompat.checkSelfPermission(
                c, Manifest.permission.ACCESS_BACKGROUND_LOCATION
            ) == PackageManager.PERMISSION_GRANTED
        return fine && background
    }
}
