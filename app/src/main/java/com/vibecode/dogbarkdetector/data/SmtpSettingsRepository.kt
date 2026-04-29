package com.vibecode.dogbarkdetector.data

import android.content.Context
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private const val DATASTORE_NAME = "smtp_settings"

private val Context.smtpDataStore by preferencesDataStore(name = DATASTORE_NAME)

class SmtpSettingsRepository(
    private val context: Context
) {
    val settings: Flow<SmtpSettings> = context.smtpDataStore.data.map { preferences ->
        preferences.toSmtpSettings()
    }

    suspend fun save(settings: SmtpSettings) {
        context.smtpDataStore.edit { preferences ->
            preferences[Keys.HOST] = settings.host
            preferences[Keys.PORT] = settings.port
            preferences[Keys.USERNAME] = settings.username
            preferences[Keys.PASSWORD] = settings.password
            preferences[Keys.SENDER] = settings.senderAddress
            preferences[Keys.RECIPIENT] = settings.recipientAddress
            preferences[Keys.TLS] = settings.useTls
        }
    }

    private fun Preferences.toSmtpSettings(): SmtpSettings = SmtpSettings(
        host = this[Keys.HOST].orEmpty(),
        port = this[Keys.PORT] ?: "587",
        username = this[Keys.USERNAME].orEmpty(),
        password = this[Keys.PASSWORD].orEmpty(),
        senderAddress = this[Keys.SENDER].orEmpty(),
        recipientAddress = this[Keys.RECIPIENT].orEmpty(),
        useTls = this[Keys.TLS] ?: true
    )

    private object Keys {
        val HOST = stringPreferencesKey("host")
        val PORT = stringPreferencesKey("port")
        val USERNAME = stringPreferencesKey("username")
        val PASSWORD = stringPreferencesKey("password")
        val SENDER = stringPreferencesKey("sender")
        val RECIPIENT = stringPreferencesKey("recipient")
        val TLS = booleanPreferencesKey("tls")
    }
}
