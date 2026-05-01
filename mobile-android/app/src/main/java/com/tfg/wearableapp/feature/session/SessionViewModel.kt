package com.tfg.wearableapp.feature.session

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.tfg.wearableapp.core.ble.BleTransportConfig
import com.tfg.wearableapp.core.ble.model.ChunkTransportStats
import com.tfg.wearableapp.core.ble.pipeline.ChunkToBlockPipeline
import com.tfg.wearableapp.data.ble.FakeBleNotificationSource
import com.tfg.wearableapp.data.local.AppDatabase
import com.tfg.wearableapp.data.processing.LiveSessionAccumulator
import com.tfg.wearableapp.data.processing.PostSessionProcessor
import com.tfg.wearableapp.data.profile.PlayerProfilePreferencesRepository
import com.tfg.wearableapp.data.raw.SessionRawFileReader
import com.tfg.wearableapp.data.raw.SessionRawFileWriter
import com.tfg.wearableapp.data.session.RoomSessionRepository
import com.tfg.wearableapp.data.session.StoredSessionSummary
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class SessionViewModel(
    private val appContext: Context,
    private val repository: RoomSessionRepository,
    private val fakeSource: FakeBleNotificationSource = FakeBleNotificationSource(),
    private val pipeline: ChunkToBlockPipeline = ChunkToBlockPipeline(),
) : ViewModel() {
    private data class ActiveSession(
        val id: String,
        val sessionName: String,
        val startedAtEpochMs: Long,
        val mode: String,
        val rawFilePath: String,
    )

    private val _uiState = MutableStateFlow(SessionUiState())
    val uiState: StateFlow<SessionUiState> = _uiState.asStateFlow()

    private var activeSession: ActiveSession? = null
    private var sessionJob: Job? = null
    private var stats = ChunkTransportStats()
    private var samplesReceived = 0
    private var liveSessionAccumulator = LiveSessionAccumulator()
    private var lastLiveDashboardPublishEpochMs = 0L
    private val profileRepository = PlayerProfilePreferencesRepository(appContext)
    private val rawFileWriter = SessionRawFileWriter(appContext)
    private val rawFileReader = SessionRawFileReader()
    private val postSessionProcessor = PostSessionProcessor()

    init {
        _uiState.update { it.copy(playerProfile = profileRepository.load()) }
        refreshSavedSessions()
    }

    fun startSimulatedSession() {
        if (sessionJob != null) return

        val now = System.currentTimeMillis()
        val sessionId = UUID.randomUUID().toString()
        val sessionName = _uiState.value.sessionNameDraft.ifBlank {
            "Sesion ${SimpleDateFormat("ddMMyy-HHmm", Locale.getDefault()).format(Date(now))}"
        }
        activeSession = ActiveSession(
            id = sessionId,
            sessionName = sessionName,
            startedAtEpochMs = now,
            mode = "simulated",
            rawFilePath = "",
        )
        stats = ChunkTransportStats()
        samplesReceived = 0
        liveSessionAccumulator = LiveSessionAccumulator()
        lastLiveDashboardPublishEpochMs = 0L

        _uiState.update {
            it.copy(
                isRecording = true,
                notificationsSeen = 0,
                blocksCompleted = 0,
                samplesReceived = 0,
                lastPacketId = null,
                liveDashboardDetail = null,
                statusText = "Sesion simulada en curso. Recibiendo bloques y actualizando el resumen.",
            )
        }

        sessionJob = viewModelScope.launch {
            val rawFilePath = rawFileWriter.start(sessionId)
            activeSession = activeSession?.copy(rawFilePath = rawFilePath)

            repository.createSession(
                StoredSessionSummary(
                    id = activeSession!!.id,
                    sessionName = activeSession!!.sessionName,
                    startedAtEpochMs = activeSession!!.startedAtEpochMs,
                    endedAtEpochMs = activeSession!!.startedAtEpochMs,
                    durationMs = 0L,
                    mode = activeSession!!.mode,
                    notificationsSeen = 0,
                    blocksCompleted = 0,
                    samplesReceived = 0,
                    lastPacketId = null,
                    rawFilePath = activeSession!!.rawFilePath,
                )
            )

            fakeSource
                .streamRawNotifications(BleTransportConfig.targetChunkPayloadSizeBytes)
                .collectLatest { rawChunk ->
                    stats = stats.copy(notificationsSeen = stats.notificationsSeen + 1)
                    val currentSession = activeSession
                    val block = pipeline.processNotification(
                        rawChunk = rawChunk,
                        nowMs = System.currentTimeMillis(),
                    )

                    if (block != null && currentSession != null) {
                        stats = stats.copy(
                            blocksCompleted = stats.blocksCompleted + 1,
                            lastPacketId = block.packetId,
                            lastSampleCount = block.sampleCount,
                        )
                        samplesReceived += block.sampleCount
                        repository.saveSessionBlock(
                            sessionId = currentSession.id,
                            block = block,
                            receivedAtEpochMs = System.currentTimeMillis(),
                        )
                        rawFileWriter.appendBlock(
                            sessionId = currentSession.id,
                            block = block,
                        )
                        liveSessionAccumulator.addBlock(block)
                        publishLiveDashboardIfNeeded(
                            currentSession = currentSession,
                            force = block.packetId == 1L,
                        )
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
            sessionName = currentSession.sessionName,
            startedAtEpochMs = currentSession.startedAtEpochMs,
            endedAtEpochMs = endedAt,
            durationMs = endedAt - currentSession.startedAtEpochMs,
            mode = currentSession.mode,
            notificationsSeen = stats.notificationsSeen,
            blocksCompleted = stats.blocksCompleted,
            samplesReceived = samplesReceived,
            lastPacketId = stats.lastPacketId,
            rawFilePath = currentSession.rawFilePath,
        )

        activeSession = null

        viewModelScope.launch {
            repository.updateSession(summary)
            val sessionsDeferred = async { repository.loadSessions() }
            val storedBlockCountDeferred = async { repository.countBlocksForSession(summary.id) }
            val sessions = sessionsDeferred.await()
            val storedBlockCount = storedBlockCountDeferred.await()
            rawFileWriter.close()
            val finalLiveSummary = liveSessionAccumulator.snapshot()
            _uiState.update {
                it.copy(
                    isRecording = false,
                    savedSessions = sessions,
                    liveDashboardDetail = if (finalLiveSummary == null) null else {
                        SessionDetailUiState(
                            session = summary,
                            storedBlockCount = storedBlockCount,
                            processedSummary = finalLiveSummary,
                            isLive = false,
                        )
                    },
                    statusText = "Sesion simulada guardada en local con ${summary.blocksCompleted} bloques completos. Blocks persistidos: $storedBlockCount. Crudo: ${summary.rawFilePath}.",
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

    fun selectSession(session: StoredSessionSummary) {
        viewModelScope.launch {
            val blockCountDeferred = async { repository.countBlocksForSession(session.id) }
            val blockSummariesDeferred = async { repository.loadBlockSummariesForSession(session.id) }
            val rawPreviewDeferred = async { rawFileReader.readPreview(session.rawFilePath) }
            val processedSummaryDeferred = async { postSessionProcessor.process(session.rawFilePath) }

            _uiState.update {
                it.copy(
                    selectedSessionDetail = SessionDetailUiState(
                        session = session,
                        storedBlockCount = blockCountDeferred.await(),
                        blockSummaries = blockSummariesDeferred.await(),
                        rawPreview = rawPreviewDeferred.await(),
                        processedSummary = processedSummaryDeferred.await(),
                        isLive = false,
                    )
                )
            }
        }
    }

    fun clearSelectedSession() {
        _uiState.update { it.copy(selectedSessionDetail = null) }
    }

    fun updateSessionNameDraft(sessionName: String) {
        _uiState.update { it.copy(sessionNameDraft = sessionName) }
    }

    fun openLiveDashboard() {
        _uiState.update { it.copy(dashboardMode = DashboardMode.Live) }
    }

    fun openSelectedDashboard() {
        _uiState.update { it.copy(dashboardMode = DashboardMode.Selected) }
    }

    fun updateAthleteName(name: String) {
        _uiState.update {
            val updated = it.playerProfile.copy(athleteName = name)
            profileRepository.save(updated)
            it.copy(playerProfile = updated)
        }
    }

    fun updateSex(sex: String) {
        _uiState.update {
            val updated = it.playerProfile.copy(sex = sex)
            profileRepository.save(updated)
            it.copy(playerProfile = updated)
        }
    }

    fun updateDominantHand(dominantHand: String) {
        _uiState.update {
            val updated = it.playerProfile.copy(dominantHand = dominantHand)
            profileRepository.save(updated)
            it.copy(playerProfile = updated)
        }
    }

    fun updateLevel(level: String) {
        _uiState.update {
            val updated = it.playerProfile.copy(level = level)
            profileRepository.save(updated)
            it.copy(playerProfile = updated)
        }
    }

    fun updateNotes(notes: String) {
        _uiState.update {
            val updated = it.playerProfile.copy(notes = notes)
            profileRepository.save(updated)
            it.copy(playerProfile = updated)
        }
    }

    private fun publishLiveDashboardIfNeeded(
        currentSession: ActiveSession,
        force: Boolean = false,
    ) {
        val now = System.currentTimeMillis()
        val publishIntervalMs = 2_000L
        if (!force && now - lastLiveDashboardPublishEpochMs < publishIntervalMs) {
            return
        }

        val liveSummary = liveSessionAccumulator.snapshot() ?: return
        lastLiveDashboardPublishEpochMs = now
        val liveSession = StoredSessionSummary(
            id = currentSession.id,
            sessionName = currentSession.sessionName,
            startedAtEpochMs = currentSession.startedAtEpochMs,
            endedAtEpochMs = now,
            durationMs = now - currentSession.startedAtEpochMs,
            mode = "${currentSession.mode}-live",
            notificationsSeen = stats.notificationsSeen,
            blocksCompleted = stats.blocksCompleted,
            samplesReceived = samplesReceived,
            lastPacketId = stats.lastPacketId,
            rawFilePath = currentSession.rawFilePath,
        )

        _uiState.update {
            it.copy(
                liveDashboardDetail = SessionDetailUiState(
                    session = liveSession,
                    storedBlockCount = stats.blocksCompleted,
                    processedSummary = liveSummary,
                    isLive = true,
                )
            )
        }
    }

    override fun onCleared() {
        sessionJob?.cancel()
        rawFileWriter.close()
        super.onCleared()
    }

    companion object {
        fun provideFactory(appContext: Context): ViewModelProvider.Factory {
            val database = AppDatabase.getInstance(appContext)
            val repository = RoomSessionRepository(database.sessionDao())

            return object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T {
                    return SessionViewModel(
                        appContext = appContext,
                        repository = repository,
                    ) as T
                }
            }
        }
    }
}
