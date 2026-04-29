package com.tfg.wearableapp.core.ble.parser

import com.tfg.wearableapp.core.ble.BleTransportConfig
import com.tfg.wearableapp.core.ble.model.BleChunk
import java.nio.ByteBuffer
import java.nio.ByteOrder

object BleChunkParser {
    fun parse(raw: ByteArray): BleChunk {
        require(raw.size >= BleTransportConfig.chunkHeaderSizeBytes) {
            "Chunk demasiado pequeno: ${raw.size} bytes"
        }

        val buffer = ByteBuffer.wrap(raw).order(ByteOrder.LITTLE_ENDIAN)

        val protocolVersion = buffer.get().toUByte().toInt()
        val packetId = buffer.int.toUInt().toLong()
        val chunkIndex = buffer.get().toUByte().toInt()
        val chunkCount = buffer.get().toUByte().toInt()
        val payloadSize = buffer.short.toUShort().toInt()

        require(chunkCount > 0) { "chunk_count debe ser > 0" }
        require(chunkIndex < chunkCount) { "chunk_index fuera de rango" }
        require(payloadSize >= 0) { "payload_size invalido" }
        require(raw.size == BleTransportConfig.chunkHeaderSizeBytes + payloadSize) {
            "Tamano de chunk inconsistente: raw=${raw.size}, payload=$payloadSize"
        }

        val payload = ByteArray(payloadSize)
        buffer.get(payload)

        return BleChunk(
            protocolVersion = protocolVersion,
            packetId = packetId,
            chunkIndex = chunkIndex,
            chunkCount = chunkCount,
            payloadSize = payloadSize,
            payload = payload,
        )
    }
}
