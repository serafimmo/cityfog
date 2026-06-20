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
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.LinearProgressIndicator
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
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
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
private const val STYLE_URL = "https://tiles.openfreemap.org/styles/positron"

private val TEAL = Color(0xFF0F766E)
private val TEAL_BRIGHT = Color(0xFF14B8A6)
private val CYAN = Color(0xFF22D3EE)
private val REC_RED = Color(0xFFEF4444)
private val TRACK = Color(0x22000000)

@Composable
fun MapScreen(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    // Грузим маску доступности + visited.bin на фоне (тяжёлая операция).
    LaunchedEffect(Unit) {
        withContext(Dispatchers.IO) { VisitRepository.init(context.applicationContext) }
    }

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

    val revision by VisitRepository.revision.collectAsStateWithLifecycle()
    LaunchedEffect(revision) {
        val m = mapRef.value
        val r = rendererRef.value
        if (m != null && r != null) r.refresh(m)
    }

    val progress by VisitRepository.progress.collectAsStateWithLifecycle()

    Box(modifier = modifier) {
        AndroidView(factory = { mapView }, modifier = Modifier.fillMaxSize())

        GlobalProgressCard(
            progress = progress,
            tracking = tracking,
            modifier = Modifier
                .align(Alignment.TopCenter)
                .safeDrawingPadding()
                .padding(top = 10.dp)
        )

        DistrictsPanel(
            progress = progress,
            modifier = Modifier
                .align(Alignment.BottomStart)
                .safeDrawingPadding()
                .padding(start = 12.dp, bottom = 12.dp)
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

// ----------------------------------------------------------------------------- Статистика

@Composable
private fun GlobalProgressCard(
    progress: VisitRepository.Progress,
    tracking: Boolean,
    modifier: Modifier = Modifier
) {
    val loading = progress.totalAvailable == 0
    val visitedKm2 = progress.visited * 0.000_144
    val totalKm2 = progress.totalAvailable * 0.000_144
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.95f)
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 8.dp),
        shape = RoundedCornerShape(20.dp)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            ProgressRing(percent = progress.percent, size = 58.dp, stroke = 6.dp) {
                Text(
                    text = if (loading) "…" else fmtPct(progress.percent),
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold,
                    color = TEAL
                )
            }
            Column {
                Text(
                    "Казань исследована",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    if (loading) "загрузка карты…"
                    else "%.2f из %.0f км²".format(visitedKm2, totalKm2),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                AnimatedVisibility(visible = tracking) {
                    Row(
                        modifier = Modifier.padding(top = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Box(Modifier.size(8.dp).clip(CircleShape).background(REC_RED))
                        Text(
                            "идёт запись",
                            style = MaterialTheme.typography.labelSmall,
                            color = REC_RED,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ProgressRing(
    percent: Float,
    size: Dp,
    stroke: Dp,
    content: @Composable () -> Unit
) {
    Box(modifier = Modifier.size(size), contentAlignment = Alignment.Center) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val sw = stroke.toPx()
            drawArc(
                color = TRACK, startAngle = -90f, sweepAngle = 360f, useCenter = false,
                style = Stroke(width = sw, cap = StrokeCap.Round)
            )
            val frac = (percent / 100f).coerceIn(0f, 1f)
            drawArc(
                color = TEAL_BRIGHT, startAngle = -90f, sweepAngle = 360f * frac, useCenter = false,
                style = Stroke(width = sw, cap = StrokeCap.Round)
            )
        }
        content()
    }
}

@Composable
private fun DistrictsPanel(
    progress: VisitRepository.Progress,
    modifier: Modifier = Modifier
) {
    if (progress.districts.isEmpty()) return
    var expanded by rememberSaveable { mutableStateOf(false) }
    val districts = progress.districts.sortedByDescending { it.percent }

    Card(
        modifier = modifier.widthIn(max = 260.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.95f)
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 8.dp),
        shape = RoundedCornerShape(18.dp)
    ) {
        Column(modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp)) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { expanded = !expanded },
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    "Районы",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    if (expanded) "⌄" else "⌃",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            AnimatedVisibility(visible = expanded) {
                Column(modifier = Modifier.padding(top = 6.dp)) {
                    districts.forEach { d ->
                        DistrictRow(d)
                        Spacer(Modifier.height(8.dp))
                    }
                }
            }
        }
    }
}

@Composable
private fun DistrictRow(d: VisitRepository.DistrictProgress) {
    Column {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                d.name,
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 1
            )
            Text(
                fmtPct(d.percent),
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold,
                color = TEAL
            )
        }
        Spacer(Modifier.height(3.dp))
        LinearProgressIndicator(
            progress = { (d.percent / 100f).coerceIn(0f, 1f) },
            modifier = Modifier
                .fillMaxWidth()
                .height(5.dp)
                .clip(CircleShape),
            color = TEAL_BRIGHT,
            trackColor = TRACK
        )
    }
}

private fun fmtPct(p: Float): String = when {
    p <= 0f -> "0%"
    p < 1f -> "%.2f%%".format(p)
    p < 10f -> "%.1f%%".format(p)
    else -> "%.0f%%".format(p)
}

// ----------------------------------------------------------------------------- Управление

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

@SuppressLint("MissingPermission")
private fun enableUserLocation(context: Context, map: MapLibreMap, style: Style, follow: Boolean) {
    try {
        val lc = map.locationComponent
        if (!lc.isLocationComponentActivated) {
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
    }
}

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
