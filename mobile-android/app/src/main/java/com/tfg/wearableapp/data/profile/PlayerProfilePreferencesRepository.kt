package com.tfg.wearableapp.data.profile

import android.content.Context
import com.tfg.wearableapp.feature.profile.PlayerProfileUiState

class PlayerProfilePreferencesRepository(
    context: Context,
) {
    private val prefs = context.getSharedPreferences("player_profile_prefs", Context.MODE_PRIVATE)

    fun load(): PlayerProfileUiState {
        return PlayerProfileUiState(
            athleteName = prefs.getString(KEY_ATHLETE_NAME, "") ?: "",
            sex = prefs.getString(KEY_SEX, "No definido") ?: "No definido",
            dominantHand = prefs.getString(KEY_DOMINANT_HAND, "Derecha") ?: "Derecha",
            level = prefs.getString(KEY_LEVEL, "Intermedio") ?: "Intermedio",
            notes = prefs.getString(KEY_NOTES, "") ?: "",
        )
    }

    fun save(profile: PlayerProfileUiState) {
        prefs.edit()
            .putString(KEY_ATHLETE_NAME, profile.athleteName)
            .putString(KEY_SEX, profile.sex)
            .putString(KEY_DOMINANT_HAND, profile.dominantHand)
            .putString(KEY_LEVEL, profile.level)
            .putString(KEY_NOTES, profile.notes)
            .apply()
    }

    companion object {
        private const val KEY_ATHLETE_NAME = "athlete_name"
        private const val KEY_SEX = "sex"
        private const val KEY_DOMINANT_HAND = "dominant_hand"
        private const val KEY_LEVEL = "level"
        private const val KEY_NOTES = "notes"
    }
}
