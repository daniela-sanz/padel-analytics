package com.tfg.wearableapp.core.ble.model

data class BleChunk(
    val protocolVersion: Int,
    val packetId: Long,
    val chunkIndex: Int,
    val chunkCount: Int,
    val payloadSize: Int,
    val payload: ByteArray,
)
