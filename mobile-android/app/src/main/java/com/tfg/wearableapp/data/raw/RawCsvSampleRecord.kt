package com.tfg.wearableapp.data.raw

data class RawCsvSampleRecord(
    val sessionId: String,
    val packetId: Long,
    val sampleTimestampMs: Long,
    val sampleGlobalIndex: Long,
    val sampleIndexInBlock: Int,
    val stepCountTotal: Long,
    val batteryLevelPercent: Int,
    val statusFlags: Int,
    val ax: Int,
    val ay: Int,
    val az: Int,
    val gx: Int,
    val gy: Int,
    val gz: Int,
)
