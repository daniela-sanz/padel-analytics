package com.tfg.wearableapp.feature.connection

data class ConnectionUiState(
    val bluetoothAvailable: Boolean = true,
    val locationEnabled: Boolean = false,
    val locationPermissionGranted: Boolean = false,
    val isScanning: Boolean = false,
    val isConnected: Boolean = false,
    val connectedDeviceName: String? = null,
    val connectedDeviceAddress: String? = null,
    val statusText: String = "Lista para iniciar escaneo BLE real.",
    val discoveredDevices: List<DiscoveredBleDeviceUi> = emptyList(),
    val scanCallbacksSeen: Int = 0,
    val negotiatedMtu: Int? = null,
    val notificationsReceived: Int = 0,
    val lastCounter: Long? = null,
    val lastBattery: Int? = null,
    val lastFlagHex: String? = null,
    val lastPayloadHex: String = "-",
    val transportMode: String = "Smoke payload",
    val chunkNotificationsReceived: Int = 0,
    val lastChunkPacketId: Long? = null,
    val lastChunkIndex: Int? = null,
    val lastChunkCount: Int? = null,
    val blocksReassembled: Int = 0,
    val lastBlockPacketId: Long? = null,
    val lastBlockSampleCount: Int? = null,
    val lastBlockBattery: Int? = null,
)

data class DiscoveredBleDeviceUi(
    val name: String,
    val address: String,
    val rssi: Int,
    val looksLikeSmokeTest: Boolean,
    val advertisedName: String?,
    val systemName: String?,
)
