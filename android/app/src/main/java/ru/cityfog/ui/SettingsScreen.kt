package ru.cityfog.ui

import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.widget.Toast
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
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Help
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import ru.cityfog.BuildConfig
import ru.cityfog.data.TrackRepository
import ru.cityfog.data.VisitRepository
import ru.cityfog.ui.theme.AuroraBackground
import ru.cityfog.ui.theme.CityFogColors
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
            style = MaterialTheme.typography.headlineMedium
        )
        Text(
            "Данные, экспорт и справка",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 4.dp, bottom = 20.dp)
        )

        SettingsGroup(title = "Данные") {
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
                    scope.launch {
                        val msg = downloadCells(context)
                        Toast.makeText(context, msg, Toast.LENGTH_LONG).show()
                    }
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

        Spacer(Modifier.height(16.dp))

        SettingsGroup(title = "О приложении") {
            SettingsItem(
                icon = Icons.Default.Info,
                iconBg = CityFogColors.Cyan400.copy(alpha = 0.15f),
                iconTint = CityFogColors.Cyan400,
                title = "О CityFog",
                subtitle = "Версия ${BuildConfig.VERSION_NAME}",
                onClick = { showAboutDialog = true }
            )
            SettingsItem(
                icon = Icons.AutoMirrored.Filled.Help,
                iconBg = CityFogColors.TextMuted.copy(alpha = 0.15f),
                iconTint = CityFogColors.TextMuted,
                title = "Как работает туман",
                subtitle = "Кратко про клетки, GPS и районы",
                onClick = { /* Можно открыть onboarding повторно */ }
            )
        }
        }
    }

    if (showClearDialog) {
        AlertDialog(
            onDismissRequest = { showClearDialog = false },
            confirmButton = {
                TextButton(onClick = {
                    VisitRepository.clearAll(context)
                    TrackRepository.clearAll(context)
                    showClearDialog = false
                }) { Text("Удалить", color = CityFogColors.Recording) }
            },
            dismissButton = {
                TextButton(onClick = { showClearDialog = false }) { Text("Отмена") }
            },
            title = { Text("Сбросить прогресс?") },
            text = { Text("Все открытые клетки, треки и статистика будут безвозвратно удалены. Это действие нельзя отменить.") }
        )
    }

    if (showAboutDialog) {
        AlertDialog(
            onDismissRequest = { showAboutDialog = false },
            confirmButton = {
                TextButton(onClick = { showAboutDialog = false }) { Text("OK") }
            },
            title = { Text("CityFog") },
            text = {
                Text(
                    "Личный туман войны по клеткам для Казани.\n\n" +
                    "GPS открывает клетки 12×12 м, пока ты исследуешь город. " +
                    "Всё хранится локально на телефоне, без сервера и аккаунтов.\n\n" +
                    "Версия ${BuildConfig.VERSION_NAME}"
                )
            }
        )
    }
}

@Composable
private fun SettingsGroup(title: String, content: @Composable () -> Unit) {
    Column {
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
    iconBg: androidx.compose.ui.graphics.Color,
    iconTint: androidx.compose.ui.graphics.Color,
    title: String,
    subtitle: String,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
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
            Icon(imageVector = icon, contentDescription = title, tint = iconTint)
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
        Text(
            "›",
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
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
