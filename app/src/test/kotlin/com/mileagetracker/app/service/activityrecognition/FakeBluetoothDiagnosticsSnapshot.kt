package com.mileagetracker.app.service.activityrecognition

/**
 * Hand-written fake for [BluetoothDiagnosticsSnapshot] (T-020), per this project's no-mocking-
 * framework testing convention. Defaults to "not connected" — tests that don't care about the
 * Bluetooth diagnostic suffix can ignore this entirely; tests that do can call [setConnected].
 */
class FakeBluetoothDiagnosticsSnapshot : BluetoothDiagnosticsSnapshot {

    private var state = BluetoothDiagnosticsState(isConnected = false, connectedDeviceLabel = null)

    fun setConnected(deviceLabel: String) {
        state = BluetoothDiagnosticsState(isConnected = true, connectedDeviceLabel = deviceLabel)
    }

    override fun currentState(): BluetoothDiagnosticsState = state
}
