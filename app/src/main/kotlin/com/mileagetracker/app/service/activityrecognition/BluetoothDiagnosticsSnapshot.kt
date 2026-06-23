package com.mileagetracker.app.service.activityrecognition

/**
 * T-020 — debug-only diagnostic instrument. NOT a detection trigger, NOT user-facing.
 *
 * Reports the current Bluetooth connection state so it can be appended to the existing
 * `MT-ActivityRecognition` log lines (confidence readings, confident-entry / retry-exhausted
 * outcomes). Purpose: the user reads their own debug log after a real drive and cross-references
 * "detection confidence X%, Bluetooth connected Y" against their own memory of whether they were
 * actually driving, to manually tighten the locked 70% start-confidence threshold over time. See
 * `team/TASKS.md` T-020 and the `[2026-06-22 (close)] NOTE` entry in `team/LOGS.md` for full
 * context, and T-002.5 in `team/TASKS.md` for the larger, explicitly-deferred vehicle-profile idea
 * this is intentionally NOT building.
 *
 * Two implementations, swapped per Gradle build type so the feature has zero footprint in
 * release builds (field testers must never see the `BLUETOOTH_CONNECT` permission request or any
 * Bluetooth-related log line):
 * - `app/src/debug/.../BluetoothDiagnosticsSnapshotImpl.kt` — tracks real ACL connect/disconnect
 *   state via a dynamically-registered receiver.
 * - `app/src/release/.../BluetoothDiagnosticsSnapshotImpl.kt` — trivial no-op, always reports
 *   not-connected, no receiver, no permission check.
 *
 * Both implementations share this same interface name/package so callers in `src/main` (here,
 * [com.mileagetracker.app.service.activityrecognition.ConfidenceAcquisitionWindowImpl]) compile
 * against one contract without knowing which variant is active.
 */
interface BluetoothDiagnosticsSnapshot {

    /**
     * Returns the Bluetooth connection state at the moment of the call. Must never throw and
     * must never block — this is called from a hot logging path (every confidence reading).
     */
    fun currentState(): BluetoothDiagnosticsState
}

/**
 * @property isConnected true if any Bluetooth device is currently ACL-connected.
 * @property connectedDeviceLabel the connected device's name (or address as a fallback), or
 *   null if not connected, the device name isn't available, or (debug build, permission not
 *   granted) the name cannot be read. Never throws on missing permission — callers can log this
 *   as-is.
 */
data class BluetoothDiagnosticsState(
    val isConnected: Boolean,
    val connectedDeviceLabel: String?,
)
