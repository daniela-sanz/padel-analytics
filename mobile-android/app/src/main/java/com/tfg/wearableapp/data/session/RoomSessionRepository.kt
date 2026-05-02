package com.tfg.wearableapp.data.session

import com.tfg.wearableapp.core.ble.model.ImuLogicalBlock
import com.tfg.wearableapp.core.ble.model.TelemetrySnapshot
import com.tfg.wearableapp.data.local.session.SessionDao
import com.tfg.wearableapp.data.local.session.SessionBlockEntity
import com.tfg.wearableapp.data.local.session.SessionBlockSummary
import com.tfg.wearableapp.data.local.session.SessionEntity

class RoomSessionRepository(
    private val sessionDao: SessionDao,
) {
    suspend fun loadSessions(): List<StoredSessionSummary> {
        return sessionDao.getAll().map { it.toModel() }
    }

    suspend fun createSession(summary: StoredSessionSummary) {
        sessionDao.insert(summary.toEntity())
    }

    suspend fun updateSession(summary: StoredSessionSummary) {
        sessionDao.update(summary.toEntity())
    }

    suspend fun saveSessionBlock(
        sessionId: String,
        block: ImuLogicalBlock,
        receivedAtEpochMs: Long,
        telemetry: TelemetrySnapshot? = null,
    ) {
        sessionDao.insertBlock(
            SessionBlockEntity(
                sessionId = sessionId,
                packetId = block.packetId,
                timestampBlockStartMs = block.timestampBlockStartMs,
                sampleStartIndex = block.sampleStartIndex,
                sampleCount = block.sampleCount,
                stepCountTotal = telemetry?.stepCountTotal ?: block.stepCountTotal,
                batteryLevelPercent = telemetry?.batteryLevelPercent ?: block.batteryLevelPercent,
                statusFlags = telemetry?.statusFlags ?: block.statusFlags,
                receivedAtEpochMs = receivedAtEpochMs,
            )
        )
    }

    suspend fun countBlocksForSession(sessionId: String): Int {
        return sessionDao.countBlocksForSession(sessionId)
    }

    suspend fun loadBlockSummariesForSession(
        sessionId: String,
        limit: Int = 5,
    ): List<SessionBlockSummary> {
        return sessionDao.getBlockSummariesForSession(sessionId, limit)
    }

    private fun StoredSessionSummary.toEntity(): SessionEntity {
        return SessionEntity(
            id = id,
            sessionName = sessionName,
            startedAtEpochMs = startedAtEpochMs,
            endedAtEpochMs = endedAtEpochMs,
            durationMs = durationMs,
            mode = mode,
            notificationsSeen = notificationsSeen,
            blocksCompleted = blocksCompleted,
            samplesReceived = samplesReceived,
            lastPacketId = lastPacketId,
            rawFilePath = rawFilePath,
        )
    }

    private fun SessionEntity.toModel(): StoredSessionSummary {
        return StoredSessionSummary(
            id = id,
            sessionName = sessionName,
            startedAtEpochMs = startedAtEpochMs,
            endedAtEpochMs = endedAtEpochMs,
            durationMs = durationMs,
            mode = mode,
            notificationsSeen = notificationsSeen,
            blocksCompleted = blocksCompleted,
            samplesReceived = samplesReceived,
            lastPacketId = lastPacketId,
            rawFilePath = rawFilePath,
        )
    }
}
