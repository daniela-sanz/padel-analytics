package com.tfg.wearableapp.data.processing

import com.tfg.wearableapp.core.ble.BleTransportConfig
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlin.math.roundToLong
import kotlin.math.sqrt

internal class ImuSessionSummaryBuilder(
    private val impactAccelThresholdRaw: Double = 1700.0,
    private val impactGyroThresholdRaw: Double = 1500.0,
    private val impactRefractoryMs: Long = 120L,
    private val accelerationEventThresholdRaw: Double = 1100.0,
    private val estimatedStrideLengthMeters: Double = 0.70,
) {
    private val packetIds = linkedSetOf<Long>()
    private var sampleCount = 0
    private var firstTimestampMs: Long? = null
    private var lastTimestampMs: Long? = null
    private var firstBatteryPercent: Int? = null
    private var lastBatteryPercent: Int? = null
    private var firstStepCountTotal: Long? = null
    private var lastStepCountTotal: Long? = null
    private var peakAccelMagnitude = 0.0
    private var peakGyroMagnitude = 0.0
    private var accelMagnitudeSum = 0.0
    private var gyroMagnitudeSum = 0.0
    private var candidateImpactCount = 0
    private var accelerationEventCount = 0
    private var lastImpactTimestampMs = Long.MIN_VALUE
    private var lastAccelerationEventTimestampMs = Long.MIN_VALUE
    private var explosiveSampleCount = 0
    private var playerLoadAccumulator = 0.0
    private var previousAccelMagnitude: Double? = null
    private var previousGyroMagnitude: Double? = null
    private val impactGyroPeaks = mutableListOf<Double>()
    private val impactPowerPeaks = mutableListOf<Double>()

    fun addSample(
        packetId: Long,
        sampleTimestampMs: Long,
        batteryPercent: Int?,
        stepCountTotal: Long?,
        ax: Int,
        ay: Int,
        az: Int,
        gx: Int,
        gy: Int,
        gz: Int,
    ) {
        val accelMagnitude = computeMagnitude(ax, ay, az)
        val gyroMagnitude = computeMagnitude(gx, gy, gz)

        packetIds += packetId
        sampleCount += 1
        firstTimestampMs = firstTimestampMs ?: sampleTimestampMs
        lastTimestampMs = sampleTimestampMs
        firstBatteryPercent = firstBatteryPercent ?: batteryPercent
        lastBatteryPercent = batteryPercent ?: lastBatteryPercent
        firstStepCountTotal = firstStepCountTotal ?: stepCountTotal
        lastStepCountTotal = stepCountTotal ?: lastStepCountTotal
        peakAccelMagnitude = maxOf(peakAccelMagnitude, accelMagnitude)
        peakGyroMagnitude = maxOf(peakGyroMagnitude, gyroMagnitude)
        accelMagnitudeSum += accelMagnitude
        gyroMagnitudeSum += gyroMagnitude

        val previousAccel = previousAccelMagnitude
        val previousGyro = previousGyroMagnitude
        if (previousAccel != null && previousGyro != null) {
            playerLoadAccumulator += abs(accelMagnitude - previousAccel)
            playerLoadAccumulator += 0.35 * abs(gyroMagnitude - previousGyro)
        }
        previousAccelMagnitude = accelMagnitude
        previousGyroMagnitude = gyroMagnitude

        val isExplosive =
            accelMagnitude >= impactAccelThresholdRaw * 0.8 ||
                gyroMagnitude >= impactGyroThresholdRaw * 0.8
        if (isExplosive) {
            explosiveSampleCount += 1
        }

        if (
            accelMagnitude >= accelerationEventThresholdRaw &&
            sampleTimestampMs - lastAccelerationEventTimestampMs >= 180L
        ) {
            accelerationEventCount += 1
            lastAccelerationEventTimestampMs = sampleTimestampMs
        }

        val exceedsImpactThreshold =
            accelMagnitude >= impactAccelThresholdRaw ||
                gyroMagnitude >= impactGyroThresholdRaw
        val outsideRefractoryWindow =
            sampleTimestampMs - lastImpactTimestampMs >= impactRefractoryMs

        if (exceedsImpactThreshold && outsideRefractoryWindow) {
            candidateImpactCount += 1
            lastImpactTimestampMs = sampleTimestampMs
            impactGyroPeaks += gyroMagnitude
            impactPowerPeaks += (accelMagnitude * gyroMagnitude) / 1000.0
        }
    }

    fun build(): PostSessionSummary? {
        if (sampleCount == 0) {
            return null
        }

        val rawDurationMs = ((lastTimestampMs ?: 0L) - (firstTimestampMs ?: 0L)).coerceAtLeast(0L)
        val estimatedDurationMs =
            if (rawDurationMs > 0L) {
                rawDurationMs
            } else {
                (((sampleCount - 1).coerceAtLeast(1) * 1000.0) / BleTransportConfig.targetSampleRateHz)
                    .roundToLong()
            }
        val durationMinutes = (estimatedDurationMs / 60000.0).coerceAtLeast(1.0 / 60.0)
        val playerLoadScore = (playerLoadAccumulator / 100.0).roundToInt()
        val impactsPerMinute = candidateImpactCount / durationMinutes
        val playerLoadPerMinute = (playerLoadScore / durationMinutes).roundToInt()
        val explosiveExposurePercent = ((explosiveSampleCount * 100.0) / sampleCount).roundToInt()
        val rallyIntensityIndexProxy = ((impactsPerMinute * (accelMagnitudeSum / sampleCount)) / 1000.0).roundToInt()
        val swingP95 = percentile(impactGyroPeaks, 0.95)?.roundToInt()
        val powerP95 = percentile(impactPowerPeaks, 0.95)?.roundToInt()
        val estimatedDistance =
            firstStepCountTotal
                ?.let { start -> lastStepCountTotal?.minus(start) }
                ?.coerceAtLeast(0L)
                ?.let { steps -> (steps * estimatedStrideLengthMeters).roundToInt() }
        val consistency =
            impactPowerPeaks
                .takeIf { it.size >= 3 }
                ?.let { values ->
                    val mean = values.average()
                    if (mean <= 0.0) {
                        0
                    } else {
                        val variance = values.sumOf { value -> (value - mean) * (value - mean) } / values.size
                        val coefficientOfVariation = sqrt(variance) / mean
                        (100.0 - (coefficientOfVariation * 100.0)).roundToInt().coerceIn(0, 100)
                    }
                }

        return PostSessionSummary(
            sampleCount = sampleCount,
            packetCount = packetIds.size,
            durationMs = estimatedDurationMs,
            peakAccelMagnitudeRaw = peakAccelMagnitude.toInt(),
            peakGyroMagnitudeRaw = peakGyroMagnitude.toInt(),
            meanAccelMagnitudeRaw = (accelMagnitudeSum / sampleCount).toInt(),
            meanGyroMagnitudeRaw = (gyroMagnitudeSum / sampleCount).toInt(),
            candidateImpactCount = candidateImpactCount,
            accelerationEventCount = accelerationEventCount,
            impactsPerMinute = impactsPerMinute,
            playerLoadScore = playerLoadScore,
            playerLoadPerMinute = playerLoadPerMinute,
            explosiveExposurePercent = explosiveExposurePercent,
            rallyIntensityIndexProxy = rallyIntensityIndexProxy,
            swingSpeedProxyP95Raw = swingP95,
            powerIndexProxyP95 = powerP95,
            consistencyScorePercent = consistency,
            stepCountStart = firstStepCountTotal,
            stepCountEnd = lastStepCountTotal,
            estimatedDistanceMeters = estimatedDistance,
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

    private fun percentile(
        values: List<Double>,
        quantile: Double,
    ): Double? {
        if (values.isEmpty()) {
            return null
        }

        val sorted = values.sorted()
        val index = ((sorted.lastIndex) * quantile).roundToInt().coerceIn(0, sorted.lastIndex)
        return sorted[index]
    }
}
