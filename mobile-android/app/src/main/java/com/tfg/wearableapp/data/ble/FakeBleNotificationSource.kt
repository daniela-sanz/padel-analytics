package com.tfg.wearableapp.data.ble

import com.tfg.wearableapp.core.ble.BleTransportConfig
import com.tfg.wearableapp.core.ble.model.ImuLogicalBlock
import com.tfg.wearableapp.core.ble.model.ImuSample
import com.tfg.wearableapp.core.ble.serializer.BleChunkSerializer
import com.tfg.wearableapp.core.ble.serializer.ImuLogicalBlockSerializer
import kotlin.math.sin
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

class FakeBleNotificationSource {
    fun streamRawNotifications(
        chunkPayloadSizeBytes: Int = BleTransportConfig.targetChunkPayloadSizeBytes,
    ): Flow<ByteArray> = flow {
        var packetId = 1L
        var sampleStartIndex = 0L
        var elapsedMs = 0L
        var stepCount = 100L

        while (true) {
            val block = generateBlock(
                packetId = packetId,
                timestampBlockStartMs = elapsedMs,
                sampleStartIndex = sampleStartIndex,
                stepCountTotal = stepCount,
            )

            val blockBytes = ImuLogicalBlockSerializer.serialize(block)
            val rawChunks = BleChunkSerializer.splitBlockIntoRawChunks(
                packetId = block.packetId,
                blockBytes = blockBytes,
                chunkPayloadSizeBytes = chunkPayloadSizeBytes,
            )

            rawChunks.forEach { chunk ->
                emit(chunk)
                delay(24L)
            }

            packetId += 1
            sampleStartIndex += block.sampleCount
            elapsedMs += 500L
            stepCount += 1
            delay(220L)
        }
    }

    private fun generateBlock(
        packetId: Long,
        timestampBlockStartMs: Long,
        sampleStartIndex: Long,
        stepCountTotal: Long,
    ): ImuLogicalBlock {
        val samples = List(BleTransportConfig.targetSamplesPerBlock) { index ->
            val t = (sampleStartIndex + index) / BleTransportConfig.targetSampleRateHz.toDouble()
            val wave = sin(t * 7.5)
            val rotation = sin(t * 11.0)

            ImuSample(
                ax = (wave * 1200).toInt().toShort(),
                ay = (wave * 850).toInt().toShort(),
                az = (1024 + wave * 300).toInt().toShort(),
                gx = (rotation * 900).toInt().toShort(),
                gy = (rotation * 650).toInt().toShort(),
                gz = (rotation * 1300).toInt().toShort(),
            )
        }

        return ImuLogicalBlock(
            protocolVersion = BleTransportConfig.protocolVersion,
            blockType = 1,
            flags = 0b1110,
            packetId = packetId,
            timestampBlockStartMs = timestampBlockStartMs,
            sampleStartIndex = sampleStartIndex,
            sampleCount = samples.size,
            stepCountTotal = stepCountTotal,
            batteryLevelPercent = 87,
            reserved = 0,
            statusFlags = 0,
            samples = samples,
        )
    }
}
