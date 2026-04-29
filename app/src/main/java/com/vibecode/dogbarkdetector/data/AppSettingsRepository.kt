package com.vibecode.dogbarkdetector.data

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.appSettingsDataStore by preferencesDataStore(name = "app_settings")

data class AppSettings(
    val cooldownSeconds: Long = 120L,
    val confidenceThreshold: Float = 0.25f
)

class AppSettingsRepository(private val context: Context) {

    val settings: Flow<AppSettings> = context.appSettingsDataStore.data.map { prefs ->
        AppSettings(
            cooldownSeconds = prefs[Keys.COOLDOWN_SECONDS] ?: 120L,
            confidenceThreshold = prefs[Keys.CONFIDENCE_THRESHOLD] ?: 0.25f
        )
    }

    suspend fun updateCooldown(seconds: Long) {
        context.appSettingsDataStore.edit { prefs ->
            prefs[Keys.COOLDOWN_SECONDS] = seconds
        }
    }

    suspend fun updateConfidenceThreshold(threshold: Float) {
        context.appSettingsDataStore.edit { prefs ->
            prefs[Keys.CONFIDENCE_THRESHOLD] = threshold
        }
    }

    private object Keys {
        val COOLDOWN_SECONDS = longPreferencesKey("cooldown_seconds")
        val CONFIDENCE_THRESHOLD = floatPreferencesKey("confidence_threshold")
    }
}
