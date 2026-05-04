package com.tfg.wearableapp.feature.dashboard

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyHorizontalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.tfg.wearableapp.data.processing.PostSessionSummary
import com.tfg.wearableapp.feature.profile.PlayerProfileUiState
import com.tfg.wearableapp.feature.session.DashboardMode
import com.tfg.wearableapp.feature.session.SessionDetailUiState
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun DashboardScreen(
    paddingValues: PaddingValues,
    dashboardMode: DashboardMode,
    liveSessionDetail: SessionDetailUiState?,
    selectedSessionDetail: SessionDetailUiState?,
    sessionSetup: PlayerProfileUiState,
    onGoToSession: () -> Unit,
) {
    val dashboardDetail = when (dashboardMode) {
        DashboardMode.Live -> liveSessionDetail ?: selectedSessionDetail
        DashboardMode.Selected -> selectedSessionDetail ?: liveSessionDetail
    }
    val gradient = Brush.verticalGradient(
        colors = listOf(
            Color(0xFF08131C),
            Color(0xFF0F2534),
            Color(0xFF112E2B),
        )
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(gradient)
            .padding(paddingValues)
    ) {
        if (dashboardDetail?.processedSummary == null || dashboardDetail.session == null) {
            EmptyDashboardState(
                sessionSetup = sessionSetup,
                onGoToSession = onGoToSession,
            )
        } else {
            DashboardContent(
                detail = dashboardDetail,
                sessionSetup = sessionSetup,
                onGoToSession = onGoToSession,
            )
        }
    }
}

@Composable
private fun EmptyDashboardState(
    sessionSetup: PlayerProfileUiState,
    onGoToSession: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp),
    ) {
        Text(
            text = "Dashboard",
            style = MaterialTheme.typography.headlineMedium,
            color = Color(0xFFE8F8FF),
            fontWeight = FontWeight.Bold,
        )
        Text(
            text = "Todavia no hay una sesion seleccionada con resumen post-sesion. Guarda una sesion y abre su detalle para convertirla en la fuente del dashboard.",
            color = Color(0xFFB8D7E7),
            style = MaterialTheme.typography.bodyLarge,
        )
        TechPanel(title = "Perfil activo") {
            DashboardLabelRow("Nombre", sessionSetup.athleteName.ifBlank { "-" })
            DashboardLabelRow("Sexo", sessionSetup.sex)
            DashboardLabelRow("Mano dominante", sessionSetup.dominantHand)
            DashboardLabelRow("Nivel", sessionSetup.level)
        }
        Button(onClick = onGoToSession) {
            Text("Ir a sesion")
        }
    }
}

