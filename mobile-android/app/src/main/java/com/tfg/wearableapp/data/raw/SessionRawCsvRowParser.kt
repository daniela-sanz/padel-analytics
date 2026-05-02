package com.tfg.wearableapp.data.raw

object SessionRawCsvRowParser {
    fun parseRecord(line: String): RawCsvSampleRecord? {
        val parts = line.split(',')
        if (parts.size < 14) {
            return null
        }

        return RawCsvSampleRecord(
            sessionId = parts[0],
            packetId = parts[1].toLongOrNull() ?: return null,
            sampleTimestampMs = parts[2].toLongOrNull() ?: return null,
            sampleGlobalIndex = parts[3].toLongOrNull() ?: return null,
            sampleIndexInBlock = parts[4].toIntOrNull() ?: return null,
            stepCountTotal = parts[5].toLongOrNull(),
            batteryLevelPercent = parts[6].toIntOrNull(),
            statusFlags = parts[7].toIntOrNull(),
            ax = parts[8].toIntOrNull() ?: return null,
            ay = parts[9].toIntOrNull() ?: return null,
            az = parts[10].toIntOrNull() ?: return null,
            gx = parts[11].toIntOrNull() ?: return null,
            gy = parts[12].toIntOrNull() ?: return null,
            gz = parts[13].toIntOrNull() ?: return null,
        )
    }

    fun parsePreview(line: String): RawSamplePreview? {
        val record = parseRecord(line) ?: return null

        return RawSamplePreview(
            packetId = record.packetId.toString(),
            sampleGlobalIndex = record.sampleGlobalIndex.toString(),
            ax = record.ax.toString(),
            ay = record.ay.toString(),
            az = record.az.toString(),
            gx = record.gx.toString(),
            gy = record.gy.toString(),
            gz = record.gz.toString(),
        )
    }
}
