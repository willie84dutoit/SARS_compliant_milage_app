package com.mileagetracker.app

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.core.content.ContextCompat
import com.mileagetracker.app.service.TripTrackingForegroundService
import com.mileagetracker.app.service.notification.TripClassificationNotificationBuilder
import com.mileagetracker.app.ui.common.MileageTrackerTheme
import com.mileagetracker.app.ui.navigation.MileageTrackerNavHost
import com.mileagetracker.app.ui.navigation.PendingTripClassificationNavigationStore
import dagger.hilt.android.AndroidEntryPoint
import timber.log.Timber
import javax.inject.Inject

/**
 * Single-activity host (T-001 blueprint §1). All screens are Compose destinations in
 * [MileageTrackerNavHost].
 *
 * T-003: also the landing point for the trip-classification notification's `PendingIntent`
 * ([TripClassificationNotificationBuilder]). `android:launchMode="singleTop"` (manifest) means
 * Android delivers the notification's intent to:
 *  - [onCreate] when no `MainActivity` instance is alive (cold start, including from the lock
 *    screen — `android:showWhenLocked`/`turnScreenOn` on the manifest's activity entry wake the
 *    screen and show this Activity over the lock screen without requiring the user to unlock
 *    first; if the device policy disallows that, Android simply does not show the activity over
 *    the lock screen and the user sees it after unlocking instead — either way `onCreate` still
 *    runs and still extracts the tripId, satisfying the brief §5.2 fallback "must still open the
 *    app normally").
 *  - [onNewIntent] when `MainActivity` is already alive (`FLAG_ACTIVITY_SINGLE_TOP` reuses the
 *    live instance instead of creating a second one) — without this override the tripId would be
 *    silently dropped, since nothing else reads a new `Intent` delivered to a running singleTop
 *    Activity.
 *
 * Both paths funnel through [handleTripClassificationIntent], which writes into
 * [pendingTripClassificationNavigationStore] — the seam [MileageTrackerNavHost] observes to
 * perform the actual `navController.navigate(...)` call once Compose is composed. This Activity
 * never touches a `NavHostController` directly; see the store's class doc for why.
 */
@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject
    lateinit var pendingTripClassificationNavigationStore: PendingTripClassificationNavigationStore

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // T-003 / brief §5.2: programmatic equivalent of the manifest's showWhenLocked/turnScreenOn
        // attributes. minSdk is 29, so setShowWhenLocked/setTurnScreenOn (API 27+) are always
        // available — set unconditionally rather than guarded behind a Build.VERSION check. Some
        // OEM lock-screen/launcher skins are documented to honor the programmatic call more
        // reliably than the static manifest attribute alone, so both are set as defense-in-depth;
        // if the device's own policy still disallows showing over the lock screen, Android itself
        // falls back to requiring unlock first and then showing this Activity normally — which is
        // exactly the brief's required fallback, with no extra branching needed here.
        setShowWhenLocked(true)
        setTurnScreenOn(true)
        startTripTrackingServiceForDetection()
        handleTripClassificationIntent(intent)
        setContent {
            MileageTrackerTheme {
                MileageTrackerNavHost(pendingTripClassificationNavigationStore = pendingTripClassificationNavigationStore)
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleTripClassificationIntent(intent)
    }

    /**
     * Extracts `EXTRA_TRIP_ID` from an `ACTION_OPEN_TRIP_CLASSIFICATION` intent (the trip-
     * classification notification's `PendingIntent` target — see
     * [TripClassificationNotificationBuilder]) and hands it to
     * [pendingTripClassificationNavigationStore]. A no-op for any other intent (e.g. the plain
     * `MAIN`/`LAUNCHER` intent on a normal app open), so this is safe to call unconditionally from
     * both [onCreate] and [onNewIntent].
     */
    private fun handleTripClassificationIntent(intent: Intent?) {
        if (intent?.action != TripClassificationNotificationBuilder.ACTION_OPEN_TRIP_CLASSIFICATION) return
        val tripId = intent.getStringExtra(TripClassificationNotificationBuilder.EXTRA_TRIP_ID)
        if (tripId.isNullOrBlank()) {
            Timber.tag("MT-UI").e(
                "MainActivity: received ACTION_OPEN_TRIP_CLASSIFICATION with missing/blank EXTRA_TRIP_ID",
            )
            return
        }
        Timber.tag("MT-UI").i("MainActivity: trip-classification notification tapped tripId=%s", tripId)
        pendingTripClassificationNavigationStore.setPendingTripId(tripId)
    }

    /**
     * Auto-starts [TripTrackingForegroundService] with no action set on every app launch, so its
     * `onStartCommand` runs [com.mileagetracker.app.service.activityrecognition.ActivityRecognitionRegistrar.register]
     * and the automatic vehicle-entry detection pipeline (T-002) is actually listening, rather than
     * only starting once a trip has already begun manually via [TripTrackingForegroundService.ACTION_START_TRIP].
     *
     * Scoped deliberately to "user opens the app" for this chunk — per the logged T-002 decision, a
     * boot-completed receiver and a Settings always-on/sleeping-wake-on-detection redesign are both
     * explicitly deferred, not built here.
     *
     * Not gated on any permission: [TripTrackingForegroundService.onStartCommand] only re-runs the
     * no-op-safe recovery check and re-registers the (idempotent) ActivityRecognition transition
     * request when no action is supplied — it does not require ACTIVITY_RECOGNITION or location
     * permission to start safely, and its own GPS path is independently gated on
     * `hasFineLocationPermission()`.
     */
    private fun startTripTrackingServiceForDetection() {
        Timber.tag("MT-Service").i("Auto-starting detection service from app launch")
        val detectionIntent = Intent(this, TripTrackingForegroundService::class.java)
        ContextCompat.startForegroundService(this, detectionIntent)
    }
}
