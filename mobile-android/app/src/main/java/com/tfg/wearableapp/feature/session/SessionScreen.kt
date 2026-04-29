package com.tfg.wearableapp.feature.session

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
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
    onOpenInternalDemo: () -> Unit,
) {
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(paddingValues)
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        item {
            Text(
                text = "Sesion",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
            )
        }

        item {
            Text(
                text = "Esta pantalla representa el flujo funcional del MVP y guarda resumentes de sesiones simuladas en almacenamiento local.",
                style = MaterialTheme.typography.bodyMedium,
            )
        }

        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(20.dp),
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Text(
                        text = "Estado de la sesion",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                    )
                    SessionMetricRow("Grabando", if (uiState.isRecording) "Si" else "No")
                    SessionMetricRow("Notificaciones", uiState.notificationsSeen.toString())
                    SessionMetricRow("Bloques completos", uiState.blocksCompleted.toString())
                    SessionMetricRow("Muestras recibidas", uiState.samplesReceived.toString())
                    SessionMetricRow("Ultimo packet_id", uiState.lastPacketId?.toString() ?: "-")
                    Text(uiState.statusText)
                }
            }
        }

        item {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Button(
                    onClick = onStartSimulatedSession,
                    enabled = !uiState.isRecording,
                ) {
                    Text("Iniciar sesion")
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
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedButton(onClick = onRefreshSessions) {
                    Text("Refrescar sesiones")
                }
                OutlinedButton(onClick = onOpenInternalDemo) {
                    Text("Abrir demo tecnica")
                }
            }
        }

        item {
            Text(
                text = "Sesiones guardadas",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold,
            )
        }

        if (uiState.savedSessions.isEmpty()) {
            item {
                Text("Todavia no hay sesiones guardadas en local.")
            }
        } else {
            items(uiState.savedSessions, key = { it.id }) { session ->
                SavedSessionCard(session = session)
            }
        }
    }
}

@Composable
private fun SavedSessionCard(
    session: StoredSessionSummary,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = "Sesion ${session.id.take(8)}",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
            SessionMetricRow("Inicio", formatTimestamp(session.startedAtEpochMs))
            SessionMetricRow("Duracion", formatDuration(session.durationMs))
            SessionMetricRow("Modo", session.mode)
            SessionMetricRow("Notificaciones", session.notificationsSeen.toString())
            SessionMetricRow("Bloques", session.blocksCompleted.toString())
            SessionMetricRow("Muestras", session.samplesReceived.toString())
            SessionMetricRow("Ultimo packet_id", session.lastPacketId?.toString() ?: "-")
        }
    }
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
        Text(text = label)
        Text(text = value, fontWeight = FontWeight.Medium)
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
