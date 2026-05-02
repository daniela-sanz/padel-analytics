package com.tfg.wearableapp.feature.session

import com.tfg.wearableapp.data.session.StoredSessionSummary
import com.tfg.wearableapp.feature.profile.PlayerProfileUiState

data class SessionUiState(
    val isRecording: Boolean = false,
    val recordingMode: String? = null,
    val statusText: String = "Lista para iniciar una sesion. Puedes usar modo simulado o BLE real si ya conectaste la XIAO.",
    val notificationsSeen: Int = 0,
    val blocksCompleted: Int = 0,
    val samplesReceived: Int = 0,
    val lastPacketId: Long? = null,
    val lastBatteryLevel: Int? = null,
    val lastStepCountTotal: Long? = null,
    val lastStatusFlags: Int? = null,
    val packetGapCount: Int = 0,
    val sampleGapCount: Int = 0,
    val effectiveSampleRateHz: Double? = null,
    val bleDeviceConnected: Boolean = false,
    val bleDeviceName: String? = null,
    val savedSessions: List<StoredSessionSummary> = emptyList(),
    val selectedSessionDetail: SessionDetailUiState? = null,
    val liveDashboardDetail: SessionDetailUiState? = null,
    val dashboardMode: DashboardMode = DashboardMode.Live,
    val sessionNameDraft: String = "",
    val playerProfile: PlayerProfileUiState = PlayerProfileUiState(),
)
