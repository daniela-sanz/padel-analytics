package com.tfg.wearableapp.feature.session

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.tfg.wearableapp.app.TechPanelCard
import com.tfg.wearableapp.app.TechScreenBackground
import com.tfg.wearableapp.app.TechStyle
import com.tfg.wearableapp.data.local.session.SessionBlockSummary
import com.tfg.wearableapp.data.processing.PostSessionSummary
import com.tfg.wearableapp.data.raw.RawSamplePreview
import com.tfg.wearableapp.data.session.StoredSessionSummary
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun SessionScreen(
    paddingValues: PaddingValues,
    uiState: SessionUiState,
    onStartSimulatedSession: () -> Unit,
    onStopSimulatedSession: () -> Unit,
    onRefreshSessions: () -> Unit,
    onSelectSession: (StoredSessionSummary) -> Unit,
    onCloseSessionDetail: () -> Unit,
    onUpdateSessionNameDraft: (String) -> Unit,
    onOpenLiveDashboard: () -> Unit,
    onOpenSelectedDashboard: () -> Unit,
    onOpenInternalDemo: () -> Unit,
) {
    TechScreenBackground(
        modifier = Modifier
            .fillMaxSize()
            .padding(paddingValues),
    ) {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            item {
                Text(
                    text = "Sesion",
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold,
                    color = TechStyle.title,
                )
            }

            item {
                Text(
                    text = "Esta pantalla representa el flujo funcional del MVP. El perfil del jugador vive arriba a la derecha y persiste hasta que lo cambies.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = TechStyle.body,
                )
            }

            item {
                Text(
                    text = "Persistencia local actual: Room",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium,
                    color = TechStyle.accentSecondary,
                )
            }

            item {
                SessionNamingCard(
                    sessionName = uiState.sessionNameDraft,
                    onUpdateSessionNameDraft = onUpdateSessionNameDraft,
                )
            }

            item {
                TechPanelCard(modifier = Modifier.fillMaxWidth()) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        Text(
                            text = "Estado de la sesion",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = TechStyle.title,
                        )
                        SessionMetricRow("Grabando", if (uiState.isRecording) "Si" else "No")
                        SessionMetricRow("Notificaciones", uiState.notificationsSeen.toString())
                        SessionMetricRow("Bloques completos", uiState.blocksCompleted.toString())
                        SessionMetricRow("Muestras recibidas", uiState.samplesReceived.toString())
                        SessionMetricRow("Ultimo packet_id", uiState.lastPacketId?.toString() ?: "-")
                        Text(uiState.statusText, color = TechStyle.body)
                    }
                }
            }

            item {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Button(
                        onClick = onStartSimulatedSession,
                        enabled = !uiState.isRecording,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = TechStyle.accentSecondary,
                            contentColor = TechStyle.bgTop,
                        ),
                    ) {
                        Text("Iniciar sesion", fontWeight = FontWeight.Bold)
                    }
                    OutlinedButton(
                        onClick = onStopSimulatedSession,
                        enabled = uiState.isRecording,
                    ) {
                        Text("Detener y guardar")
                    }
                }
            }

            item {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    item {
                        OutlinedButton(onClick = onRefreshSessions) {
                            Text("Refrescar sesiones")
                        }
                    }
                    item {
                        OutlinedButton(onClick = onOpenLiveDashboard) {
                            Text("Dashboard live")
                        }
                    }
                    item {
                        OutlinedButton(onClick = onOpenInternalDemo) {
                            Text("Abrir demo tecnica")
                        }
                    }
                }
            }

            item {
                Text(
                    text = "Sesiones guardadas",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.SemiBold,
                    color = TechStyle.title,
                )
            }

            uiState.selectedSessionDetail?.let { detail ->
                item {
                    SessionDetailCard(
                        detail = detail,
                        onClose = onCloseSessionDetail,
                        onOpenDashboard = onOpenSelectedDashboard,
                    )
                }
            }

            if (uiState.savedSessions.isEmpty()) {
                item {
                    Text(
                        text = "Todavia no hay sesiones guardadas en local.",
                        color = TechStyle.body,
                    )
                }
            } else {
                items(uiState.savedSessions, key = { it.id }) { session ->
                    SavedSessionCard(
                        session = session,
                        onSelectSession = { onSelectSession(session) },
                    )
                }
            }
        }
    }
}

@Composable
private fun SavedSessionCard(
    session: StoredSessionSummary,
    onSelectSession: () -> Unit,
) {
    TechPanelCard(
        modifier = Modifier.fillMaxWidth(),
        alt = true,
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = session.sessionName,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = TechStyle.title,
            )
            Text(
                text = "id ${session.id.take(8)}",
                style = MaterialTheme.typography.bodySmall,
                color = TechStyle.label,
            )
            SessionMetricRow("Inicio", formatTimestamp(session.startedAtEpochMs))
            SessionMetricRow("Duracion", formatDuration(session.durationMs))
            SessionMetricRow("Modo", session.mode)
            SessionMetricRow("Notificaciones", session.notificationsSeen.toString())
            SessionMetricRow("Bloques", session.blocksCompleted.toString())
            SessionMetricRow("Muestras", session.samplesReceived.toString())
            SessionMetricRow("Ultimo packet_id", session.lastPacketId?.toString() ?: "-")
            SessionMetricRow("Archivo crudo", session.rawFilePath?.let(::extractFileName) ?: "-")
            OutlinedButton(onClick = onSelectSession) {
                Text("Ver detalle")
            }
        }
    }
}

