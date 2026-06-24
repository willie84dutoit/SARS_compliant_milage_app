package com.mileagetracker.app.domain.location

/**
 * Pure decision logic for T-004.2/T-004.3's anchor-tracking + 8.0m noise-floor filter — extracted
 * out of [com.mileagetracker.app.service.location.TripLocationCallback] specifically to make this
 * real, non-trivial, shipped behavior unit-testable on the plain JVM (no `android.location.Location`,
 * no Android imports), following the same extraction pattern as
 * [com.mileagetracker.app.ui.setup.SetupPermissionsPlanner].
 *
 * `android.location.Location` cannot be safely constructed/read in this project's `app/src/test`
 * unit tests: there is no `testOptions { unitTests { isReturnDefaultValues = true } }` configured
 * in `app/build.gradle.kts`, and this project has no Robolectric dependency, so an unmocked
 * `Location` getter throws `RuntimeException("Method ... not mocked")` at test time rather than
 * returning a usable value. [TripLocationCallback] therefore extracts its lat/lng `Double`s from
 * the `Location` object at the call site and hands plain doubles to this class — the only place
 * the anchor/noise-floor/exception-safety *decision* actually lives.
 *
 * This class owns the anchor itself (mutable internal state, intentionally not a pure function of
 * its arguments) so that callers don't have to thread anchor state through every call — mirroring
 * the stateful-but-isolated shape [TripLocationCallback] had before extraction.
 */
class GpsAnchorTracker(
    private val haversineDistanceCalculator: (Double, Double, Double, Double) -> Double =
        HaversineDistanceCalculator::distanceInMeters,
) {

    /** The last fix this tracker *accepted* (i.e. moved the anchor) — null until the first fix arrives. */
    private var lastAcceptedLatitude: Double? = null
    private var lastAcceptedLongitude: Double? = null

    /**
     * Evaluates one incoming fix against the current anchor and returns what happened. Mutates
     * the internal anchor as a side effect when the fix is the first fix of the trip or clears the
     * noise floor; leaves the anchor untouched on [Outcome.Discarded] or [Outcome.ComputationFailed].
     */
    fun evaluateFix(latitude: Double, longitude: Double): Outcome {
        val previousLatitude = lastAcceptedLatitude
        val previousLongitude = lastAcceptedLongitude

        if (previousLatitude == null || previousLongitude == null) {
            // First fix of the trip: anchor immediately, no distance to add yet.
            lastAcceptedLatitude = latitude
            lastAcceptedLongitude = longitude
            return Outcome.FirstFix(latitude = latitude, longitude = longitude)
        }

        val deltaMeters = try {
            haversineDistanceCalculator(previousLatitude, previousLongitude, latitude, longitude)
        } catch (haversineComputationFailure: Exception) {
            // Do not update lastAcceptedLatitude/lastAcceptedLongitude on failure; next fix will be
            // compared against the last known-good point.
            return Outcome.ComputationFailed(cause = haversineComputationFailure)
        }

        if (deltaMeters < GPS_NOISE_FLOOR_METERS) {
            // T-004.3 (locked): below the 8.0m noise floor — discard, no distance added, anchor
            // unchanged, inactivity timer not reset.
            return Outcome.Discarded(deltaMeters = deltaMeters)
        }

        lastAcceptedLatitude = latitude
        lastAcceptedLongitude = longitude
        return Outcome.AcceptedMovement(
            deltaMeters = deltaMeters,
            latestLatitude = latitude,
            latestLongitude = longitude,
        )
    }

    /** Resets the anchor so the next fix is always treated as a first fix (e.g. on a new trip). */
    fun reset() {
        lastAcceptedLatitude = null
        lastAcceptedLongitude = null
    }

    sealed interface Outcome {
        /** The fix that just anchored the trip — zero distance added. */
        data class FirstFix(val latitude: Double, val longitude: Double) : Outcome

        /** The fix cleared the [GPS_NOISE_FLOOR_METERS] threshold and moved the anchor. */
        data class AcceptedMovement(
            val deltaMeters: Double,
            val latestLatitude: Double,
            val latestLongitude: Double,
        ) : Outcome

        /** The fix was below the noise floor — anchor unchanged. */
        data class Discarded(val deltaMeters: Double) : Outcome

        /** The Haversine computation threw — anchor unchanged, caller must log and continue. */
        data class ComputationFailed(val cause: Exception) : Outcome
    }

    companion object {
        /** T-004.3 locked value: strictly below the 10m provider filter, above stationary GPS drift. */
        const val GPS_NOISE_FLOOR_METERS = 8.0
    }
}
