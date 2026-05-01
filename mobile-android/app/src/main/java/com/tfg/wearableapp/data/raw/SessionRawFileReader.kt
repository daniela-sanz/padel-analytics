package com.tfg.wearableapp.data.raw

import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class SessionRawFileReader {
    suspend fun readPreview(
        rawFilePath: String?,
        maxRows: Int = 5,
    ): List<RawSamplePreview> = withContext(Dispatchers.IO) {
        if (rawFilePath.isNullOrBlank()) {
            return@withContext emptyList()
        }

        val file = File(rawFilePath)
        if (!file.exists()) {
            return@withContext emptyList()
        }

        file.useLines { lines ->
            lines
                .drop(1)
                .take(maxRows)
                .mapNotNull(SessionRawCsvRowParser::parsePreview)
                .toList()
        }
    }
}
