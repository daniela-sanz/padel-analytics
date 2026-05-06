package com.tfg.wearableapp.data.processing

import com.tfg.wearableapp.data.raw.SessionRawCsvRowParser
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class PostSessionProcessor {
    suspend fun process(
        rawFilePath: String?,
        playerSex: String = "No definido",
        impactDeltaThresholdRaw: Double = 3000.0,
        impactGyroValidationThresholdRaw: Double = 5500.0,
        impactRefractoryMs: Long = 120L,
    ): PostSessionSummary? = withContext(Dispatchers.IO) {
        if (rawFilePath.isNullOrBlank()) {
            return@withContext null
        }

        val file = File(rawFilePath)
        if (!file.exists()) {
            return@withContext null
        }

        val summaryBuilder = ImuSessionSummaryBuilder(
            impactDeltaThresholdRaw = impactDeltaThresholdRaw,
            impactGyroValidationThresholdRaw = impactGyroValidationThresholdRaw,
            impactRefractoryMs = impactRefractoryMs,
            playerSex = playerSex,
        )

        file.useLines { lines ->
            lines
                .drop(1)
                .forEach { line ->
                    val record = SessionRawCsvRowParser.parseRecord(line) ?: return@forEach
                    summaryBuilder.addSample(
                        packetId = record.packetId,
                        sampleTimestampMs = record.sampleTimestampMs,
                        batteryPercent = record.batteryLevelPercent,
                        stepCountTotal = record.stepCountTotal,
                        ax = record.ax,
                        ay = record.ay,
                        az = record.az,
                        gx = record.gx,
                        gy = record.gy,
                        gz = record.gz,
                    )
                }
        }

        summaryBuilder.build()
    }
}
