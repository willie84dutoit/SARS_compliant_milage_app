package com.mileagetracker.app.service.activityrecognition

import com.mileagetracker.app.domain.statemachine.TripStartEvent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject

/** Locked v1 value (brief §5.9 / blueprint §4): the 30s silent-retry confidence window. */
internal const val CONFIDENCE_WINDOW_TIMEOUT_MILLIS = 30_000L

/** Locked v1 value: start confidence threshold. */
internal const val START_CONFIDENCE_THRESHOLD_PERCENT = 70

/**
 * Production implementation of [ConfidenceAcquisitionWindow] (T-002.4, per
 * `team/blueprints/T-002-vehicle-detection-spec.md` §4).
 *
 * Implements [VehicleEntryConfidenceGateway] directly so this class is the production binding for
 * both seams: [onVehicleEntryDetected] is a thin delegate to [startWindow], exactly as
 * [VehicleEntryConfidenceGateway]'s doc comment anticipated.
 *
 * State ownership: [maxConfidenceSeenInWindow] tracks the RUNNING MAXIMUM across the window's
 * readings, never the latest reading — a single high reading (e.g. a brief highway-speed spike)
 * must count even if a lower reading is delivered after it. Do not "simplify" this to `= confidence`.
 */
class ConfidenceAcquisitionWindowImpl @Inject constructor(
    private val activityUpdatesRegistrar: ActivityUpdatesRegistrar,
    private val coroutineScope: CoroutineScope,
    private val bluetoothDiagnosticsSnapshot: BluetoothDiagnosticsSnapshot,
) : ConfidenceAcquisitionWindow, VehicleEntryConfidenceGateway {

    // P0.2: Both fields are mutated from the Android main thread (broadcast-receiver callbacks
    // call startWindow / onConfidenceReading / cancel, which are non-suspend and therefore cannot
    // use withLock) AND read from the Dispatchers.Default-backed timeout coroutine that calls
    // fireRetryExhausted. @Volatile gives the cross-thread visibility guarantee that eliminates
    // the data race without requiring the callers to be suspend functions:
    //   - timeoutJob: single writer (startWindow / cancel / fireRetryExhausted, always one at a
    //     time on main), @Volatile ensures the Default-thread reader in fireRetryExhausted sees
    //     the latest reference.
    //   - maxConfidenceSeenInWindow: only onConfidenceReading (main thread) performs the
    //     read-modify-write via maxOf(); fireRetryExhausted (Default thread) only reads it once.
    //     Because the write side is single-threaded (main), @Volatile is sufficient — there is no
    //     lost-update risk, only a visibility risk, which @Volatile resolves.
    // A Mutex is not used because withLock is suspend and cannot be called from the non-suspend
    // overrides. Dispatchers.Main.immediate was considered but rejected because it requires a main
    // dispatcher to be installed, which breaks plain-JVM unit tests that use TestScope.
    @Volatile private var timeoutJob: Job? = null
    @Volatile private var maxConfidenceSeenInWindow: Int = 0
    private val resultFlow = MutableSharedFlow<TripStartEvent>(extraBufferCapacity = 1)

    override fun onVehicleEntryDetected(enteredAtEpochMillis: Long) {
        startWindow(enteredAtEpochMillis)
    }

    override fun startWindow(enteredAtEpochMillis: Long) {
        if (timeoutJob?.isActive == true) {
            Timber.tag("MT-ActivityRecognition").d("startWindow called while a window is already active — no-op")
            return
        }

        maxConfidenceSeenInWindow = 0
        activityUpdatesRegistrar.register()
        Timber.tag("MT-ActivityRecognition").i("Confidence-acquisition window started at %d", enteredAtEpochMillis)

        timeoutJob = coroutineScope.launch {
            delay(CONFIDENCE_WINDOW_TIMEOUT_MILLIS)
            fireRetryExhausted()
        }
    }

    override fun onConfidenceReading(confidencePercent: Int) {
        if (timeoutJob?.isActive != true) {
            // No active window — a stray callback arrived after teardown/firing. Ignore it.
            return
        }

        maxConfidenceSeenInWindow = maxOf(maxConfidenceSeenInWindow, confidencePercent)
        Timber.tag("MT-ActivityRecognition").d(
            "Confidence reading %d (running max %d)%s",
            confidencePercent,
            maxConfidenceSeenInWindow,
            bluetoothSnapshotLogSuffix(),
        )

        if (maxConfidenceSeenInWindow >= START_CONFIDENCE_THRESHOLD_PERCENT) {
            fireConfidentEntry()
        }
    }

    override fun isWindowActive(): Boolean = timeoutJob?.isActive == true

    override fun cancel() {
        timeoutJob?.cancel()
        timeoutJob = null
        activityUpdatesRegistrar.unregister()
    }

    override fun observeResults(): Flow<TripStartEvent> = resultFlow

    private fun fireConfidentEntry() {
        timeoutJob?.cancel()
        timeoutJob = null
        activityUpdatesRegistrar.unregister()
        val confirmedConfidence = maxConfidenceSeenInWindow
        Timber.tag("MT-ActivityRecognition").i(
            "Confident vehicle entry at %d%% — window closed%s",
            confirmedConfidence,
            bluetoothSnapshotLogSuffix(),
        )
        resultFlow.tryEmit(
            TripStartEvent.ConfidentVehicleEntry(
                confidencePercent = confirmedConfidence,
                detectedAtEpochMillis = System.currentTimeMillis(),
            ),
        )
    }

    private fun fireRetryExhausted() {
        timeoutJob = null
        activityUpdatesRegistrar.unregister()
        val bestObservedConfidence = maxConfidenceSeenInWindow
        Timber.tag("MT-ActivityRecognition").i(
            "Confidence window timed out — best observed %d%%%s",
            bestObservedConfidence,
            bluetoothSnapshotLogSuffix(),
        )
        resultFlow.tryEmit(
            TripStartEvent.LowConfidenceRetryExhausted(
                lastObservedConfidencePercent = bestObservedConfidence,
            ),
        )
    }

    /**
     * T-020 — formats the current [BluetoothDiagnosticsSnapshot] state as a log suffix, e.g.
     * " | bluetoothConnected=true device=Pixel Buds" or " | bluetoothConnected=false". In a
     * release build [bluetoothDiagnosticsSnapshot] is always the no-op
     * (`isConnected=false, connectedDeviceLabel=null`), so this suffix is always
     * " | bluetoothConnected=false" there — harmless, diagnostic-only text with no behavioral
     * effect, not a leak of any debug-only capability (no permission is requested to produce it).
     */
    private fun bluetoothSnapshotLogSuffix(): String {
        val snapshot = bluetoothDiagnosticsSnapshot.currentState()
        return if (snapshot.isConnected) {
            " | bluetoothConnected=true device=${snapshot.connectedDeviceLabel}"
        } else {
            " | bluetoothConnected=false"
        }
    }
}
