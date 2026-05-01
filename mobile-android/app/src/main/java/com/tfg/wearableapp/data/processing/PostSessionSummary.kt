package com.tfg.wearableapp.data.processing

data class PostSessionSummary(
    val sampleCount: Int,
    val packetCount: Int,
    val durationMs: Long,
    val peakAccelMagnitudeRaw: Int,
    val peakGyroMagnitudeRaw: Int,
    val meanAccelMagnitudeRaw: Int,
    val meanGyroMagnitudeRaw: Int,
    val candidateImpactCount: Int,
    val batteryAtStartPercent: Int?,
    val batteryAtEndPercent: Int?,
)
