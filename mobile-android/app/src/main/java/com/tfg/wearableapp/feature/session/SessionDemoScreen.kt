package com.tfg.wearableapp.feature.session

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

@Composable
fun SessionDemoScreen(
    paddingValues: PaddingValues = PaddingValues(0.dp),
    uiState: SessionDemoUiState,
    useSimulatedBle: Boolean,
    onToggleSimulation: (Boolean) -> Unit,
    onStartDemo: () -> Unit,
    onStopDemo: () -> Unit,
) {
    MaterialTheme {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(
                text = "Demo tecnica interna",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
            )

            Text(
                text = "Scaffold Android + transporte BLE binario + reensamblado de chunks.",
                style = MaterialTheme.typography.bodyMedium,
            )

            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(20.dp),
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text("Modo simulado")
                        Switch(
                            checked = useSimulatedBle,
                            onCheckedChange = onToggleSimulation,
                        )
                    }

                    MetricRow("Modo actual", uiState.modeLabel)
                    MetricRow("MTU asumido/negociado", "${uiState.negotiatedMtu} bytes")
                    MetricRow("Payload chunk disponible", "${uiState.computedChunkPayloadBytes} bytes")
                    MetricRow("Notificaciones vistas", uiState.notificationsSeen.toString())
                    MetricRow("Bloques completados", uiState.blocksCompleted.toString())
                    MetricRow("Ultimo packet_id", uiState.lastPacketId?.toString() ?: "-")
                    MetricRow("Ultimo sample_count", uiState.lastSampleCount.toString())
                }
            }

            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(20.dp),
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Text(
                        text = "Estado",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text(uiState.statusText)
                }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Button(onClick = onStartDemo, enabled = !uiState.isStreaming) {
                    Text("Iniciar")
                }
                OutlinedButton(onClick = onStopDemo, enabled = uiState.isStreaming) {
                    Text("Detener")
                }
            }
        }
    }
}

@Composable
private fun MetricRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(text = label, style = MaterialTheme.typography.bodyMedium)
        Text(text = value, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
    }
}
