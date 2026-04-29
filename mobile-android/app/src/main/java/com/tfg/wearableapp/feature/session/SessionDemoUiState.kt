package com.tfg.wearableapp.feature.session

data class SessionDemoUiState(
    val isStreaming: Boolean = false,
    val modeLabel: String = "Simulado",
    val negotiatedMtu: Int = 23,
    val computedChunkPayloadBytes: Int = 11,
    val notificationsSeen: Int = 0,
    val blocksCompleted: Int = 0,
    val lastPacketId: Long? = null,
    val lastSampleCount: Int = 0,
    val statusText: String = "Listo para iniciar la demo de transporte BLE.",
)
