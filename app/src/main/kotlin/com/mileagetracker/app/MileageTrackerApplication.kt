package com.mileagetracker.app

import android.app.Application
import com.mileagetracker.app.BuildConfig
import com.mileagetracker.app.data.di.ApplicationScope
import com.mileagetracker.app.data.logging.FileLoggingTree
import com.mileagetracker.app.data.signing.TripSigningOrchestrator
import com.mileagetracker.app.service.notification.TripAlertNotificationChannel
import dagger.hilt.android.HiltAndroidApp
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import timber.log.Timber
import java.io.File
import javax.inject.Inject

/**
 * Application entry point. Per T-001 build order step 5/6: notification channel creation is a
 * one-time `onCreate` call, done before any service that might post to it.
 *
 * T-018: [FileLoggingTree] is installed here, once, before anything else runs — every
 * `Timber.tag(...).log(...)` call anywhere in the app (service, OCR, export, repository) writes
 * to the on-device log file from the very first line of `onCreate` onward, so a field tester's
 * crash report always has a log file to attach, not just whatever happened to be in logcat.
 *
 * T-008 Chunk 4: [TripSigningOrchestrator.rebuildChainTailFromRoom] is launched here on the
 * [ApplicationScope] coroutine scope immediately after Timber/notification setup. This is the
 * earliest possible point at which the Hilt graph is available (injected fields are populated by
 * `super.onCreate()` via [HiltAndroidApp]) and is guaranteed to run before any ViewModel or
 * foreground service can read or write trip data. The self-heal is fire-and-forget from the
 * application's perspective — a failure is logged but never crashes the app.
 */
@HiltAndroidApp
class MileageTrackerApplication : Application() {

    @Inject
    lateinit var tripAlertNotificationChannel: TripAlertNotificationChannel

    @Inject
    lateinit var tripSigningOrchestrator: TripSigningOrchestrator

    @Inject
    @ApplicationScope
    lateinit var applicationScope: CoroutineScope

    override fun onCreate() {
        super.onCreate()
        // T-030 P0.4 Option A (debug-only): FileLoggingTree writes PII (business reason,
        // odometer value, photo path) to on-device storage. Gate to DEBUG builds only so a
        // Play Store release never writes that file. The full tree remains intact for field
        // testing with the debug APK.
        // PII-redaction / FileProvider-share deferred to a pre-Play-Store task (T-038 / T-030 P0.4 follow-up).
        if (BuildConfig.DEBUG) {
            Timber.plant(FileLoggingTree(logsDirectory = File(filesDir, "logs")))
        }
        tripAlertNotificationChannel.createChannel()
        launchChainTailSelfHeal()
    }

    /**
     * Launches the T-008 cold-start chain-tail self-heal on [applicationScope]. Any exception
     * (DataStore I/O failure, Room query failure) is logged and swallowed — the app must start
     * normally even if the tail cache cannot be rebuilt. The next signing call will use whatever
     * tail value is in DataStore at that point, which is still a valid (if potentially stale) chain
     * link — a full audit can still detect the inconsistency via the sequence-number gap.
     */
    private fun launchChainTailSelfHeal() {
        applicationScope.launch {
            try {
                tripSigningOrchestrator.rebuildChainTailFromRoom()
            } catch (cancellationException: CancellationException) {
                throw cancellationException
            } catch (selfHealException: Exception) {
                Timber.tag("MT-Trip").e(
                    selfHealException,
                    "launchChainTailSelfHeal: self-heal failed — chain tail may be stale",
                )
            }
        }
    }
}
