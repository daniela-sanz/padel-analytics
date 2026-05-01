package com.tfg.wearableapp.feature.session

import com.tfg.wearableapp.data.local.session.SessionBlockSummary
import com.tfg.wearableapp.data.processing.PostSessionSummary
import com.tfg.wearableapp.data.raw.RawSamplePreview
import com.tfg.wearableapp.data.session.StoredSessionSummary

data class SessionDetailUiState(
    val session: StoredSessionSummary? = null,
    val storedBlockCount: Int = 0,
    val blockSummaries: List<SessionBlockSummary> = emptyList(),
    val rawPreview: List<RawSamplePreview> = emptyList(),
    val processedSummary: PostSessionSummary? = null,
    val isLive: Boolean = false,
)
