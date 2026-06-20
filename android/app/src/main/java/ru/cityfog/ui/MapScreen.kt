package ru.cityfog.ui

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SmallFloatingActionButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.maplibre.android.MapLibre
import org.maplibre.android.camera.CameraUpdateFactory
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.location.LocationComponentActivationOptions
import org.maplibre.android.location.LocationComponentOptions
import org.maplibre.android.location.modes.CameraMode
import org.maplibre.android.location.modes.RenderMode
import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.maps.MapView
import org.maplibre.android.maps.Style
import ru.cityfog.data.VisitRepository
import ru.cityfog.map.FogRenderer
import ru.cityfog.tracking.LocationService

private val KAZAN = LatLng(55.7963, 49.1088)
private const val START_ZOOM = 12.0
private const val FOLLOW_ZOOM = 16.5

// Positron — приглушённый светло-серый стиль; под тёмным туманом он смотрится как ночная карта.
private const val STYLE_URL = "https://tiles.openfreemap.org/styles/positron"

private val TEAL = Color(0xFF0F766E)
private val TEAL_BRIGHT = Color(0xFF14B8A6)
private val REC_RED = Color(0xFFEF4444)

/**
 * Экран карты с туманом войны. MapLibre — обычная Android View (MapView); встраиваем её в
 * Compose через AndroidView и вручную пробрасываем lifecycle. Поверх — статистика и кнопки.
 */
@Composable
fun MapScreen(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    LaunchedEffect(Unit) { VisitRepository.init(context.applicationContext) }

    val mapRef = remember { mutableStateOf<MapLibreMap?>(null) }
    val styleRef = remember { mutableStateOf<Style?>(null) }
    val rendererRef = remember { mutableStateOf<FogRenderer?>(null) }
    val locationOn = remember { mutableStateOf(false) }
    var tracking by rememberSaveable { mutableStateOf(false) }

    val mapView = remember {
        MapLibre.getInstance(context)
        MapView(context).apply {
            onCreate(null)
            getMapAsync { map ->
                mapRef.value = map
                map.moveCamera(CameraUpdateFactory.newLatLngZoom(KAZAN, START_ZOOM))
                map.setStyle(Style.Builder().fromUri(STYLE_URL)) { style ->
                    styleRef.value = style
                    val renderer = FogRenderer(style)
                    rendererRef.value = renderer
                    renderer.refresh(map)
                    map.addOnCameraIdleListener { renderer.refresh(map) }
                    // Если разрешение уже есть — сразу показать синюю точку (без слежения).
                    if (hasFineLocation(context)) {
                        enableUserLocation(context, map, style, follow = false)
                        locationOn.value = true
                    }
                }
            }
        }
    }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_START -> mapView.onStart()
                Lifecycle.Event.ON_RESUME -> mapView.onResume()
                Lifecycle.Event.ON_PAUSE -> mapView.onPause()
                Lifecycle.Event.ON_STOP -> mapView.onStop()
                else -> {}
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            mapView.onStop()
            mapView.onDestroy()
        }
    }

    // Клетки открываются и при неподвижной камере → перерисовываем туман по revision.
    val revision by VisitRepository.revision.collectAsStateWithLifecycle()
    LaunchedEffect(revision) {
        val m = mapRef.value
        val r = rendererRef.value
        if (m != null && r != null) r.refresh(m)
    }

    val count by VisitRepository.count.collectAsStateWithLifecycle()

    Box(modifier = modifier) {
        AndroidView(factory = { mapView }, modifier = Modifier.fillMaxSize())

        StatsCard(
            count = count,
            tracking = tracking,
            modifier = Modifier
                .align(Alignment.TopCenter)
                .safeDrawingPadding()
                .padding(top = 12.dp)
        )

        Controls(
            tracking = tracking,
            onToggleTracking = { tracking = it },
            mapRef = mapRef,
            styleRef = styleRef,
            locationOn = locationOn,
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .safeDrawingPadding()
                .padding(16.dp)
        )
    }
}

@Composable
private fun StatsCard(count: Int, tracking: Boolean, modifier: Modifier = Modifier) {
    val areaKm2 = count * (GRID_CELL_AREA_M2 / 1_000_000.0)
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.94f)
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 8.dp),
        shape = RoundedCornerShape(18.dp)
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 22.dp, vertical = 12.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "%,d".format(count),
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                color = TEAL
            )
            Text(
                text = if (count == 0) "клеток открыто" else "клеток · %.2f км²".format(areaKm2),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            AnimatedVisibility(visible = tracking) {
                Row(
                    modifier = Modifier.padding(top = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Box(
                        Modifier
                            .size(8.dp)
                            .clip(CircleShape)
                            .background(REC_RED)
                    )
                    Text(
                        "идёт запись",
                        style = MaterialTheme.typography.labelSmall,
                        color = REC_RED,
                        fontWeight = FontWeight.Medium
                    )
                }
            }
            if (count == 0 && !tracking) {
                Text(
                    "Нажми «Старт» и иди",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 4.dp)
                )
            }
        }
    }
}