@Composable
private fun DashboardContent(
    detail: SessionDetailUiState,
    sessionSetup: PlayerProfileUiState,
    onGoToSession: () -> Unit,
) {
    val session = detail.session!!
    val summary = detail.processedSummary!!
    val dashboardMetrics = listOf(
        DashboardMetric("Player Load", summary.playerLoadScore.toString(), "carga total"),
        DashboardMetric("Carga/min", summary.playerLoadPerMinute.toString(), "workload"),
        DashboardMetric("Distancia est.", summary.estimatedDistanceMeters?.let { "${it} m" } ?: "-", "pasos"),
        DashboardMetric("Impactos/min", formatOneDecimal(summary.impactsPerMinute), "ritmo"),
        DashboardMetric("Perfil accel", summary.accelerationEventCount.toString(), "eventos"),
        DashboardMetric("Exposicion", "${summary.explosiveExposurePercent}%", "alta intensidad"),
        DashboardMetric("RII proxy", summary.rallyIntensityIndexProxy.toString(), "rally"),
        DashboardMetric("Swing p95", summary.swingSpeedProxyP95Raw?.toString() ?: "-", "tecnica"),
        DashboardMetric("Potencia p95", summary.powerIndexProxyP95?.toString() ?: "-", "golpe"),
        DashboardMetric("Consistencia", summary.consistencyScorePercent?.let { "$it%" } ?: "-", "estabilidad"),
    )

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp),
    ) {
        item {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    text = "Dashboard",
                    style = MaterialTheme.typography.headlineMedium,
                    color = Color(0xFFF2FFFF),
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    text = if (detail.isLive) {
                        "Lectura en sesion activa, actualizada de forma ligera sobre el flujo IMU en memoria."
                    } else {
                        "Lectura post-sesion calculada a partir del CSV crudo y los metadatos guardados."
                    },
                    color = Color(0xFF9ED7D0),
                    style = MaterialTheme.typography.bodyLarge,
                )
            }
        }

        item {
            HeroDashboardCard(
                sessionName = session.sessionName,
                sessionId = session.id.take(8),
                sessionMode = session.mode,
                startedAt = formatTimestamp(session.startedAtEpochMs),
                duration = formatDuration(summary.durationMs),
                batteryBand = "${summary.batteryAtStartPercent ?: "-"}% -> ${summary.batteryAtEndPercent ?: "-"}%",
                isLive = detail.isLive,
            )
        }

        item {
            LazyHorizontalGrid(
                rows = GridCells.Fixed(2),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(400.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                items(dashboardMetrics) { metric ->
                    MetricTile(metric)
                }
            }
        }

        item {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                TechPanel(
                    title = "Perfil de sesion",
                ) {
                    DashboardLabelRow("Nombre", sessionSetup.athleteName.ifBlank { "-" })
                    DashboardLabelRow("Sexo", sessionSetup.sex)
                    DashboardLabelRow("Mano", sessionSetup.dominantHand)
                    DashboardLabelRow("Nivel", sessionSetup.level)
                }
                TechPanel(
                    title = "Carga y movilidad",
                ) {
                    DashboardLabelRow("Pasos", formatStepBand(summary.stepCountStart, summary.stepCountEnd))
                    DashboardLabelRow("Distancia estimada", summary.estimatedDistanceMeters?.let { "$it m" } ?: "-")
                    DashboardLabelRow("Player Load", summary.playerLoadScore.toString())
                    DashboardLabelRow("Carga por minuto", summary.playerLoadPerMinute.toString())
                    DashboardLabelRow("Perfil de aceleraciones", summary.accelerationEventCount.toString())
                    DashboardLabelRow("Exposicion explosiva", "${summary.explosiveExposurePercent}%")
                }
                TechPanel(
                    title = "Tecnica y golpeo",
                ) {
                    DashboardLabelRow("Impactos detectados", summary.candidateImpactCount.toString())
                    DashboardLabelRow("Impactos/min", formatOneDecimal(summary.impactsPerMinute))
                    DashboardLabelRow("RII proxy", summary.rallyIntensityIndexProxy.toString())
                    DashboardLabelRow("Swing p95", summary.swingSpeedProxyP95Raw?.toString() ?: "-")
                    DashboardLabelRow("Potencia p95", summary.powerIndexProxyP95?.toString() ?: "-")
                    DashboardLabelRow("Consistencia", summary.consistencyScorePercent?.let { "$it%" } ?: "-")
                }
                TechPanel(
                    title = "Lectura tecnica base",
                ) {
                    DashboardLabelRow("Muestras", summary.sampleCount.toString())
                    DashboardLabelRow("Bloques", detail.storedBlockCount.toString())
                    DashboardLabelRow("Packets", summary.packetCount.toString())
                    DashboardLabelRow("Media accel", summary.meanAccelMagnitudeRaw.toString())
                    DashboardLabelRow("Media giro", summary.meanGyroMagnitudeRaw.toString())
                    DashboardLabelRow("Pico accel", summary.peakAccelMagnitudeRaw.toString())
                    DashboardLabelRow("Pico giro", summary.peakGyroMagnitudeRaw.toString())
                    DashboardLabelRow("Archivo", session.rawFilePath?.substringAfterLast('\\')?.substringAfterLast('/') ?: "-")
                }
            }
        }

        item {
            TechPanel(title = "Interpretacion inicial") {
                Text(
                    text = buildInterpretation(summary),
                    color = Color(0xFFEAF8F4),
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        }

        item {
            OutlinedButton(onClick = onGoToSession) {
                Text("Volver a sesion")
            }
        }
    }
}

@Composable
private fun HeroDashboardCard(
    sessionName: String,
    sessionId: String,
    sessionMode: String,
    startedAt: String,
    duration: String,
    batteryBand: String,
    isLive: Boolean,
) {
    val brush = Brush.linearGradient(
        colors = listOf(Color(0xFF00B7FF), Color(0xFF00E28A)),
    )

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF091A24)),
        shape = RoundedCornerShape(28.dp),
    ) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Box(
                modifier = Modifier
                    .width(92.dp)
                    .height(6.dp)
                    .background(brush, RoundedCornerShape(50))
            )
            Text(
                text = sessionName,
                style = MaterialTheme.typography.headlineSmall,
                color = Color.White,
                fontWeight = FontWeight.Bold,
            )
            Text(
                text = "id $sessionId",
                color = Color(0xFF9ADBE0),
                style = MaterialTheme.typography.bodySmall,
            )
            Text(
                text = if (isLive) "LIVE SNAPSHOT" else "POST-SESSION SNAPSHOT",
                color = if (isLive) Color(0xFF4BFFB8) else Color(0xFF9ADBE0),
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Bold,
            )
            DashboardModeBadge(sessionMode)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                DashboardLabelColumn("Modo", sessionMode)
                DashboardLabelColumn("Inicio", startedAt)
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                DashboardLabelColumn("Duracion", duration)
                DashboardLabelColumn("Bateria", batteryBand)
            }
        }
    }
}

