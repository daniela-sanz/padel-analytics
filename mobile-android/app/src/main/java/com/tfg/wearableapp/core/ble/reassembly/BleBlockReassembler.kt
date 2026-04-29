package com.tfg.wearableapp.core.ble.reassembly

import com.tfg.wearableapp.core.ble.BleTransportConfig
import com.tfg.wearableapp.core.ble.model.BleChunk
import com.tfg.wearableapp.core.ble.model.ReassemblyStatus

class BleBlockReassembler(
    private val timeoutMs: Long = BleTransportConfig.blockReassemblyTimeoutMs,
) {
    private data class PartialBlock(
        val createdAtMs: Long,
        val chunkCount: Int,
        val payloads: Array<ByteArray?>,
    ) {
        fun isComplete(): Boolean = payloads.all { it != null }

        fun join(): ByteArray {
            val totalSize = payloads.sumOf { it?.size ?: 0 }
            val joined = ByteArray(totalSize)
            var offset = 0
            payloads.forEach { payload ->
                requireNotNull(payload) { "No se puede unir un bloque incompleto" }
                payload.copyInto(joined, destinationOffset = offset)
                offset += payload.size
            }
            return joined
        }
    }

    private val partialBlocks = linkedMapOf<Long, PartialBlock>()

    fun accept(
        chunk: BleChunk,
        nowMs: Long,
    ): ReassemblyStatus {
        dropExpired(nowMs)

        val existing = partialBlocks[chunk.packetId]
        val block = if (existing == null) {
            PartialBlock(
                createdAtMs = nowMs,
                chunkCount = chunk.chunkCount,
                payloads = arrayOfNulls(chunk.chunkCount),
            )
        } else {
            require(existing.chunkCount == chunk.chunkCount) {
                "chunk_count inconsistente para packet_id=${chunk.packetId}"
            }
            existing
        }

        block.payloads[chunk.chunkIndex] = chunk.payload
        partialBlocks[chunk.packetId] = block

        if (!block.isComplete()) {
            return ReassemblyStatus.WaitingMoreChunks
        }

        partialBlocks.remove(chunk.packetId)

        return ReassemblyStatus.Completed(
            packetId = chunk.packetId,
            bytes = block.join(),
        )
    }

    fun flushExpired(nowMs: Long): ReassemblyStatus {
        val expiredPacketIds = dropExpired(nowMs)
        return if (expiredPacketIds.isEmpty()) {
            ReassemblyStatus.WaitingMoreChunks
        } else {
            ReassemblyStatus.DroppedExpiredPackets(expiredPacketIds)
        }
    }

    private fun dropExpired(nowMs: Long): List<Long> {
        val expiredPacketIds = partialBlocks
            .filterValues { partial -> nowMs - partial.createdAtMs > timeoutMs }
            .keys
            .toList()

        expiredPacketIds.forEach { partialBlocks.remove(it) }
        return expiredPacketIds
    }
}
