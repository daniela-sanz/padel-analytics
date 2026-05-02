package com.tfg.wearableapp.feature.session

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.tfg.wearableapp.core.ble.BleTransportConfig
import com.tfg.wearableapp.core.ble.model.BleTransportMessage
import com.tfg.wearableapp.core.ble.model.ChunkTransportStats
import com.tfg.wearableapp.core.ble.model.ImuLogicalBlock
import com.tfg.wearableapp.core.ble.model.TelemetrySnapshot
import com.tfg.wearableapp.core.ble.parser.BleChunkParser
import com.tfg.wearableapp.core.ble.pipeline.BlePipelineEvent
import com.tfg.wearableapp.core.ble.pipeline.ChunkToBlockPipeline
import com.tfg.wearableapp.data.ble.BleSmokeTestClient
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
    private val bleClient: BleSmokeTestClient,
    private val fakeSource: FakeBleNotificationSource = FakeBleNotificationSource(),
    private var pipeline: ChunkToBlockPipeline = ChunkToBlockPipeline(),
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
    private var simulatedSessionJob: Job? = null
    private var stats = ChunkTransportStats()
    private var samplesReceived = 0
    private var liveSessionAccumulator = LiveSessionAccumulator()
    private var lastLiveDashboardPublishEpochMs = 0L
    private var latestTelemetry = TelemetrySnapshot()
    private var firstSignalTimestampMs: Long? = null
    private var lastSignalTimestampMs: Long? = null
    private var lastCompletedPacketId: Long? = null
    private var lastExpectedSampleStartIndex: Long? = null
    private var packetGapCount = 0
    private var sampleGapCount = 0
    private val profileRepository = PlayerProfilePreferencesRepository(appContext)
    private val rawFileWriter = SessionRawFileWriter(appContext)
    private val rawFileReader = SessionRawFileReader()
    private val postSessionProcessor = PostSessionProcessor()

    private val bleListener =
        object : BleSmokeTestClient.Listener {
            override fun onStatusChanged(status: String) {
                if (activeSession?.mode == "ble-real" && _uiState.value.isRecording) {
                    _uiState.update { it.copy(statusText = status) }
                }
            }

            override fun onScanDevice(
                device: android.bluetooth.BluetoothDevice,
                rssi: Int,
                advertisedName: String?,
                advertisesExpectedService: Boolean,
            ) = Unit

            override fun onConnectionChanged(
                isConnected: Boolean,
                deviceName: String?,
                address: String?,
            ) {
                _uiState.update {
                    it.copy(
                        bleDeviceConnected = isConnected,
                        bleDeviceName = if (isConnected) deviceName else null,
                    )
                }

                if (!isConnected && activeSession?.mode == "ble-real" && _uiState.value.isRecording) {
                    stopActiveSession(disconnectedUnexpectedly = true)
                }
            }

            override fun onMtuNegotiated(mtu: Int) = Unit

            override fun onNotification(payload: ByteArray) {
                val currentSession = activeSession ?: return
                if (currentSession.mode != "ble-real" || !_uiState.value.isRecording) return

                viewModelScope.launch {
                    processIncomingChunk(currentSession, payload)
                }
            }
        }

    init {
        _uiState.update {
            it.copy(
                playerProfile = profileRepository.load(),
                bleDeviceConnected = bleClient.isConnected(),
                bleDeviceName = bleClient.connectedDeviceName(),
            )
        }
        bleClient.addListener(bleListener)
        refreshSavedSessions()
    }

    fun startSimulatedSession() {
        if (_uiState.value.isRecording) return

        val currentSession = prepareNewSession(mode = "simulated")
        _uiState.update {
            it.copy(
                isRecording = true,
                recordingMode = "Simulada",
                notificationsSeen = 0,
                blocksCompleted = 0,
                samplesReceived = 0,
                lastPacketId = null,
                lastBatteryLevel = null,
                lastStepCountTotal = null,
                lastStatusFlags = null,
                packetGapCount = 0,
                sampleGapCount = 0,
                effectiveSampleRateHz = null,
                liveDashboardDetail = null,
                statusText = "Sesion simulada en curso. Recibiendo bloques y actualizando el resumen.",
            )
        }

        simulatedSessionJob = viewModelScope.launch {
            val rawFilePath = rawFileWriter.start(currentSession.id)
            activeSession = currentSession.copy(rawFilePath = rawFilePath)

            repository.createSession(
                StoredSessionSummary(
                    id = currentSession.id,
                    sessionName = currentSession.sessionName,
                    startedAtEpochMs = currentSession.startedAtEpochMs,
                    endedAtEpochMs = currentSession.startedAtEpochMs,
                    durationMs = 0L,
                    mode = currentSession.mode,
                    notificationsSeen = 0,
                    blocksCompleted = 0,
                    samplesReceived = 0,
                    lastPacketId = null,
                    rawFilePath = rawFilePath,
                )
            )

            fakeSource
                .streamRawNotifications(BleTransportConfig.targetChunkPayloadSizeBytes)
                .collectLatest { rawChunk ->
                    val session = activeSession ?: return@collectLatest
                    processIncomingChunk(session, rawChunk)
                }
        }
    }

    fun startRealBleSession() {
        if (_uiState.value.isRecording) return

        if (!bleClient.isConnected()) {
            _uiState.update {
                it.copy(
                    statusText = "No hay un dispositivo BLE real conectado. Conecta la XIAO en la pestaña Conexion primero.",
                )
            }
            return
        }

        val currentSession = prepareNewSession(mode = "ble-real")
        viewModelScope.launch {
            val rawFilePath = rawFileWriter.start(currentSession.id)
            activeSession = currentSession.copy(rawFilePath = rawFilePath)

            repository.createSession(
                StoredSessionSummary(
                    id = currentSession.id,
                    sessionName = currentSession.sessionName,
                    startedAtEpochMs = currentSession.startedAtEpochMs,
                    endedAtEpochMs = currentSession.startedAtEpochMs,
                    durationMs = 0L,
                    mode = currentSession.mode,
                    notificationsSeen = 0,
                    blocksCompleted = 0,
                    samplesReceived = 0,
                    lastPacketId = null,
                    rawFilePath = rawFilePath,
                )
            )

            _uiState.update {
                it.copy(
                    isRecording = true,
                    recordingMode = "BLE real",
                    notificationsSeen = 0,
                    blocksCompleted = 0,
                    samplesReceived = 0,
                    lastPacketId = null,
                    lastBatteryLevel = null,
                    lastStepCountTotal = null,
                    lastStatusFlags = null,
                    packetGapCount = 0,
                    sampleGapCount = 0,
                    effectiveSampleRateHz = null,
                    liveDashboardDetail = null,
                    statusText = "Sesion BLE real en curso. Esperando chunks desde la XIAO.",
                )
            }

            bleClient.sendStartSessionCommand()
        }
    }

    fun stopSimulatedSession() {
        stopActiveSession()
    }

    fun stopRealBleSession() {
        if (_uiState.value.isRecording && activeSession?.mode == "ble-real") {
            bleClient.sendStopSessionCommand()
        }
        stopActiveSession()
    }

    private fun prepareNewSession(mode: String): ActiveSession {
        val now = System.currentTimeMillis()
        val sessionId = UUID.randomUUID().toString()
        val sessionName = _uiState.value.sessionNameDraft.ifBlank {
            "Sesion ${SimpleDateFormat("ddMMyy-HHmm", Locale.getDefault()).format(Date(now))}"
        }

        activeSession = ActiveSession(
            id = sessionId,
            sessionName = sessionName,
            startedAtEpochMs = now,
            mode = mode,
            rawFilePath = "",
        )
        simulatedSessionJob?.cancel()
        simulatedSessionJob = null
        stats = ChunkTransportStats()
        samplesReceived = 0
        liveSessionAccumulator = LiveSessionAccumulator()
        lastLiveDashboardPublishEpochMs = 0L
        latestTelemetry = TelemetrySnapshot()
        firstSignalTimestampMs = null
        lastSignalTimestampMs = null
        lastCompletedPacketId = null
        lastExpectedSampleStartIndex = null
        packetGapCount = 0
        sampleGapCount = 0
        pipeline = ChunkToBlockPipeline()
        return requireNotNull(activeSession)
    }

    private suspend fun processIncomingChunk(
        currentSession: ActiveSession,
        rawChunk: ByteArray,
    ) {
        val nowMs = System.currentTimeMillis()
        stats = stats.copy(notificationsSeen = stats.notificationsSeen + 1)
        val parsedMessage = runCatching { BleChunkParser.parse(rawChunk) }.getOrNull()
        val event = pipeline.processNotification(
            rawChunk = rawChunk,
            nowMs = nowMs,
        )

        when (event) {
            is BlePipelineEvent.BlockCompleted -> {
                onCompletedBlock(currentSession, event.block)
            }
            is BlePipelineEvent.TelemetryUpdated -> {
                latestTelemetry = latestTelemetry.merge(event.telemetry)
            }
            is BlePipelineEvent.ExpiredPacketsDropped -> {
                _uiState.update {
                    it.copy(
                        statusText = "Expiraron packets en reensamblado: ${event.packetIds.joinToString()}",
                    )
                }
            }
            else -> Unit
        }

        val transportLabel = when (parsedMessage) {
            is BleTransportMessage.LegacyChunkMessage -> "Chunk v1"
            is BleTransportMessage.FirstChunkMessage,
            is BleTransportMessage.ContinuationChunkMessage,
            is BleTransportMessage.TelemetryMessage,
            -> "Chunk v2 (MTU 23)"
            null -> "Desconocido"
        }

        _uiState.update {
            it.copy(
                notificationsSeen = stats.notificationsSeen,
                blocksCompleted = stats.blocksCompleted,
                samplesReceived = samplesReceived,
                lastPacketId = stats.lastPacketId,
                lastBatteryLevel = latestTelemetry.batteryLevelPercent ?: it.lastBatteryLevel,
                lastStepCountTotal = latestTelemetry.stepCountTotal ?: it.lastStepCountTotal,
                lastStatusFlags = latestTelemetry.statusFlags ?: it.lastStatusFlags,
                packetGapCount = packetGapCount,
                sampleGapCount = sampleGapCount,
                effectiveSampleRateHz = computeEffectiveSampleRatePreview(),
                statusText = if (event is BlePipelineEvent.BlockCompleted) {
                    "Sesion ${if (currentSession.mode == "ble-real") "BLE real" else "simulada"} activa con $transportLabel. Ultimo bloque completo: ${event.block.packetId}."
                } else {
                    "Chunk recibido para la sesion ($transportLabel). Esperando completar el siguiente bloque."
                },
            )
        }
    }

    private suspend fun onCompletedBlock(
        currentSession: ActiveSession,
        block: ImuLogicalBlock,
    ) {
        stats = stats.copy(
            blocksCompleted = stats.blocksCompleted + 1,
            lastPacketId = block.packetId,
            lastSampleCount = block.sampleCount,
        )
        samplesReceived += block.sampleCount
        updateIntegrityCounters(block)
        repository.saveSessionBlock(
            sessionId = currentSession.id,
            block = block,
            receivedAtEpochMs = System.currentTimeMillis(),
            telemetry = latestTelemetry,
        )
        rawFileWriter.appendBlock(
            sessionId = currentSession.id,
            block = block,
            telemetry = latestTelemetry,
        )
        liveSessionAccumulator.addBlock(block, latestTelemetry)
        publishLiveDashboardIfNeeded(
            currentSession = currentSession,
            force = block.packetId == 1L,
        )
    }

    private fun stopActiveSession(
        disconnectedUnexpectedly: Boolean = false,
    ) {
        val currentSession = activeSession ?: return

        simulatedSessionJob?.cancel()
        simulatedSessionJob = null

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
            pipeline = ChunkToBlockPipeline()
            val finalLiveSummary = liveSessionAccumulator.snapshot()
            _uiState.update {
                it.copy(
                    isRecording = false,
                    recordingMode = null,
                    savedSessions = sessions,
                liveDashboardDetail = if (finalLiveSummary == null) null else {
                        SessionDetailUiState(
                            session = summary,
                            storedBlockCount = storedBlockCount,
                            processedSummary = finalLiveSummary,
                            isLive = false,
                        )
                    },
                    statusText = if (disconnectedUnexpectedly) {
                        "Sesion BLE real detenida porque la conexion se cerro. Se guardaron ${summary.blocksCompleted} bloques. Blocks persistidos: $storedBlockCount."
                    } else {
                        "Sesion guardada en local con ${summary.blocksCompleted} bloques completos. Blocks persistidos: $storedBlockCount. Crudo: ${summary.rawFilePath}."
                    },
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
        simulatedSessionJob?.cancel()
        rawFileWriter.close()
        bleClient.removeListener(bleListener)
        super.onCleared()
    }

    private fun updateIntegrityCounters(block: ImuLogicalBlock) {
        lastCompletedPacketId?.let { previousPacketId ->
            val missingPackets = (block.packetId - previousPacketId - 1).coerceAtLeast(0L).toInt()
            packetGapCount += missingPackets
        }
        lastExpectedSampleStartIndex?.let { expectedSampleStart ->
            val missingSamples = (block.sampleStartIndex - expectedSampleStart).coerceAtLeast(0L).toInt()
            sampleGapCount += missingSamples
        }

        lastCompletedPacketId = block.packetId
        lastExpectedSampleStartIndex = block.sampleStartIndex + block.sampleCount

        if (firstSignalTimestampMs == null) {
            firstSignalTimestampMs = block.timestampBlockStartMs
        }
        lastSignalTimestampMs = block.timestampBlockStartMs +
            (((block.sampleCount - 1) * 1000.0) / BleTransportConfig.targetSampleRateHz.toDouble()).toLong()
    }

    private fun computeEffectiveSampleRatePreview(): Double? {
        val first = firstSignalTimestampMs ?: return null
        val last = lastSignalTimestampMs ?: return null
        val elapsedMs = last - first
        if (elapsedMs <= 0L || samplesReceived <= 0) return null
        return samplesReceived * 1000.0 / elapsedMs.toDouble()
    }

    private fun TelemetrySnapshot.merge(newValue: TelemetrySnapshot): TelemetrySnapshot {
        return copy(
            batteryLevelPercent = newValue.batteryLevelPercent ?: batteryLevelPercent,
            stepCountTotal = newValue.stepCountTotal ?: stepCountTotal,
            statusFlags = newValue.statusFlags ?: statusFlags,
        )
    }

    companion object {
        fun provideFactory(
            appContext: Context,
            bleClient: BleSmokeTestClient,
        ): ViewModelProvider.Factory {
            val database = AppDatabase.getInstance(appContext)
            val repository = RoomSessionRepository(database.sessionDao())

            return object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T {
                    return SessionViewModel(
                        appContext = appContext,
                        repository = repository,
                        bleClient = bleClient,
                    ) as T
                }
            }
        }
    }
}
