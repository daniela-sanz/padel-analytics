package com.tfg.wearableapp.core.ble.parser

import com.tfg.wearableapp.core.ble.BleTransportConfig
import com.tfg.wearableapp.core.ble.model.ImuLogicalBlock
import com.tfg.wearableapp.core.ble.model.ImuSample
import java.nio.ByteBuffer
import java.nio.ByteOrder

object ImuLogicalBlockParser {
    fun parse(bytes: ByteArray): ImuLogicalBlock {
        require(bytes.size >= BleTransportConfig.logicalBlockHeaderSizeBytes) {
            "Bloque demasiado pequeno: ${bytes.size} bytes"
        }

        val buffer = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)

        val protocolVersion = buffer.get().toUByte().toInt()
        val blockType = buffer.get().toUByte().toInt()
        val flags = buffer.short.toUShort().toInt()
        val packetId = buffer.int.toUInt().toLong()
        val timestampBlockStartMs = buffer.int.toUInt().toLong()
        val sampleStartIndex = buffer.int.toUInt().toLong()
        val sampleCount = buffer.short.toUShort().toInt()
        val stepCountTotal = buffer.int.toUInt().toLong()
        val batteryLevelPercent = buffer.get().toUByte().toInt()
        val reserved = buffer.get().toUByte().toInt()
        val statusFlags = buffer.short.toUShort().toInt()

        val expectedSampleBytes = sampleCount * BleTransportConfig.bytesPerImuSample
        val expectedTotalBytes = BleTransportConfig.logicalBlockHeaderSizeBytes + expectedSampleBytes

        require(bytes.size == expectedTotalBytes) {
            "Tamano de bloque inconsistente: bytes=${bytes.size}, esperado=$expectedTotalBytes"
        }

        val samples = buildList(sampleCount) {
            repeat(sampleCount) {
                add(
                    ImuSample(
                        ax = buffer.short,
                        ay = buffer.short,
                        az = buffer.short,
                        gx = buffer.short,
                        gy = buffer.short,
                        gz = buffer.short,
                    )
                )
            }
        }

        return ImuLogicalBlock(
            protocolVersion = protocolVersion,
            blockType = blockType,
            flags = flags,
            packetId = packetId,
            timestampBlockStartMs = timestampBlockStartMs,
            sampleStartIndex = sampleStartIndex,
            sampleCount = sampleCount,
            stepCountTotal = stepCountTotal,
            batteryLevelPercent = batteryLevelPercent,
            reserved = reserved,
            statusFlags = statusFlags,
            samples = samples,
        )
    }
}
