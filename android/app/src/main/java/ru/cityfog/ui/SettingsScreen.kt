package ru.cityfog.ui

import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.automirrored.filled.Help
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import ru.cityfog.BuildConfig
import ru.cityfog.data.TrackRepository
import ru.cityfog.data.VisitRepository
import ru.cityfog.ui.theme.AuroraBackground
import ru.cityfog.ui.theme.CityFogColors
import ru.cityfog.ui.theme.MonoFamily
import ru.cityfog.ui.theme.glassPanel
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun SettingsScreen(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var showClearDialog by remember { mutableStateOf(false) }
    var showAboutDialog by remember { mutableStateOf(false) }
    var showFogDialog by remember { mutableStateOf(false) }
    // Глass-тост результата экспорта: системный Toast выпадал из «ночного сканера».
    var toastMsg by remember { mutableStateOf<String?>(null) }
    // Последний непустой текст держим отдельно, чтобы exit-анимация не рисовала пустую строку.
    var shownToast by remember { mutableStateOf("") }
    LaunchedEffect(toastMsg) {
        val msg = toastMsg
        if (msg != null) {
            shownToast = msg
            delay(2600)
            toastMsg = null
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        AuroraBackground(Modifier.fillMaxSize())
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp)
                // Низ: с запасом под плавающий навбар (≈92dp).
                .padding(top = 12.dp, bottom = 108.dp)
        ) {
        Text(
            "Настройки",
            style = MaterialTheme.typography.displaySmall,
            color = MaterialTheme.colorScheme.onBackground
        )
        Text(
            "Данные, экспорт и справка",
            style = MaterialTheme.typography.bodyMedium,
            color = CityFogColors.TextMuted,
            modifier = Modifier.padding(top = 4.dp, bottom = 20.dp)
        )

        SettingsGroup(title = "Данные", modifier = Modifier.cascadeIn(0)) {
            SettingsItem(
                icon = Icons.Default.Share,
                iconBg = CityFogColors.Cyan400.copy(alpha = 0.15f),
                iconTint = CityFogColors.Cyan400,
                title = "Поделиться клетками",
                subtitle = "Отправить CSV в другое приложение",
                onClick = { scope.launch { shareCells(context) } }
            )
            SettingsItem(
                icon = Icons.Default.Download,
                iconBg = CityFogColors.Teal500.copy(alpha = 0.15f),
                iconTint = CityFogColors.Teal400,
                title = "Скачать клетки в файл",
                subtitle = "Сохранить CSV в папку «Загрузки»",
                onClick = {
                    scope.launch { toastMsg = downloadCells(context) }
                }
            )
            SettingsItem(
                icon = Icons.Default.Delete,
                iconBg = CityFogColors.Recording.copy(alpha = 0.15f),
                iconTint = CityFogColors.Recording,
                title = "Очистить прогресс",
                subtitle = "Удалить все открытые клетки и треки",
                onClick = { showClearDialog = true }
            )
        }

        Spacer(Modifier.height(24.dp))

        SettingsGroup(title = "О приложении", modifier = Modifier.cascadeIn(1)) {
            SettingsItem(
                icon = Icons.Default.Info,
                iconBg = CityFogColors.Cyan400.copy(alpha = 0.15f),
                iconTint = CityFogColors.Cyan400,
                title = "О CityFog",
                subtitle = "Версия ${BuildConfig.VERSION_NAME}",
                showChevron = true,
                onClick = { showAboutDialog = true }
            )
            SettingsItem(
                icon = Icons.AutoMirrored.Filled.Help,
                iconBg = CityFogColors.TextMuted.copy(alpha = 0.15f),
                iconTint = CityFogColors.TextMuted,
                title = "Как работает туман",
                subtitle = "Кратко про клетки, GPS и районы",
                showChevron = true,
                onClick = { showFogDialog = true }
            )
        }

        // Футер-якорь: нижняя половина экрана больше не «провисает» пустотой —
        // тихий вордмарк закрывает композицию, как титры.
        Spacer(Modifier.height(56.dp))
        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                "CITYFOG",
                style = MaterialTheme.typography.titleLarge,
                color = CityFogColors.TextMuted.copy(alpha = 0.5f)
            )
            Spacer(Modifier.height(4.dp))
            Text(
                "V${BuildConfig.VERSION_NAME} · КАЗАНЬ",
                style = MaterialTheme.typography.labelSmall.copy(
                    fontFamily = MonoFamily, letterSpacing = 2.sp
                ),
                color = CityFogColors.TextMuted.copy(alpha = 0.35f)
            )
        }
        }

        // Глass-тост: выезжает снизу над доком и сам тает через пару секунд.
        AnimatedVisibility(
            visible = toastMsg != null,
            enter = slideInVertically { it } + fadeIn(),
            exit = fadeOut(),
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 100.dp)
        ) {
            Row(
                modifier = Modifier
                    .glassPanel(18.dp)
                    .padding(horizontal = 18.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Check,
                    contentDescription = null,
                    tint = CityFogColors.Success,
                    modifier = Modifier.size(18.dp)
                )
                Text(
                    shownToast,
                    style = MaterialTheme.typography.bodyMedium,
                    color = CityFogColors.TextPrimary
                )
            }
        }
    }

    if (showClearDialog) {
        GlassDialog(onDismiss = { showClearDialog = false }) {
            Text(
                "Сбросить прогресс?",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold,
                color = CityFogColors.TextPrimary
            )
            Spacer(Modifier.height(12.dp))
            Text(
                "Все открытые клетки, треки и статистика будут безвозвратно удалены. " +
                    "Это действие нельзя отменить.",
                style = MaterialTheme.typography.bodyMedium,
                color = CityFogColors.TextSecondary
            )
            Spacer(Modifier.height(24.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                GhostCapsule(
                    label = "Отмена",
                    modifier = Modifier.weight(1f),
                    onClick = { showClearDialog = false }
                )
                DangerCapsule(
                    label = "Удалить",
                    modifier = Modifier.weight(1f),
                    onClick = {
                        VisitRepository.clearAll(context)
                        TrackRepository.clearAll(context)
                        showClearDialog = false
                    }
                )
            }
        }
    }

    if (showAboutDialog) {
        GlassDialog(onDismiss = { showAboutDialog = false }) {
            Text(
                "О CityFog",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold,
                color = CityFogColors.TextPrimary
            )
            Spacer(Modifier.height(12.dp))
            Text(
                "Личный туман войны по клеткам для Казани.\n\n" +
                    "GPS открывает клетки 12×12 м, пока ты исследуешь город. " +
                    "Всё хранится локально на телефоне, без сервера и аккаунтов.\n\n" +
                    "Версия ${BuildConfig.VERSION_NAME}",
                style = MaterialTheme.typography.bodyMedium,
                color = CityFogColors.TextSecondary
            )
            Spacer(Modifier.height(24.dp))
            CyanCapsule(
                label = "OK",
                modifier = Modifier.fillMaxWidth(),
                onClick = { showAboutDialog = false }
            )
        }
    }

    if (showFogDialog) {
        GlassDialog(onDismiss = { showFogDialog = false }) {
            Text(
                "Как работает туман",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold,
                color = CityFogColors.TextPrimary
            )
            Spacer(Modifier.height(12.dp))
            Text(
                "Город разбит на клетки 12×12 м, и в начале все они скрыты туманом.\n\n" +
                    "GPS отмечает, где ты проходишь, и открывает клетки — тёмная карта " +
                    "светлеет там, где ты уже был.\n\n" +
                    "Проценты в статистике считаются по районам: чем больше клеток " +
                    "района открыто, тем выше его прогресс.",
                style = MaterialTheme.typography.bodyMedium,
                color = CityFogColors.TextSecondary
            )
            Spacer(Modifier.height(24.dp))
            CyanCapsule(
                label = "Понятно",
                modifier = Modifier.fillMaxWidth(),
                onClick = { showFogDialog = false }
            )
        }
    }
}

