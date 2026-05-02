package com.tfg.wearableapp.core.ble.reassembly

import com.tfg.wearableapp.core.ble.BleTransportConfig
import com.tfg.wearableapp.core.ble.model.BleTransportMessage
import com.tfg.wearableapp.core.ble.model.ImuLogicalBlock
import com.tfg.wearableapp.core.ble.model.ImuSample

class Mtu23BlockReassembler(
    private val timeoutMs: Long = BleTransportConfig.blockReassemblyTimeoutMs,
) {
    private data class PartialBlock(
        val createdAtMs: Long,
        val packetId: Long,
        val sampleStartIndex: Long,
        val sampleCount: Int,
        val timestampBlockStartMs: Long,
        val firstPayload: ByteArray,
        val continuationPayloads: MutableMap<Int, ByteArray> = linkedMapOf(),
    ) {
        val expectedSampleBytes: Int = sampleCount * BleTransportConfig.bytesPerImuSample

        fun totalPayloadBytes(): Int {
            return firstPayload.size + continuationPayloads.values.sumOf { it.size }
        }

        fun hasContiguousSequences(): Boolean {
            if (continuationPayloads.isEmpty()) return true
            val ordered = continuationPayloads.keys.sorted()
            return ordered.first() == 1 && ordered.indices.all { idx -> ordered[idx] == idx + 1 }
        }
    }

    private val partialBlocks = linkedMapOf<Long, PartialBlock>()

    fun accept(
        message: BleTransportMessage,
        nowMs: Long,
    ): ImuLogicalBlock? {
        dropExpired(nowMs)

        return when (message) {
            is BleTransportMessage.FirstChunkMessage -> acceptFirstChunk(message, nowMs)
            is BleTransportMessage.ContinuationChunkMessage -> acceptContinuationChunk(message)
            else -> null
        }
    }

    fun flushExpired(nowMs: Long): List<Long> = dropExpired(nowMs)

    private fun acceptFirstChunk(
        message: BleTransportMessage.FirstChunkMessage,
        nowMs: Long,
    ): ImuLogicalBlock? {
        val partial = PartialBlock(
            createdAtMs = nowMs,
            packetId = message.packetId,
            sampleStartIndex = message.sampleStartIndex,
            sampleCount = message.sampleCount,
            timestampBlockStartMs = message.timestampBlockStartMs,
            firstPayload = message.payload,
        )

        partialBlocks[message.packetId] = partial
        return completeIfReady(partial)
    }

    private fun acceptContinuationChunk(
        message: BleTransportMessage.ContinuationChunkMessage,
    ): ImuLogicalBlock? {
        val partial = partialBlocks[message.packetId] ?: return null
        partial.continuationPayloads[message.chunkSequence] = message.payload
        return completeIfReady(partial)
    }

    private fun completeIfReady(partial: PartialBlock): ImuLogicalBlock? {
        if (partial.totalPayloadBytes() < partial.expectedSampleBytes) {
            return null
        }
        if (!partial.hasContiguousSequences()) {
            return null
        }

        val combined = ByteArray(partial.totalPayloadBytes())
        var offset = 0
        partial.firstPayload.copyInto(combined, offset)
        offset += partial.firstPayload.size

        partial.continuationPayloads
            .toSortedMap()
            .values
            .forEach { payload ->
                payload.copyInto(combined, offset)
                offset += payload.size
            }

        require(combined.size >= partial.expectedSampleBytes) {
            "Payload insuficiente para packet_id=${partial.packetId}"
        }

        val sampleBytes = combined.copyOfRange(0, partial.expectedSampleBytes)
        partialBlocks.remove(partial.packetId)

        return ImuLogicalBlock(
            protocolVersion = BleTransportConfig.protocolVersion,
            blockType = 1,
            flags = 0,
            packetId = partial.packetId,
            timestampBlockStartMs = partial.timestampBlockStartMs,
            sampleStartIndex = partial.sampleStartIndex,
            sampleCount = partial.sampleCount,
            reserved = 0,
            samples = parseSamples(sampleBytes, partial.sampleCount),
        )
    }

    private fun parseSamples(
        bytes: ByteArray,
        sampleCount: Int,
    ): List<ImuSample> {
        var offset = 0
        return List(sampleCount) {
            val ax = readShortLE(bytes, offset)
            val ay = readShortLE(bytes, offset + 2)
            val az = readShortLE(bytes, offset + 4)
            val gx = readShortLE(bytes, offset + 6)
            val gy = readShortLE(bytes, offset + 8)
            val gz = readShortLE(bytes, offset + 10)
            offset += BleTransportConfig.bytesPerImuSample
            ImuSample(ax = ax, ay = ay, az = az, gx = gx, gy = gy, gz = gz)
        }
    }

    private fun readShortLE(
        bytes: ByteArray,
        offset: Int,
    ): Short {
        val low = bytes[offset].toInt() and 0xFF
        val high = bytes[offset + 1].toInt() and 0xFF
        return ((high shl 8) or low).toShort()
    }

    private fun dropExpired(nowMs: Long): List<Long> {
        val expired = partialBlocks
            .filterValues { nowMs - it.createdAtMs > timeoutMs }
            .keys
            .toList()

        expired.forEach { partialBlocks.remove(it) }
        return expired
    }
}
