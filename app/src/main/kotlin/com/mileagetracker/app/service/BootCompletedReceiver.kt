package com.mileagetracker.app.service

import android.Manifest
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat
import dagger.hilt.android.AndroidEntryPoint
import timber.log.Timber

/**
 * Re-arms automatic vehicle-entry detection after a device reboot (T-035).
 *
 * `RECEIVE_BOOT_COMPLETED` has been declared in the manifest since T-001, but until this receiver
 * existed nothing ever consumed the broadcast — after a reboot, [TripTrackingForegroundService]
 * was never restarted, so [com.mileagetracker.app.service.activityrecognition.ActivityRecognitionRegistrar]
 * never re-registered its transition request and automatic detection silently stopped until the
 * user next opened the app. This receiver closes that gap by performing the exact same
 * app-launch arming call as [com.mileagetracker.app.MainActivity.startTripTrackingServiceForDetection]
 * — start [TripTrackingForegroundService] with no action set, which makes its
 * `onStartCommand` (a) run the in-progress-trip recovery check (blueprint §2 — safe, idempotent,
 * never creates a duplicate trip) and (b) call `activityRecognitionRegistrar.register()`. No
 * parallel arming path is invented here; this receiver is purely a second trigger for the one
 * arming sequence the app already has.
 *
 * Degrade-never-block (standing project principle): if `ACCESS_FINE_LOCATION` is not granted at
 * boot time, [BootRearmDecision.shouldStartDetectionService] returns false and this receiver skips
 * the service start entirely — it does not crash, does not throw, and does not force any UI. The
 * user re-arms detection the next time they open the app (same recovery path as a fresh install
 * before Setup has run). No other permission is checked here because no other permission gates
 * `onStartCommand`'s foreground-service promotion (see [BootRearmDecision]'s class doc).
 *
 * `@AndroidEntryPoint` is applied even though this receiver currently injects nothing, matching
 * the established pattern for every other receiver in this package
 * ([com.mileagetracker.app.service.activityrecognition.ActivityTransitionReceiver],
 * [com.mileagetracker.app.service.activityrecognition.ConfidenceUpdateReceiver]) — consistent
 * Hilt wiring across all manifest-declared receivers, and a ready seam if this receiver ever needs
 * an injected dependency later.
 */
@AndroidEntryPoint
class BootCompletedReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) return

        val isFineLocationGranted = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.ACCESS_FINE_LOCATION,
        ) == PackageManager.PERMISSION_GRANTED

        if (!BootRearmDecision.shouldStartDetectionService(isFineLocationGranted)) {
            Timber.tag("MT-Service").w(
                "BootCompletedReceiver: skipping detection re-arm — ACCESS_FINE_LOCATION not " +
                    "granted. Detection will re-arm on next app open.",
            )
            return
        }

        Timber.tag("MT-Service").i("BootCompletedReceiver: re-arming detection service after reboot")
        val detectionIntent = Intent(context, TripTrackingForegroundService::class.java)
        ContextCompat.startForegroundService(context, detectionIntent)
    }
}
