package com.mileagetracker.app.domain.repository

import com.mileagetracker.app.domain.model.PhotoRetentionMode
import kotlinx.coroutines.flow.Flow

/**
 * Domain-owned contract over the DataStore-backed settings (T-001 blueprint §3 `SettingsModule`).
 * Backs the Settings screen, the first-run-setup-completed flag (Setup/Permissions screen), and
 * the T-008 `chainTailHash` rolling cache used by the trip-signing mechanism.
 */
interface SettingsRepository {

    fun observePhotoRetentionMode(): Flow<PhotoRetentionMode>

    suspend fun setPhotoRetentionMode(mode: PhotoRetentionMode)

    fun observeHasCompletedFirstRunSetup(): Flow<Boolean>

    suspend fun setHasCompletedFirstRunSetup(completed: Boolean)

    fun observeBluetoothVehicleTriggerEnabled(): Flow<Boolean>

    suspend fun setBluetoothVehicleTriggerEnabled(enabled: Boolean)

    /** T-008's rolling chain-tail cache — a derived, rebuildable value, not the durability anchor. */
    suspend fun getChainTailHash(): String?

    suspend fun setChainTailHash(hash: String)
}
