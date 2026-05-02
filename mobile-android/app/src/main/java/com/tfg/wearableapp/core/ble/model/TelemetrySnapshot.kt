package com.tfg.wearableapp.core.ble.model

data class TelemetrySnapshot(
    val batteryLevelPercent: Int? = null,
    val stepCountTotal: Long? = null,
    val statusFlags: Int? = null,
)
