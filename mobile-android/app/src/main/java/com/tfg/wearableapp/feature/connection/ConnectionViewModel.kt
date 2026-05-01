package com.tfg.wearableapp.feature.connection

import android.bluetooth.BluetoothDevice
import android.content.Context
import android.location.LocationManager
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.tfg.wearableapp.data.ble.BleSmokeTestClient
import com.tfg.wearableapp.core.ble.model.ImuLogicalBlock
import com.tfg.wearableapp.core.ble.parser.BleChunkParser
import com.tfg.wearableapp.core.ble.pipeline.ChunkToBlockPipeline
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

class ConnectionViewModel(
    applicationContext: Context,
) : ViewModel() {
    private val appContext = applicationContext.applicationContext
    private val bleClient = BleSmokeTestClient(applicationContext)
    private val discoveredDevices = linkedMapOf<String, BluetoothDevice>()
    private val discoveredRssi = linkedMapOf<String, Int>()
    private val discoveredNames = linkedMapOf<String, String>()
    private val discoveredServiceHints = linkedMapOf<String, Boolean>()
    private val chunkPipeline = ChunkToBlockPipeline()

    private val _uiState = MutableStateFlow(
        ConnectionUiState(
            bluetoothAvailable = bleClient.isBluetoothAvailable(),
            locationEnabled = isLocationEnabled(),
        )
    )
    val uiState: StateFlow<ConnectionUiState> = _uiState.asStateFlow()

    init {
        bleClient.setListener(
            object : BleSmokeTestClient.Listener {
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
                    val parsedChunk = runCatching { BleChunkParser.parse(payload) }.getOrNull()
                    if (parsedChunk != null) {
                        val block = runCatching {
                            chunkPipeline.processNotification(payload, System.currentTimeMillis())
                        }.getOrNull()

                        _uiState.update { current ->
                            current.copy(
                                notificationsReceived = current.notificationsReceived + 1,
                                chunkNotificationsReceived = current.chunkNotificationsReceived + 1,
                                transportMode = "Chunk v1",
                                lastChunkPacketId = parsedChunk.packetId,
                                lastChunkIndex = parsedChunk.chunkIndex,
                                lastChunkCount = parsedChunk.chunkCount,
                                lastPayloadHex = payload.toHexString(),
                            )
                        }

                        if (block != null) {
                            updateCompletedBlock(block)
                        }
                        return
                    }

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
            }
        )
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
            )
        }
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

    private fun updateCompletedBlock(block: ImuLogicalBlock) {
        _uiState.update { current ->
            current.copy(
                blocksReassembled = current.blocksReassembled + 1,
                lastBlockPacketId = block.packetId,
                lastBlockSampleCount = block.sampleCount,
                lastBlockBattery = block.batteryLevelPercent,
                statusText = "Bloque reensamblado packet=${block.packetId} samples=${block.sampleCount}",
            )
        }
    }

    private fun isLocationEnabled(): Boolean {
        val locationManager = appContext.getSystemService(LocationManager::class.java)
        return locationManager?.isLocationEnabled == true
    }

    companion object {
        fun provideFactory(applicationContext: Context): ViewModelProvider.Factory =
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T {
                    return ConnectionViewModel(applicationContext) as T
                }
            }
    }
}