/**
 * Фирменный диалог «ночного сканера»: стеклянная карточка с пружинным входом —
 * тот же язык, что у итогов прогулки на карте. Стоковый AlertDialog выглядел
 * чужим среди стекла и неона.
 */
@Composable
private fun GlassDialog(
    onDismiss: () -> Unit,
    content: @Composable ColumnScope.() -> Unit
) {
    Dialog(onDismissRequest = onDismiss) {
        // Пружинное появление: карточка «выпрыгивает» с лёгким перелётом.
        var shown by remember { mutableStateOf(false) }
        LaunchedEffect(Unit) { shown = true }
        val scale by animateFloatAsState(
            targetValue = if (shown) 1f else 0.86f,
            animationSpec = spring(dampingRatio = 0.55f, stiffness = 420f),
            label = "dialog-scale"
        )
        val alpha by animateFloatAsState(
            targetValue = if (shown) 1f else 0f,
            animationSpec = tween(200),
            label = "dialog-alpha"
        )
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .graphicsLayer {
                    scaleX = scale
                    scaleY = scale
                    this.alpha = alpha
                }
                .glassPanel(28.dp)
        ) {
            Column(modifier = Modifier.padding(24.dp), content = content)
        }
    }
}

/** Деструктив: тёплая orange→red капсула со свечением — «горячее» действие видно сразу. */
@Composable
private fun DangerCapsule(label: String, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Box(
        modifier = modifier
            .shadow(
                14.dp, CircleShape,
                ambientColor = CityFogColors.Recording, spotColor = CityFogColors.Recording
            )
            .clip(CircleShape)
            .background(
                Brush.horizontalGradient(listOf(Color(0xFFF97316), CityFogColors.Recording))
            )
            .clickable(onClick = onClick)
            .padding(vertical = 12.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            label,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.Bold,
            color = CityFogColors.Navy900
        )
    }
}

