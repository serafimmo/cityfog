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
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
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
import androidx.compose.material.icons.automirrored.filled.DirectionsWalk
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Whatshot
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
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
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
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
import ru.cityfog.tracking.SessionTracker
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
                    // На момент style-loaded вьюпорт ещё может быть не измерен —
                    // projection.visibleRegion вырождается, и первый туман строится
                    // пустым (карта открывается без полога, пока её не подвигаешь).
                    // Дорисовываем один раз, когда карта впервые «устаканилась».
                    val mv = this@apply
                    mv.addOnDidBecomeIdleListener(object : MapView.OnDidBecomeIdleListener {
                        override fun onDidBecomeIdle() {
                            renderer.refresh(map)
                            mv.removeOnDidBecomeIdleListener(this)
                        }
                    })
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
                Lifecycle.Event.ON_RESUME -> {
                    mapView.onResume()
                    // Запись могли остановить из шторки — синхронизируем кнопку с фактом.
                    tracking = TrackingState.isActive(context)
                }
                Lifecycle.Event.ON_PAUSE -> mapView.onPause()
                Lifecycle.Event.ON_STOP -> mapView.onStop()
                else -> {}
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            rendererRef.value?.release()
            mapView.onStop()
            mapView.onDestroy()
        }
    }

    val haptic = LocalHapticFeedback.current
    val revision by VisitRepository.revision.collectAsStateWithLifecycle()
    LaunchedEffect(revision) {
        val m = mapRef.value
        val r = rendererRef.value
        if (m != null && r != null) r.refresh(m)
        if (revision > 0) {
            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
        }
    }

    // Вспышка на месте только что открытой клетки.
    val lastOpened by VisitRepository.lastOpened.collectAsStateWithLifecycle()
    LaunchedEffect(lastOpened) {
        if (lastOpened != 0L) rendererRef.value?.flashCell(lastOpened)
    }

    val progress by VisitRepository.progress.collectAsStateWithLifecycle()
    val isLoading = progress.totalAvailable == 0

    // Живая сессия прогулки: HUD в верхней карточке + итоги по «Стоп».
    val session by SessionTracker.session.collectAsStateWithLifecycle()
    var walkSummary by remember { mutableStateOf<WalkSummary?>(null) }

    // Приложение перезапустили при активной записи — поднимаем сессию из prefs,
    // чтобы HUD показывал живые цифры, не дожидаясь перезапуска сервиса.
    LaunchedEffect(tracking, isLoading) {
        if (tracking && !isLoading && !session.active) {
            SessionTracker.startOrResume(context, progress.visited)
        }
    }

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
            GlobalProgressCard(progress = progress, tracking = tracking, session = session)
        }

        AnimatedVisibility(
            visible = revealed,
            enter = fadeIn(tween(450, delayMillis = 120)) +
                slideInVertically(tween(450, delayMillis = 120, easing = FastOutSlowInEasing)) { it },
            modifier = Modifier
                .align(Alignment.BottomStart)
                .safeDrawingPadding()
                .padding(start = 12.dp, bottom = 100.dp)
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
                // Низ с запасом под плавающий навбар (≈92dp), чтобы кнопки не залезали на меню.
                .padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 100.dp)
        ) {
            Controls(
                tracking = tracking,
                onToggleTracking = { tracking = it },
                onStopSession = {
                    val fin = SessionTracker.finish(context)
                    if (fin != null) {
                        val dur = System.currentTimeMillis() - fin.startTs
                        val cells = (progress.visited - fin.startCells).coerceAtLeast(0)
                        // Итоги показываем, только если прогулка была настоящей,
                        // а не случайный тап «Старт-Стоп».
                        if (dur >= 30_000L || cells > 0) {
                            walkSummary = WalkSummary(
                                durationMs = dur,
                                distanceM = fin.distanceM,
                                newCells = cells,
                                pctGained = if (progress.totalAvailable > 0)
                                    cells * 100f / progress.totalAvailable else 0f
                            )
                        }
                    }
                },
                mapRef = mapRef,
                styleRef = styleRef,
                rendererRef = rendererRef,
                locationOn = locationOn,
                heatmapOn = heatmapOn
            )
        }

        AnimatedVisibility(visible = isLoading) {
            SplashOverlay()
        }

        walkSummary?.let { s ->
            WalkSummaryDialog(summary = s, onDismiss = { walkSummary = null })
        }
    }
}

