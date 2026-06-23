package com.mileagetracker.app.domain.repository

import com.mileagetracker.app.domain.model.PhotoRetentionMode
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * Hand-written fake for [SettingsRepository] — this project uses hand-written fakes only, no
 * mocking framework. Backed by in-memory [MutableStateFlow]s so a test can both observe and
 * directly mutate state (`fineLocationFlow.value = ...`) without going through the suspend
 * setters when convenient.
 */
class FakeSettingsRepository(
    initialPhotoRetentionMode: PhotoRetentionMode = PhotoRetentionMode.SAVED,
    initialHasCompletedFirstRunSetup: Boolean = false,
    initialBluetoothVehicleTriggerEnabled: Boolean = false,
) : SettingsRepository {

    val photoRetentionModeFlow = MutableStateFlow(initialPhotoRetentionMode)
    val hasCompletedFirstRunSetupFlow = MutableStateFlow(initialHasCompletedFirstRunSetup)
    val bluetoothVehicleTriggerEnabledFlow = MutableStateFlow(initialBluetoothVehicleTriggerEnabled)
    var chainTailHash: String? = null

    override fun observePhotoRetentionMode(): StateFlow<PhotoRetentionMode> = photoRetentionModeFlow

    override suspend fun setPhotoRetentionMode(mode: PhotoRetentionMode) {
        photoRetentionModeFlow.value = mode
    }

    override fun observeHasCompletedFirstRunSetup(): StateFlow<Boolean> = hasCompletedFirstRunSetupFlow

    override suspend fun setHasCompletedFirstRunSetup(completed: Boolean) {
        hasCompletedFirstRunSetupFlow.value = completed
    }

    override fun observeBluetoothVehicleTriggerEnabled(): StateFlow<Boolean> = bluetoothVehicleTriggerEnabledFlow

    override suspend fun setBluetoothVehicleTriggerEnabled(enabled: Boolean) {
        bluetoothVehicleTriggerEnabledFlow.value = enabled
    }

    override suspend fun getChainTailHash(): String? = chainTailHash

    override suspend fun setChainTailHash(hash: String) {
        chainTailHash = hash
    }
}
