package com.tfg.wearableapp.feature.session

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.tfg.wearableapp.app.TechPanelCard
import com.tfg.wearableapp.app.TechScreenBackground
import com.tfg.wearableapp.app.TechStyle

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
        TechScreenBackground(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                Text(
                    text = "Demo tecnica interna",
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold,
                    color = TechStyle.title,
                )

                Text(
                    text = "Scaffold Android + transporte BLE binario + reensamblado de chunks.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = TechStyle.body,
                )

                TechPanelCard(modifier = Modifier.fillMaxWidth()) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text("Modo simulado", color = TechStyle.title)
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

                TechPanelCard(
                    modifier = Modifier.fillMaxWidth(),
                    alt = true,
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Text(
                            text = "Estado",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = TechStyle.title,
                        )
                        Text(uiState.statusText, color = TechStyle.body)
                    }
                }

                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Button(
                        onClick = onStartDemo,
                        enabled = !uiState.isStreaming,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = TechStyle.accentSecondary,
                            contentColor = TechStyle.bgTop,
                        ),
                    ) {
                        Text("Iniciar", fontWeight = FontWeight.Bold)
                    }
                    OutlinedButton(onClick = onStopDemo, enabled = uiState.isStreaming) {
                        Text("Detener")
                    }
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
        Text(text = label, style = MaterialTheme.typography.bodyMedium, color = TechStyle.body)
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium,
            color = TechStyle.title,
        )
    }
}
