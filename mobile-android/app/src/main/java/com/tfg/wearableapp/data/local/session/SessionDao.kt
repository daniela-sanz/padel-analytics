package com.tfg.wearableapp.data.local.session

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update

@Dao
interface SessionDao {
    @Query("SELECT * FROM sessions ORDER BY startedAtEpochMs DESC")
    suspend fun getAll(): List<SessionEntity>

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(session: SessionEntity)

    @Update
    suspend fun update(session: SessionEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertBlock(block: SessionBlockEntity)

    @Query("SELECT COUNT(*) FROM session_blocks WHERE sessionId = :sessionId")
    suspend fun countBlocksForSession(sessionId: String): Int

    @Query(
        """
        SELECT packetId, timestampBlockStartMs, sampleStartIndex, sampleCount, batteryLevelPercent
        FROM session_blocks
        WHERE sessionId = :sessionId
        ORDER BY packetId ASC
        LIMIT :limit
        """
    )
    suspend fun getBlockSummariesForSession(
        sessionId: String,
        limit: Int,
    ): List<SessionBlockSummary>
}
