package com.mileagetracker.app.domain.statemachine

import com.mileagetracker.app.domain.model.TripClassification
import com.mileagetracker.app.domain.model.TripStatus

/**
 * Pure transition logic for a single trip's lifecycle (T-001 blueprint §4). This class does NO
 * I/O — no Room, no notification posting, no ActivityRecognition calls. Callers (the foreground
 * service, primarily) feed it events and persisted/transient state; it returns what the new
 * state should be. This keeps the highest-risk logic in the app (brief §13: false starts/stops)
 * unit-testable on the plain JVM with zero Android/Robolectric dependency.
 *
 * Two layers, per the blueprint — do not blur them:
 * - [TransientPhase] values are never written to `Trip.status`.
 * - [TripStatus] values are the only thing the repository persists.
 *
 * This is a skeleton for T-001: the exact method bodies (confidence-threshold comparisons,
 * 30s/3min/2min timer wiring) are implemented in T-002 (vehicle detection) and T-004 (GPS/stop
 * detection), coordinated with geo-sensors-specialist. T-001 establishes the shape only — the
 * sealed types, the entry points, and the one documented decision below (pending_ocr resolution)
 * so later sessions implement against a fixed contract, not an evolving one.
 */
class TripLifecycleStateMachine {

    /** Transient, in-memory-only phases that precede a persisted `active` trip. */
    sealed interface TransientPhase {
        data object NoTrip : TransientPhase
        data class SilentRetry(val lastConfidencePercent: Int, val retryStartedAtEpochMillis: Long) : TransientPhase
        data class PromptPending(val confidencePercent: Int, val isForcedLowConfidence: Boolean) : TransientPhase
    }

    /**
     * Handles a start-side event while no trip is in progress (or while in [TransientPhase.SilentRetry]).
     * Returns the next [TransientPhase]; the caller is responsible for calling
     * [resolvePromptPendingIntoActiveTrip] once the classification notification has actually been
     * shown (per blueprint §4, `insertTrip()` happens at that exact moment, not before).
     */
    fun onStartEvent(currentPhase: TransientPhase, event: TripStartEvent): TransientPhase {
        return when (event) {
            is TripStartEvent.ConfidentVehicleEntry ->
                TransientPhase.PromptPending(event.confidencePercent, isForcedLowConfidence = false)

            is TripStartEvent.LowConfidenceRetryExhausted ->
                TransientPhase.PromptPending(event.lastObservedConfidencePercent, isForcedLowConfidence = true)

            is TripStartEvent.ManualStart ->
                TransientPhase.PromptPending(confidencePercent = 100, isForcedLowConfidence = false)
        }
    }

    /**
     * The single call site that creates a new persisted trip (blueprint §2's anti-duplication
     * guarantee: exactly one insert point, gated by the caller having already confirmed
     * `getInProgressTrip()` returned null). [tripId] must be a UUIDv4 generated once by the
     * caller — never regenerated, never timestamp-derived.
     */
    fun resolvePromptPendingIntoActiveTrip(tripId: String, startedAtEpochMillis: Long): TripStatus {
        return TripStatus.ACTIVE
    }

    /**
     * Handles a stop-side event for a trip currently `active`. Per blueprint §4's "completion is
     * two-part" decision, every stop event lands on PENDING_OCR first — never directly on
     * COMPLETED or PENDING_BUSINESS_REASON. Those are only reachable via
     * [resolvePendingOcrAfterOdometerConfirmed] once the odometer step resolves.
     */
    fun onStopEvent(event: TripStopEvent): TripStatus {
        return TripStatus.PENDING_OCR
    }

    /**
     * Resolves PENDING_OCR once the user has accepted an OCR result (>=80% confidence) or
     * submitted a manual odometer value (blueprint §4's "completion is two-part" decision).
     * PENDING_BUSINESS_REASON takes priority for display purposes when both conditions apply
     * (Work trip + blank reason) — it is the actionable blocker, OCR is not.
     */
    fun resolvePendingOcrAfterOdometerConfirmed(
        classification: TripClassification,
        businessReason: String?,
    ): TripStatus {
        val isWorkTripMissingReason = classification == TripClassification.WORK && businessReason.isNullOrBlank()
        return if (isWorkTripMissingReason) TripStatus.PENDING_BUSINESS_REASON else TripStatus.COMPLETED
    }

    /**
     * Resolves PENDING_BUSINESS_REASON once the user submits a non-blank reason. The non-empty
     * check itself lives in [com.mileagetracker.app.domain.classification.ClassificationRules] —
     * this method assumes that gate already passed.
     */
    fun resolvePendingBusinessReasonAfterReasonSubmitted(): TripStatus = TripStatus.COMPLETED
}
