package com.tfg.wearableapp.core.ble.model

sealed interface BleTransportMessage {
    data class LegacyChunkMessage(
        val chunk: BleChunk,
    ) : BleTransportMessage

    data class FirstChunkMessage(
        val packetId: Long,
        val sampleStartIndex: Long,
        val sampleCount: Int,
        val timestampBlockStartMs: Long,
        val payload: ByteArray,
    ) : BleTransportMessage

    data class ContinuationChunkMessage(
        val packetId: Long,
        val chunkSequence: Int,
        val payload: ByteArray,
    ) : BleTransportMessage

    data class TelemetryMessage(
        val snapshot: TelemetrySnapshot,
        val telemetryFlags: Int,
    ) : BleTransportMessage
}
