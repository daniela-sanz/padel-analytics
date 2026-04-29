package com.tfg.wearableapp.feature.session

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.tfg.wearableapp.core.ble.BleTransportConfig
import com.tfg.wearableapp.core.ble.model.ChunkTransportStats
import com.tfg.wearableapp.core.ble.pipeline.ChunkToBlockPipeline
import com.tfg.wearableapp.data.ble.FakeBleNotificationSource
import com.tfg.wearableapp.data.session.FileSessionRepository
import com.tfg.wearableapp.data.session.StoredSessionSummary
import java.util.UUID
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class SessionViewModel(
    private val repository: FileSessionRepository,
    private val fakeSource: FakeBleNotificationSource = FakeBleNotificationSource(),
    private val pipeline: ChunkToBlockPipeline = ChunkToBlockPipeline(),
) : ViewModel() {
    private data class ActiveSession(
        val id: String,
        val startedAtEpochMs: Long,
        val mode: String,
    )

    private val _uiState = MutableStateFlow(SessionUiState())
    val uiState: StateFlow<SessionUiState> = _uiState.asStateFlow()

    private var activeSession: ActiveSession? = null
    private var sessionJob: Job? = null
    private var stats = ChunkTransportStats()
    private var samplesReceived = 0

    init {
        refreshSavedSessions()
    }

    fun startSimulatedSession() {
        if (sessionJob != null) return

        val now = System.currentTimeMillis()
        activeSession = ActiveSession(
            id = UUID.randomUUID().toString(),
            startedAtEpochMs = now,
            mode = "simulated",
        )
        stats = ChunkTransportStats()
        samplesReceived = 0

        _uiState.update {
            it.copy(
                isRecording = true,
                notificationsSeen = 0,
                blocksCompleted = 0,
                samplesReceived = 0,
                lastPacketId = null,
                statusText = "Sesion simulada en curso. Recibiendo bloques y actualizando el resumen.",
            )
        }

        sessionJob = viewModelScope.launch {
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
                        samplesReceived += block.sampleCount
                    }

                    _uiState.update {
                        it.copy(
                            notificationsSeen = stats.notificationsSeen,
                            blocksCompleted = stats.blocksCompleted,
                            samplesReceived = samplesReceived,
                            lastPacketId = stats.lastPacketId,
                            statusText = if (block == null) {
                                "Chunk recibido para la sesion. Esperando completar el siguiente bloque."
                            } else {
                                "Sesion simulada activa. Ultimo bloque completo: ${block.packetId}."
                            },
                        )
                    }
                }
        }
    }

    fun stopSimulatedSession() {
        val currentSession = activeSession ?: return

        sessionJob?.cancel()
        sessionJob = null

        val endedAt = System.currentTimeMillis()
        val summary = StoredSessionSummary(
            id = currentSession.id,
            startedAtEpochMs = currentSession.startedAtEpochMs,
            endedAtEpochMs = endedAt,
            durationMs = endedAt - currentSession.startedAtEpochMs,
            mode = currentSession.mode,
            notificationsSeen = stats.notificationsSeen,
            blocksCompleted = stats.blocksCompleted,
            samplesReceived = samplesReceived,
            lastPacketId = stats.lastPacketId,
        )

        activeSession = null

        viewModelScope.launch {
            repository.saveSession(summary)
            val sessions = repository.loadSessions()
            _uiState.update {
                it.copy(
                    isRecording = false,
                    savedSessions = sessions,
                    statusText = "Sesion simulada guardada en local con ${summary.blocksCompleted} bloques completos.",
                )
            }
        }
    }

    fun refreshSavedSessions() {
        viewModelScope.launch {
            val sessions = repository.loadSessions()
            _uiState.update { it.copy(savedSessions = sessions) }
        }
    }

    override fun onCleared() {
        sessionJob?.cancel()
        super.onCleared()
    }

    companion object {
        fun provideFactory(appContext: Context): ViewModelProvider.Factory {
            val repository = FileSessionRepository(appContext)

            return object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T {
                    return SessionViewModel(repository = repository) as T
                }
            }
        }
    }
}
