package com.mileagetracker.app.service

import android.Manifest
import android.app.Service
import android.content.Intent
import android.content.pm.PackageManager
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.Priority
import com.mileagetracker.app.R
import com.mileagetracker.app.domain.model.Trip
import com.mileagetracker.app.domain.model.TripClassification
import com.mileagetracker.app.domain.model.TripStatus
import com.mileagetracker.app.domain.repository.SettingsRepository
import com.mileagetracker.app.domain.repository.TripRepository
import com.mileagetracker.app.domain.statemachine.TripLifecycleStateMachine
import com.mileagetracker.app.domain.statemachine.TripStartEvent
import com.mileagetracker.app.domain.statemachine.TripStopEvent
import com.mileagetracker.app.service.activityrecognition.ActivityRecognitionRegistrar
import com.mileagetracker.app.service.location.TripLocationCallback
import com.mileagetracker.app.service.notification.TripAlertNotificationChannel
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import timber.log.Timber
import java.util.UUID
import javax.inject.Inject

/**
 * The one foreground service (T-001 blueprint §1/§6). Owns the platform plumbing for trip
 * tracking end to end — GPS subscription, Haversine distance accumulation via
 * [TripLocationCallback], notification, and the single `insertNewActiveTrip` call site for the
 * no-duplicate-trips guarantee (blueprint §2 "Recovery requirement" step 1) — but every
 * lifecycle *decision* (what transient phase a start event produces, what [TripStatus] a stop
 * event lands on) is delegated to [tripLifecycleStateMachine]. This service must never re-encode
 * a threshold or a transition rule itself; if a decision is missing from the state machine's
 * public API, the state machine's API is extended, not duplicated here (per the T-001 rewire
 * directive).
 *
 * Automatic ActivityRecognition-triggered start (T-002) is registered but not yet wired to feed
 * [TripStartEvent.ConfidentVehicleEntry] into [tripLifecycleStateMachine] — that lands once the
 * confidence-window logic (T-002.4) is implemented. For this MVP build the trip lifecycle is
 * driven by explicit [ACTION_START_TRIP]/[ACTION_STOP_TRIP] intents from
 * [com.mileagetracker.app.ui.home.HomeStatusViewModel], which the service maps to
 * [TripStartEvent.ManualStart] / [TripStopEvent.ManualStop] before asking the state machine what
 * happens next.
 */
@AndroidEntryPoint
class TripTrackingForegroundService : Service(), TripLocationCallback.Listener {

    @Inject
    lateinit var tripRepository: TripRepository

    @Inject
    lateinit var settingsRepository: SettingsRepository

    @Inject
    lateinit var activityRecognitionRegistrar: ActivityRecognitionRegistrar

    @Inject
    lateinit var fusedLocationProviderClient: FusedLocationProviderClient

    @Inject
    lateinit var tripAlertNotificationChannel: TripAlertNotificationChannel

    @Inject
    lateinit var tripLifecycleStateMachine: TripLifecycleStateMachine

    private val serviceJob: Job = SupervisorJob()
    private val serviceScope = CoroutineScope(serviceJob)

    private val tripLocationCallback = TripLocationCallback(listener = this)

    /** In-memory state for the currently active trip; null whenever no trip is being tracked. */
    private var activeTripId: String? = null
    private var accumulatedDistanceMeters: Double = 0.0

    /**
     * The transient pre-trip phase, per [TripLifecycleStateMachine.TransientPhase]. Owned here
     * (not in the state machine, which is intentionally stateless/pure — see its class doc) so
     * the service has somewhere to keep "where are we in the start sequence" between events. The
     * state machine is consulted for every transition; this field only ever holds the value it
     * last returned.
     */
    private var transientPhase: TripLifecycleStateMachine.TransientPhase =
        TripLifecycleStateMachine.TransientPhase.NoTrip

    private var inactivityTimerJob: Job? = null
    private var unstableSignalTimerJob: Job? = null

