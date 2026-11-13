package com.example.domain.security

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.example.localization.AppLanguage
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.oneHomeDataStore by preferencesDataStore(name = "one_home_pro_secure_prefs")

class SessionStore(private val context: Context) {
    private object Keys {
        val SESSION_TOKEN = stringPreferencesKey("session_token")
        val DEVICE_ID = stringPreferencesKey("device_id")
        val LANGUAGE = stringPreferencesKey("app_language")
        val DARK_MODE = booleanPreferencesKey("dark_mode")
    }

    val languageFlow: Flow<AppLanguage> = context.oneHomeDataStore.data.map { prefs ->
        val code = prefs[Keys.LANGUAGE] ?: AppLanguage.EN.name
        AppLanguage.entries.firstOrNull { it.name == code } ?: AppLanguage.EN
    }

    val darkModeFlow: Flow<Boolean> = context.oneHomeDataStore.data.map { prefs ->
        prefs[Keys.DARK_MODE] ?: false
    }

    suspend fun getOrCreateDeviceId(): String {
        val current = context.oneHomeDataStore.data.first()[Keys.DEVICE_ID]
        if (!current.isNullOrBlank()) return current
        val created = "DEV-${CryptoUtil.newUuid().take(12).uppercase()}"
        context.oneHomeDataStore.edit { it[Keys.DEVICE_ID] = created }
        return created
    }

    suspend fun saveSessionToken(token: String) {
        context.oneHomeDataStore.edit { it[Keys.SESSION_TOKEN] = token }
    }

    suspend fun getSessionToken(): String? =
        context.oneHomeDataStore.data.first()[Keys.SESSION_TOKEN]

    suspend fun clearSessionToken() {
        context.oneHomeDataStore.edit { it.remove(Keys.SESSION_TOKEN) }
    }

    suspend fun setLanguage(language: AppLanguage) {
        context.oneHomeDataStore.edit { it[Keys.LANGUAGE] = language.name }
    }

    suspend fun setDarkMode(enabled: Boolean) {
        context.oneHomeDataStore.edit { it[Keys.DARK_MODE] = enabled }
    }
}
