package com.tfg.wearableapp.feature.connection

import android.bluetooth.BluetoothDevice
import android.content.Context
import android.location.LocationManager
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.tfg.wearableapp.core.ble.model.ImuLogicalBlock
import com.tfg.wearableapp.core.ble.model.BleTransportMessage
import com.tfg.wearableapp.core.ble.model.TelemetrySnapshot
import com.tfg.wearableapp.core.ble.parser.BleChunkParser
import com.tfg.wearableapp.core.ble.pipeline.BlePipelineEvent
import com.tfg.wearableapp.core.ble.pipeline.ChunkToBlockPipeline
import com.tfg.wearableapp.data.ble.BleSmokeTestClient
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

class ConnectionViewModel(
    applicationContext: Context,
    private val bleClient: BleSmokeTestClient = BleSmokeTestClient(applicationContext),
) : ViewModel() {
    private val appContext = applicationContext.applicationContext
    private val discoveredDevices = linkedMapOf<String, BluetoothDevice>()
    private val discoveredRssi = linkedMapOf<String, Int>()
    private val discoveredNames = linkedMapOf<String, String>()
    private val discoveredServiceHints = linkedMapOf<String, Boolean>()
    private val chunkPipeline = ChunkToBlockPipeline()
    private var latestTelemetry = TelemetrySnapshot()
    private var firstSignalTimestampMs: Long? = null
    private var lastSignalTimestampMs: Long? = null
    private var lastCompletedPacketId: Long? = null
    private var lastExpectedSampleStartIndex: Long? = null
    private var packetGapCount = 0
    private var sampleGapCount = 0
    private var samplesReassembled = 0

    private val _uiState = MutableStateFlow(
        ConnectionUiState(
            bluetoothAvailable = bleClient.isBluetoothAvailable(),
            locationEnabled = isLocationEnabled(),
        )
    )
    val uiState: StateFlow<ConnectionUiState> = _uiState.asStateFlow()

    private val listener = object : BleSmokeTestClient.Listener {
        override fun onStatusChanged(status: String) {
            _uiState.update { it.copy(statusText = status) }
        }

        override fun onScanDevice(
            device: BluetoothDevice,
            rssi: Int,
            advertisedName: String?,
            advertisesExpectedService: Boolean,
        ) {
            val guessedName = (advertisedName ?: device.name).orEmpty().trim()
            val displayName = when {
                guessedName.isNotBlank() -> guessedName
                advertisesExpectedService -> "Candidato XIAO smoke test"
                else -> "Dispositivo BLE sin nombre"
            }

            discoveredDevices[device.address] = device
            discoveredRssi[device.address] = rssi
            discoveredNames[device.address] = displayName
            discoveredServiceHints[device.address] = advertisesExpectedService

            val rows = discoveredDevices.values.map { discovered ->
                DiscoveredBleDeviceUi(
                    name = discoveredNames[discovered.address] ?: "Dispositivo BLE",
                    address = discovered.address,
                    rssi = discoveredRssi[discovered.address] ?: -999,
                    looksLikeSmokeTest = discoveredServiceHints[discovered.address] == true,
                    advertisedName = advertisedName?.takeIf { discovered.address == device.address },
                    systemName = discovered.name,
                )
            }.sortedByDescending { it.rssi }

            _uiState.update { current ->
                current.copy(
                    discoveredDevices = rows,
                    scanCallbacksSeen = current.scanCallbacksSeen + 1,
                    statusText = "Scan activo. Callbacks vistos: ${current.scanCallbacksSeen + 1}",
                )
            }
        }

        override fun onConnectionChanged(
            isConnected: Boolean,
            deviceName: String?,
            address: String?,
        ) {
            _uiState.update { current ->
                current.copy(
                    isConnected = isConnected,
                    isScanning = false,
                    connectedDeviceName = if (isConnected) deviceName else null,
                    connectedDeviceAddress = if (isConnected) address else null,
                    negotiatedMtu = if (isConnected) current.negotiatedMtu else null,
                )
            }
        }

        override fun onMtuNegotiated(mtu: Int) {
            _uiState.update { it.copy(negotiatedMtu = mtu) }
        }

        override fun onNotification(payload: ByteArray) {
            val nowMs = System.currentTimeMillis()
            val parsedMessage = runCatching { BleChunkParser.parse(payload) }.getOrNull()
            val event = runCatching {
                chunkPipeline.processNotification(payload, nowMs)
            }.getOrNull()

            if (parsedMessage != null) {
                updateChunkUi(parsedMessage, payload)
            } else {
                val counter = parseCounter(payload)
                val battery = payload.getOrNull(4)?.toInt()?.and(0xFF)
                val flag = payload.getOrNull(5)?.toInt()?.and(0xFF)

                _uiState.update { current ->
                    current.copy(
                        notificationsReceived = current.notificationsReceived + 1,
                        transportMode = "Smoke payload",
                        lastCounter = counter,
                        lastBattery = battery,
                        lastFlagHex = flag?.let { "0x" + it.toString(16).uppercase() },
                        lastPayloadHex = payload.toHexString(),
                    )
                }
            }

            when (event) {
                is BlePipelineEvent.BlockCompleted -> updateCompletedBlock(event.block, event.transportKind)
                is BlePipelineEvent.TelemetryUpdated -> {
                    latestTelemetry = latestTelemetry.merge(event.telemetry)
                    _uiState.update {
                        it.copy(
                            lastBattery = latestTelemetry.batteryLevelPercent ?: it.lastBattery,
                            lastStepCountTotal = latestTelemetry.stepCountTotal ?: it.lastStepCountTotal,
                            lastStatusFlags = latestTelemetry.statusFlags ?: it.lastStatusFlags,
                            lastFlagHex = latestTelemetry.statusFlags?.let { status -> "0x" + status.toString(16).uppercase() }
                                ?: it.lastFlagHex,
                            statusText = "Telemetria BLE actualizada.",
                        )
                    }
                }
                is BlePipelineEvent.ExpiredPacketsDropped -> {
                    _uiState.update {
                        it.copy(
                            statusText = "Reensamblado: caducaron packets ${event.packetIds.joinToString()}",
                        )
                    }
                }
                else -> Unit
            }
        }
    }

    init {
        bleClient.addListener(listener)
    }

    fun startScan() {
        discoveredDevices.clear()
        discoveredRssi.clear()
        discoveredNames.clear()
        discoveredServiceHints.clear()
        _uiState.update {
            it.copy(
                locationEnabled = isLocationEnabled(),
                isScanning = true,
                discoveredDevices = emptyList(),
                scanCallbacksSeen = 0,
                statusText = "Preparando escaneo BLE real...",
            )
        }
        bleClient.startScan()
    }

    fun stopScan() {
        bleClient.stopScan()
        _uiState.update { it.copy(isScanning = false, statusText = "Escaneo BLE detenido.") }
    }

    fun onBlePermissionsDenied() {
        _uiState.update {
            it.copy(
                isScanning = false,
                locationEnabled = isLocationEnabled(),
                statusText = "Permisos BLE no concedidos. Revisa Dispositivos cercanos en Ajustes.",
            )
        }
    }

    fun updatePermissionSnapshot(locationPermissionGranted: Boolean) {
        _uiState.update {
            it.copy(
                locationEnabled = isLocationEnabled(),
                locationPermissionGranted = locationPermissionGranted,
            )
        }
    }

    fun connectToDevice(address: String) {
        val device = discoveredDevices[address]
        if (device == null) {
            _uiState.update { it.copy(statusText = "Dispositivo seleccionado no disponible.") }
            return
        }

        _uiState.update {
            it.copy(
                notificationsReceived = 0,
                chunkNotificationsReceived = 0,
                lastCounter = null,
                lastBattery = null,
                lastStepCountTotal = null,
                lastStatusFlags = null,
                lastFlagHex = null,
                lastPayloadHex = "-",
                transportMode = "Desconocido",
                lastChunkPacketId = null,
                lastChunkIndex = null,
                lastChunkCount = null,
                blocksReassembled = 0,
                lastBlockPacketId = null,
                lastBlockSampleCount = null,
                lastBlockBattery = null,
                packetGapCount = 0,
                sampleGapCount = 0,
                effectiveSampleRateHz = null,
                samplesPerSecondObserved = null,
            )
        }
        latestTelemetry = TelemetrySnapshot()
        firstSignalTimestampMs = null
        lastSignalTimestampMs = null
        lastCompletedPacketId = null
        lastExpectedSampleStartIndex = null
        packetGapCount = 0
        sampleGapCount = 0
        samplesReassembled = 0
        bleClient.connect(device)
    }

    fun disconnect() {
        bleClient.disconnect()
        _uiState.update {
            it.copy(
                isConnected = false,
                connectedDeviceName = null,
                connectedDeviceAddress = null,
                negotiatedMtu = null,
                statusText = "Conexion BLE cerrada.",
            )
        }
    }

    override fun onCleared() {
        bleClient.removeListener(listener)
        bleClient.disconnect()
        super.onCleared()
    }

    private fun parseCounter(payload: ByteArray): Long? {
        if (payload.size < 4) return null

        return ((payload[0].toLong() and 0xFF) or
            ((payload[1].toLong() and 0xFF) shl 8) or
            ((payload[2].toLong() and 0xFF) shl 16) or
            ((payload[3].toLong() and 0xFF) shl 24))
    }

    private fun ByteArray.toHexString(): String =
        joinToString(separator = " ") { byte ->
            byte.toInt().and(0xFF).toString(16).uppercase().padStart(2, '0')
        }

    private fun updateChunkUi(
        message: BleTransportMessage,
        payload: ByteArray,
    ) {
        _uiState.update { current ->
            when (message) {
                is BleTransportMessage.LegacyChunkMessage -> current.copy(
                    notificationsReceived = current.notificationsReceived + 1,
                    chunkNotificationsReceived = current.chunkNotificationsReceived + 1,
                    transportMode = "Chunk v1",
                    lastChunkPacketId = message.chunk.packetId,
                    lastChunkIndex = message.chunk.chunkIndex,
                    lastChunkCount = message.chunk.chunkCount,
                    lastPayloadHex = payload.toHexString(),
                )
                is BleTransportMessage.FirstChunkMessage -> current.copy(
                    notificationsReceived = current.notificationsReceived + 1,
                    chunkNotificationsReceived = current.chunkNotificationsReceived + 1,
                    transportMode = "Chunk v2 (MTU 23)",
                    lastChunkPacketId = message.packetId,
                    lastChunkIndex = 0,
                    lastChunkCount = null,
                    lastPayloadHex = payload.toHexString(),
                )
                is BleTransportMessage.ContinuationChunkMessage -> current.copy(
                    notificationsReceived = current.notificationsReceived + 1,
                    chunkNotificationsReceived = current.chunkNotificationsReceived + 1,
                    transportMode = "Chunk v2 (MTU 23)",
                    lastChunkPacketId = message.packetId,
                    lastChunkIndex = message.chunkSequence,
                    lastChunkCount = null,
                    lastPayloadHex = payload.toHexString(),
                )
                is BleTransportMessage.TelemetryMessage -> current.copy(
                    notificationsReceived = current.notificationsReceived + 1,
                    transportMode = "Chunk v2 (MTU 23)",
                    lastPayloadHex = payload.toHexString(),
                )
            }
        }
    }

    private fun updateCompletedBlock(
        block: ImuLogicalBlock,
        transportKind: String,
    ) {
        updateIntegrityCounters(block)
        samplesReassembled += block.sampleCount
        val effectiveRate = computeEffectiveSampleRate(block)
        _uiState.update { current ->
            current.copy(
                blocksReassembled = current.blocksReassembled + 1,
                lastBlockPacketId = block.packetId,
                lastBlockSampleCount = block.sampleCount,
                lastBlockBattery = latestTelemetry.batteryLevelPercent ?: block.batteryLevelPercent,
                packetGapCount = packetGapCount,
                sampleGapCount = sampleGapCount,
                effectiveSampleRateHz = effectiveRate,
                samplesPerSecondObserved = effectiveRate,
                statusText = "Bloque reensamblado (${transportKind}) packet=${block.packetId} samples=${block.sampleCount}",
            )
        }
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
    }

    private fun computeEffectiveSampleRate(block: ImuLogicalBlock): Double? {
        if (block.sampleCount <= 0) return null
        val blockEndTimestampMs = block.timestampBlockStartMs +
            (((block.sampleCount - 1) * 1000.0) / 104.0).toLong()

        if (firstSignalTimestampMs == null) {
            firstSignalTimestampMs = block.timestampBlockStartMs
        }
        lastSignalTimestampMs = blockEndTimestampMs

        val elapsedMs = (lastSignalTimestampMs ?: return null) - (firstSignalTimestampMs ?: return null)
        if (elapsedMs <= 0L) return null

        return samplesReassembled * 1000.0 / elapsedMs.toDouble()
    }

    private fun TelemetrySnapshot.merge(newValue: TelemetrySnapshot): TelemetrySnapshot {
        return copy(
            batteryLevelPercent = newValue.batteryLevelPercent ?: batteryLevelPercent,
            stepCountTotal = newValue.stepCountTotal ?: stepCountTotal,
            statusFlags = newValue.statusFlags ?: statusFlags,
        )
    }

    private fun isLocationEnabled(): Boolean {
        val locationManager = appContext.getSystemService(LocationManager::class.java)
        return locationManager?.isLocationEnabled == true
    }

    companion object {
        fun provideFactory(
            applicationContext: Context,
            bleClient: BleSmokeTestClient,
        ): ViewModelProvider.Factory =
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T {
                    return ConnectionViewModel(applicationContext, bleClient) as T
                }
            }
    }
}