/** Главное действие: неоновая teal→cyan капсула — сестра StartStopButton с карты. */
@Composable
private fun CyanCapsule(label: String, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Box(
        modifier = modifier
            .shadow(
                14.dp, CircleShape,
                ambientColor = CityFogColors.Cyan400, spotColor = CityFogColors.Cyan400
            )
            .clip(CircleShape)
            .background(
                Brush.horizontalGradient(listOf(CityFogColors.Teal400, CityFogColors.Cyan400))
            )
            .clickable(onClick = onClick)
            .padding(vertical = 12.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            label,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.Bold,
            color = CityFogColors.Navy900
        )
    }
}

/** Второстепенное действие: ghost-капсула — только контур, никакой заливки. */
@Composable
private fun GhostCapsule(label: String, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Box(
        modifier = modifier
            .clip(CircleShape)
            .border(1.dp, CityFogColors.Navy600, CircleShape)
            .clickable(onClick = onClick)
            .padding(vertical = 12.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            label,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.SemiBold,
            color = CityFogColors.TextSecondary
        )
    }
}

@Composable
private fun SettingsGroup(
    title: String,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    Column(modifier) {
        // Eyebrow-заголовок группы: КАПС с широким разрядом (labelLarge теперь моно —
        // буква «О» там путается с нулём).
        Text(
            title.uppercase(),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(start = 8.dp, bottom = 8.dp)
        )
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .glassPanel(24.dp)
        ) {
            Column(modifier = Modifier.padding(vertical = 8.dp)) {
                content()
            }
        }
    }
}

