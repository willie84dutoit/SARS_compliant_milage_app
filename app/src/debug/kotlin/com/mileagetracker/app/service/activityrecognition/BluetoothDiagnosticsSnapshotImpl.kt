package com.mileagetracker.app.service.activityrecognition

import android.Manifest
import android.bluetooth.BluetoothDevice
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import androidx.core.content.ContextCompat
import dagger.hilt.android.qualifiers.ApplicationContext
import timber.log.Timber
import java.util.concurrent.atomic.AtomicReference
import javax.inject.Inject
import javax.inject.Singleton

/**
 * DEBUG-ONLY real implementation (T-020). Never compiled into a release build — see the matching
 * no-op in `app/src/release/.../BluetoothDiagnosticsSnapshotImpl.kt` and
 * [com.mileagetracker.app.service.di.BluetoothDiagnosticsModule] for how the two are swapped.
 *
 * Tracks ACL connect/disconnect state via a dynamically-registered [BroadcastReceiver] held by
 * this singleton (registered once, for the app's lifetime — mirrors this codebase's existing
 * receiver conventions in spirit, but deliberately dynamic rather than manifest-declared: a
 * manifest `<receiver>` for `ACL_CONNECTED`/`ACL_DISCONNECTED` would need its own
 * `AndroidManifest.xml` entry duplicated across debug/release, undermining the "doesn't exist at
 * all in release" goal; a dynamic registration confined to this debug-only class avoids that).
 *
 * Reading [BluetoothDevice.getName] on API 31+ requires `BLUETOOTH_CONNECT` at runtime. No in-app
 * permission-request flow is planned (diagnostic tool only) — if the permission is missing, this
 * degrades gracefully: reports connected=false/label=null and logs a single one-time warning via
 * [Timber] instead of spamming the log or crashing with a `SecurityException`. The user grants the
 * permission manually for their own test device (e.g. `adb shell pm grant ... BLUETOOTH_CONNECT`).
 */
@Singleton
class BluetoothDiagnosticsSnapshotImpl @Inject constructor(
    @ApplicationContext private val appContext: Context,
) : BluetoothDiagnosticsSnapshot {

    /** Holds the most recently connected device's label, or null if nothing is connected. */
    private val connectedDeviceLabel = AtomicReference<String?>(null)

    /** Ensures the "permission not granted" log line is emitted at most once, not per reading. */
    private var hasLoggedMissingPermission = false

    private val aclStateReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            when (intent.action) {
                BluetoothDevice.ACTION_ACL_CONNECTED -> {
                    val device = extractDeviceExtra(intent)
                    connectedDeviceLabel.set(resolveDeviceLabel(device))
                }

                BluetoothDevice.ACTION_ACL_DISCONNECTED -> {
                    connectedDeviceLabel.set(null)
                }
            }
        }
    }

    init {
        val filter = IntentFilter().apply {
            addAction(BluetoothDevice.ACTION_ACL_CONNECTED)
            addAction(BluetoothDevice.ACTION_ACL_DISCONNECTED)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            appContext.registerReceiver(aclStateReceiver, filter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            @Suppress("UnspecifiedRegisterReceiverFlag")
            appContext.registerReceiver(aclStateReceiver, filter)
        }
    }

    override fun currentState(): BluetoothDiagnosticsState {
        val label = connectedDeviceLabel.get()
        return BluetoothDiagnosticsState(
            isConnected = label != null,
            connectedDeviceLabel = label,
        )
    }

    @Suppress("DEPRECATION") // BluetoothDevice EXTRA_DEVICE is the documented API for this intent.
    private fun extractDeviceExtra(intent: Intent): BluetoothDevice? {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE, BluetoothDevice::class.java)
        } else {
            intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE)
        }
    }

    private fun resolveDeviceLabel(device: BluetoothDevice?): String {
        if (device == null) return "unknown device"

        val hasConnectPermission = Build.VERSION.SDK_INT < Build.VERSION_CODES.S ||
            ContextCompat.checkSelfPermission(appContext, Manifest.permission.BLUETOOTH_CONNECT) ==
            android.content.pm.PackageManager.PERMISSION_GRANTED

        if (!hasConnectPermission) {
            if (!hasLoggedMissingPermission) {
                hasLoggedMissingPermission = true
                Timber.tag("MT-Bluetooth")
                    .w("Bluetooth diagnostics unavailable — BLUETOOTH_CONNECT permission not granted")
            }
            return device.address
        }

        return try {
            device.name ?: device.address
        } catch (missingPermission: SecurityException) {
            // Defensive: permission could be revoked between the check above and this read.
            if (!hasLoggedMissingPermission) {
                hasLoggedMissingPermission = true
                Timber.tag("MT-Bluetooth")
                    .w(missingPermission, "Bluetooth diagnostics unavailable — permission revoked")
            }
            device.address
        }
    }
}
