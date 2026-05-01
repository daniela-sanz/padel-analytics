package com.tfg.wearableapp.data.local.session

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "sessions")
data class SessionEntity(
    @PrimaryKey
    val id: String,
    val sessionName: String,
    val startedAtEpochMs: Long,
    val endedAtEpochMs: Long,
    val durationMs: Long,
    val mode: String,
    val notificationsSeen: Int,
    val blocksCompleted: Int,
    val samplesReceived: Int,
    val lastPacketId: Long?,
    val rawFilePath: String?,
)
