package com.tfg.wearableapp.feature.session

import com.tfg.wearableapp.data.session.StoredSessionSummary

data class SessionUiState(
    val isRecording: Boolean = false,
    val statusText: String = "Lista para iniciar una sesion simulada.",
    val notificationsSeen: Int = 0,
    val blocksCompleted: Int = 0,
    val samplesReceived: Int = 0,
    val lastPacketId: Long? = null,
    val savedSessions: List<StoredSessionSummary> = emptyList(),
)
