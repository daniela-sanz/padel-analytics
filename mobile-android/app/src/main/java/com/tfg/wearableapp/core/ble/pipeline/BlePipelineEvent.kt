package com.tfg.wearableapp.core.ble.pipeline

import com.tfg.wearableapp.core.ble.model.BleTransportMessage
import com.tfg.wearableapp.core.ble.model.ImuLogicalBlock
import com.tfg.wearableapp.core.ble.model.TelemetrySnapshot

sealed interface BlePipelineEvent {
    data class ChunkObserved(
        val message: BleTransportMessage,
    ) : BlePipelineEvent

    data class BlockCompleted(
        val block: ImuLogicalBlock,
        val transportKind: String,
    ) : BlePipelineEvent

    data class TelemetryUpdated(
        val telemetry: TelemetrySnapshot,
    ) : BlePipelineEvent

    data class ExpiredPacketsDropped(
        val packetIds: List<Long>,
    ) : BlePipelineEvent

    data object WaitingMoreData : BlePipelineEvent
}
