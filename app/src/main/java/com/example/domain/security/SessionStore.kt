package com.example.domain.security

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.core.handlers.ReplaceFileCorruptionHandler
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.preferencesDataStoreFile
import com.example.localization.AppLanguage
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import java.util.concurrent.ConcurrentHashMap

class SessionStore(
    context: Context,
    private val storeName: String = "one_home_pro_secure_prefs"
) {
    private val appContext: Context = context.applicationContext ?: context
    private val dataStore: DataStore<Preferences> = getOrCreateDataStore(appContext, storeName)

    private object Keys {
        val SESSION_TOKEN = stringPreferencesKey("session_token")
        val SERVER_SESSION_ID = stringPreferencesKey("server_session_id")
        val DEVICE_ID = stringPreferencesKey("device_id")
        val LANGUAGE = stringPreferencesKey("app_language")
        val DARK_MODE = booleanPreferencesKey("dark_mode")
        val SA_FAIL_COUNT = intPreferencesKey("sa_auth_fail_count")
        val SA_LOCK_UNTIL = longPreferencesKey("sa_auth_lock_until_epoch")
        val REVOKED_DEVICE_TAG = stringPreferencesKey("revoked_device_tag")
        val REVOKED_DEVICE_REASON = stringPreferencesKey("revoked_device_reason")
    }

    val languageFlow: Flow<AppLanguage> = dataStore.data
        .catch { emit(emptyPreferences()) }
        .map { prefs ->
            val code = prefs[Keys.LANGUAGE] ?: AppLanguage.EN.name
            AppLanguage.entries.firstOrNull { it.name == code } ?: AppLanguage.EN
        }

    val darkModeFlow: Flow<Boolean> = dataStore.data
        .catch { emit(emptyPreferences()) }
        .map { prefs ->
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

    suspend fun saveServerSessionId(serverSessionId: String) {
        dataStore.edit { it[Keys.SERVER_SESSION_ID] = serverSessionId }
    }

    suspend fun getServerSessionId(): String? =
        dataStore.data.first()[Keys.SERVER_SESSION_ID]

    suspend fun clearServerSessionId() {
        dataStore.edit { it.remove(Keys.SERVER_SESSION_ID) }
    }

    suspend fun clearSessionToken() {
        dataStore.edit {
            it.remove(Keys.SESSION_TOKEN)
            it.remove(Keys.SERVER_SESSION_ID)
        }
    }

    suspend fun setLanguage(language: AppLanguage) {
        dataStore.edit { it[Keys.LANGUAGE] = language.name }
    }

    suspend fun setDarkMode(enabled: Boolean) {
        dataStore.edit { it[Keys.DARK_MODE] = enabled }
    }

    suspend fun getSuperAdminLockoutRemainingMs(nowEpoch: Long = System.currentTimeMillis()): Long {
        val lockUntil = dataStore.data.first()[Keys.SA_LOCK_UNTIL] ?: 0L
        return if (lockUntil > nowEpoch) lockUntil - nowEpoch else 0L
    }

    suspend fun recordSuperAdminAuthFailure(nowEpoch: Long = System.currentTimeMillis()): Pair<Int, Long?> {
        var failCount = 0
        var lockUntil: Long? = null
        dataStore.edit { prefs ->
            val currentUntil = prefs[Keys.SA_LOCK_UNTIL] ?: 0L
            val nextFails = (prefs[Keys.SA_FAIL_COUNT] ?: 0) + 1
            failCount = nextFails
            prefs[Keys.SA_FAIL_COUNT] = nextFails
            if (nextFails >= 5 || currentUntil > nowEpoch) {
                val multiplier = (nextFails - 4).coerceIn(1, 6)
                val durationMs = 5L * 60_000L * multiplier
                val until = nowEpoch + durationMs
                lockUntil = until
                prefs[Keys.SA_LOCK_UNTIL] = until
            }
        }
        return failCount to lockUntil
    }

    suspend fun resetSuperAdminAuthFailures() {
        dataStore.edit {
            it.remove(Keys.SA_FAIL_COUNT)
            it.remove(Keys.SA_LOCK_UNTIL)
        }
    }

    suspend fun cacheRevokedDeviceStatus(uid: String, deviceId: String, revoked: Boolean, reason: String? = null) {
        val tag = "${uid}_${deviceId}"
        dataStore.edit { prefs ->
            if (revoked) {
                prefs[Keys.REVOKED_DEVICE_TAG] = tag
                if (!reason.isNullOrBlank()) {
                    prefs[Keys.REVOKED_DEVICE_REASON] = reason.take(250)
                }
            } else if (prefs[Keys.REVOKED_DEVICE_TAG] == tag) {
                prefs.remove(Keys.REVOKED_DEVICE_TAG)
                prefs.remove(Keys.REVOKED_DEVICE_REASON)
            }
        }
    }

    suspend fun getCachedRevokedDeviceReason(uid: String, deviceId: String): String? {
        val prefs = dataStore.data.first()
        val tag = "${uid}_${deviceId}"
        return if (prefs[Keys.REVOKED_DEVICE_TAG] == tag) {
            prefs[Keys.REVOKED_DEVICE_REASON] ?: "Device previously marked LOST/REVOKED."
        } else {
            null
        }
    }

    companion object {
        private val storesByPath = ConcurrentHashMap<String, DataStore<Preferences>>()

        private fun getOrCreateDataStore(context: Context, storeName: String): DataStore<Preferences> {
            val targetFile = context.preferencesDataStoreFile(storeName)
            val key = runCatching { targetFile.canonicalPath }.getOrElse { targetFile.absolutePath }
            return storesByPath[key] ?: synchronized(storesByPath) {
                storesByPath.getOrPut(key) {
                    PreferenceDataStoreFactory.create(
                        corruptionHandler = ReplaceFileCorruptionHandler { emptyPreferences() },
                        produceFile = { targetFile }
                    )
                }
            }
        }
    }
}