@Composable
private fun MetricTile(metric: DashboardMetric) {
    Card(
        modifier = Modifier
            .width(168.dp)
            .height(112.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF0C1F2B)),
        shape = RoundedCornerShape(24.dp),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = metric.label,
                color = Color(0xFF94BBC8),
                style = MaterialTheme.typography.labelLarge,
            )
            Text(
                text = metric.value,
                color = Color(0xFFF2FFFF),
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
            )
            Text(
                text = metric.hint,
                color = Color(0xFF35D8A6),
                style = MaterialTheme.typography.bodySmall,
            )
        }
    }
}

@Composable
private fun TechPanel(
    title: String,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF0D1D25)),
        shape = RoundedCornerShape(22.dp),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
            content = {
                Text(
                    text = title,
                    color = Color.White,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                )
                content()
            },
        )
    }
}

@Composable
private fun DashboardLabelRow(
    label: String,
    value: String,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(text = label, color = Color(0xFFA6C2CE))
        Text(text = value, color = Color.White, fontWeight = FontWeight.Medium)
    }
}

@Composable
private fun DashboardLabelColumn(
    label: String,
    value: String,
) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(text = label, color = Color(0xFF9ADBE0), style = MaterialTheme.typography.labelMedium)
        Text(text = value, color = Color.White, fontWeight = FontWeight.Medium)
    }
}

private data class DashboardMetric(
    val label: String,
    val value: String,
    val hint: String,
)

private fun buildInterpretation(summary: PostSessionSummary): String {
    val intensity = when {
        summary.peakAccelMagnitudeRaw >= 1850 -> "alta"
        summary.peakAccelMagnitudeRaw >= 1450 -> "media"
        else -> "baja"
    }
    val distanceText = summary.estimatedDistanceMeters?.let { "$it m" } ?: "-"
    val consistencyText = summary.consistencyScorePercent?.let { "$it%" } ?: "-"

    return "Sesion con intensidad $intensity, Player Load ${summary.playerLoadScore} y " +
        "${formatOneDecimal(summary.impactsPerMinute)} impactos por minuto. La distancia estimada " +
        "por pasos es $distanceText y la consistencia tecnica actual se resume en $consistencyText. " +
        "Esta lectura sigue la idea de carga, movilidad, tecnica y eventos del documento de KPIs."
}

@Composable
private fun DashboardModeBadge(
    mode: String,
) {
    val normalized = mode.lowercase(Locale.getDefault())
    val label = when {
        normalized.contains("ble-real") -> "FUENTE BLE REAL"
        normalized.contains("simulated") -> "FUENTE SIMULADA"
        else -> mode.uppercase(Locale.getDefault())
    }
    val background = when {
        normalized.contains("ble-real") -> Color(0xFF35D8A6).copy(alpha = 0.18f)
        normalized.contains("simulated") -> Color(0xFF00B7FF).copy(alpha = 0.18f)
        else -> Color(0xFF9ADBE0).copy(alpha = 0.18f)
    }
    val content = when {
        normalized.contains("ble-real") -> Color(0xFF4BFFB8)
        normalized.contains("simulated") -> Color(0xFF9ADBE0)
        else -> Color.White
    }

    Surface(
        color = background,
        shape = RoundedCornerShape(50),
    ) {
        Text(
            text = label,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 5.dp),
            color = content,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Bold,
        )
    }
}

private fun formatTimestamp(timestampMs: Long): String {
    return SimpleDateFormat("dd/MM HH:mm", Locale.getDefault())
        .format(Date(timestampMs))
}

private fun formatDuration(durationMs: Long): String {
    val totalSeconds = durationMs / 1000
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return "${minutes}m ${seconds}s"
}

private fun formatOneDecimal(value: Double): String {
    return String.format(Locale.getDefault(), "%.1f", value)
}

private fun formatStepBand(
    start: Long?,
    end: Long?,
): String {
    if (start == null || end == null) return "-"
    return "$start -> $end"
}
