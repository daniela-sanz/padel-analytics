package com.tfg.wearableapp.core.ble.model

sealed interface ReassemblyStatus {
    data object WaitingMoreChunks : ReassemblyStatus

    data class Completed(
        val packetId: Long,
        val bytes: ByteArray,
    ) : ReassemblyStatus

    data class DroppedExpiredPackets(
        val expiredPacketIds: List<Long>,
    ) : ReassemblyStatus
}
