package com.mileagetracker.app.service.notification

import com.mileagetracker.app.service.di.ClassificationPromptTimeoutScope
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject

/**
 * Locked v1 value (brief L132, developer_handoff_brief.md): the classification-prompt timeout.
 *
 * T-039 item 9 ruling: 30s with NO response is a SAFE AUTO-FINALIZE, never a loss and never a
 * silent default classification — the trip is already persisted to
 * `TripStatus.PENDING_OCR` (a Room write) the instant the trip stops, *before* the
 * classification notification is ever posted (see
 * `TripTrackingForegroundService.handleStopEvent`). This timer's only job is to flip the
 * notification into a persistent "needs classification" reminder once 30s elapses with no
 * response, satisfying brief L135 ("keep the prompt visible long enough... without losing the
 * trip") without re-litigating L132's 30s figure.
 */
internal const val CLASSIFICATION_PROMPT_TIMEOUT_MILLIS = 30_000L

/**
 * Fires exactly one [PromptTimedOut] event [CLASSIFICATION_PROMPT_TIMEOUT_MILLIS] after
 * [startTimeoutFor] is called for a given [tripId], unless [cancelFor] is called first (the user
 * responded — opened the classification screen and saved). Mirrors
 * [com.mileagetracker.app.service.activityrecognition.ConfidenceAcquisitionWindowImpl]'s
 * injected-[CoroutineScope] + single-result-[Flow] shape so this class is unit-testable with
 * `TestScope`/`advanceTimeBy`, exactly like that class's own test suite — no real device, no
 * Robolectric, no dependency on the (untestable) `Service` base class.
 *
 * One trip tracked at a time, matching [TripTrackingForegroundService]'s own single-active-trip
 * invariant — starting a new timeout while one is already running for a *different* tripId
 * cancels the previous one first (defensive; the service in practice only ever has one trip
 * in flight, so this should never actually race in production).
 */
class ClassificationPromptTimeoutScheduler @Inject constructor(
    @ClassificationPromptTimeoutScope private val coroutineScope: CoroutineScope,
) {

    data class PromptTimedOut(val tripId: String)

    @Volatile private var timeoutJob: Job? = null
    @Volatile private var pendingTripId: String? = null
    private val resultFlow = MutableSharedFlow<PromptTimedOut>(extraBufferCapacity = 1)

    /** Starts (or restarts, for a different tripId) the 30s countdown for [tripId]. */
    fun startTimeoutFor(tripId: String) {
        if (pendingTripId == tripId && timeoutJob?.isActive == true) {
            Timber.tag("MT-Service").d("startTimeoutFor called again for the same tripId=%s — no-op", tripId)
            return
        }
        timeoutJob?.cancel()
        pendingTripId = tripId

        timeoutJob = coroutineScope.launch {
            delay(CLASSIFICATION_PROMPT_TIMEOUT_MILLIS)
            Timber.tag("MT-Service").i("Classification prompt timed out at %dms tripId=%s", CLASSIFICATION_PROMPT_TIMEOUT_MILLIS, tripId)
            resultFlow.tryEmit(PromptTimedOut(tripId))
        }
    }

    /** Cancels the in-flight countdown for [tripId] — call when the user responds before timeout. No-op for any other tripId. */
    fun cancelFor(tripId: String) {
        if (pendingTripId != tripId) return
        timeoutJob?.cancel()
        timeoutJob = null
        pendingTripId = null
    }

    /** Cancels any in-flight countdown unconditionally (service teardown). */
    fun cancel() {
        timeoutJob?.cancel()
        timeoutJob = null
        pendingTripId = null
    }

    fun observeTimeouts(): Flow<PromptTimedOut> = resultFlow
}
