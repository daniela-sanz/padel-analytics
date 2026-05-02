package com.tfg.wearableapp.data.local.session

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "session_blocks",
    foreignKeys = [
        ForeignKey(
            entity = SessionEntity::class,
            parentColumns = ["id"],
            childColumns = ["sessionId"],
            onDelete = ForeignKey.CASCADE,
        )
    ],
    indices = [
        Index(value = ["sessionId"]),
        Index(value = ["sessionId", "packetId"], unique = true),
    ],
)
data class SessionBlockEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0L,
    val sessionId: String,
    val packetId: Long,
    val timestampBlockStartMs: Long,
    val sampleStartIndex: Long,
    val sampleCount: Int,
    val stepCountTotal: Long?,
    val batteryLevelPercent: Int?,
    val statusFlags: Int?,
    val receivedAtEpochMs: Long,
)