    /**
     * Serializes every trip-lifecycle mutation (the recovery check, start, and stop) through one
     * critical section at a time. Without this, a STOP intent's `onStartCommand` call and its own
     * recovery-check coroutine can interleave — the recovery check reads the trip as still ACTIVE
     * (the status write to PENDING_OCR hasn't committed yet) and calls [resumeTrackingExistingTrip],
     * re-registering location updates and stop timers on a trip that is simultaneously being
     * stopped. That race is what silently resurrected a trip after Stop in MVP integration testing
     * — every `onStartCommand` must run its recovery check and its requested action as one
     * atomic sequence, never as two independently-scheduled coroutines.
     */
    private val tripLifecycleMutex = Mutex()

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startForeground(FOREGROUND_NOTIFICATION_ID, buildPersistentNotification())

        val requestedAction = intent?.action
        serviceScope.launch {
            // Top-level boundary (T-018): nothing below this point may crash the service
            // silently. CancellationException must always propagate (structured-concurrency
            // contract — cancelling this coroutine, e.g. on service teardown, must not be
            // swallowed); every other exception is logged under MT-Service and the service
            // keeps running rather than dying mid-trip.
            try {
                tripLifecycleMutex.withLock {
                    // Blueprint §2 step 1: this check MUST happen before any new trip is allowed
                    // to start, and (per the race fixed above) before this same call's requested
                    // action runs — never concurrently with it.
                    val inProgressTrip = tripRepository.getInProgressTrip()
                    if (inProgressTrip != null && inProgressTrip.status == TripStatus.ACTIVE) {
                        resumeTrackingExistingTrip(inProgressTrip)
                    }

                    when (requestedAction) {
                        ACTION_START_TRIP -> handleStartTripRequested()
                        ACTION_STOP_TRIP -> handleStopTripRequested()
                    }
                }
            } catch (cancellationException: CancellationException) {
                throw cancellationException
            } catch (unexpectedException: Exception) {
                Timber.tag("MT-Service").e(
                    unexpectedException,
                    "onStartCommand failed for action=%s",
                    requestedAction,
                )
            }
        }

        activityRecognitionRegistrar.register()

