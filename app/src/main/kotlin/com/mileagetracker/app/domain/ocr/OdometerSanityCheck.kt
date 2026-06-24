package com.mileagetracker.app.domain.ocr

/**
 * T-005.4: applies the trip-level sanity check to a scored OCR candidate and decides the final
 * [OdometerOcrResult]. Extracted out of [com.mileagetracker.app.data.ocr.MlKitOdometerOcrClient]
 * into its own pure, plain-JVM, Android-free function so this real decision logic — not just the
 * geometry scoring in [OdometerCandidateScorer] — is unit-testable without Robolectric, following
 * the same extraction pattern as [com.mileagetracker.app.domain.location.GpsAnchorTracker] (T-004)
 * and [com.mileagetracker.app.domain.statemachine.TripLifecycleStateMachine] (T-001).
 *
 * Design choice (documented per this project's convention of making forks visible, see T-003's
 * notification-timing fork and T-004's distance-write divergence): this check lives in the OCR
 * client/domain layer, not in [com.mileagetracker.app.ui.odometer.OdometerCaptureViewModel].
 * Reasoning: [OdometerOcrResult.Confident] is meant to be an unconditional guarantee — "every
 * validation this domain owns has already passed" — so that no current or future consumer of
 * [OdometerOcrResult.Confident] has to re-derive plausibility checks after the fact. Putting the
 * check in the ViewModel instead would have meant a smaller diff (no [OdometerOcrClient] interface
 * change), but it would leave [OdometerOcrResult.Confident] an unsafe type to trust blindly,
 * which directly contradicts this project's "never trust raw OCR output" rule.
 */
object OdometerSanityCheck {

    /**
     * Decides the final [OdometerOcrResult] for [bestCandidate] given the trip's
     * [startOdometerKm]. A candidate that would otherwise be confident, but whose value is below
     * [startOdometerKm] (an odometer cannot decrease over the course of a trip), is rejected and
     * demoted to [OdometerOcrResult.LowConfidence] — carrying its real computed score forward, not
     * a placeholder — so manual-entry fallback can still show it as a hint while refusing to
     * auto-accept it.
     */
    fun toOcrResult(bestCandidate: OdometerCandidateScorer.ScoredCandidate, startOdometerKm: Double): OdometerOcrResult {
        val isConfident = bestCandidate.confidencePercent >= OdometerCandidateScorer.CONFIDENT_THRESHOLD_PERCENT
        val failsSanityCheck = bestCandidate.valueKm < startOdometerKm

        return when {
            isConfident && failsSanityCheck ->
                OdometerOcrResult.LowConfidence(bestCandidate.valueKm, bestCandidate.confidencePercent)
            isConfident ->
                OdometerOcrResult.Confident(bestCandidate.valueKm, bestCandidate.confidencePercent)
            else ->
                OdometerOcrResult.LowConfidence(bestCandidate.valueKm, bestCandidate.confidencePercent)
        }
    }
}
