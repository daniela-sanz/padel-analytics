package com.tfg.wearableapp.data.processing

import com.tfg.wearableapp.core.ble.BleTransportConfig
import com.tfg.wearableapp.core.ble.model.ImuLogicalBlock
import com.tfg.wearableapp.core.ble.model.TelemetrySnapshot

class LiveSessionAccumulator(
    private val impactDeltaThresholdRaw: Double = 3000.0,
    private val impactGyroValidationThresholdRaw: Double = 5500.0,
    private val impactRefractoryMs: Long = 120L,
    private val playerSex: String = "No definido",
) {
    private val summaryBuilder = ImuSessionSummaryBuilder(
        impactDeltaThresholdRaw = impactDeltaThresholdRaw,
        impactGyroValidationThresholdRaw = impactGyroValidationThresholdRaw,
        impactRefractoryMs = impactRefractoryMs,
        playerSex = playerSex,
    )

    fun addBlock(
        block: ImuLogicalBlock,
        telemetry: TelemetrySnapshot? = null,
    ) {
        val batteryLevel = telemetry?.batteryLevelPercent ?: block.batteryLevelPercent

        block.samples.forEachIndexed { index, sample ->
            val sampleTimestampMs =
                block.timestampBlockStartMs +
                    ((index * 1000.0) / BleTransportConfig.targetSampleRateHz.toDouble()).toLong()
            summaryBuilder.addSample(
                packetId = block.packetId,
                sampleTimestampMs = sampleTimestampMs,
                batteryPercent = batteryLevel,
                stepCountTotal = telemetry?.stepCountTotal ?: block.stepCountTotal,
                ax = sample.ax.toInt(),
                ay = sample.ay.toInt(),
                az = sample.az.toInt(),
                gx = sample.gx.toInt(),
                gy = sample.gy.toInt(),
                gz = sample.gz.toInt(),
            )
        }
    }

    fun snapshot(): PostSessionSummary? {
        return summaryBuilder.build()
    }
}
