package com.tfg.wearableapp.data.session

data class StoredSessionSummary(
    val id: String,
    val startedAtEpochMs: Long,
    val endedAtEpochMs: Long,
    val durationMs: Long,
    val mode: String,
    val notificationsSeen: Int,
    val blocksCompleted: Int,
    val samplesReceived: Int,
    val lastPacketId: Long?,
)