@Composable
private fun SessionDetailCard(
    detail: SessionDetailUiState,
    onClose: () -> Unit,
    onOpenDashboard: () -> Unit,
) {
    val session = detail.session ?: return

    TechPanelCard(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(
                text = "Detalle de ${session.sessionName}",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = TechStyle.title,
            )
            Text(
                text = "id ${session.id.take(8)}",
                style = MaterialTheme.typography.bodySmall,
                color = TechStyle.label,
            )
            SessionMetricRow("Bloques en Room", detail.storedBlockCount.toString())
            SessionMetricRow("Archivo crudo", session.rawFilePath?.let(::extractFileName) ?: "-")

            Text(
                text = "Resumen post-sesion",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = TechStyle.title,
            )
            ProcessedSummarySection(detail.processedSummary)

            Text(
                text = "Primeros bloques",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = TechStyle.title,
            )
            if (detail.blockSummaries.isEmpty()) {
                Text("No hay bloques guardados para esta sesion.", color = TechStyle.body)
            } else {
                detail.blockSummaries.forEach { block ->
                    BlockPreviewRow(block)
                }
            }

            Text(
                text = "Previsualizacion de muestras crudas",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = TechStyle.title,
            )
            if (detail.rawPreview.isEmpty()) {
                Text("No se pudo leer previsualizacion del archivo crudo.", color = TechStyle.body)
            } else {
                detail.rawPreview.forEach { sample ->
                    RawPreviewRow(sample)
                }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedButton(onClick = onClose) {
                    Text("Cerrar detalle")
                }
                Button(
                    onClick = onOpenDashboard,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = TechStyle.accent,
                        contentColor = TechStyle.bgTop,
                    ),
                ) {
                    Text("Ir al dashboard", fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@Composable
private fun SessionNamingCard(
    sessionName: String,
    onUpdateSessionNameDraft: (String) -> Unit,
) {
    TechPanelCard(
        modifier = Modifier.fillMaxWidth(),
        alt = true,
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Text(
                text = "Nombre de sesion",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = TechStyle.title,
            )
            Text(
                text = "Puedes dejarlo vacio y se generara uno automatico al iniciar.",
                style = MaterialTheme.typography.bodySmall,
                color = TechStyle.body,
            )
            OutlinedTextField(
                value = sessionName,
                onValueChange = onUpdateSessionNameDraft,
                label = { Text("Ej. Partido pista 3 / Entreno bandeja") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
            )
        }
    }
}

@Composable
private fun ProcessedSummarySection(
    summary: PostSessionSummary?,
) {
    if (summary == null) {
        Text("No se pudo procesar el archivo crudo de esta sesion.", color = TechStyle.body)
        return
    }

    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        SessionMetricRow("Muestras procesadas", summary.sampleCount.toString())
        SessionMetricRow("Packets distintos", summary.packetCount.toString())
        SessionMetricRow("Duracion cruda", formatDuration(summary.durationMs))
        SessionMetricRow("Pico accel (raw)", summary.peakAccelMagnitudeRaw.toString())
        SessionMetricRow("Pico giro (raw)", summary.peakGyroMagnitudeRaw.toString())
        SessionMetricRow("Media accel (raw)", summary.meanAccelMagnitudeRaw.toString())
        SessionMetricRow("Media giro (raw)", summary.meanGyroMagnitudeRaw.toString())
        SessionMetricRow("Golpes candidatos", summary.candidateImpactCount.toString())
        SessionMetricRow(
            "Bateria inicio-fin",
            "${summary.batteryAtStartPercent ?: "-"}% -> ${summary.batteryAtEndPercent ?: "-"}%",
        )
        Text(
            text = "Estas metricas salen del CSV crudo y usan unidades raw del sensor, no valores calibrados finales.",
            style = MaterialTheme.typography.bodySmall,
            color = TechStyle.body,
        )
    }
}

@Composable
private fun BlockPreviewRow(
    block: SessionBlockSummary,
) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(
            text = "packet ${block.packetId} | samples ${block.sampleCount}",
            fontWeight = FontWeight.Medium,
            color = TechStyle.title,
        )
        Text(
            text = "sampleStartIndex=${block.sampleStartIndex} | battery=${block.batteryLevelPercent}%",
            color = TechStyle.body,
        )
    }
}

@Composable
private fun RawPreviewRow(
    sample: RawSamplePreview,
) {
    Text(
        text = "pkt ${sample.packetId} | idx ${sample.sampleGlobalIndex} | a=(${sample.ax}, ${sample.ay}, ${sample.az}) | g=(${sample.gx}, ${sample.gy}, ${sample.gz})",
        style = MaterialTheme.typography.bodySmall,
        color = TechStyle.body,
    )
}

@Composable
private fun SessionMetricRow(
    label: String,
    value: String,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(text = label, color = TechStyle.body)
        Text(text = value, fontWeight = FontWeight.Medium, color = TechStyle.title)
    }
}

private fun formatTimestamp(timestampMs: Long): String {
    return SimpleDateFormat("dd/MM/yyyy HH:mm:ss", Locale.getDefault())
        .format(Date(timestampMs))
}

private fun formatDuration(durationMs: Long): String {
    val totalSeconds = durationMs / 1000
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return "${minutes}m ${seconds}s"
}

private fun extractFileName(path: String): String {
    return path.substringAfterLast('/').substringAfterLast('\\')
}
