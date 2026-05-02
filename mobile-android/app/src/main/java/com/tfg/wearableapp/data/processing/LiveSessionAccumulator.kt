package com.tfg.wearableapp.data.processing

import com.tfg.wearableapp.core.ble.BleTransportConfig
import com.tfg.wearableapp.core.ble.model.ImuLogicalBlock
import com.tfg.wearableapp.core.ble.model.TelemetrySnapshot
import kotlin.math.sqrt

class LiveSessionAccumulator(
    private val impactAccelThresholdRaw: Double = 1700.0,
    private val impactGyroThresholdRaw: Double = 1500.0,
    private val impactRefractoryMs: Long = 120L,
) {
    private var sampleCount = 0
    private val packetIds = linkedSetOf<Long>()
    private var firstTimestampMs: Long? = null
    private var lastTimestampMs: Long? = null
    private var firstBatteryPercent: Int? = null
    private var lastBatteryPercent: Int? = null
    private var peakAccelMagnitude = 0.0
    private var peakGyroMagnitude = 0.0
    private var accelMagnitudeSum = 0.0
    private var gyroMagnitudeSum = 0.0
    private var candidateImpactCount = 0
    private var lastImpactTimestampMs = Long.MIN_VALUE

    fun addBlock(
        block: ImuLogicalBlock,
        telemetry: TelemetrySnapshot? = null,
    ) {
        packetIds += block.packetId
        firstTimestampMs = firstTimestampMs ?: block.timestampBlockStartMs
        val batteryLevel = telemetry?.batteryLevelPercent ?: block.batteryLevelPercent
        firstBatteryPercent = firstBatteryPercent ?: batteryLevel
        lastBatteryPercent = batteryLevel

        block.samples.forEachIndexed { index, sample ->
            val sampleTimestampMs =
                block.timestampBlockStartMs +
                    ((index * 1000.0) / BleTransportConfig.targetSampleRateHz.toDouble()).toLong()
            val accelMagnitude = computeMagnitude(sample.ax.toInt(), sample.ay.toInt(), sample.az.toInt())
            val gyroMagnitude = computeMagnitude(sample.gx.toInt(), sample.gy.toInt(), sample.gz.toInt())

            sampleCount += 1
            lastTimestampMs = sampleTimestampMs
            peakAccelMagnitude = maxOf(peakAccelMagnitude, accelMagnitude)
            peakGyroMagnitude = maxOf(peakGyroMagnitude, gyroMagnitude)
            accelMagnitudeSum += accelMagnitude
            gyroMagnitudeSum += gyroMagnitude

            val exceedsImpactThreshold =
                accelMagnitude >= impactAccelThresholdRaw ||
                    gyroMagnitude >= impactGyroThresholdRaw
            val outsideRefractoryWindow =
                sampleTimestampMs - lastImpactTimestampMs >= impactRefractoryMs

            if (exceedsImpactThreshold && outsideRefractoryWindow) {
                candidateImpactCount += 1
                lastImpactTimestampMs = sampleTimestampMs
            }
        }
    }

    fun snapshot(): PostSessionSummary? {
        if (sampleCount == 0) {
            return null
        }

        return PostSessionSummary(
            sampleCount = sampleCount,
            packetCount = packetIds.size,
            durationMs = ((lastTimestampMs ?: 0L) - (firstTimestampMs ?: 0L)).coerceAtLeast(0L),
            peakAccelMagnitudeRaw = peakAccelMagnitude.toInt(),
            peakGyroMagnitudeRaw = peakGyroMagnitude.toInt(),
            meanAccelMagnitudeRaw = (accelMagnitudeSum / sampleCount).toInt(),
            meanGyroMagnitudeRaw = (gyroMagnitudeSum / sampleCount).toInt(),
            candidateImpactCount = candidateImpactCount,
            batteryAtStartPercent = firstBatteryPercent,
            batteryAtEndPercent = lastBatteryPercent,
        )
    }

    private fun computeMagnitude(
        x: Int,
        y: Int,
        z: Int,
    ): Double {
        return sqrt(
            (x.toDouble() * x.toDouble()) +
                (y.toDouble() * y.toDouble()) +
                (z.toDouble() * z.toDouble())
        )
    }
}
