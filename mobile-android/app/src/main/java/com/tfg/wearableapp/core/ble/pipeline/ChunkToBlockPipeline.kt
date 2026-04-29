package com.tfg.wearableapp.core.ble.pipeline

import com.tfg.wearableapp.core.ble.model.ImuLogicalBlock
import com.tfg.wearableapp.core.ble.model.ReassemblyStatus
import com.tfg.wearableapp.core.ble.parser.BleChunkParser
import com.tfg.wearableapp.core.ble.parser.ImuLogicalBlockParser
import com.tfg.wearableapp.core.ble.reassembly.BleBlockReassembler

class ChunkToBlockPipeline(
    private val reassembler: BleBlockReassembler = BleBlockReassembler(),
) {
    fun processNotification(
        rawChunk: ByteArray,
        nowMs: Long,
    ): ImuLogicalBlock? {
        val chunk = BleChunkParser.parse(rawChunk)
        return when (val result = reassembler.accept(chunk, nowMs)) {
            is ReassemblyStatus.Completed -> ImuLogicalBlockParser.parse(result.bytes)
            is ReassemblyStatus.WaitingMoreChunks -> null
            is ReassemblyStatus.DroppedExpiredPackets -> null
        }
    }

    fun flushExpired(nowMs: Long): ReassemblyStatus {
        return reassembler.flushExpired(nowMs)
    }
}
