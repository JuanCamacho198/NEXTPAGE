package com.nextpage.presentation.screen.settings

import android.app.Application
import android.content.Intent
import android.widget.Toast
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import androidx.lifecycle.viewmodel.compose.viewModel
import com.nextpage.R
import com.nextpage.presentation.viewmodel.PerformanceTiming
import com.nextpage.presentation.viewmodel.PerformanceViewModel
import com.nextpage.ui.components.molecules.NextPageSettingsSubPage
import com.nextpage.ui.icons.NextPageIcons
import java.io.File
import java.util.Locale

@Composable
fun PerformanceScreen(
    onBack: () -> Unit,
    viewModel: PerformanceViewModel = viewModel(
        factory = androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.getInstance(
            LocalContext.current.applicationContext as Application
        )
    )
) {
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current

    NextPageSettingsSubPage(
        title = stringResource(R.string.settings_performance_title),
        onBack = onBack
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // ── Card A: Tiempos clave ────────────────────────────────────
            PerformanceCard(
                title = stringResource(R.string.performance_card_timings_title),
                subtitle = stringResource(R.string.performance_card_timings_subtitle)
            ) {
                if (uiState.timings.isEmpty()) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(12.dp),
                        horizontalArrangement = Arrangement.Center
                    ) {
                        CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text = stringResource(R.string.performance_loading),
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                } else {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        uiState.timings.forEach { timing ->
                            TimingRow(timing = timing)
                            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                        }
                        if (uiState.lastMeasuredAt != null) {
                            Text(
                                text = stringResource(R.string.performance_last_measured, uiState.lastMeasuredAt!!),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
                Spacer(Modifier.height(4.dp))
                Button(
                    onClick = { viewModel.measureNow() },
                    enabled = !uiState.isMeasuring,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    if (uiState.isMeasuring) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(16.dp),
                            strokeWidth = 2.dp,
                            color = MaterialTheme.colorScheme.onPrimary
                        )
                        Spacer(Modifier.width(8.dp))
                    } else {
                        Icon(
                            imageVector = NextPageIcons.Speed,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(Modifier.width(8.dp))
                    }
                    Text(text = stringResource(R.string.performance_measure_now))
                }
            }

            // ── Card B: Recursos ─────────────────────────────────────────
            PerformanceCard(
                title = stringResource(R.string.performance_card_resources_title),
                subtitle = stringResource(R.string.performance_card_resources_subtitle)
            ) {
                val res = uiState.resources
                if (res == null) {
                    Box(modifier = Modifier.fillMaxWidth().padding(12.dp), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                    }
                } else {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        ResourceRow(
                            label = stringResource(R.string.performance_db_size),
                            value = res.dbSizeLabel
                        )
                        ResourceRow(
                            label = stringResource(R.string.performance_highlights_count),
                            value = res.highlightsCount.toString()
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Text(
                                    text = stringResource(R.string.performance_cache_size),
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.Medium
                                )
                                Text(
                                    text = res.cacheSizeLabel,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            OutlinedButton(
                                onClick = {
                                    viewModel.clearCache { success, _ ->
                                        Toast.makeText(
                                            context,
                                            if (success) context.getString(R.string.performance_cache_cleared)
                                            else context.getString(R.string.performance_cache_clear_error),
                                            Toast.LENGTH_SHORT
                                        ).show()
                                    }
                                },
                                enabled = !uiState.isClearingCache
                            ) {
                                if (uiState.isClearingCache) {
                                    CircularProgressIndicator(modifier = Modifier.size(14.dp), strokeWidth = 2.dp)
                                    Spacer(Modifier.width(6.dp))
                                }
                                Text(text = stringResource(R.string.performance_clear_cache))
                            }
                        }
                        ResourceRow(
                            label = stringResource(R.string.performance_memory),
                            value = String.format(Locale.US, "%.0f / %.0f MB", res.memoryUsageMb, res.memoryTotalMb)
                        )
                    }
                }
            }

            // ── Card C: Estado sync ──────────────────────────────────────
            PerformanceCard(
                title = stringResource(R.string.performance_card_sync_title),
                subtitle = stringResource(R.string.performance_card_sync_subtitle)
            ) {
                val sync = uiState.syncStatus
                if (sync == null) {
                    Box(modifier = Modifier.fillMaxWidth().padding(12.dp), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                    }
                } else {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                imageVector = if (sync.realtimeConnected) Icons.Rounded.CheckCircle else Icons.Rounded.ErrorOutline,
                                contentDescription = null,
                                tint = if (sync.realtimeConnected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
                                modifier = Modifier.size(20.dp)
                            )
                            Text(
                                text = if (sync.realtimeConnected)
                                    stringResource(R.string.performance_realtime_connected)
                                else
                                    stringResource(R.string.performance_realtime_disconnected),
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = if (sync.realtimeConnected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error
                            )
                            AssistChip(
                                onClick = {},
                                label = {
                                    Text(
                                        text = if (sync.realtimeConnected) stringResource(R.string.performance_status_connected)
                                        else stringResource(R.string.performance_status_disconnected),
                                        style = MaterialTheme.typography.labelSmall
                                    )
                                },
                                colors = AssistChipDefaults.assistChipColors(
                                    containerColor = if (sync.realtimeConnected)
                                        MaterialTheme.colorScheme.primaryContainer
                                    else
                                        MaterialTheme.colorScheme.errorContainer
                                )
                            )
                        }
                        ResourceRow(
                            label = stringResource(R.string.performance_last_sync),
                            value = sync.lastSyncLabel
                        )
                        ResourceRow(
                            label = stringResource(R.string.performance_outbox_pending),
                            value = sync.outboxPending.toString()
                        )
                    }
                }
            }

            // ── Card D: Diagnóstico ──────────────────────────────────────
            PerformanceCard(
                title = stringResource(R.string.performance_card_diagnostics_title),
                subtitle = stringResource(R.string.performance_card_diagnostics_subtitle)
            ) {
                val diag = uiState.diagnostics
                if (diag == null) {
                    Box(modifier = Modifier.fillMaxWidth().padding(12.dp), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                    }
                } else {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        ResourceRow(
                            label = stringResource(R.string.performance_fps_scroll),
                            value = diag.fpsLabel
                        )
                        ResourceRow(
                            label = stringResource(R.string.performance_anrs),
                            value = diag.anrCount.toString()
                        )
                        Text(
                            text = stringResource(R.string.performance_crashes_title),
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold
                        )
                        if (diag.crashes.isEmpty()) {
                            Text(
                                text = stringResource(R.string.performance_no_crashes),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        } else {
                            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                diag.crashes.forEach { crash ->
                                    Column(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(8.dp)
                                    ) {
                                        Text(
                                            text = crash.name,
                                            style = MaterialTheme.typography.bodySmall,
                                            fontWeight = FontWeight.Medium
                                        )
                                        Text(
                                            text = crash.timestamp,
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                        Text(
                                            text = crash.stackSnippet,
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
                                }
                            }
                        }
                    }
                }
                Spacer(Modifier.height(4.dp))
                OutlinedButton(
                    onClick = {
                        viewModel.generateReport { file ->
                            if (file != null) {
                                Toast.makeText(
                                    context,
                                    context.getString(R.string.performance_report_generated, file.name),
                                    Toast.LENGTH_LONG
                                ).show()
                                shareFile(context, file)
                            } else {
                                Toast.makeText(
                                    context,
                                    context.getString(R.string.performance_report_error),
                                    Toast.LENGTH_SHORT
                                ).show()
                            }
                        }
                    },
                    enabled = !uiState.isGeneratingReport,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    if (uiState.isGeneratingReport) {
                        CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                        Spacer(Modifier.width(8.dp))
                    }
                    Text(text = stringResource(R.string.performance_generate_report))
                }
                if (uiState.reportPath != null) {
                    TextButton(
                        onClick = {
                            val f = File(uiState.reportPath!!)
                            if (f.exists()) shareFile(context, f)
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = stringResource(R.string.performance_report_path, File(uiState.reportPath!!).name),
                            style = MaterialTheme.typography.labelSmall
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}

private fun shareFile(context: android.content.Context, file: File) {
    runCatching {
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "application/zip"
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(intent, context.getString(R.string.performance_share_report)))
    }
}

@Composable
private fun PerformanceCard(
    title: String,
    subtitle: String,
    content: @Composable () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Column {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f))
            content()
        }
    }
}

@Composable
private fun TimingRow(timing: PerformanceTiming) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = timing.labelFallback,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
                modifier = Modifier.weight(1f)
            )
            Sparkline(samples = timing.samples, modifier = Modifier.width(72.dp).height(24.dp))
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            MetricChip(label = "avg", value = "${timing.avgMs} ms")
            MetricChip(label = "p95", value = "${timing.p95Ms} ms")
            MetricChip(label = "max", value = "${timing.maxMs} ms")
        }
    }
}

