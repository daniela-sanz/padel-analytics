package com.tfg.wearableapp.feature.profile

data class PlayerProfileUiState(
    val athleteName: String = "",
    val sex: String = "No definido",
    val dominantHand: String = "Derecha",
    val level: String = "Intermedio",
    val notes: String = "",
)
