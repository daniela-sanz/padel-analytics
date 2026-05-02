package com.tfg.wearableapp.core.ble.pipeline

import com.tfg.wearableapp.core.ble.model.BleTransportMessage
import com.tfg.wearableapp.core.ble.parser.BleChunkParser
import com.tfg.wearableapp.core.ble.parser.ImuLogicalBlockParser
import com.tfg.wearableapp.core.ble.reassembly.BleBlockReassembler
import com.tfg.wearableapp.core.ble.reassembly.Mtu23BlockReassembler

class ChunkToBlockPipeline(
    private val legacyReassembler: BleBlockReassembler = BleBlockReassembler(),
    private val mtu23Reassembler: Mtu23BlockReassembler = Mtu23BlockReassembler(),
) {
    fun processNotification(
        rawChunk: ByteArray,
        nowMs: Long,
    ): BlePipelineEvent {
        val message = BleChunkParser.parse(rawChunk)
        return when (message) {
            is BleTransportMessage.LegacyChunkMessage -> {
                when (val result = legacyReassembler.accept(message.chunk, nowMs)) {
                    is com.tfg.wearableapp.core.ble.model.ReassemblyStatus.Completed -> {
                        BlePipelineEvent.BlockCompleted(
                            block = ImuLogicalBlockParser.parse(result.bytes),
                            transportKind = "legacy-v1",
                        )
                    }
                    is com.tfg.wearableapp.core.ble.model.ReassemblyStatus.DroppedExpiredPackets -> {
                        BlePipelineEvent.ExpiredPacketsDropped(result.expiredPacketIds)
                    }
                    is com.tfg.wearableapp.core.ble.model.ReassemblyStatus.WaitingMoreChunks -> {
                        BlePipelineEvent.ChunkObserved(message)
                    }
                }
            }
            is BleTransportMessage.FirstChunkMessage,
            is BleTransportMessage.ContinuationChunkMessage,
            -> {
                val block = mtu23Reassembler.accept(message, nowMs)
                if (block != null) {
                    BlePipelineEvent.BlockCompleted(
                        block = block,
                        transportKind = "chunk-v2-mtu23",
                    )
                } else {
                    BlePipelineEvent.ChunkObserved(message)
                }
            }
            is BleTransportMessage.TelemetryMessage -> {
                BlePipelineEvent.TelemetryUpdated(message.snapshot)
            }
        }
    }

    fun flushExpired(nowMs: Long): BlePipelineEvent {
        val dropped = legacyReassembler.flushExpired(nowMs)
        if (dropped is com.tfg.wearableapp.core.ble.model.ReassemblyStatus.DroppedExpiredPackets) {
            return BlePipelineEvent.ExpiredPacketsDropped(dropped.expiredPacketIds)
        }

        val mtu23Dropped = mtu23Reassembler.flushExpired(nowMs)
        return if (mtu23Dropped.isNotEmpty()) {
            BlePipelineEvent.ExpiredPacketsDropped(mtu23Dropped)
        } else {
            BlePipelineEvent.WaitingMoreData
        }
    }
}
