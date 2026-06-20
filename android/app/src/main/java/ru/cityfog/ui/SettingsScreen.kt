package ru.cityfog.ui

import android.content.Context
import android.content.Intent
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Help
import androidx.compose.material.icons.filled.Delete
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
import ru.cityfog.ui.theme.CityFogColors
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun SettingsScreen(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var showClearDialog by remember { mutableStateOf(false) }
    var showAboutDialog by remember { mutableStateOf(false) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp)
            .padding(top = 24.dp, bottom = 32.dp)
    ) {
        Text(
            "Настройки",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold
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
                iconBg = CityFogColors.Teal500.copy(alpha = 0.15f),
                iconTint = CityFogColors.Teal400,
                title = "Экспорт посещённых клеток",
                subtitle = "Поделиться CSV со списком клеток",
                onClick = { scope.launch { exportCells(context) } }
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
        Text(
            title,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(start = 8.dp, bottom = 8.dp)
        )
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
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

private suspend fun exportCells(context: Context) {
    withContext(Dispatchers.IO) {
        val file = File(context.cacheDir, "cityfog_export_${timestamp()}.csv")
        file.bufferedWriter().use { out ->
            out.write("cell_id,cell_x,cell_y,first_visit_ts,first_visit_iso\n")
            // Прямой доступ к файлу — проще, чем тащить всё через репозиторий.
            val data = java.io.File(context.filesDir, "visited.bin").readBytes()
            val count = data.size / 16
            val buf = java.nio.ByteBuffer.wrap(data).order(java.nio.ByteOrder.LITTLE_ENDIAN)
            repeat(count) {
                val id = buf.long
                val ts = buf.long
                val x = (id ushr 32).toInt()
                val y = (id and 0xFFFFFFFFL).toInt()
                out.write("$id,$x,$y,$ts,${iso(ts)}\n")
            }
        }
        shareFile(context, file, "text/csv")
    }
}

private fun shareFile(context: Context, file: File, mime: String) {
    val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
    val intent = Intent(Intent.ACTION_SEND).apply {
        type = mime
        putExtra(Intent.EXTRA_STREAM, uri)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    val chooser = Intent.createChooser(intent, "Экспорт CityFog")
    chooser.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    context.startActivity(chooser)
}

private fun timestamp(): String =
    SimpleDateFormat("yyyy-MM-dd_HH-mm", Locale.getDefault()).format(Date())

private fun iso(ts: Long): String =
    SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).format(Date(ts))
