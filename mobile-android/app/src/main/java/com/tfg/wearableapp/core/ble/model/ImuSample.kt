package com.tfg.wearableapp.core.ble.model

data class ImuSample(
    val ax: Short,
    val ay: Short,
    val az: Short,
    val gx: Short,
    val gy: Short,
    val gz: Short,
)
