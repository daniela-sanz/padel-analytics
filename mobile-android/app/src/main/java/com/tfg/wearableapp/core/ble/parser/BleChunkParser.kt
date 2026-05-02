package com.tfg.wearableapp.core.ble.parser

import com.tfg.wearableapp.core.ble.BleTransportConfig
import com.tfg.wearableapp.core.ble.model.BleChunk
import com.tfg.wearableapp.core.ble.model.BleTransportMessage
import com.tfg.wearableapp.core.ble.model.TelemetrySnapshot
import java.nio.ByteBuffer
import java.nio.ByteOrder

object BleChunkParser {
    fun parse(raw: ByteArray): BleTransportMessage {
        parseLegacyChunkOrNull(raw)?.let { legacyChunk ->
            return BleTransportMessage.LegacyChunkMessage(legacyChunk)
        }

        require(raw.isNotEmpty()) { "Mensaje BLE vacio" }

        return when (val chunkType = raw[0].toInt() and 0xFF) {
            BleTransportConfig.chunkTypeFirst -> parseFirstChunk(raw)
            BleTransportConfig.chunkTypeContinuation -> parseContinuationChunk(raw)
            BleTransportConfig.chunkTypeTelemetry -> parseTelemetryMessage(raw)
            else -> error("chunk_type desconocido: 0x${chunkType.toString(16).uppercase()}")
        }
    }

    private fun parseLegacyChunkOrNull(raw: ByteArray): BleChunk? {
        if (raw.size < BleTransportConfig.chunkHeaderSizeBytes) {
            return null
        }

        val buffer = ByteBuffer.wrap(raw).order(ByteOrder.LITTLE_ENDIAN)

        val protocolVersion = buffer.get().toUByte().toInt()
        val packetId = buffer.int.toUInt().toLong()
        val chunkIndex = buffer.get().toUByte().toInt()
        val chunkCount = buffer.get().toUByte().toInt()
        val payloadSize = buffer.short.toUShort().toInt()

        if (chunkCount <= 0 || chunkIndex >= chunkCount || payloadSize < 0) {
            return null
        }
        if (raw.size != BleTransportConfig.chunkHeaderSizeBytes + payloadSize) {
            return null
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

    private fun parseFirstChunk(raw: ByteArray): BleTransportMessage.FirstChunkMessage {
        require(raw.size >= BleTransportConfig.mtu23FirstChunkHeaderBytes) {
            "First chunk demasiado pequeno: ${raw.size} bytes"
        }

        val buffer = ByteBuffer.wrap(raw).order(ByteOrder.LITTLE_ENDIAN)
        buffer.get() // chunk_type

        val packetId = buffer.short.toUShort().toLong()
        val sampleStartIndex = buffer.int.toUInt().toLong()
        val sampleCount = buffer.get().toUByte().toInt()
        val timestampBlockStartMs = buffer.int.toUInt().toLong()
        val payload = ByteArray(raw.size - BleTransportConfig.mtu23FirstChunkHeaderBytes)
        buffer.get(payload)

        require(sampleCount > 0) { "sample_count debe ser > 0" }

        return BleTransportMessage.FirstChunkMessage(
            packetId = packetId,
            sampleStartIndex = sampleStartIndex,
            sampleCount = sampleCount,
            timestampBlockStartMs = timestampBlockStartMs,
            payload = payload,
        )
    }

    private fun parseContinuationChunk(raw: ByteArray): BleTransportMessage.ContinuationChunkMessage {
        require(raw.size >= BleTransportConfig.mtu23ContinuationHeaderBytes) {
            "Continuation chunk demasiado pequeno: ${raw.size} bytes"
        }

        val buffer = ByteBuffer.wrap(raw).order(ByteOrder.LITTLE_ENDIAN)
        buffer.get() // chunk_type

        val packetId = buffer.short.toUShort().toLong()
        val chunkSequence = buffer.get().toUByte().toInt()
        val payload = ByteArray(raw.size - BleTransportConfig.mtu23ContinuationHeaderBytes)
        buffer.get(payload)

        return BleTransportMessage.ContinuationChunkMessage(
            packetId = packetId,
            chunkSequence = chunkSequence,
            payload = payload,
        )
    }

    private fun parseTelemetryMessage(raw: ByteArray): BleTransportMessage.TelemetryMessage {
        require(raw.size == BleTransportConfig.telemetryMessageSizeBytes) {
            "Telemetry message debe ocupar ${BleTransportConfig.telemetryMessageSizeBytes} bytes, recibido=${raw.size}"
        }

        val buffer = ByteBuffer.wrap(raw).order(ByteOrder.LITTLE_ENDIAN)
        buffer.get() // chunk_type

        val telemetryFlags = buffer.get().toUByte().toInt()
        val batteryLevel = buffer.get().toUByte().toInt()
        val stepCountTotal = buffer.int.toUInt().toLong()
        val statusFlags = buffer.get().toUByte().toInt()

        val snapshot = TelemetrySnapshot(
            batteryLevelPercent = batteryLevel.takeIf {
                telemetryFlags and BleTransportConfig.telemetryFlagBattery != 0
            },
            stepCountTotal = stepCountTotal.takeIf {
                telemetryFlags and BleTransportConfig.telemetryFlagSteps != 0
            },
            statusFlags = statusFlags.takeIf {
                telemetryFlags and BleTransportConfig.telemetryFlagStatus != 0
            },
        )

        return BleTransportMessage.TelemetryMessage(
            snapshot = snapshot,
            telemetryFlags = telemetryFlags,
        )
    }
}
