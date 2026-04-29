package com.tfg.wearableapp.feature.session

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tfg.wearableapp.core.ble.BleMtuMath
import com.tfg.wearableapp.core.ble.BleTransportConfig
import com.tfg.wearableapp.core.ble.model.ChunkTransportStats
import com.tfg.wearableapp.core.ble.pipeline.ChunkToBlockPipeline
import com.tfg.wearableapp.data.ble.FakeBleNotificationSource
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class SessionDemoViewModel(
    private val fakeSource: FakeBleNotificationSource = FakeBleNotificationSource(),
    private val pipeline: ChunkToBlockPipeline = ChunkToBlockPipeline(),
) : ViewModel() {
    private val _uiState = MutableStateFlow(
        SessionDemoUiState(
            negotiatedMtu = 185,
            computedChunkPayloadBytes = BleMtuMath.maxChunkPayloadBytes(185),
        )
    )
    val uiState: StateFlow<SessionDemoUiState> = _uiState.asStateFlow()

    private var streamingJob: Job? = null
    private var stats = ChunkTransportStats()

    fun startSimulation() {
        if (streamingJob != null) return

        stats = ChunkTransportStats()
        _uiState.update {
            it.copy(
                isStreaming = true,
                modeLabel = "Simulado",
                statusText = "Recibiendo notificaciones BLE simuladas y reensamblando bloques.",
            )
        }

        streamingJob = viewModelScope.launch {
            fakeSource
                .streamRawNotifications(BleTransportConfig.targetChunkPayloadSizeBytes)
                .collectLatest { rawChunk ->
                    stats = stats.copy(notificationsSeen = stats.notificationsSeen + 1)
                    val block = pipeline.processNotification(
                        rawChunk = rawChunk,
                        nowMs = System.currentTimeMillis(),
                    )

                    if (block != null) {
                        stats = stats.copy(
                            blocksCompleted = stats.blocksCompleted + 1,
                            lastPacketId = block.packetId,
                            lastSampleCount = block.sampleCount,
                        )
                    }

                    publishStats(
                        statusText = if (block == null) {
                            "Chunk recibido. Esperando completar el bloque logico."
                        } else {
                            "Bloque ${block.packetId} completado con ${block.sampleCount} muestras."
                        }
                    )
                }
        }
    }

    fun stopSimulation() {
        streamingJob?.cancel()
        streamingJob = null
        _uiState.update {
            it.copy(
                isStreaming = false,
                statusText = "Demo detenida.",
            )
        }
    }

    fun prepareRealBleMode() {
        stopSimulation()
        _uiState.update {
            it.copy(
                modeLabel = "BLE real",
                negotiatedMtu = 23,
                computedChunkPayloadBytes = BleMtuMath.maxChunkPayloadBytes(23),
                statusText = "Scaffold preparado para conectar un BluetoothGattCallback real.",
            )
        }
    }

    private fun publishStats(statusText: String) {
        _uiState.update {
            it.copy(
                notificationsSeen = stats.notificationsSeen,
                blocksCompleted = stats.blocksCompleted,
                lastPacketId = stats.lastPacketId,
                lastSampleCount = stats.lastSampleCount,
                statusText = statusText,
            )
        }
    }

    override fun onCleared() {
        stopSimulation()
        super.onCleared()
    }
}