        return START_STICKY
    }

    override fun onDestroy() {
        super.onDestroy()
        runCatching {
            fusedLocationProviderClient.removeLocationUpdates(tripLocationCallback)
        }.onFailure { removeLocationUpdatesException ->
            Timber.tag("MT-Service").e(removeLocationUpdatesException, "onDestroy step failed: removeLocationUpdates")
        }
        runCatching {
            activityRecognitionRegistrar.unregister()
        }.onFailure { unregisterException ->
            Timber.tag("MT-Service").e(unregisterException, "onDestroy step failed: activityRecognitionRegistrar.unregister")
        }
        runCatching {
            inactivityTimerJob?.cancel()
        }.onFailure { inactivityCancelException ->
            Timber.tag("MT-Service").e(inactivityCancelException, "onDestroy step failed: inactivityTimerJob.cancel")
        }
        runCatching {
            unstableSignalTimerJob?.cancel()
        }.onFailure { unstableCancelException ->
            Timber.tag("MT-Service").e(unstableCancelException, "onDestroy step failed: unstableSignalTimerJob.cancel")
        }
        runCatching {
            serviceJob.cancel()
        }.onFailure { serviceJobCancelException ->
            Timber.tag("MT-Service").e(serviceJobCancelException, "onDestroy step failed: serviceJob.cancel")
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    // --- Trip start -----------------------------------------------------------------------

    /** Caller must hold [tripLifecycleMutex]. */
    private suspend fun handleStartTripRequested() {
        // Re-check here too: a START intent arriving while a trip is already active must be a
        // no-op, not a second insert (defends against a double-tap on the Start button — now
        // additionally guarded against racing the onStartCommand recovery check by the mutex).
        if (activeTripId != null) return
        if (tripRepository.getInProgressTrip() != null) return

        val startedAtEpochMillis = System.currentTimeMillis()

        // Ask the state machine, not the service, what a manual start produces. Per blueprint §4
        // a manual start always resolves to PromptPending (confidencePercent = 100) — the service
        // never re-derives that transition itself.
        transientPhase = tripLifecycleStateMachine.onStartEvent(
            currentPhase = transientPhase,
            event = TripStartEvent.ManualStart(startedAtEpochMillis = startedAtEpochMillis),
        )

        val tripId = UUID.randomUUID().toString()
        val photoRetention = settingsRepository.observePhotoRetentionMode().first()

        // Blueprint §4: insertTrip() happens at the exact moment PromptPending resolves into an
        // active trip — the manual-start path has no real notification-render delay, so that
        // moment is "now," but the state machine (not this service) is still the single source
        // of truth for which TripStatus that resolution lands on.
        val resolvedStatus = tripLifecycleStateMachine.resolvePromptPendingIntoActiveTrip(
            tripId = tripId,
            startedAtEpochMillis = startedAtEpochMillis,
        )
        transientPhase = TripLifecycleStateMachine.TransientPhase.NoTrip

        activeTripId = tripId
        accumulatedDistanceMeters = 0.0
        tripLocationCallback.reset()

        tripRepository.insertNewActiveTrip(
            Trip(
                id = tripId,
                classification = TripClassification.PRIVATE,
                startTimestamp = startedAtEpochMillis,
                endTimestamp = startedAtEpochMillis,
                startOdometerKm = 0.0,
                endOdometerKm = 0.0,
                verifiedOdometerKm = null,
                distanceKm = 0.0,
                businessReason = null,
                startLatitude = null,
                startLongitude = null,
                endLatitude = null,
                endLongitude = null,
                status = resolvedStatus,
                photoRetention = photoRetention,
                createdAt = startedAtEpochMillis,
                updatedAt = startedAtEpochMillis,
                signatureBase64 = null,
                signingKeyId = null,
            ),
        )

        startLocationUpdates()
        resetUnstableSignalTimer()
        resetInactivityTimer()
    }

    /** Caller must hold [tripLifecycleMutex]. */
    private suspend fun resumeTrackingExistingTrip(inProgressTrip: Trip) {
        if (activeTripId == inProgressTrip.id) return // already tracking it in this process

        activeTripId = inProgressTrip.id
        accumulatedDistanceMeters = inProgressTrip.distanceKm * METERS_PER_KILOMETER
        tripLocationCallback.reset()

        startLocationUpdates()
        resetUnstableSignalTimer()
        resetInactivityTimer()
    }

    private fun startLocationUpdates() {
        if (!hasFineLocationPermission()) return

        val locationRequest = LocationRequest.Builder(LOCATION_UPDATE_INTERVAL_MILLIS)
            .setPriority(Priority.PRIORITY_HIGH_ACCURACY)
            .setMinUpdateDistanceMeters(GPS_PROVIDER_DISTANCE_FILTER_METERS)
            .build()

        fusedLocationProviderClient.requestLocationUpdates(
            locationRequest,
            tripLocationCallback,
            mainLooper,
        )
    }

    private fun hasFineLocationPermission(): Boolean {
        return ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) ==
            PackageManager.PERMISSION_GRANTED
    }

    // --- TripLocationCallback.Listener -----------------------------------------------------

    override fun onAnyLocationCallbackReceived() {
        resetUnstableSignalTimer()
    }

    override fun onFirstFixAcquired(latitude: Double, longitude: Double) {
        val tripId = activeTripId ?: return
        serviceScope.launch {
            tripRepository.updateStartLocationIfUnset(tripId, latitude, longitude)
        }
    }

    override fun onAcceptedMovement(deltaMeters: Double, latestLatitude: Double, latestLongitude: Double) {
        accumulatedDistanceMeters += deltaMeters
        resetInactivityTimer()

        val tripId = activeTripId ?: return
        serviceScope.launch {
            tripRepository.updateDistanceKm(tripId, accumulatedDistanceMeters / METERS_PER_KILOMETER)
            tripRepository.updateEndLocation(tripId, latestLatitude, latestLongitude)
        }
    }

    // --- Stop timers (T-004.4, locked thresholds) ------------------------------------------

    private fun resetInactivityTimer() {
        inactivityTimerJob?.cancel()
        inactivityTimerJob = serviceScope.launch {
            delay(CONFIRMED_INACTIVITY_TIMEOUT_MILLIS)
            tripLifecycleMutex.withLock {
                handleStopEvent(TripStopEvent.ConfirmedInactivity(inactiveSinceEpochMillis = System.currentTimeMillis()))
            }
        }
    }

    private fun resetUnstableSignalTimer() {
        unstableSignalTimerJob?.cancel()
        unstableSignalTimerJob = serviceScope.launch {
            delay(UNSTABLE_SIGNAL_TIMEOUT_MILLIS)
            tripLifecycleMutex.withLock {
                handleStopEvent(TripStopEvent.UnstableSignalTimeout(signalLostSinceEpochMillis = System.currentTimeMillis()))
            }
        }
    }

    // --- Trip stop ------------------------------------------------------------------------

    /** Caller must hold [tripLifecycleMutex]. */
    private suspend fun handleStopTripRequested() {
        handleStopEvent(TripStopEvent.ManualStop(stoppedAtEpochMillis = System.currentTimeMillis()))
    }

    private suspend fun handleStopEvent(event: TripStopEvent) {
        val tripId = activeTripId ?: return

        // CRITICAL: handleStopEvent can itself be running *inside* inactivityTimerJob or
        // unstableSignalTimerJob (both schedule a delayed call back into this function). Calling
        // `.cancel()` on the job that is currently executing this very coroutine throws
        // CancellationException at the next suspension point, aborting the Room writes and
        // stopSelf() below before they run — the trip never leaves ACTIVE in that case. Null out
        // both references first and only cancel a job that isn't the one currently running.
        val currentJob = currentCoroutineContext()[Job]
        val inactivityJobToCancel = inactivityTimerJob
        val unstableSignalJobToCancel = unstableSignalTimerJob
        inactivityTimerJob = null
        unstableSignalTimerJob = null
        if (inactivityJobToCancel !== currentJob) inactivityJobToCancel?.cancel()
        if (unstableSignalJobToCancel !== currentJob) unstableSignalJobToCancel?.cancel()

        fusedLocationProviderClient.removeLocationUpdates(tripLocationCallback)

        // Blueprint §4 "completion is two-part": the state machine — not this service — decides
        // what status a stop event lands on. Today that is always PENDING_OCR regardless of
        // which of the three [event] variants fired (the classification/odometer screens resolve
        // PENDING_OCR -> COMPLETED or PENDING_BUSINESS_REASON), but the service must keep asking
        // rather than re-encoding that landing state as a literal.
        val resolvedStatus = tripLifecycleStateMachine.onStopEvent(event)
        tripRepository.updateStatus(tripId, resolvedStatus)
        tripRepository.updateEndTimestamp(tripId, endTimestampEpochMillis = System.currentTimeMillis())

        activeTripId = null
        accumulatedDistanceMeters = 0.0
        tripLocationCallback.reset()

        stopSelf()
    }

    private fun buildPersistentNotification() =
        NotificationCompat.Builder(this, TripAlertNotificationChannel.CHANNEL_ID)
            .setContentTitle("Mileage Tracker is running")
            .setContentText(if (activeTripId != null) "Tracking a trip" else "Watching for trip activity")
            .setSmallIcon(R.drawable.ic_notification_trip)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setOngoing(true)
            .build()

    companion object {
        const val FOREGROUND_NOTIFICATION_ID = 2001
        const val ACTION_START_TRIP = "com.mileagetracker.app.action.START_TRIP"
        const val ACTION_STOP_TRIP = "com.mileagetracker.app.action.STOP_TRIP"

        private const val METERS_PER_KILOMETER = 1_000.0
        private const val LOCATION_UPDATE_INTERVAL_MILLIS = 5_000L

        /** T-004.1 locked value. */
        private const val GPS_PROVIDER_DISTANCE_FILTER_METERS = 10.0f

        /** T-004.4 locked value: 3 minutes. */
        private const val CONFIRMED_INACTIVITY_TIMEOUT_MILLIS = 3 * 60 * 1_000L

        /** T-004.4 locked value: 2 minutes. */
        private const val UNSTABLE_SIGNAL_TIMEOUT_MILLIS = 2 * 60 * 1_000L
    }
}
