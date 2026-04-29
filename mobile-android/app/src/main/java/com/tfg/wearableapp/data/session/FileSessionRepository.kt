package com.tfg.wearableapp.data.session

import android.content.Context
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

class FileSessionRepository(
    context: Context,
) {
    private val file: File = File(context.filesDir, "session_summaries.json")

    suspend fun loadSessions(): List<StoredSessionSummary> = withContext(Dispatchers.IO) {
        if (!file.exists()) {
            return@withContext emptyList()
        }

        val content = file.readText()
        if (content.isBlank()) {
            return@withContext emptyList()
        }

        val array = JSONArray(content)
        buildList(array.length()) {
            for (index in 0 until array.length()) {
                val item = array.getJSONObject(index)
                add(item.toStoredSessionSummary())
            }
        }.sortedByDescending { it.startedAtEpochMs }
    }

    suspend fun saveSession(summary: StoredSessionSummary) = withContext(Dispatchers.IO) {
        val currentSessions = loadSessions().toMutableList()
        currentSessions.add(0, summary)

        val array = JSONArray()
        currentSessions
            .distinctBy { it.id }
            .take(50)
            .forEach { array.put(it.toJson()) }

        file.writeText(array.toString())
    }

    private fun StoredSessionSummary.toJson(): JSONObject {
        return JSONObject()
            .put("id", id)
            .put("startedAtEpochMs", startedAtEpochMs)
            .put("endedAtEpochMs", endedAtEpochMs)
            .put("durationMs", durationMs)
            .put("mode", mode)
            .put("notificationsSeen", notificationsSeen)
            .put("blocksCompleted", blocksCompleted)
            .put("samplesReceived", samplesReceived)
            .put("lastPacketId", lastPacketId ?: JSONObject.NULL)
    }

    private fun JSONObject.toStoredSessionSummary(): StoredSessionSummary {
        return StoredSessionSummary(
            id = getString("id"),
            startedAtEpochMs = getLong("startedAtEpochMs"),
            endedAtEpochMs = getLong("endedAtEpochMs"),
            durationMs = getLong("durationMs"),
            mode = getString("mode"),
            notificationsSeen = getInt("notificationsSeen"),
            blocksCompleted = getInt("blocksCompleted"),
            samplesReceived = getInt("samplesReceived"),
            lastPacketId = if (isNull("lastPacketId")) null else getLong("lastPacketId"),
        )
    }
}
