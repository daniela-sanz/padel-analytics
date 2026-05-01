package com.tfg.wearableapp.data.raw

import android.content.Context
import com.tfg.wearableapp.core.ble.BleTransportConfig
import com.tfg.wearableapp.core.ble.model.ImuLogicalBlock
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class SessionRawFileWriter(
    private val context: Context,
) {
    private var outputFile: File? = null

    suspend fun start(sessionId: String): String = withContext(Dispatchers.IO) {
        val directory = File(context.filesDir, "raw_sessions")
        if (!directory.exists()) {
            directory.mkdirs()
        }

        val file = File(directory, "session_$sessionId.csv")
        file.writeText(
            "session_id,packet_id,block_timestamp_ms,sample_global_index,sample_index_in_block,step_count_total,battery_level,status_flags,ax,ay,az,gx,gy,gz\n"
        )
        outputFile = file
        file.absolutePath
    }

    suspend fun appendBlock(
        sessionId: String,
        block: ImuLogicalBlock,
    ) = withContext(Dispatchers.IO) {
        val file = requireNotNull(outputFile) {
            "No se puede escribir el crudo porque la sesion no ha inicializado su archivo"
        }

        val samplePeriodMs = 1000.0 / BleTransportConfig.targetSampleRateHz.toDouble()
        val builder = StringBuilder()

        block.samples.forEachIndexed { index, sample ->
            val sampleGlobalIndex = block.sampleStartIndex + index
            val sampleTimestampMs = block.timestampBlockStartMs + (index * samplePeriodMs).toLong()

            builder.append(sessionId)
                .append(',')
                .append(block.packetId)
                .append(',')
                .append(sampleTimestampMs)
                .append(',')
                .append(sampleGlobalIndex)
                .append(',')
                .append(index)
                .append(',')
                .append(block.stepCountTotal)
                .append(',')
                .append(block.batteryLevelPercent)
                .append(',')
                .append(block.statusFlags)
                .append(',')
                .append(sample.ax)
                .append(',')
                .append(sample.ay)
                .append(',')
                .append(sample.az)
                .append(',')
                .append(sample.gx)
                .append(',')
                .append(sample.gy)
                .append(',')
                .append(sample.gz)
                .append('\n')
        }

        file.appendText(builder.toString())
    }

    fun close() {
        outputFile = null
    }
}
