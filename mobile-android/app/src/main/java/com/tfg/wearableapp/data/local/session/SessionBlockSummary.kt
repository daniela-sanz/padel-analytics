package com.tfg.wearableapp.data.local.session

data class SessionBlockSummary(
    val packetId: Long,
    val timestampBlockStartMs: Long,
    val sampleStartIndex: Long,
    val sampleCount: Int,
    val batteryLevelPercent: Int?,
)
