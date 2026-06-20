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
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Whatshot
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SmallFloatingActionButton
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
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
import ru.cityfog.tracking.TrackingState
import ru.cityfog.ui.theme.CityFogColors

private val KAZAN = LatLng(55.7963, 49.1088)
private const val START_ZOOM = 12.0
private const val FOLLOW_ZOOM = 16.5
private const val STYLE_URL = "https://tiles.openfreemap.org/styles/liberty"

private val TEAL = CityFogColors.Teal500
private val TEAL_BRIGHT = CityFogColors.Teal400
private val CYAN = CityFogColors.Cyan400
private val REC_RED = CityFogColors.Recording
private val TRACK = CityFogColors.Navy600.copy(alpha = 0.35f)

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
    // Источник правды о записи — сохранённый флаг: переживает убийство процесса и ребут.
    var tracking by rememberSaveable { mutableStateOf(TrackingState.isActive(context)) }
    val heatmapOn = rememberSaveable { mutableStateOf(false) }

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

    val haptic = LocalHapticFeedback.current
    val snackbarHostState = remember { SnackbarHostState() }
    var previousRevision by remember { mutableLongStateOf(0L) }
    val revision by VisitRepository.revision.collectAsStateWithLifecycle()
    LaunchedEffect(revision) {
        val m = mapRef.value
        val r = rendererRef.value
        if (m != null && r != null) r.refresh(m)
        if (revision > 0) {
            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
        }
        if (revision > previousRevision && previousRevision > 0) {
            val newCells = (revision - previousRevision).toInt()
            val message = if (newCells == 1) "Открыта 1 клетка" else "Открыто $newCells клетки"
            snackbarHostState.showSnackbar(message)
        }
        previousRevision = revision
    }

    val progress by VisitRepository.progress.collectAsStateWithLifecycle()
    val isLoading = progress.totalAvailable == 0

    // Оркестрованное появление панелей: как только карта загрузилась и сплэш ушёл —
    // верхняя карточка спускается сверху, нижние панели поднимаются снизу.
    var revealed by remember { mutableStateOf(false) }
    LaunchedEffect(isLoading) { if (!isLoading) revealed = true }

    Box(modifier = modifier) {
        AndroidView(factory = { mapView }, modifier = Modifier.fillMaxSize())

        AnimatedVisibility(
            visible = revealed,
            enter = fadeIn(tween(450)) + slideInVertically(tween(450, easing = FastOutSlowInEasing)) { -it },
            modifier = Modifier
                .align(Alignment.TopCenter)
                .safeDrawingPadding()
                .padding(top = 10.dp)
        ) {
            GlobalProgressCard(progress = progress, tracking = tracking)
        }

        AnimatedVisibility(
            visible = revealed,
            enter = fadeIn(tween(450, delayMillis = 120)) +
                slideInVertically(tween(450, delayMillis = 120, easing = FastOutSlowInEasing)) { it },
            modifier = Modifier
                .align(Alignment.BottomStart)
                .safeDrawingPadding()
                .padding(start = 12.dp, bottom = 12.dp)
        ) {
            DistrictsPanel(progress = progress)
        }

        AnimatedVisibility(
            visible = revealed,
            enter = fadeIn(tween(450, delayMillis = 220)) +
                slideInVertically(tween(450, delayMillis = 220, easing = FastOutSlowInEasing)) { it },
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .safeDrawingPadding()
                .padding(16.dp)
        ) {
            Controls(
                tracking = tracking,
                onToggleTracking = { tracking = it },
                mapRef = mapRef,
                styleRef = styleRef,
                rendererRef = rendererRef,
                locationOn = locationOn,
                heatmapOn = heatmapOn
            )
        }

        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 88.dp),
            snackbar = { data ->
                androidx.compose.material3.Snackbar(
                    snackbarData = data,
                    containerColor = CityFogColors.Navy800,
                    contentColor = CityFogColors.TextPrimary,
                    shape = RoundedCornerShape(16.dp)
                )
            }
        )

        AnimatedVisibility(visible = isLoading) {
            SplashOverlay()
        }
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
    // Плавный счётчик: процент «доезжает» до значения, а не прыгает.
    val animatedPct by animateFloatAsState(
        targetValue = if (loading) 0f else progress.percent,
        animationSpec = tween(1000, easing = FastOutSlowInEasing),
        label = "global-pct"
    )
    Card(
        modifier = modifier.border(
            1.dp, CityFogColors.Cyan400.copy(alpha = 0.12f), RoundedCornerShape(24.dp)
        ),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.92f)
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 10.dp),
        shape = RoundedCornerShape(24.dp)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 18.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            ProgressRing(percent = animatedPct, size = 64.dp, stroke = 6.dp) {
                Text(
                    text = if (loading) "…" else fmtPct(animatedPct),
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold,
                    color = CYAN
                )
            }
            Column {
                Text(
                    "КАЗАНЬ ИССЛЕДОВАНА",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    if (loading) "загрузка карты…"
                    else "%.2f км²".format(visitedKm2),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onBackground
                )
                Text(
                    if (loading) "" else "из %.0f км²".format(totalKm2),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                AnimatedVisibility(visible = tracking) {
                    Row(
                        modifier = Modifier.padding(top = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        RecordingPulse()
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
private fun RecordingPulse() {
    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val scale by infiniteTransition.animateFloat(
        initialValue = 0.6f,
        targetValue = 1.8f,
        animationSpec = infiniteRepeatable(
            animation = tween(900, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "scale"
    )
    val alpha by infiniteTransition.animateFloat(
        initialValue = 0.8f,
        targetValue = 0f,
        animationSpec = infiniteRepeatable(
            animation = tween(900, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "alpha"
    )
    Box(
        modifier = Modifier.size(10.dp),
        contentAlignment = Alignment.Center
    ) {
        Box(
            Modifier
                .fillMaxSize()
                .clip(CircleShape)
                .background(REC_RED.copy(alpha = alpha))
                .scale(scale)
        )
        Box(
            Modifier
                .size(6.dp)
                .clip(CircleShape)
                .background(REC_RED)
        )
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
        modifier = modifier.widthIn(max = 280.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.92f)
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 10.dp),
        shape = RoundedCornerShape(22.dp)
    ) {
        Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
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
                Column(modifier = Modifier.padding(top = 8.dp)) {
                    districts.forEach { d ->
                        DistrictRow(d)
                        Spacer(Modifier.height(10.dp))
                    }
                }
            }
        }
    }
}

@Composable
private fun DistrictRow(d: VisitRepository.DistrictProgress) {
    val color = districtColor(d.id)
    Column {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier
                        .size(8.dp)
                        .clip(CircleShape)
                        .background(color)
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    d.name,
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 1
                )
            }
            Text(
                fmtPct(d.percent),
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold,
                color = color
            )
        }
        Spacer(Modifier.height(4.dp))
        LinearProgressIndicator(
            progress = { (d.percent / 100f).coerceIn(0f, 1f) },
            modifier = Modifier
                .fillMaxWidth()
                .height(5.dp)
                .clip(CircleShape),
            color = color,
            trackColor = TRACK
        )
    }
}

internal fun districtColor(id: Int): Color =
    CityFogColors.DistrictColors.getOrElse(id - 1) { CityFogColors.Cyan400 }

private fun fmtPct(p: Float): String = when {
    p <= 0f -> "0%"
    p < 1f -> "%.2f%%".format(p)
    p < 10f -> "%.1f%%".format(p)
    else -> "%.0f%%".format(p)
}

// ----------------------------------------------------------------------------- Splash

@Composable
private fun SplashOverlay() {
    val infiniteTransition = rememberInfiniteTransition(label = "splash")
    val pulse by infiniteTransition.animateFloat(
        initialValue = 0.7f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(1200, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "splash-pulse"
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(CityFogColors.Navy900),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Box(
                modifier = Modifier
                    .size(120.dp)
                    .scale(pulse),
                contentAlignment = Alignment.Center
            ) {
                // Символичная «карта» из клеток: 5×5 сетка с мерцающими точками.
                val cellSize = 14.dp
                val gap = 4.dp
                val grid = 5
                Column(verticalArrangement = Arrangement.spacedBy(gap)) {
                    repeat(grid) { row ->
                        Row(horizontalArrangement = Arrangement.spacedBy(gap)) {
                            repeat(grid) { col ->
                                val active = (row + col) % 3 == 0
                                val alpha = if (active) 1f else 0.25f
                                Box(
                                    Modifier
                                        .size(cellSize)
                                        .clip(RoundedCornerShape(3.dp))
                                        .background(CYAN.copy(alpha = alpha))
                                )
                            }
                        }
                    }
                }
            }
            Spacer(Modifier.height(28.dp))
            Text(
                "CityFog",
                style = MaterialTheme.typography.headlineLarge,
                fontWeight = FontWeight.Bold,
                color = CityFogColors.TextPrimary
            )
            Spacer(Modifier.height(8.dp))
            Text(
                "Загружаю карту Казани…",
                style = MaterialTheme.typography.bodyMedium,
                color = CityFogColors.TextSecondary
            )
            Spacer(Modifier.height(32.dp))
            CircularProgressIndicator(
                modifier = Modifier.size(28.dp),
                color = CYAN,
                trackColor = CityFogColors.Navy700
            )
        }
    }
}

// ----------------------------------------------------------------------------- Управление

@Composable
private fun Controls(
    tracking: Boolean,
    onToggleTracking: (Boolean) -> Unit,
    mapRef: MutableState<MapLibreMap?>,
    styleRef: MutableState<Style?>,
    rendererRef: MutableState<FogRenderer?>,
    locationOn: MutableState<Boolean>,
    heatmapOn: MutableState<Boolean>,
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
            onClick = {
                val on = !heatmapOn.value
                heatmapOn.value = on
                rendererRef.value?.setHeatmap(on)
            },
            containerColor = if (heatmapOn.value) CYAN
            else MaterialTheme.colorScheme.surface.copy(alpha = 0.95f),
            contentColor = if (heatmapOn.value) CityFogColors.Navy900 else CYAN,
            shape = CircleShape
        ) {
            Icon(
                imageVector = Icons.Default.Whatshot,
                contentDescription = "Тепловая карта",
                modifier = Modifier.size(22.dp)
            )
        }
        SmallFloatingActionButton(
            onClick = { startFollowing(context, mapRef.value, styleRef.value, locationOn) },
            containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.95f),
            contentColor = CYAN,
            shape = CircleShape
        ) {
            Icon(
                imageVector = Icons.Default.MyLocation,
                contentDescription = "Моё местоположение",
                modifier = Modifier.size(22.dp)
            )
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
            contentColor = Color.White,
            shape = CircleShape,
            icon = {
                Icon(
                    imageVector = if (tracking) Icons.Default.Stop else Icons.Default.PlayArrow,
                    contentDescription = if (tracking) "Стоп" else "Старт"
                )
            },
            text = {
                Text(
                    if (tracking) "Стоп" else "Старт",
                    fontWeight = FontWeight.SemiBold
                )
            }
        )
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
                .pulseAlpha(0.45f)
                .pulseSingleDuration(1200f)
                .pulseMaxRadius(40f)
                .pulseInterpolator(android.view.animation.DecelerateInterpolator())
                .foregroundTintColor(android.graphics.Color.parseColor("#22D3EE"))
                .foregroundStaleTintColor(android.graphics.Color.parseColor("#22D3EE"))
                .bearingTintColor(android.graphics.Color.parseColor("#22D3EE"))
                .accuracyColor(android.graphics.Color.parseColor("#22D3EE"))
                .accuracyAlpha(0.12f)
                .elevation(8f)
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