@Composable
private fun SettingsItem(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    iconBg: Color,
    iconTint: Color,
    title: String,
    subtitle: String,
    showChevron: Boolean = false,
    onClick: () -> Unit
) {
    // Микро-отклик: строка едва заметно поджимается, шеврон подаётся вперёд —
    // нажатие чувствуется ещё до того, как что-то открылось.
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed) 0.98f else 1f,
        animationSpec = spring(dampingRatio = 0.5f, stiffness = 650f),
        label = "item-press"
    )
    val dx by animateDpAsState(
        targetValue = if (pressed) 4.dp else 0.dp,
        animationSpec = spring(dampingRatio = 0.5f, stiffness = 650f),
        label = "item-chevron"
    )
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .clickable(interactionSource = interaction, indication = null, onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Box(
            modifier = Modifier
                .size(44.dp)
                .clip(CircleShape)
                .background(iconBg),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = title,
                tint = iconTint,
                modifier = Modifier.size(22.dp)
            )
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(
                title,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium
            )
            Text(
                subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        // Шеврон только у пунктов-переходов; действия (share/download/удалить)
        // говорят за себя сами и стрелку не обещают.
        if (showChevron) {
            Icon(
                imageVector = Icons.Filled.ChevronRight,
                contentDescription = null,
                tint = CityFogColors.TextMuted.copy(alpha = 0.7f),
                modifier = Modifier
                    .size(20.dp)
                    .offset(x = dx)
            )
        }
    }
}

/**
 * Сохранить список открытых клеток в CSV прямо в публичную папку «Загрузки».
 * На Android 10+ — через MediaStore (без разрешений, файл виден в «Файлах»);
 * на старых — в app-specific external dir. Возвращает текст для тоста.
 */
private suspend fun downloadCells(context: Context): String = withContext(Dispatchers.IO) {
    try {
        val name = "cityfog_cells_${timestamp()}.csv"
        val bytes = buildCellsCsv(context).toByteArray()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val resolver = context.contentResolver
            val values = ContentValues().apply {
                put(MediaStore.Downloads.DISPLAY_NAME, name)
                put(MediaStore.Downloads.MIME_TYPE, "text/csv")
                put(MediaStore.Downloads.IS_PENDING, 1)
            }
            val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
                ?: return@withContext "Не удалось создать файл"
            resolver.openOutputStream(uri)?.use { it.write(bytes) }
            values.clear()
            values.put(MediaStore.Downloads.IS_PENDING, 0)
            resolver.update(uri, values, null, null)
            "Сохранено в «Загрузки»: $name"
        } else {
            @Suppress("DEPRECATION")
            val dir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
            dir.mkdirs()
            val f = File(dir, name)
            f.writeBytes(bytes)
            "Сохранено: ${f.absolutePath}"
        }
    } catch (e: Exception) {
        "Не удалось сохранить: ${e.message}"
    }
}

/** Поделиться CSV открытых клеток через системный «Поделиться» (во временный файл + FileProvider). */
private suspend fun shareCells(context: Context) {
    val uri = withContext(Dispatchers.IO) {
        val file = File(context.cacheDir, "cityfog_cells_${timestamp()}.csv")
        file.writeText(buildCellsCsv(context))
        FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
    }
    val intent = Intent(Intent.ACTION_SEND).apply {
        type = "text/csv"
        putExtra(Intent.EXTRA_STREAM, uri)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    context.startActivity(
        Intent.createChooser(intent, "Поделиться клетками")
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    )
}

/** CSV открытых клеток (читаем visited.bin напрямую — проще, чем через репозиторий). */
private fun buildCellsCsv(context: Context): String {
    val sb = StringBuilder()
    sb.append("cell_id,cell_x,cell_y,first_visit_ts,first_visit_iso\n")
    val file = File(context.filesDir, "visited.bin")
    if (file.exists()) {
        val data = file.readBytes()
        val count = data.size / 16
        val buf = ByteBuffer.wrap(data).order(ByteOrder.LITTLE_ENDIAN)
        repeat(count) {
            val id = buf.long
            val ts = buf.long
            val x = (id ushr 32).toInt()
            val y = (id and 0xFFFFFFFFL).toInt()
            sb.append("$id,$x,$y,$ts,${iso(ts)}\n")
        }
    }
    return sb.toString()
}

private fun timestamp(): String =
    SimpleDateFormat("yyyy-MM-dd_HH-mm", Locale.getDefault()).format(Date())

private fun iso(ts: Long): String =
    SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).format(Date(ts))
