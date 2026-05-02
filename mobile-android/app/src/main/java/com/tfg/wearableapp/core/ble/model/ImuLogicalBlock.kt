package com.tfg.wearableapp.core.ble.model

data class ImuLogicalBlock(
    val protocolVersion: Int,
    val blockType: Int,
    val flags: Int,
    val packetId: Long,
    val timestampBlockStartMs: Long,
    val sampleStartIndex: Long,
    val sampleCount: Int,
    val stepCountTotal: Long? = null,
    val batteryLevelPercent: Int? = null,
    val reserved: Int,
    val statusFlags: Int? = null,
    val samples: List<ImuSample>,
)
