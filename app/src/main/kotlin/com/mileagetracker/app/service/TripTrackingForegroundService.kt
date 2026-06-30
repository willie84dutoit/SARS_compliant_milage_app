package com.mileagetracker.app.service

import android.Manifest
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
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
import com.mileagetracker.app.service.activityrecognition.ConfidenceAcquisitionWindow
import com.mileagetracker.app.service.location.TripLocationCallback
import com.mileagetracker.app.service.notification.ClassificationPromptTimeoutScheduler
import com.mileagetracker.app.service.notification.TripAlertNotificationChannel
import com.mileagetracker.app.service.notification.TripClassificationNotificationBuilder
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collect
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
 * Automatic ActivityRecognition-triggered start (T-002) is wired end to end: this service collects
 * [confidenceAcquisitionWindow]'s [ConfidenceAcquisitionWindow.observeResults] flow (started once
 * in [onCreate]) and feeds each emitted [TripStartEvent.ConfidentVehicleEntry] /
 * [TripStartEvent.LowConfidenceRetryExhausted] into [tripLifecycleStateMachine] exactly the way the
 * manual-start path below does, sharing the same insert/notify tail ([completeTripStart]) and the
 * same [tripLifecycleMutex] discipline. Manual starts/stops continue to come from explicit
 * [ACTION_START_TRIP]/[ACTION_STOP_TRIP] intents sent by
 * [com.mileagetracker.app.ui.home.HomeStatusViewModel], which the service maps to
 * [TripStartEvent.ManualStart] / [TripStopEvent.ManualStop] before asking the state machine what
 * happens next.
 *
 * A third entry point exists alongside the two action intents above: [com.mileagetracker.app.MainActivity]
 * starts this service with **no action set** (a plain `Intent(context, TripTrackingForegroundService::class.java)`)
 * on every app launch, purely so [onStartCommand] runs and calls [activityRecognitionRegistrar]'s
 * `register()` even before any trip has ever been started manually — otherwise the automatic
 * detection pipeline above never gets a chance to fire. `requestedAction == null` falls through the
 * `when (requestedAction)` below as a deliberate no-op: the recovery check still runs (safe — it
 * only re-attaches to an already-ACTIVE trip) and `register()` still re-runs (safe — re-registering
 * the same PendingIntent is idempotent per [ActivityRecognitionRegistrar]'s own class doc).
 *
 * T-035: [com.mileagetracker.app.service.BootCompletedReceiver] is a second caller of this same
 * no-action start — after a device reboot it performs the identical
 * `Intent(context, TripTrackingForegroundService::class.java)` / `startForegroundService()` call
 * MainActivity makes on every app launch, so `onStartCommand` re-arms detection without the user
 * needing to reopen the app first. An always-on/sleeping-wake-on-detection redesign beyond that is
 * still deferred, not built here.
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
    lateinit var tripClassificationNotificationBuilder: TripClassificationNotificationBuilder

    @Inject
    lateinit var tripLifecycleStateMachine: TripLifecycleStateMachine

    @Inject
    lateinit var confidenceAcquisitionWindow: ConfidenceAcquisitionWindow

    /**
     * T-039 item 9: shared singleton (also injected into [com.mileagetracker.app.ui.classification.TripClassificationViewModel],
     * the same sharing pattern already used for [tripLifecycleStateMachine]) so the ViewModel can
     * cancel a trip's countdown the moment the user actually saves a classification, while this
     * service owns starting the countdown and reacting to its timeout.
     */
    @Inject
    lateinit var classificationPromptTimeoutScheduler: ClassificationPromptTimeoutScheduler

    private val serviceJob: Job = SupervisorJob()
    private val serviceScope = CoroutineScope(serviceJob)

    private val tripLocationCallback = TripLocationCallback(listener = this)

    /** In-memory state for the currently active trip; null whenever no trip is being tracked. */
    private var activeTripId: String? = null
    private var accumulatedDistanceMeters: Double = 0.0
    /** H-2 fix: mirrors [isManualStart] for the active trip so notifyTripAwaitingClassification can pass the correct title. */
    private var activeTripIsManualStart: Boolean = false

    /**
     * H-1/H-2 fix: records whether the pending trip start was triggered manually (ManualStart
     * intent) or automatically (ConfidentVehicleEntry/LowConfidenceRetryExhausted from
     * ActivityRecognition). Set in [handleStartTripRequested] / [handleAutomaticStartEvent] before
     * calling [completeTripStart], consumed and reset to false in [completeTripStart]. Defaults to
     * false (auto-detected) so the sentinel value is conservative — an unset field reads as
     * auto-detected, not manual.
     */
    private var pendingTripIsManualStart: Boolean = false

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

    override fun onCreate() {
        super.onCreate()
        // Started exactly once per service instance (onCreate runs once before any
        // onStartCommand), so this is a single, never-resubscribed collector for the lifetime of
        // the service — per T-002 spec §6, automatic start must not re-subscribe to this Flow on
        // every onStartCommand. The per-event try/catch lives inside handleAutomaticStartEvent
        // (not wrapped around this whole `collect`) so that one bad event is logged and skipped
        // rather than throwing out of `collect` and silently ending the subscription for every
        // subsequent ENTER event for the rest of the service's life.
        serviceScope.launch {
            confidenceAcquisitionWindow.observeResults().collect { startEvent ->
                handleAutomaticStartEvent(startEvent)
            }
        }

        // T-039 item 9: single, never-resubscribed collector (same shape/reasoning as the
        // confidenceAcquisitionWindow collector above) for the classification-prompt 30s timeout.
        // The per-event try/catch lives inside handlePromptTimeout, not wrapped around this whole
        // `collect`, so one bad timeout event is logged and skipped rather than silently ending
        // the subscription for the rest of the service's life.
        serviceScope.launch {
            classificationPromptTimeoutScheduler.observeTimeouts().collect { timedOutEvent ->
                handlePromptTimeout(timedOutEvent.tripId)
            }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // C-1 fix: always call startForeground() before any other logic (Android requires it within
        // 5 seconds of startForegroundService()). The primary caller — MainActivity — is now gated
        // on the location permission so we normally arrive here with the permission already granted.
        // As a safety net for stale pending intents or other callers, we catch SecurityException
        // (API 29–33, permission absent) and InvalidForegroundServiceTypeException (API 34+, same
        // root cause) and immediately stop the service if promotion fails. This satisfies the brief's
        // "graceful limited mode when permissions denied" without leaving a zombie foreground service.
        val fgsType = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION
        } else {
            0
        }
        try {
            ServiceCompat.startForeground(
                this,
                FOREGROUND_NOTIFICATION_ID,
                buildPersistentNotification(),
                fgsType,
            )
        } catch (securityException: SecurityException) {
            Timber.tag("MT-Service").w(
                securityException,
                "onStartCommand: SecurityException promoting to foreground — location permission " +
                    "absent. Stopping service (C-1 graceful limited mode).",
            )
            stopSelf()
            return START_NOT_STICKY
        } catch (runtimeException: RuntimeException) {
            // Catches InvalidForegroundServiceTypeException on API 34+ (subclass of RuntimeException)
            // when the location permission is absent.
            Timber.tag("MT-Service").w(
                runtimeException,
                "onStartCommand: RuntimeException promoting to foreground — likely location " +
                    "permission absent on API 34+. Stopping service (C-1 graceful limited mode).",
            )
            stopSelf()
            return START_NOT_STICKY
        }

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

    /**
     * Safely executes a teardown block, catching and logging any exception without propagating it.
     * Used in [onDestroy] to ensure all cleanup steps complete even if one fails.
     */
    private inline fun safeTeardown(label: String, block: () -> Unit) {
        runCatching {
            block()
        }.onFailure { teardownException ->
            Timber.tag("MT-Service").e(teardownException, "onDestroy step failed: %s", label)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        safeTeardown("removeLocationUpdates") {
            fusedLocationProviderClient.removeLocationUpdates(tripLocationCallback)
        }
        safeTeardown("activityRecognitionRegistrar.unregister") {
            activityRecognitionRegistrar.unregister()
        }
        safeTeardown("confidenceAcquisitionWindow.cancel") {
            confidenceAcquisitionWindow.cancel()
        }
        safeTeardown("classificationPromptTimeoutScheduler.cancel") {
            classificationPromptTimeoutScheduler.cancel()
        }
        safeTeardown("inactivityTimerJob.cancel") {
            inactivityTimerJob?.cancel()
        }
        safeTeardown("unstableSignalTimerJob.cancel") {
            unstableSignalTimerJob?.cancel()
        }
        safeTeardown("serviceJob.cancel") {
            serviceJob.cancel()
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
        // H-1/H-2 fix: flag this as a manual start so completeTripStart can persist it on the trip.
        pendingTripIsManualStart = true

        completeTripStart(startedAtEpochMillis)
    }

    /**
     * Automatic-start path (T-002): handles a terminal [TripStartEvent] emitted by
     * [confidenceAcquisitionWindow]'s [ConfidenceAcquisitionWindow.observeResults] flow —
     * [TripStartEvent.ConfidentVehicleEntry] or [TripStartEvent.LowConfidenceRetryExhausted].
     * Mirrors [handleStartTripRequested] exactly (same no-op guard, same state-machine call, same
     * [completeTripStart] tail) but is entered from the detection pipeline rather than a manual
     * [ACTION_START_TRIP] intent, so it acquires [tripLifecycleMutex] itself rather than relying on
     * [onStartCommand]'s existing critical section.
     */
    private suspend fun handleAutomaticStartEvent(startEvent: TripStartEvent) {
        try {
            tripLifecycleMutex.withLock {
                // A confidence result can arrive after a trip has already started by some other
                // path (manual start, or a second ENTER/result racing this one) — same no-op
                // guard as handleStartTripRequested, re-checked here because this path is not
                // gated by onStartCommand's own pre-action recovery check.
                if (activeTripId != null) return@withLock
                if (tripRepository.getInProgressTrip() != null) return@withLock

                val startedAtEpochMillis = System.currentTimeMillis()

                transientPhase = tripLifecycleStateMachine.onStartEvent(
                    currentPhase = transientPhase,
                    event = startEvent,
                )
                // H-1/H-2 fix: automatic detection path — explicit false so the stored trip is
                // not mislabelled as manual.
                pendingTripIsManualStart = false

                completeTripStart(startedAtEpochMillis)
            }
        } catch (cancellationException: CancellationException) {
            throw cancellationException
        } catch (unexpectedException: Exception) {
            // Per T-002 spec §6 / item 5: one bad confidence-window result must not kill the
            // collector for the rest of the service's life. Logged and skipped; the next ENTER
            // event still gets its own confidence-acquisition window and its own chance to start
            // a trip.
            Timber.tag("MT-Service").e(
                unexpectedException,
                "handleAutomaticStartEvent failed for event=%s",
                startEvent,
            )
        }
    }

    /**
     * Shared tail for every start path (manual and automatic) once the state machine has already
     * resolved [transientPhase] to [TripLifecycleStateMachine.TransientPhase.PromptPending] for
     * the in-flight [TripStartEvent]. Resolves that phase into an active trip, performs the single
     * `insertNewActiveTrip` call site (blueprint §2 anti-duplication guarantee), and starts GPS +
     * stop timers. Caller must hold [tripLifecycleMutex].
     */
    private suspend fun completeTripStart(startedAtEpochMillis: Long) {
        val tripId = UUID.randomUUID().toString()
        val photoRetention = settingsRepository.observePhotoRetentionMode().first()
        // H-1/H-2 fix: snapshot and reset the flag atomically within this critical section
        // (caller holds tripLifecycleMutex) so a concurrent start path cannot read a stale value.
        val tripIsManualStart = pendingTripIsManualStart
        pendingTripIsManualStart = false

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
        activeTripIsManualStart = tripIsManualStart
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
                tripSequenceNumber = 0,
                isManualStart = tripIsManualStart,
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

        // T-003: single shared notify() call site for every stop path (manual, 3-min
        // ConfirmedInactivity, 2-min UnstableSignalTimeout) — all three land here via this one
        // `handleStopEvent` tail, so the notification is never duplicated per-path. Every stop
        // today always resolves to PENDING_OCR (see the state-machine comment above), so this
        // fires unconditionally rather than re-deciding when to notify.
        // H-2 fix: pass the active trip's manual-start flag so the notification title is correct.
        notifyTripAwaitingClassification(tripId, isManualStart = activeTripIsManualStart)

        // T-039 item 9: start the 30s classification-prompt countdown the moment the prompt is
        // actually posted. The trip itself is already safe — resolvedStatus was written to Room
        // above, before this line — so this timer's only job is the notification, never the data.
        classificationPromptTimeoutScheduler.startTimeoutFor(tripId)

        activeTripId = null
        activeTripIsManualStart = false
        accumulatedDistanceMeters = 0.0
        tripLocationCallback.reset()

        stopSelf()
    }

    /**
     * Posts the trip-classification prompt notification (brief §5.2) for [tripId] using a
     * deterministic notification id — `tripId.hashCode()`, the same convention
     * [TripClassificationNotificationBuilder] already uses for its `PendingIntent` request code —
     * so re-stopping the same trip (should that ever happen, e.g. a future retry path) or restart
     * recovery re-running this code path never stacks a second, duplicate notification for a trip
     * that already has one showing; `notify()` with the same id simply replaces it.
     */
    /**
     * H-2 fix: [isManualStart] is forwarded to the notification builder so the title reads
     * "Trip recorded" for manual starts and "Trip detected" for automatic detection.
     */
    private fun notifyTripAwaitingClassification(tripId: String, isManualStart: Boolean) {
        val notificationManager = ContextCompat.getSystemService(this, NotificationManager::class.java)
        if (notificationManager == null) {
            Timber.tag("MT-Service").e(
                "notifyTripAwaitingClassification: NotificationManager unavailable, tripId=%s",
                tripId,
            )
            return
        }
        Timber.tag("MT-Trip").i(
            "TripTrackingForegroundService: trip stopped, posting classification notification " +
                "tripId=%s isManualStart=%s",
            tripId,
            isManualStart,
        )
        notificationManager.notify(
            tripId.hashCode(),
            tripClassificationNotificationBuilder.build(tripId, isManualStart = isManualStart),
        )
    }

    /**
     * T-039 item 9: fired by [classificationPromptTimeoutScheduler] exactly
     * [com.mileagetracker.app.service.notification.CLASSIFICATION_PROMPT_TIMEOUT_MILLIS]
     * (30s) after a prompt was posted with no response. The trip was already safely persisted to
     * `PENDING_OCR` at stop time — this never re-touches `Trip.status` and never assigns a
     * default classification. Its only job is to re-post the SAME notification id
     * (`tripId.hashCode()`, replacing the original prompt in place, not stacking a second one) as
     * the persistent "needs classification" reminder variant (`isOverdueReminder = true` —
     * ongoing, non-swipe-dismissable, per the locked ruling).
     *
     * Re-checks the trip's current status first: if the user already classified it inside the
     * 30s window (a race between the timer firing and the user's save landing), the trip is no
     * longer PENDING_OCR and this is a silent no-op — the reminder must never resurrect a
     * notification for a trip the user already finished.
     */
    private suspend fun handlePromptTimeout(tripId: String) {
        try {
            val trip = tripRepository.getTripById(tripId)
            if (trip == null || trip.status != TripStatus.PENDING_OCR) {
                Timber.tag("MT-Service").d(
                    "handlePromptTimeout: tripId=%s no longer PENDING_OCR (status=%s) — skipping reminder",
                    tripId,
                    trip?.status,
                )
                return
            }

            val notificationManager = ContextCompat.getSystemService(this, NotificationManager::class.java)
            if (notificationManager == null) {
                Timber.tag("MT-Service").e(
                    "handlePromptTimeout: NotificationManager unavailable, tripId=%s",
                    tripId,
                )
                return
            }
            Timber.tag("MT-Trip").i(
                "TripTrackingForegroundService: classification prompt timed out, posting persistent reminder tripId=%s",
                tripId,
            )
            notificationManager.notify(
                tripId.hashCode(),
                tripClassificationNotificationBuilder.build(
                    tripId,
                    isManualStart = trip.isManualStart,
                    isOverdueReminder = true,
                ),
            )
        } catch (cancellationException: CancellationException) {
            throw cancellationException
        } catch (unexpectedException: Exception) {
            Timber.tag("MT-Service").e(
                unexpectedException,
                "handlePromptTimeout failed for tripId=%s",
                tripId,
            )
        }
    }

    private fun buildPersistentNotification() =
        NotificationCompat.Builder(this, TripAlertNotificationChannel.CHANNEL_ID)
            .setContentTitle("Mileage Tracker is running")
            .setContentText(if (activeTripId != null) "Tracking a trip" else "Watching for trip activity")
            .setSmallIcon(R.drawable.ic_notification_trip)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setOngoing(true)
            .build()

    /**
     * C-1 fix: fallback notification used when location permission is absent. Informs the user
     * that the app is running in limited mode (brief §locked "graceful limited mode when
     * permissions denied"). No location foregroundServiceType is requested, so no SecurityException
     * is thrown before the Setup screen has collected permissions.
     */
    private fun buildLimitedModePersistentNotification() =
        NotificationCompat.Builder(this, TripAlertNotificationChannel.CHANNEL_ID)
            .setContentTitle("Mileage Tracker — limited mode")
            .setContentText("Location permission not granted — open the app to grant access")
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
