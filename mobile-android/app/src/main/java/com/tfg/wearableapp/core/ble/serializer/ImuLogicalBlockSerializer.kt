package com.tfg.wearableapp.core.ble.serializer

import com.tfg.wearableapp.core.ble.BleTransportConfig
import com.tfg.wearableapp.core.ble.model.ImuLogicalBlock
import java.nio.ByteBuffer
import java.nio.ByteOrder

object ImuLogicalBlockSerializer {
    fun serialize(block: ImuLogicalBlock): ByteArray {
        val expectedCapacity = BleTransportConfig.logicalBlockHeaderSizeBytes +
            block.samples.size * BleTransportConfig.bytesPerImuSample

        return ByteBuffer
            .allocate(expectedCapacity)
            .order(ByteOrder.LITTLE_ENDIAN)
            .apply {
                put(block.protocolVersion.toByte())
                put(block.blockType.toByte())
                putShort(block.flags.toShort())
                putInt(block.packetId.toInt())
                putInt(block.timestampBlockStartMs.toInt())
                putInt(block.sampleStartIndex.toInt())
                putShort(block.sampleCount.toShort())
                putInt((block.stepCountTotal ?: 0L).toInt())
                put((block.batteryLevelPercent ?: 0).toByte())
                put(block.reserved.toByte())
                putShort((block.statusFlags ?: 0).toShort())

                block.samples.forEach { sample ->
                    putShort(sample.ax)
                    putShort(sample.ay)
                    putShort(sample.az)
                    putShort(sample.gx)
                    putShort(sample.gy)
                    putShort(sample.gz)
                }
            }
            .array()
    }
}