@Composable
private fun MetricChip(label: String, value: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = value,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.SemiBold
        )
    }
}

@Composable
private fun Sparkline(
    samples: List<Float>,
    modifier: Modifier = Modifier
) {
    val primary = MaterialTheme.colorScheme.primary
    val outline = MaterialTheme.colorScheme.outlineVariant
    val points = remember(samples) { samples }
    Canvas(modifier = modifier) {
        if (points.isEmpty()) return@Canvas
        val min = points.minOrNull() ?: 0f
        val max = points.maxOrNull() ?: 1f
        val range = (max - min).coerceAtLeast(1f)
        val w = size.width
        val h = size.height
        val stepX = if (points.size > 1) w / (points.size - 1) else w

        // baseline
        drawLine(
            color = outline.copy(alpha = 0.7f),
            start = Offset(0f, h),
            end = Offset(w, h),
            strokeWidth = 1.dp.toPx()
        )

        val path = Path().apply {
            points.forEachIndexed { i, v ->
                val x = i * stepX
                val y = h - ((v - min) / range) * h * 0.85f - h * 0.07f
                if (i == 0) moveTo(x, y) else lineTo(x, y)
            }
        }
        drawPath(
            path = path,
            color = primary,
            style = Stroke(width = 1.8.dp.toPx(), cap = StrokeCap.Round)
        )
        // dots
        points.forEachIndexed { i, v ->
            val x = i * stepX
            val y = h - ((v - min) / range) * h * 0.85f - h * 0.07f
            drawCircle(color = primary, radius = 1.6.dp.toPx(), center = Offset(x, y))
        }
    }
}

@Composable
private fun ResourceRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium
        )
    }
}
