package com.tfg.wearableapp.core.ble.serializer

import com.tfg.wearableapp.core.ble.BleTransportConfig
import java.nio.ByteBuffer
import java.nio.ByteOrder

object BleChunkSerializer {
    fun splitBlockIntoRawChunks(
        packetId: Long,
        blockBytes: ByteArray,
        chunkPayloadSizeBytes: Int = BleTransportConfig.targetChunkPayloadSizeBytes,
        protocolVersion: Int = BleTransportConfig.protocolVersion,
    ): List<ByteArray> {
        require(chunkPayloadSizeBytes > 0) { "chunkPayloadSizeBytes debe ser > 0" }

        val chunkCount = (blockBytes.size + chunkPayloadSizeBytes - 1) / chunkPayloadSizeBytes

        return List(chunkCount) { chunkIndex ->
            val start = chunkIndex * chunkPayloadSizeBytes
            val endExclusive = minOf(start + chunkPayloadSizeBytes, blockBytes.size)
            val payload = blockBytes.copyOfRange(start, endExclusive)

            ByteBuffer
                .allocate(BleTransportConfig.chunkHeaderSizeBytes + payload.size)
                .order(ByteOrder.LITTLE_ENDIAN)
                .apply {
                    put(protocolVersion.toByte())
                    putInt(packetId.toInt())
                    put(chunkIndex.toByte())
                    put(chunkCount.toByte())
                    putShort(payload.size.toShort())
                    put(payload)
                }
                .array()
        }
    }
}