// ----------------------------------------------------------------------------- Статистика

@Composable
private fun GlobalProgressCard(
    progress: VisitRepository.Progress,
    tracking: Boolean,
    session: SessionTracker.Session,
    modifier: Modifier = Modifier
) {
    val loading = progress.totalAvailable == 0
    val visitedKm2 = progress.visited * 0.000_144
    val totalKm2 = progress.totalAvailable * 0.000_144
    // Секундный тикер таймера сессии — живёт только пока идёт запись.
    var nowTs by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(tracking, session.active) {
        while (tracking && session.active) {
            nowTs = System.currentTimeMillis()
            delay(1_000)
        }
    }
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
                    Column(modifier = Modifier.padding(top = 6.dp)) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            RecordingPulse()
                            Text(
                                if (session.active)
                                    "запись · ${fmtDuration((nowTs - session.startTs).coerceAtLeast(0))}"
                                else "идёт запись",
                                style = MaterialTheme.typography.labelSmall,
                                color = REC_RED,
                                fontWeight = FontWeight.Medium
                            )
                        }
                        if (session.active) {
                            val newCells = (progress.visited - session.startCells).coerceAtLeast(0)
                            Row(
                                modifier = Modifier.padding(top = 3.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Text(
                                    fmtDistance(session.distanceM),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Text(
                                    "+$newCells ${cellsWord(newCells)}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = CYAN,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                        }
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
            // Дуга с переливом teal → cyan; рисуем в повёрнутой системе, чтобы
            // sweep-градиент начинался с 12 часов вместе с дугой.
            rotate(-90f) {
                drawArc(
                    brush = Brush.sweepGradient(
                        0f to TEAL_BRIGHT, 0.55f to CYAN, 1f to TEAL_BRIGHT
                    ),
                    startAngle = 0f, sweepAngle = 360f * frac, useCenter = false,
                    style = Stroke(width = sw, cap = StrokeCap.Round)
                )
            }
            // «Комета» на конце дуги: прогресс виден даже при долях процента.
            val ang = Math.toRadians(-90.0 + 360.0 * frac)
            val r = (this.size.minDimension - sw) / 2f
            val head = Offset(
                center.x + (r * kotlin.math.cos(ang)).toFloat(),
                center.y + (r * kotlin.math.sin(ang)).toFloat()
            )
            drawCircle(CYAN.copy(alpha = 0.35f), radius = sw * 1.35f, center = head)
            drawCircle(Color(0xFFCFFAFE), radius = sw * 0.62f, center = head)
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
                // Шеврон в кружке; при раскрытии пружинно переворачивается вниз.
                val chevron by animateFloatAsState(
                    targetValue = if (expanded) 180f else 0f,
                    animationSpec = spring(dampingRatio = 0.6f, stiffness = 380f),
                    label = "districts-chevron"
                )
                Box(
                    modifier = Modifier
                        .size(28.dp)
                        .clip(CircleShape)
                        .background(CityFogColors.Navy700),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.ExpandLess,
                        contentDescription = if (expanded) "Свернуть" else "Развернуть",
                        tint = CYAN,
                        modifier = Modifier
                            .size(20.dp)
                            .rotate(chevron)
                    )
                }
            }
            AnimatedVisibility(
                visible = expanded,
                enter = expandVertically(tween(280, easing = FastOutSlowInEasing)) +
                    fadeIn(tween(280)),
                exit = shrinkVertically(tween(200)) + fadeOut(tween(160))
            ) {
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
        // Полоска «доезжает» до значения при раскрытии панели.
        val animPct by animateFloatAsState(
            targetValue = (d.percent / 100f).coerceIn(0f, 1f),
            animationSpec = tween(800, easing = FastOutSlowInEasing),
            label = "district-bar"
        )
        LinearProgressIndicator(
            progress = { animPct },
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

internal fun fmtPct(p: Float): String = when {
    p <= 0f -> "0.0%"
    p < 1f -> "%.2f%%".format(p)
    p < 10f -> "%.1f%%".format(p)
    else -> "%.0f%%".format(p)
}

internal fun fmtDuration(ms: Long): String {
    val s = ms / 1000
    return if (s >= 3600) "%d:%02d:%02d".format(s / 3600, (s % 3600) / 60, s % 60)
    else "%d:%02d".format(s / 60, s % 60)
}

internal fun fmtDistance(m: Double): String = when {
    m < 1000 -> "${m.toInt()} м"
    m < 10_000 -> "%.2f км".format(m / 1000)
    else -> "%.1f км".format(m / 1000)
}

/** Русское склонение: 1 клетка, 2 клетки, 5 клеток. */
internal fun cellsWord(n: Int): String {
    val r10 = n % 10
    val r100 = n % 100
    return when {
        r100 in 11..14 -> "клеток"
        r10 == 1 -> "клетка"
        r10 in 2..4 -> "клетки"
        else -> "клеток"
    }
}

// ----------------------------------------------------------------------------- Итоги прогулки

private data class WalkSummary(
    val durationMs: Long,
    val distanceM: Double,
    val newCells: Int,
    val pctGained: Float
)

@Composable
private fun WalkSummaryDialog(summary: WalkSummary, onDismiss: () -> Unit) {
    Dialog(onDismissRequest = onDismiss) {
        // Пружинное появление: карточка «выпрыгивает» с лёгким перелётом.
        var shown by remember { mutableStateOf(false) }
        LaunchedEffect(Unit) { shown = true }
        val scale by animateFloatAsState(
            targetValue = if (shown) 1f else 0.86f,
            animationSpec = spring(dampingRatio = 0.55f, stiffness = 420f),
            label = "summary-scale"
        )
        val alpha by animateFloatAsState(
            targetValue = if (shown) 1f else 0f,
            animationSpec = tween(200),
            label = "summary-alpha"
        )
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .graphicsLayer {
                    scaleX = scale
                    scaleY = scale
                    this.alpha = alpha
                }
                .border(1.dp, CYAN.copy(alpha = 0.25f), RoundedCornerShape(28.dp)),
            shape = RoundedCornerShape(28.dp),
            colors = CardDefaults.cardColors(containerColor = CityFogColors.Navy800),
            elevation = CardDefaults.cardElevation(defaultElevation = 12.dp)
        ) {
            Column(
                modifier = Modifier.padding(horizontal = 24.dp, vertical = 28.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Box(
                    modifier = Modifier
                        .size(56.dp)
                        .clip(CircleShape)
                        .background(TEAL_BRIGHT.copy(alpha = 0.15f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.DirectionsWalk,
                        contentDescription = null,
                        tint = TEAL_BRIGHT,
                        modifier = Modifier.size(30.dp)
                    )
                }
                Spacer(Modifier.height(14.dp))
                Text(
                    "Прогулка завершена",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = CityFogColors.TextPrimary
                )
                Spacer(Modifier.height(22.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    SummaryStat("Время", fmtDuration(summary.durationMs))
                    SummaryStat("Путь", fmtDistance(summary.distanceM))
                    SummaryStat("Клетки", "+${summary.newCells}", CYAN)
                }
                if (summary.newCells > 0) {
                    Spacer(Modifier.height(18.dp))
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(50))
                            .background(CYAN.copy(alpha = 0.12f))
                            .padding(horizontal = 14.dp, vertical = 6.dp)
                    ) {
                        // Совсем маленький вклад в % города честнее показать площадью.
                        Text(
                            if (summary.pctGained >= 0.001f)
                                "+${fmtGainedPct(summary.pctGained)} Казани"
                            else "+${summary.newCells * 144} м² Казани",
                            style = MaterialTheme.typography.labelMedium,
                            color = CYAN,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
                Spacer(Modifier.height(24.dp))
                Button(
                    onClick = onDismiss,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = TEAL_BRIGHT,
                        contentColor = Color.White
                    ),
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        "Отлично",
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.padding(vertical = 4.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun SummaryStat(
    label: String,
    value: String,
    valueColor: Color = CityFogColors.TextPrimary
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            value,
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.ExtraBold,
            color = valueColor
        )
        Spacer(Modifier.height(2.dp))
        Text(
            label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

internal fun fmtGainedPct(p: Float): String = when {
    p >= 1f -> "%.1f%%".format(p)
    p >= 0.01f -> "%.2f%%".format(p)
    else -> "%.3f%%".format(p)
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
            Image(
                painter = painterResource(ru.cityfog.R.mipmap.ic_launcher_art),
                contentDescription = null,
                modifier = Modifier
                    .size(132.dp)
                    .scale(pulse)
                    .clip(RoundedCornerShape(30.dp))
            )
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
    onStopSession: () -> Unit,
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
        // Тепловая карта: во включённом состоянии кнопка «дышит» пульсирующим ореолом.
        val heatContainer by animateColorAsState(
            targetValue = if (heatmapOn.value) CYAN
            else MaterialTheme.colorScheme.surface.copy(alpha = 0.95f),
            animationSpec = tween(300),
            label = "heat-color"
        )
        Box(contentAlignment = Alignment.Center) {
            if (heatmapOn.value) PulseHalo(color = CYAN, size = 40.dp)
            SmallFloatingActionButton(
                onClick = {
                    val on = !heatmapOn.value
                    heatmapOn.value = on
                    rendererRef.value?.setHeatmap(on)
                },
                containerColor = heatContainer,
                contentColor = if (heatmapOn.value) CityFogColors.Navy900 else CYAN,
                shape = CircleShape
            ) {
                Icon(
                    imageVector = Icons.Default.Whatshot,
                    contentDescription = "Тепловая карта",
                    modifier = Modifier.size(22.dp)
                )
            }
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
                    // Сначала фиксируем итоги (пока сессия жива), потом гасим сервис.
                    onStopSession()
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
            containerColor = animateColorAsState(
                targetValue = if (tracking) REC_RED else TEAL_BRIGHT,
                animationSpec = tween(350),
                label = "fab-color"
            ).value,
            contentColor = Color.White,
            shape = CircleShape,
            icon = {
                // Иконка сменяется с лёгким «схлопыванием» — старт/стоп ощущается щелчком.
                AnimatedContent(
                    targetState = tracking,
                    transitionSpec = {
                        (scaleIn(tween(220)) + fadeIn(tween(220)))
                            .togetherWith(scaleOut(tween(140)) + fadeOut(tween(140)))
                    },
                    label = "fab-icon"
                ) { t ->
                    Icon(
                        imageVector = if (t) Icons.Default.Stop else Icons.Default.PlayArrow,
                        contentDescription = if (t) "Стоп" else "Старт"
                    )
                }
            },
            text = {
                AnimatedContent(
                    targetState = tracking,
                    transitionSpec = {
                        (fadeIn(tween(220))).togetherWith(fadeOut(tween(140)))
                    },
                    label = "fab-text"
                ) { t ->
                    Text(
                        if (t) "Стоп" else "Старт",
                        fontWeight = FontWeight.SemiBold
                    )
                }
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

/** Пульсирующий ореол вокруг активной круглой кнопки (расширяется и тает). */
@Composable
private fun PulseHalo(color: Color, size: Dp) {
    val t = rememberInfiniteTransition(label = "halo")
    val scale by t.animateFloat(
        initialValue = 1f,
        targetValue = 1.7f,
        animationSpec = infiniteRepeatable(
            animation = tween(1400, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "halo-scale"
    )
    val alpha by t.animateFloat(
        initialValue = 0.45f,
        targetValue = 0f,
        animationSpec = infiniteRepeatable(
            animation = tween(1400, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "halo-alpha"
    )
    Box(
        Modifier
            .size(size)
            .scale(scale)
            .clip(CircleShape)
            .background(color.copy(alpha = alpha))
    )
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
    // Железобетонный зум «к себе»: zoomWhileTracking иногда не срабатывает, если
    // компонент локации уже активен (после рестарта). Поэтому, если знаем позицию,
    // явно анимируем камеру к ней на уличный зум.
    val loc = try { map.locationComponent.lastKnownLocation } catch (_: Exception) { null }
    if (loc != null) {
        map.animateCamera(
            CameraUpdateFactory.newLatLngZoom(LatLng(loc.latitude, loc.longitude), FOLLOW_ZOOM),
            700
        )
    }
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
