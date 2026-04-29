package com.tfg.wearableapp.core.ble

object BleTransportConfig {
    const val protocolVersion: Int = 1

    const val imuAxesPerSample: Int = 6
    const val bytesPerAxis: Int = 2
    const val bytesPerImuSample: Int = imuAxesPerSample * bytesPerAxis

    const val logicalBlockHeaderSizeBytes: Int = 26
    const val chunkHeaderSizeBytes: Int = 9

    const val targetSampleRateHz: Int = 104
    const val targetSamplesPerBlock: Int = 52
    const val targetChunkPayloadSizeBytes: Int = 180

    const val blockReassemblyTimeoutMs: Long = 3_000L
}
