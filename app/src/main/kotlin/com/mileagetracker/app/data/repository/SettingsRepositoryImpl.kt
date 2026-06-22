package com.mileagetracker.app.data.repository

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import com.mileagetracker.app.domain.model.PhotoRetentionMode
import com.mileagetracker.app.domain.repository.SettingsRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import javax.inject.Inject

/**
 * DataStore-backed implementation of [SettingsRepository] (T-001 blueprint §3 `SettingsModule`).
 * Also hosts the T-008 `chainTailHash` rolling cache — a derived, rebuildable value reconciled
 * from Room on every cold start, never the durability anchor itself.
 */
class SettingsRepositoryImpl @Inject constructor(
    private val settingsDataStore: DataStore<Preferences>,
) : SettingsRepository {

    private object PreferenceKeys {
        val PHOTO_RETENTION_MODE = stringPreferencesKey("photo_retention_mode")
        val HAS_COMPLETED_FIRST_RUN_SETUP = booleanPreferencesKey("has_completed_first_run_setup")
        val BLUETOOTH_VEHICLE_TRIGGER_ENABLED = booleanPreferencesKey("bluetooth_vehicle_trigger_enabled")
        val CHAIN_TAIL_HASH = stringPreferencesKey("chain_tail_hash")
    }

    override fun observePhotoRetentionMode(): Flow<PhotoRetentionMode> {
        return settingsDataStore.data.map { preferences ->
            // Brief §5.3/§5.9: default ON (SAVED) unless the user has explicitly turned it off.
            when (preferences[PreferenceKeys.PHOTO_RETENTION_MODE]) {
                "temporary" -> PhotoRetentionMode.TEMPORARY
                else -> PhotoRetentionMode.SAVED
            }
        }
    }

    override suspend fun setPhotoRetentionMode(mode: PhotoRetentionMode) {
        settingsDataStore.edit { preferences ->
            preferences[PreferenceKeys.PHOTO_RETENTION_MODE] = when (mode) {
                PhotoRetentionMode.SAVED -> "saved"
                PhotoRetentionMode.TEMPORARY -> "temporary"
            }
        }
    }

    override fun observeHasCompletedFirstRunSetup(): Flow<Boolean> {
        return settingsDataStore.data.map { preferences ->
            preferences[PreferenceKeys.HAS_COMPLETED_FIRST_RUN_SETUP] ?: false
        }
    }

    override suspend fun setHasCompletedFirstRunSetup(completed: Boolean) {
        settingsDataStore.edit { preferences ->
            preferences[PreferenceKeys.HAS_COMPLETED_FIRST_RUN_SETUP] = completed
        }
    }

    override fun observeBluetoothVehicleTriggerEnabled(): Flow<Boolean> {
        return settingsDataStore.data.map { preferences ->
            // Brief §5.1: off by default.
            preferences[PreferenceKeys.BLUETOOTH_VEHICLE_TRIGGER_ENABLED] ?: false
        }
    }

    override suspend fun setBluetoothVehicleTriggerEnabled(enabled: Boolean) {
        settingsDataStore.edit { preferences ->
            preferences[PreferenceKeys.BLUETOOTH_VEHICLE_TRIGGER_ENABLED] = enabled
        }
    }

    override suspend fun getChainTailHash(): String? {
        return settingsDataStore.data.first()[PreferenceKeys.CHAIN_TAIL_HASH]
    }

    override suspend fun setChainTailHash(hash: String) {
        settingsDataStore.edit { preferences ->
            preferences[PreferenceKeys.CHAIN_TAIL_HASH] = hash
        }
    }
}
