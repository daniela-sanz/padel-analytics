package com.tfg.wearableapp.core.ble.model

data class ChunkTransportStats(
    val notificationsSeen: Int = 0,
    val blocksCompleted: Int = 0,
    val lastPacketId: Long? = null,
    val lastSampleCount: Int = 0,
)