@Composable
private fun Controls(
    tracking: Boolean,
    onToggleTracking: (Boolean) -> Unit,
    mapRef: MutableState<MapLibreMap?>,
    styleRef: MutableState<Style?>,
    locationOn: MutableState<Boolean>,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var showBgDialog by remember { mutableStateOf(false) }

    val bgLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { requestIgnoreBattery(context) }

    val notifLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { }

    val fineLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { res ->
        val granted = res[Manifest.permission.ACCESS_FINE_LOCATION] == true ||
            res[Manifest.permission.ACCESS_COARSE_LOCATION] == true
        if (granted) {
            startFollowing(context, mapRef.value, styleRef.value, locationOn)
            LocationService.start(context)
            onToggleTracking(true)
            if (!hasBackground(context)) showBgDialog = true
        }
    }

    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.End,
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        SmallFloatingActionButton(
            onClick = { startFollowing(context, mapRef.value, styleRef.value, locationOn) },
            containerColor = MaterialTheme.colorScheme.surface,
            contentColor = TEAL
        ) {
            Text("◎", fontSize = 22.sp)
        }
        ExtendedFloatingActionButton(
            onClick = {
                if (tracking) {
                    LocationService.stop(context)
                    onToggleTracking(false)
                } else {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                        ContextCompat.checkSelfPermission(
                            context, Manifest.permission.POST_NOTIFICATIONS
                        ) != PackageManager.PERMISSION_GRANTED
                    ) {
                        notifLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                    }
                    if (hasFineLocation(context)) {
                        startFollowing(context, mapRef.value, styleRef.value, locationOn)
                        LocationService.start(context)
                        onToggleTracking(true)
                        if (!hasBackground(context)) showBgDialog = true
                    } else {
                        fineLauncher.launch(
                            arrayOf(
                                Manifest.permission.ACCESS_FINE_LOCATION,
                                Manifest.permission.ACCESS_COARSE_LOCATION
                            )
                        )
                    }
                }
            },
            containerColor = if (tracking) REC_RED else TEAL_BRIGHT,
            contentColor = Color.White
        ) {
            Text(if (tracking) "■  Стоп" else "▶  Старт", fontWeight = FontWeight.SemiBold)
        }
    }

    if (showBgDialog) {
        AlertDialog(
            onDismissRequest = { showBgDialog = false },
            confirmButton = {
                TextButton(onClick = {
                    showBgDialog = false
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                        bgLauncher.launch(Manifest.permission.ACCESS_BACKGROUND_LOCATION)
                    } else {
                        requestIgnoreBattery(context)
                    }
                }) { Text("Открыть настройки") }
            },
            dismissButton = {
                TextButton(onClick = { showBgDialog = false }) { Text("Позже") }
            },
            title = { Text("Чтобы карта открывалась в фоне") },
            text = {
                Text(
                    "На следующем экране выбери «Разрешать всегда» — тогда клетки будут " +
                        "открываться даже с погашенным экраном. Потом разреши игнорировать " +
                        "экономию батареи."
                )
            }
        )
    }
}

private const val GRID_CELL_AREA_M2 = 12.0 * 12.0

/** Включить синюю точку позиции и (опц.) слежение камеры. Безопасно к повторным вызовам. */
@SuppressLint("MissingPermission")
private fun enableUserLocation(context: Context, map: MapLibreMap, style: Style, follow: Boolean) {
    try {
        val lc = map.locationComponent
        if (!lc.isLocationComponentActivated) {
            // Пульсирующая точка в бирюзовом тоне — в цвет свечения исследованной зоны.
            val opts = LocationComponentOptions.builder(context)
                .pulseEnabled(true)
                .pulseColor(android.graphics.Color.parseColor("#22D3EE"))
                .pulseAlpha(0.5f)
                .foregroundTintColor(android.graphics.Color.parseColor("#06B6D4"))
                .bearingTintColor(android.graphics.Color.parseColor("#06B6D4"))
                .accuracyColor(android.graphics.Color.parseColor("#22D3EE"))
                .accuracyAlpha(0.15f)
                .build()
            lc.activateLocationComponent(
                LocationComponentActivationOptions.builder(context, style)
                    .locationComponentOptions(opts)
                    .useDefaultLocationEngine(true)
                    .build()
            )
        }
        lc.isLocationComponentEnabled = true
        lc.renderMode = RenderMode.COMPASS
        if (follow) {
            lc.cameraMode = CameraMode.TRACKING
            lc.zoomWhileTracking(FOLLOW_ZOOM)
        }
    } catch (_: Exception) {
        // Если движок локации недоступен — просто не показываем точку, без краша.
    }
}

/** Центрировать карту на себе и включить слежение. */
private fun startFollowing(
    context: Context,
    map: MapLibreMap?,
    style: Style?,
    locationOn: MutableState<Boolean>
) {
    if (map == null || style == null || !hasFineLocation(context)) return
    enableUserLocation(context, map, style, follow = true)
    locationOn.value = true
}

private fun hasFineLocation(c: Context): Boolean =
    ContextCompat.checkSelfPermission(c, Manifest.permission.ACCESS_FINE_LOCATION) ==
        PackageManager.PERMISSION_GRANTED ||
        ContextCompat.checkSelfPermission(c, Manifest.permission.ACCESS_COARSE_LOCATION) ==
        PackageManager.PERMISSION_GRANTED

private fun hasBackground(c: Context): Boolean =
    Build.VERSION.SDK_INT < Build.VERSION_CODES.Q ||
        ContextCompat.checkSelfPermission(c, Manifest.permission.ACCESS_BACKGROUND_LOCATION) ==
        PackageManager.PERMISSION_GRANTED

private fun requestIgnoreBattery(c: Context) {
    val pm = c.getSystemService(Context.POWER_SERVICE) as PowerManager
    if (!pm.isIgnoringBatteryOptimizations(c.packageName)) {
        val i = Intent(
            Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,
            Uri.parse("package:${c.packageName}")
        ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        try {
            c.startActivity(i)
        } catch (_: Exception) {
        }
    }
}
