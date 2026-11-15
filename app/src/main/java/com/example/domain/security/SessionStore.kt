package com.example.domain.security

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStoreFile
import com.example.localization.AppLanguage
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import java.util.concurrent.ConcurrentHashMap

class SessionStore(context: Context) {
    private val appContext: Context = context.applicationContext ?: context
    private val dataStore: DataStore<Preferences> = getOrCreateDataStore(appContext)

    private object Keys {
        val SESSION_TOKEN = stringPreferencesKey("session_token")
        val DEVICE_ID = stringPreferencesKey("device_id")
        val LANGUAGE = stringPreferencesKey("app_language")
        val DARK_MODE = booleanPreferencesKey("dark_mode")
    }

    val languageFlow: Flow<AppLanguage> = dataStore.data.map { prefs ->
        val code = prefs[Keys.LANGUAGE] ?: AppLanguage.EN.name
        AppLanguage.entries.firstOrNull { it.name == code } ?: AppLanguage.EN
    }

    val darkModeFlow: Flow<Boolean> = dataStore.data.map { prefs ->
        prefs[Keys.DARK_MODE] ?: false
    }

    suspend fun getOrCreateDeviceId(): String {
        val current = dataStore.data.first()[Keys.DEVICE_ID]
        if (!current.isNullOrBlank()) return current
        val created = "DEV-${CryptoUtil.newUuid().take(12).uppercase()}"
        dataStore.edit { it[Keys.DEVICE_ID] = created }
        return created
    }

    suspend fun saveSessionToken(token: String) {
        dataStore.edit { it[Keys.SESSION_TOKEN] = token }
    }

    suspend fun getSessionToken(): String? =
        dataStore.data.first()[Keys.SESSION_TOKEN]

    suspend fun clearSessionToken() {
        dataStore.edit { it.remove(Keys.SESSION_TOKEN) }
    }

    suspend fun setLanguage(language: AppLanguage) {
        dataStore.edit { it[Keys.LANGUAGE] = language.name }
    }

    suspend fun setDarkMode(enabled: Boolean) {
        dataStore.edit { it[Keys.DARK_MODE] = enabled }
    }

    companion object {
        private val storesByPath = ConcurrentHashMap<String, DataStore<Preferences>>()

        private fun getOrCreateDataStore(context: Context): DataStore<Preferences> {
            val targetFile = context.preferencesDataStoreFile("one_home_pro_secure_prefs")
            val key = runCatching { targetFile.canonicalPath }.getOrElse { targetFile.absolutePath }
            return storesByPath[key] ?: synchronized(storesByPath) {
                storesByPath.getOrPut(key) {
                    PreferenceDataStoreFactory.create(
                        produceFile = { targetFile }
                    )
                }
            }
        }
    }
}
