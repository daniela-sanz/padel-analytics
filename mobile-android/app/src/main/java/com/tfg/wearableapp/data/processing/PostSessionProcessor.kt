package com.tfg.wearableapp.data.processing

import com.tfg.wearableapp.data.raw.SessionRawCsvRowParser
import java.io.File
import kotlin.math.sqrt
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class PostSessionProcessor {
    suspend fun process(
        rawFilePath: String?,
        impactAccelThresholdRaw: Double = 1700.0,
        impactGyroThresholdRaw: Double = 1500.0,
        impactRefractoryMs: Long = 120L,
    ): PostSessionSummary? = withContext(Dispatchers.IO) {
        if (rawFilePath.isNullOrBlank()) {
            return@withContext null
        }

        val file = File(rawFilePath)
        if (!file.exists()) {
            return@withContext null
        }

        var sampleCount = 0
        var firstTimestampMs: Long? = null
        var lastTimestampMs: Long? = null
        var firstBatteryPercent: Int? = null
        var lastBatteryPercent: Int? = null
        val packetIds = linkedSetOf<Long>()
        var peakAccelMagnitude = 0.0
        var peakGyroMagnitude = 0.0
        var accelMagnitudeSum = 0.0
        var gyroMagnitudeSum = 0.0
        var candidateImpactCount = 0
        var lastImpactTimestampMs = Long.MIN_VALUE

        file.useLines { lines ->
            lines
                .drop(1)
                .forEach { line ->
                    val record = SessionRawCsvRowParser.parseRecord(line) ?: return@forEach
                    val accelMagnitude = computeMagnitude(record.ax, record.ay, record.az)
                    val gyroMagnitude = computeMagnitude(record.gx, record.gy, record.gz)

                    sampleCount += 1
                    packetIds += record.packetId
                    firstTimestampMs = firstTimestampMs ?: record.sampleTimestampMs
                    lastTimestampMs = record.sampleTimestampMs
                    firstBatteryPercent = firstBatteryPercent ?: record.batteryLevelPercent
                    lastBatteryPercent = record.batteryLevelPercent
                    peakAccelMagnitude = maxOf(peakAccelMagnitude, accelMagnitude)
                    peakGyroMagnitude = maxOf(peakGyroMagnitude, gyroMagnitude)
                    accelMagnitudeSum += accelMagnitude
                    gyroMagnitudeSum += gyroMagnitude

                    val exceedsImpactThreshold =
                        accelMagnitude >= impactAccelThresholdRaw ||
                            gyroMagnitude >= impactGyroThresholdRaw
                    val outsideRefractoryWindow =
                        record.sampleTimestampMs - lastImpactTimestampMs >= impactRefractoryMs

                    if (exceedsImpactThreshold && outsideRefractoryWindow) {
                        candidateImpactCount += 1
                        lastImpactTimestampMs = record.sampleTimestampMs
                    }
                }
        }

        if (sampleCount == 0) {
            return@withContext null
        }

        PostSessionSummary(
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
