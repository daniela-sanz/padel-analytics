package com.tfg.wearableapp.core.ble

object BleTransportConfig {
    const val protocolVersion: Int = 1

    const val imuAxesPerSample: Int = 6
    const val bytesPerAxis: Int = 2
    const val bytesPerImuSample: Int = imuAxesPerSample * bytesPerAxis

    const val logicalBlockHeaderSizeBytes: Int = 26
    const val chunkHeaderSizeBytes: Int = 9
    const val preferredAttMtu: Int = 247
    const val minimumAttMtu: Int = 23
    const val mtu23AttPayloadBytes: Int = 20
    const val preferredAttPayloadBytes: Int = 244
    const val mtu23FirstChunkHeaderBytes: Int = 12
    const val mtu23ContinuationHeaderBytes: Int = 4
    const val telemetryMessageSizeBytes: Int = 8

    const val chunkTypeFirst: Int = 0x01
    const val chunkTypeContinuation: Int = 0x02
    const val chunkTypeTelemetry: Int = 0x03

    const val telemetryFlagBattery: Int = 1 shl 0
    const val telemetryFlagSteps: Int = 1 shl 1
    const val telemetryFlagStatus: Int = 1 shl 2

    const val targetSampleRateHz: Int = 104
    const val targetSamplesPerBlock: Int = 52
    const val targetChunkPayloadSizeBytes: Int = 180

    const val blockReassemblyTimeoutMs: Long = 3_000L
}
