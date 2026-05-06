package com.tfg.wearableapp.data.processing

import com.tfg.wearableapp.core.ble.BleTransportConfig
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlin.math.roundToLong
import kotlin.math.sqrt

internal class ImuSessionSummaryBuilder(
    private val impactDeltaThresholdRaw: Double = 3000.0,
    private val impactGyroValidationThresholdRaw: Double = 5500.0,
    private val impactValidationWindowMs: Long = 60L,
    private val impactRefractoryMs: Long = 120L,
    private val accelerationEventDeltaThresholdRaw: Double = 800.0,
    private val playerSex: String = "No definido",
) {
    private data class MutableAccelerationBucket(
        val startSecond: Int,
        val endSecond: Int,
        var sampleCount: Int = 0,
        var accelMagnitudeSum: Double = 0.0,
        var maxAccelMagnitude: Double = 0.0,
        var playerLoadAccumulator: Double = 0.0,
        var impactCount: Int = 0,
        var directionChangeCount: Int = 0,
        var firstStepCountTotal: Long? = null,
        var lastStepCountTotal: Long? = null,
        var lowIntensitySampleCount: Int = 0,
        var mediumIntensitySampleCount: Int = 0,
        var highIntensitySampleCount: Int = 0,
        var explosiveIntensitySampleCount: Int = 0,
        val impactPowerPeaks: MutableList<Double> = mutableListOf(),
    )

    private val gestureAccelTriggerThresholdRaw = 6200.0
    private val gestureGyroTriggerThresholdRaw = 4100.0
    private val gestureCaptureWindowMs = 1000L
    private val accelerationBucketDurationMs = 10_000L
    private val lowIntensityThresholdRaw = 1200.0
    private val mediumIntensityThresholdRaw = 1800.0
    private val highIntensityThresholdRaw = 2500.0

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
    private var previousAx: Int? = null
    private var previousAy: Int? = null
    private var previousAz: Int? = null
    private var previousAccelMagnitude: Double? = null
    private var previousGyroMagnitude: Double? = null
    private val impactGyroPeaks = mutableListOf<Double>()
    private val impactPowerPeaks = mutableListOf<Double>()
    private var gestureWindowEndMs: Long? = null
    private var pendingImpactWindowEndMs: Long? = null
    private var pendingImpactTriggerTimestampMs = 0L
    private var pendingImpactMaxGyroMagnitude = 0.0
    private var pendingImpactMaxAccelMagnitude = 0.0
    private var pendingImpactMaxDelta = 0.0
    private val accelerationBuckets = mutableMapOf<Int, MutableAccelerationBucket>()

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
        flushExpiredPendingImpact(sampleTimestampMs)

        val deltaAccelAxesMean =
            if (previousAx == null || previousAy == null || previousAz == null) {
                0.0
            } else {
                (
                    abs(ax - previousAx!!) +
                        abs(ay - previousAy!!) +
                        abs(az - previousAz!!)
                    ) / 3.0
            }

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
        bucketFor(sampleTimestampMs).apply {
            sampleCount += 1
            accelMagnitudeSum += accelMagnitude
            maxAccelMagnitude = maxOf(maxAccelMagnitude, accelMagnitude)
            firstStepCountTotal = firstStepCountTotal ?: stepCountTotal
            lastStepCountTotal = stepCountTotal ?: lastStepCountTotal
            when (classifyAccelerationIntensity(accelMagnitude)) {
                AccelerationIntensity.Low -> lowIntensitySampleCount += 1
                AccelerationIntensity.Medium -> mediumIntensitySampleCount += 1
                AccelerationIntensity.High -> highIntensitySampleCount += 1
                AccelerationIntensity.Explosive -> explosiveIntensitySampleCount += 1
            }
        }

        if (previousAx != null && previousAy != null && previousAz != null) {
            val deltaAx = ax - previousAx!!
            val deltaAy = ay - previousAy!!
            val deltaAz = az - previousAz!!
            val playerLoadDelta = sqrt(
                (deltaAx.toDouble() * deltaAx.toDouble()) +
                    (deltaAy.toDouble() * deltaAy.toDouble()) +
                    (deltaAz.toDouble() * deltaAz.toDouble())
            )
            playerLoadAccumulator += playerLoadDelta
            bucketFor(sampleTimestampMs).playerLoadAccumulator += playerLoadDelta
        }
        previousAx = ax
        previousAy = ay
        previousAz = az
        previousAccelMagnitude = accelMagnitude
        previousGyroMagnitude = gyroMagnitude

        if (
            accelMagnitude >= gestureAccelTriggerThresholdRaw &&
            gyroMagnitude >= gestureGyroTriggerThresholdRaw
        ) {
            gestureWindowEndMs = sampleTimestampMs + gestureCaptureWindowMs
        }

        val insideGestureWindow = gestureWindowEndMs?.let { sampleTimestampMs <= it } == true

        val isExplosive =
            (insideGestureWindow && deltaAccelAxesMean >= impactDeltaThresholdRaw * 0.6) ||
                gyroMagnitude >= impactGyroValidationThresholdRaw * 0.8
        if (isExplosive) {
            explosiveSampleCount += 1
        }

        if (deltaAccelAxesMean >= accelerationEventDeltaThresholdRaw &&
            sampleTimestampMs - lastAccelerationEventTimestampMs >= 180L) {
            accelerationEventCount += 1
            lastAccelerationEventTimestampMs = sampleTimestampMs
            bucketFor(sampleTimestampMs).directionChangeCount += 1
        }

        if (pendingImpactWindowEndMs != null) {
            pendingImpactMaxGyroMagnitude = maxOf(pendingImpactMaxGyroMagnitude, gyroMagnitude)
            pendingImpactMaxAccelMagnitude = maxOf(pendingImpactMaxAccelMagnitude, accelMagnitude)
            pendingImpactMaxDelta = maxOf(pendingImpactMaxDelta, deltaAccelAxesMean)
        } else if (
            insideGestureWindow &&
            deltaAccelAxesMean >= impactDeltaThresholdRaw &&
            sampleTimestampMs - lastImpactTimestampMs >= impactRefractoryMs
        ) {
            pendingImpactWindowEndMs = sampleTimestampMs + impactValidationWindowMs
            pendingImpactTriggerTimestampMs = sampleTimestampMs
            pendingImpactMaxGyroMagnitude = gyroMagnitude
            pendingImpactMaxAccelMagnitude = accelMagnitude
            pendingImpactMaxDelta = deltaAccelAxesMean
        }
    }

    fun build(): PostSessionSummary? {
        if (sampleCount == 0) {
            return null
        }

        flushExpiredPendingImpact(Long.MAX_VALUE)

        val rawDurationMs = ((lastTimestampMs ?: 0L) - (firstTimestampMs ?: 0L)).coerceAtLeast(0L)
        val estimatedDurationMs =
            if (rawDurationMs > 0L) {
                rawDurationMs
            } else {
                (((sampleCount - 1).coerceAtLeast(1) * 1000.0) / BleTransportConfig.targetSampleRateHz)
                    .roundToLong()
            }
        val durationMinutes = (estimatedDurationMs / 60000.0).coerceAtLeast(1.0 / 60.0)
        val playerLoadScore = (playerLoadAccumulator / 1000.0).roundToInt()
        val impactsPerMinute = candidateImpactCount / durationMinutes
        val playerLoadPerMinute = (playerLoadScore / durationMinutes).roundToInt()
        val explosiveExposurePercent = ((explosiveSampleCount * 100.0) / sampleCount).roundToInt()
        val rallyIntensityIndexProxy = ((impactsPerMinute * (accelMagnitudeSum / sampleCount)) / 1000.0).roundToInt()
        val swingP95 = percentile(impactGyroPeaks, 0.95)?.roundToInt()
        val powerP95 = percentile(impactPowerPeaks, 0.95)?.roundToInt()
        val estimatedStrideLengthMeters = estimatedStrideLengthMetersForSex(playerSex)
        val estimatedDistance =
            firstStepCountTotal
                ?.let { start -> lastStepCountTotal?.minus(start) }
                ?.coerceAtLeast(0L)
                ?.let { steps -> (steps * estimatedStrideLengthMeters).roundToInt() }
        val accelerationBucketSummaries = accelerationBuckets
            .toSortedMap()
            .values
            .map { bucket ->
                val estimatedBucketDistanceMeters =
                    bucket.firstStepCountTotal
                        ?.let { start -> bucket.lastStepCountTotal?.minus(start) }
                        ?.coerceAtLeast(0L)
                        ?.let { steps -> (steps * estimatedStrideLengthMeters).roundToInt() }
                        ?: 0
                val highIntensityFraction =
                    if (bucket.sampleCount == 0) {
                        0.0
                    } else {
                        (bucket.highIntensitySampleCount + bucket.explosiveIntensitySampleCount).toDouble() / bucket.sampleCount
                    }
                val explosiveDistanceMeters = (estimatedBucketDistanceMeters * highIntensityFraction).roundToInt()
                val bucketDurationMinutes =
                    ((bucket.sampleCount / BleTransportConfig.targetSampleRateHz.toDouble()) / 60.0)
                        .coerceAtLeast(1.0 / 60.0)
                val impactsPerMinuteBucket = bucket.impactCount / bucketDurationMinutes
                val meanAccelBucket =
                    if (bucket.sampleCount == 0) 0.0 else bucket.accelMagnitudeSum / bucket.sampleCount
                val rallyIntensityIndexProxyBucket = ((impactsPerMinuteBucket * meanAccelBucket) / 1000.0).roundToInt()
                val bucketPowerIndex = percentile(bucket.impactPowerPeaks, 0.95)?.roundToInt()
                val bucketConsistency =
                    bucket.impactPowerPeaks
                        .takeIf { it.size >= 2 }
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

                AccelerationBucketSummary(
                    startSecond = bucket.startSecond,
                    endSecond = bucket.endSecond,
                    sampleCount = bucket.sampleCount,
                    meanAccelMagnitudeRaw = if (bucket.sampleCount == 0) 0 else {
                        meanAccelBucket.roundToInt()
                    },
                    maxAccelMagnitudeRaw = bucket.maxAccelMagnitude.roundToInt(),
                    playerLoadScore = (bucket.playerLoadAccumulator / 1000.0).roundToInt(),
                    impactCount = bucket.impactCount,
                    directionChangeCount = bucket.directionChangeCount,
                    estimatedDistanceMeters = estimatedBucketDistanceMeters,
                    explosiveDistanceMeters = explosiveDistanceMeters,
                    rallyIntensityIndexProxy = rallyIntensityIndexProxyBucket,
                    powerIndexProxy = bucketPowerIndex,
                    consistencyScorePercent = bucketConsistency,
                    lowIntensitySampleCount = bucket.lowIntensitySampleCount,
                    mediumIntensitySampleCount = bucket.mediumIntensitySampleCount,
                    highIntensitySampleCount = bucket.highIntensitySampleCount,
                    explosiveIntensitySampleCount = bucket.explosiveIntensitySampleCount,
                )
            }
        val totalExplosiveDistanceMeters = accelerationBucketSummaries.sumOf { it.explosiveDistanceMeters }
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
            explosiveDistanceMeters = totalExplosiveDistanceMeters,
            batteryAtStartPercent = firstBatteryPercent,
            batteryAtEndPercent = lastBatteryPercent,
            accelerationBuckets = accelerationBucketSummaries,
        )
    }

    private fun flushExpiredPendingImpact(currentTimestampMs: Long) {
        val windowEnd = pendingImpactWindowEndMs ?: return
        if (currentTimestampMs < windowEnd) {
            return
        }

        if (
            pendingImpactMaxGyroMagnitude >= impactGyroValidationThresholdRaw &&
            pendingImpactTriggerTimestampMs - lastImpactTimestampMs >= impactRefractoryMs
        ) {
            candidateImpactCount += 1
            val bucket = bucketFor(pendingImpactTriggerTimestampMs)
            bucket.impactCount += 1
            lastImpactTimestampMs = pendingImpactTriggerTimestampMs
            impactGyroPeaks += pendingImpactMaxGyroMagnitude
            val powerPeak = (pendingImpactMaxAccelMagnitude * pendingImpactMaxGyroMagnitude) / 1000.0
            impactPowerPeaks += powerPeak
            bucket.impactPowerPeaks += powerPeak
        }

        pendingImpactWindowEndMs = null
        pendingImpactTriggerTimestampMs = 0L
        pendingImpactMaxGyroMagnitude = 0.0
        pendingImpactMaxAccelMagnitude = 0.0
        pendingImpactMaxDelta = 0.0
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

    private fun bucketFor(sampleTimestampMs: Long): MutableAccelerationBucket {
        val sessionStart = firstTimestampMs ?: sampleTimestampMs
        val relativeMs = (sampleTimestampMs - sessionStart).coerceAtLeast(0L)
        val bucketIndex = (relativeMs / accelerationBucketDurationMs).toInt()
        return accelerationBuckets.getOrPut(bucketIndex) {
            val startSecond = (bucketIndex * accelerationBucketDurationMs / 1000L).toInt()
            val endSecond = startSecond + (accelerationBucketDurationMs / 1000L).toInt()
            MutableAccelerationBucket(
                startSecond = startSecond,
                endSecond = endSecond,
            )
        }
    }

    private fun classifyAccelerationIntensity(accelMagnitude: Double): AccelerationIntensity {
        return when {
            accelMagnitude < lowIntensityThresholdRaw -> AccelerationIntensity.Low
            accelMagnitude < mediumIntensityThresholdRaw -> AccelerationIntensity.Medium
            accelMagnitude < highIntensityThresholdRaw -> AccelerationIntensity.High
            else -> AccelerationIntensity.Explosive
        }
    }

    private enum class AccelerationIntensity {
        Low,
        Medium,
        High,
        Explosive,
    }

    private fun estimatedStrideLengthMetersForSex(sex: String): Double {
        return when (sex.trim().lowercase()) {
            "masculino" -> 0.78
            "femenino" -> 0.70
            else -> 0.74
        }
    }
}
